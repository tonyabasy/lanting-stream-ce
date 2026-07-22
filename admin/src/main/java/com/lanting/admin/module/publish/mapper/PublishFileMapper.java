package com.lanting.admin.module.publish.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lanting.admin.module.publish.entity.PublishFileEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 发布文件生命周期表 Mapper（lanting_publish_file）。
 *
 * @author wangzhao
 */
@Mapper
public interface PublishFileMapper extends BaseMapper<PublishFileEntity> {
}
