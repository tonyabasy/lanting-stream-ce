package com.lanting.admin.module.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lanting.admin.module.file.entity.FileIndexEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 文件系统元数据索引表 Mapper。
 *
 * @author wangzhao
 */
@Mapper
public interface FileIndexMapper extends BaseMapper<FileIndexEntity> {

    /**
     * 按父路径查询直接子节点。
     *
     * @param parentPath 父路径
     * @return 子节点列表
     */
    @Select("SELECT * FROM lanting_file_index WHERE parent_path = #{parentPath} ORDER BY name ASC")
    List<FileIndexEntity> selectByParentPath(@Param("parentPath") String parentPath);
}

