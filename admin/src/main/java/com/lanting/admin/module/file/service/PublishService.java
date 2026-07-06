package com.lanting.admin.module.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lanting.admin.common.exception.BusinessException;
import com.lanting.admin.common.page.PageQuery;
import com.lanting.admin.common.page.PageResult;
import com.lanting.admin.module.file.dto.PublishDTO;
import com.lanting.admin.module.file.entity.PublishEntity;
import com.lanting.admin.module.file.mapper.PublishMapper;
import com.lanting.admin.module.file.result.FileResultCode;
import com.lanting.admin.module.file.vo.PublishVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 发布服务。
 *
 * @author wangzhao
 */
@Slf4j
@Service
public class PublishService {

    @Autowired
    private PublishMapper publishMapper;

    @Autowired
    private GitFileService gitFileService;

    /**
     * 发布：打 tag + 落库。落库失败时补偿删除 tag，
     * 避免产生 Git 中有 tag 但发布列表查不到的“幽灵发布”。
     *
     * @param dto      发布参数
     * @param username 发布人
     * @return 发布结果
     */
    public PublishVO publish(PublishDTO dto, String username) {
        PublishVO vo = gitFileService.publish(dto);
        try {
            create(vo.getTagName(), vo.getDisplayName(), vo.getCommitHash(), username);
        } catch (Exception e) {
            log.error("发布记录落库失败，补偿删除 tag：{}", vo.getTagName(), e);
            gitFileService.deleteTag(vo.getTagName());
            throw new BusinessException(FileResultCode.GIT_OPERATION_FAILED, "发布记录写入失败：" + e.getMessage());
        }
        return vo;
    }

    /**
     * 创建发布记录。createTime/updateTime 由 MyBatis-Plus 自动填充。
     *
     * @param tagName     发布 tag
     * @param displayName 显示名
     * @param commitHash  对应 commit SHA
     * @param createdBy   发布人
     */
    public void create(String tagName, String displayName, String commitHash, String createdBy) {
        PublishEntity entity = new PublishEntity();
        entity.setTagName(tagName);
        entity.setDisplayName(displayName);
        entity.setCommitHash(commitHash);
        entity.setCreatedBy(createdBy);
        publishMapper.insert(entity);
    }

    /**
     * 分页查询发布历史。已接入 PageQuery 统一分页校验，并通过 PageResult.of 转换 entity -> VO。
     *
     * @param query 分页查询参数
     * @return 分页发布记录
     */
    public PageResult<PublishVO> list(PageQuery query) {
        Page<PublishEntity> mpPage = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<PublishEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(PublishEntity::getCreateTime);
        publishMapper.selectPage(mpPage, wrapper);
        return PageResult.of(mpPage, this::toVO);
    }

    private PublishVO toVO(PublishEntity entity) {
        PublishVO vo = new PublishVO();
        vo.setTagName(entity.getTagName());
        vo.setDisplayName(entity.getDisplayName());
        vo.setCommitHash(entity.getCommitHash());
        vo.setTimestamp(entity.getCreateTime());
        return vo;
    }
}
