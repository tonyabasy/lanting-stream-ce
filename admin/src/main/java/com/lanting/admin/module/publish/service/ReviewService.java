package com.lanting.admin.module.publish.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lanting.admin.common.exception.BusinessException;
import com.lanting.admin.module.file.entity.FileIndexEntity;
import com.lanting.admin.module.file.service.FileIndexService;
import com.lanting.admin.module.publish.entity.ReviewEntity;
import com.lanting.admin.module.publish.mapper.ReviewMapper;
import com.lanting.admin.module.file.result.FileResultCode;
import com.lanting.admin.module.publish.result.PublishResultCode;
import com.lanting.admin.module.publish.vo.ReviewVO;
import org.springframework.stereotype.Service;
import org.apache.commons.lang3.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import static com.lanting.admin.common.util.SecurityUtils.currentUser;

/**
 * CR 评审服务（v2）。评审绑定 (file_id, commit_hash)，写入时强校验 commitHash
 * 与文件当前 latest_commit_hash 一致，防止“对着旧代码写的意见错挂到新 commit”。
 *
 * @author wangzhao
 */
@Service
public class ReviewService {

    private final ReviewMapper reviewMapper;
    private final FileIndexService fileIndexService;

    public ReviewService(ReviewMapper reviewMapper, FileIndexService fileIndexService) {
        this.reviewMapper = reviewMapper;
        this.fileIndexService = fileIndexService;
    }

    /**
     * 提交/修改评审。同一 file+commit 只保留最新一条（upsert），reviewer 更新为当前操作人。
     */
    @Transactional(rollbackFor = Exception.class)
    public void review(Long fileId, String commitHash, String result, String comment) {
        FileIndexEntity idx = fileIndexService.getById(fileId);
        if (idx == null) {
            throw new BusinessException(FileResultCode.FILE_NOT_FOUND);
        }
        String latest = idx.getLatestCommitHash();
        if (latest == null || !latest.equals(commitHash)) {
            throw new BusinessException(PublishResultCode.REVIEW_COMMIT_STALE, "检测到该文件已有最新提交，请刷新后重新审核");
        }

        String reviewer = currentUser();
        ReviewEntity exist = reviewMapper.selectOne(new LambdaQueryWrapper<ReviewEntity>()
                .eq(ReviewEntity::getFileId, fileId)
                .eq(ReviewEntity::getCommitHash, commitHash)
                .eq(ReviewEntity::getReviewer, reviewer));

        if (exist != null) {
            exist.setReviewer(reviewer);
            exist.setResult(result);
            exist.setComment(comment == null ? "" : comment);
            reviewMapper.updateById(exist);
        } else {
            ReviewEntity entity = new ReviewEntity();
            entity.setFileId(fileId);
            entity.setCommitHash(commitHash);
            entity.setReviewer(reviewer);
            entity.setComment(comment == null ? "" : comment);
            entity.setResult(result);
            reviewMapper.insert(entity);
        }
    }

    /**
     * 删除自己的评审。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long reviewId) {
        ReviewEntity entity = reviewMapper.selectById(reviewId);
        if (entity == null) {
            throw new BusinessException(PublishResultCode.REVIEW_NOT_FOUND);
        }
        if (!entity.getReviewer().equals(currentUser())) {
            throw new BusinessException(PublishResultCode.REVIEW_DEL_FORBIDDEN);
        }
        reviewMapper.deleteById(reviewId);
    }

    /**
     * 查 CR：指定 commitHash 则按该 commit 查，否则按文件当前 latest_commit_hash 查。
     */
    public List<ReviewVO> list(Long fileId, String commitHash) {
        String target = commitHash;
        if (StringUtils.isEmpty(target)) {
            FileIndexEntity idx = fileIndexService.getById(fileId);
            target = idx != null ? idx.getLatestCommitHash() : null;
        }
        LambdaQueryWrapper<ReviewEntity> wrapper = new LambdaQueryWrapper<ReviewEntity>()
                .eq(ReviewEntity::getFileId, fileId)
                .orderByDesc(ReviewEntity::getUpdateTime);
        if (!StringUtils.isEmpty(target)) {
            wrapper.eq(ReviewEntity::getCommitHash, target);
        }
        return reviewMapper.selectList(wrapper).stream().map(ReviewVO::of).collect(Collectors.toList());
    }

    /**
     * 最新一次 Review Result
     */
    public ReviewEntity latestReview(Long fileId, String commitHash) {
        return reviewMapper.selectOne(new LambdaQueryWrapper<ReviewEntity>()
                .eq(ReviewEntity::getFileId, fileId)
                .eq(ReviewEntity::getCommitHash, commitHash)
                .orderByDesc(ReviewEntity::getUpdateTime));
    }
}
