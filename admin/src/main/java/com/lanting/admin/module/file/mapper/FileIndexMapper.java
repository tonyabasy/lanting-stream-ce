package com.lanting.admin.module.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lanting.admin.module.file.entity.FileIndexEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 文件系统元数据索引表 Mapper。
 *
 * <p>普通查询统一在 {@link com.lanting.admin.module.file.service.FileIndexService} 中通过
 * MyBatis-Plus {@code LambdaQueryWrapper} 构建，保持类型安全；Mapper 中仅保留批量
 * 插入/更新和文件夹路径前缀替换等复杂 SQL。
 *
 * @author wangzhao
 */
@Mapper
public interface FileIndexMapper extends BaseMapper<FileIndexEntity> {

    /**
     * 文件夹重命名后批量更新索引：path 和 parent_path 中匹配 oldPrefix 的前缀替换为 newPrefix。
     * <p>
     * 被重命名文件夹自身的 parent_path 保持不变；仅其子孙节点的 parent_path 按规则替换。
     *
     * @param oldPrefix 原文件夹路径
     * @param newPrefix 新文件夹路径
     */
    @Update("UPDATE lanting_file_index SET " +
            "path = #{newPrefix} || SUBSTR(path, LENGTH(#{oldPrefix}) + 1), " +
            "parent_path = CASE " +
            "WHEN path = #{oldPrefix} THEN parent_path " +
            "WHEN parent_path = #{oldPrefix} THEN #{newPrefix} " +
            "ELSE #{newPrefix} || SUBSTR(parent_path, LENGTH(#{oldPrefix}) + 1) END " +
            "WHERE path = #{oldPrefix} OR path LIKE #{oldPrefix} || '/%'")
    void updatePathsByPrefix(@Param("oldPrefix") String oldPrefix, @Param("newPrefix") String newPrefix);
}
