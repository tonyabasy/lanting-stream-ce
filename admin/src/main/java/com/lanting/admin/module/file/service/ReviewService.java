package com.lanting.admin.module.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lanting.admin.module.file.entity.ReviewEntity;
import com.lanting.admin.module.file.mapper.ReviewMapper;
import com.lanting.admin.module.file.vo.ReviewVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Review 服务。
 *
 * @author wangzhao
 */
@Service
public class ReviewService {

    @Autowired
    private ReviewMapper reviewMapper;

    /**
     * 添加 review。
     *
     * @param tagName  发布 tag
     * @param comment  备注
     * @param reviewer reviewer username
     */
    public void add(String tagName, String comment, String reviewer) {
        ReviewEntity entity = new ReviewEntity();
        entity.setTagName(tagName);
        entity.setComment(comment);
        entity.setReviewer(reviewer);
        reviewMapper.insert(entity);
    }

    /**
     * 查询某个发布的 review 列表。
     *
     * @param tagName 发布 tag
     * @return review 列表
     */
    public List<ReviewVO> list(String tagName) {
        LambdaQueryWrapper<ReviewEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReviewEntity::getTagName, tagName)
                .orderByDesc(ReviewEntity::getCreateTime);
        List<ReviewEntity> entities = reviewMapper.selectList(wrapper);
        return entities.stream().map(this::toVO).toList();
    }

    private ReviewVO toVO(ReviewEntity entity) {
        ReviewVO vo = new ReviewVO();
        vo.setTagName(entity.getTagName());
        vo.setReviewer(entity.getReviewer());
        vo.setComment(entity.getComment());
        vo.setTimestamp(entity.getCreateTime());
        return vo;
    }
}
