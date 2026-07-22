package com.lanting.admin.module.publish.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lanting.admin.module.publish.entity.ReviewEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 发布评审记录表 Mapper（lanting_file_review）。
 *
 * @author wangzhao
 */
@Mapper
public interface ReviewMapper extends BaseMapper<ReviewEntity> {
}
