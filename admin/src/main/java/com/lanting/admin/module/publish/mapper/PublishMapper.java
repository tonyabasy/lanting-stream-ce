package com.lanting.admin.module.publish.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lanting.admin.module.publish.entity.PublishEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 发布批次头表 Mapper（lanting_publish）。
 *
 * @author wangzhao
 */
@Mapper
public interface PublishMapper extends BaseMapper<PublishEntity> {
}
