package com.lanting.admin.module.publish.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lanting.admin.common.exception.BusinessException;
import com.lanting.admin.common.page.PageQuery;
import com.lanting.admin.common.page.PageResult;
import com.lanting.admin.common.util.IdGeneratorUtils;
import com.lanting.admin.module.file.entity.FileIndexEntity;
import com.lanting.admin.module.file.result.FileResultCode;
import com.lanting.admin.module.file.service.FileIndexService;
import com.lanting.admin.module.file.service.GitFileService;
import com.lanting.admin.module.file.vo.UncommitVO;
import com.lanting.admin.module.publish.entity.PublishEntity;
import com.lanting.admin.module.publish.entity.PublishFileEntity;
import com.lanting.admin.module.publish.entity.ReviewEntity;
import com.lanting.admin.module.publish.mapper.PublishFileMapper;
import com.lanting.admin.module.publish.mapper.PublishMapper;
import com.lanting.admin.module.publish.result.PublishResultCode;
import com.lanting.admin.module.publish.vo.CommitVO;
import com.lanting.admin.module.publish.vo.PublishVO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static com.lanting.admin.common.util.SecurityUtils.currentUser;
import static com.lanting.admin.module.publish.entity.PublishFileEntity.*;

/**
 * 发布服务（v2）。
 * 负责 git commit + 待发布池管理 + 发布批次生命周期。
 *
 * @author wangzhao
 */
@Service
public class PublishService {

    private final PublishMapper publishMapper;
    private final PublishFileMapper publishFileMapper;
    private final ReviewService reviewService;
    private final FileIndexService fileIndexService;
    private final GitFileService gitFileService;

    public final static String ONLY_COMMITTED = "OnlyCommitted";
    public final static String ONLY_CANCELED = "OnlyCanceled";
    public final static String ONLY_PUBLISHED = "OnlyPublished";
    public final static String EXCLUDE_PUBLISHED = "ExcludePublished";

    public PublishService(PublishMapper publishMapper, PublishFileMapper publishFileMapper,
                          ReviewService reviewService, FileIndexService fileIndexService,
                          GitFileService gitFileService) {
        this.publishMapper = publishMapper;
        this.publishFileMapper = publishFileMapper;
        this.reviewService = reviewService;
        this.fileIndexService = fileIndexService;
        this.gitFileService = gitFileService;
    }

    /**
     * 通过 fileIds + status 获取 PublishFileEntity 列表
     */
    public List<PublishFileEntity> listPublishFileByIds(List<Long> fileIds, String statusQueryCondition) {
        if (fileIds == null || fileIds.isEmpty()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<PublishFileEntity> wrapper = new LambdaQueryWrapper<PublishFileEntity>()
                .in(PublishFileEntity::getFileId, fileIds);

        switch (statusQueryCondition) {
            case ONLY_COMMITTED:
                wrapper.eq(PublishFileEntity::getStatus, STATUS_COMMITTED);
                break;
            case ONLY_CANCELED:
                wrapper.eq(PublishFileEntity::getStatus, STATUS_CANCELED);
                break;
            case ONLY_PUBLISHED:
                wrapper.eq(PublishFileEntity::getStatus, STATUS_PUBLISHED);
                break;
            case EXCLUDE_PUBLISHED:
                wrapper.ne(PublishFileEntity::getStatus, STATUS_PUBLISHED);
                break;
            default:
                String[] conditions = new String[]{STATUS_COMMITTED, STATUS_CANCELED, STATUS_PUBLISHED};
                throw new IllegalArgumentException("未知 Status Query Condition '" + statusQueryCondition + "'，只允许 " + Arrays.toString(conditions));
        }

        return publishFileMapper.selectList(wrapper);
    }

    /**
     * 根据 PublishID 查具体的发布文件详情
     */
    private List<PublishFileEntity> listPublishFileByPublishId(String publishId) {
        return publishFileMapper.selectList(new LambdaQueryWrapper<PublishFileEntity>()
                .eq(PublishFileEntity::getPublishId, publishId));
    }

    /**
     * 分页查询待发布候选列表（含动态 CR 状态：匹配文件当前 latest_commit_hash 的最新评审结果）。
     */
    public PageResult<CommitVO> listCommittedPages(PageQuery query) {
        Page<PublishFileEntity> mpPage = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<PublishFileEntity> wrapper = new LambdaQueryWrapper<PublishFileEntity>()
                .eq(PublishFileEntity::getStatus, STATUS_COMMITTED)
                .orderByDesc(PublishFileEntity::getUpdateTime);
        publishFileMapper.selectPage(mpPage, wrapper);

        List<Long> fileIds = mpPage.getRecords().stream()
                .map(PublishFileEntity::getFileId).collect(Collectors.toList());
        Map<Long, FileIndexEntity> indexMap = fileIds.isEmpty() ? Map.of()
                : fileIndexService.listByIds(fileIds).stream()
                .collect(Collectors.toMap(FileIndexEntity::getId, e -> e));

        return PageResult.of(mpPage, e -> {
            FileIndexEntity idx = indexMap.get(e.getFileId());
            String latestHash = idx != null ? idx.getLatestCommitHash() : null;
            ReviewEntity reviewEntity = latestHash == null ? null : reviewService.latestReview(e.getFileId(), latestHash);

            CommitVO vo = new CommitVO();
            vo.setFileId(e.getFileId());
            vo.setName(idx != null ? idx.getName() : null);
            vo.setCommitHash(latestHash);
            vo.setStatus(reviewEntity != null ? reviewEntity.getResult() : null);
            return vo;
        });
    }

    /**
     * 已发布批次列表
     */
    public PageResult<PublishVO> listPublished(PageQuery query, String keyword) {
        Page<PublishEntity> mpPage = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<PublishEntity> wrapper = new LambdaQueryWrapper<PublishEntity>()
                .orderByDesc(PublishEntity::getCreateTime);
        if (!StringUtils.isEmpty(keyword)) {
            wrapper.like(PublishEntity::getDisplayName, keyword);
        }
        publishMapper.selectPage(mpPage, wrapper);
        return PageResult.of(mpPage, PublishVO::of);
    }

    /**
     * 通过 fileId 获取单个发布文件，根据 Status 过滤
     */
    public PublishFileEntity getPublishFileByFileId(Long fileId, String status) {
        return publishFileMapper.selectOne(new LambdaQueryWrapper<PublishFileEntity>()
                .eq(PublishFileEntity::getFileId, fileId)
                .eq(PublishFileEntity::getStatus, status));
    }

    /**
     * 提交并加入待发布池（原子操作）。
     * 无变更 → 抛异常；有变更 → git commit → 已池中则更新，否则新增。
     */
    @Transactional(rollbackFor = Exception.class)
    public void addCommittedList(List<Long> fileIds, String message) {
        // 1. 无变更直接拒绝
        UncommitVO uv = gitFileService.uncommit(fileIds);
        if (uv.isEmpty()) {
            throw new BusinessException(PublishResultCode.NOTHING_TO_COMMIT);
        }

        // 2. git commit
        gitFileService.commit(fileIds, message);

        // 3. 已池中 → 更新；未池中 → 新增
        String currentUser = currentUser();
        List<PublishFileEntity> existing = listPublishFileByIds(fileIds, ONLY_COMMITTED);
        Set<Long> existingFileIds = existing.stream()
                .map(PublishFileEntity::getFileId).collect(Collectors.toSet());
        Map<Long, String> fileNames = fileIndexService.listByIds(fileIds).stream()
                .collect(Collectors.toMap(FileIndexEntity::getId, FileIndexEntity::getName));

        for (Long fileId : fileIds) {
            if (existingFileIds.contains(fileId)) {
                publishFileMapper.update(new LambdaUpdateWrapper<PublishFileEntity>()
                        .set(PublishFileEntity::getUpdatedBy, currentUser)
                        .set(PublishFileEntity::getFileName, fileNames.get(fileId))
                        .set(PublishFileEntity::getUpdateTime, System.currentTimeMillis())
                        .eq(PublishFileEntity::getFileId, fileId)
                        .eq(PublishFileEntity::getStatus, STATUS_COMMITTED));
            } else {
                PublishFileEntity entity = new PublishFileEntity();
                entity.setPublishId("");
                entity.setFileId(fileId);
                entity.setFileName(fileNames.get(fileId));
                entity.setCommitHash("");
                entity.setStatus(STATUS_COMMITTED);
                entity.setCreatedBy(currentUser);
                entity.setUpdatedBy(currentUser);
                publishFileMapper.insert(entity);
            }
        }
    }

    /**
     * 取消发布：COMMITTED 行转为 CANCELED（仅影响未发布文件）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelPublish(List<Long> fileIds) {
        publishFileMapper.update(new LambdaUpdateWrapper<PublishFileEntity>()
                .set(PublishFileEntity::getStatus, STATUS_CANCELED)
                .set(PublishFileEntity::getUpdatedBy, currentUser())
                .in(PublishFileEntity::getFileId, fileIds)
                .eq(PublishFileEntity::getStatus, STATUS_COMMITTED));
    }

    /**
     * 发布线上，发布只与 commit 绑定
     */
    @Transactional(rollbackFor = Exception.class)
    public PublishVO publish(List<Long> fileIds, String displayName) {
        if (fileIds == null || fileIds.isEmpty()) {
            throw new BusinessException(PublishResultCode.EMPTY_PUBLISH);
        }
        String currentUser = currentUser();

        // 新增 publish 记录
        PublishEntity publish = new PublishEntity();
        publish.setId(IdGeneratorUtils.uuid());
        publish.setDisplayName(displayName);
        publish.setPublishedBy(currentUser);
        long now = System.currentTimeMillis();
        publish.setCreateTime(now);
        publish.setUpdateTime(now);
        publishMapper.insert(publish);

        List<PublishVO.PublishFileItem> itemList = new ArrayList<>();
        for (Long fileId : fileIds) {
            FileIndexEntity file = fileIndexService.getById(fileId);
            if (file == null) {
                throw new BusinessException(FileResultCode.FILE_NOT_FOUND, "文件不存在，fileId：" + fileId);
            }
            if (StringUtils.isEmpty(file.getLatestCommitHash())) {
                throw new BusinessException(PublishResultCode.FILE_NOT_COMMITTED, "文件未提交，没有找到文件最新一次提交的 Commit Hash，fileId：" + fileId);
            }

            PublishFileEntity publishFile = getPublishFileByFileId(fileId, STATUS_COMMITTED);
            if (publishFile == null) {
                throw new BusinessException(PublishResultCode.FILE_NOT_COMMITTED, "文件没有在待发布列表中，fileId：" + fileId);
            }

            publishFile.setStatus(STATUS_PUBLISHED);
            publishFile.setPublishId(publish.getId());
            publishFile.setCommitHash(file.getLatestCommitHash());
            publishFile.setUpdatedBy(currentUser);
            publishFileMapper.updateById(publishFile);
            itemList.add(PublishVO.PublishFileItem.of(publishFile));
        }

        return PublishVO.of(publish, itemList);
    }


    /**
     * 发布批次详情（含文件清单及各自 commit SHA）。
     */
    public PublishVO getPublish(String publishId) {
        PublishEntity entity = publishMapper.selectById(publishId);
        if (entity == null) {
            throw new BusinessException(PublishResultCode.PUBLISH_NOT_FOUND);
        }
        List<PublishFileEntity> files = listPublishFileByPublishId(publishId);
        List<PublishVO.PublishFileItem> itemList = files.stream()
                .map(PublishVO.PublishFileItem::of)
                .toList();

        return PublishVO.of(entity, itemList);
    }

    /**
     * 最近一次发布该文件时的快照记录。
     */
    private PublishFileEntity getLatestPublishFileByFileId(Long fileId) {
        return publishFileMapper.selectOne(new LambdaQueryWrapper<PublishFileEntity>()
                .eq(PublishFileEntity::getFileId, fileId)
                .eq(PublishFileEntity::getStatus, STATUS_PUBLISHED)
                .orderByDesc(PublishFileEntity::getUpdateTime));
    }

    /**
     * 发布场景 diff：当前版本 vs 上次发布版本的 unified diff 文本。
     * 从未发布过的文件 from 传 null，JGit EmptyTreeIterator 处理空树 → 全量新增。
     */
    public String diff(Long fileId) {
        FileIndexEntity file = fileIndexService.getById(fileId);
        if (file == null) {
            throw new BusinessException(FileResultCode.FILE_NOT_FOUND);
        }
        if (StringUtils.isEmpty(file.getLatestCommitHash())) {
            throw new BusinessException(PublishResultCode.FILE_NOT_COMMITTED);
        }

        PublishFileEntity lastPublished = getLatestPublishFileByFileId(fileId);
        String from = lastPublished != null ? lastPublished.getCommitHash() : null;

        return gitFileService.diff(fileId, from, file.getLatestCommitHash());
    }
}
