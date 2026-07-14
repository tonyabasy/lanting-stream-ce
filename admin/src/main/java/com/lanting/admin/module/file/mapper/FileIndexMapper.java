package com.lanting.admin.module.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lanting.admin.module.file.entity.FileIndexEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 文件系统元数据索引表 Mapper。
 *
 * @author wangzhao
 */
@Mapper
public interface FileIndexMapper extends BaseMapper<FileIndexEntity> {

    /**
     * 按主键查询索引记录。
     *
     * @param id 主键 ID
     * @return 索引记录
     */
    @Select("SELECT * FROM lanting_file_index WHERE id = #{id}")
    FileIndexEntity selectById(@Param("id") Long id);

    /**
     * 文件夹重命名：批量更新 path 和 parent_path 的前缀。
     * <p>
     * 注意：被重命名文件夹自身的 parent_path 不属于 oldPrefix 前缀，应保持不变；
     * 只有子节点的 parent_path 才需要替换。
     *
     * @param oldPrefix 原文件夹路径
     * @param newPrefix 新文件夹路径
     */
    @Update("UPDATE lanting_file_index SET " +
            "path = CASE WHEN path = #{oldPrefix} THEN #{newPrefix} " +
            "ELSE #{newPrefix} || SUBSTR(path, LENGTH(#{oldPrefix}) + 1) END, " +
            "parent_path = CASE WHEN parent_path = #{oldPrefix} THEN #{newPrefix} " +
            "WHEN parent_path LIKE #{oldPrefix} || '/%' THEN #{newPrefix} || SUBSTR(parent_path, LENGTH(#{oldPrefix}) + 1) " +
            "ELSE parent_path END " +
            "WHERE path = #{oldPrefix} OR path LIKE #{oldPrefix} || '/%'")
    void updatePathsByPrefix(@Param("oldPrefix") String oldPrefix, @Param("newPrefix") String newPrefix);
    /**
     * 批量按路径查询索引记录。
     *
     * @param paths 文件相对路径列表
     * @return 索引记录列表
     */
    @Select("<script>SELECT * FROM lanting_file_index WHERE path IN " +
            "<foreach collection='paths' item='p' open='(' separator=',' close=')'>#{p}</foreach></script>")
    List<FileIndexEntity> selectByPaths(@Param("paths") List<String> paths);

    /**
     * 批量插入索引记录。
     *
     * @param list 待插入的实体列表
     */
    @Insert("<script>INSERT INTO lanting_file_index (path, name, type, parent_path, mtime, crc32, create_time, update_time) VALUES " +
            "<foreach collection='list' item='e' separator=','>" +
            "(#{e.path}, #{e.name}, #{e.type}, #{e.parentPath}, #{e.mtime}, #{e.crc32}, #{e.createTime}, #{e.updateTime})" +
            "</foreach></script>")
    void insertBatch(@Param("list") List<FileIndexEntity> list);

    /**
     * 批量更新已有记录的 mtime 和 crc32。使用 CASE WHEN 单条 SQL 完成。
     *
     * @param list       待更新实体列表（需含 path、mtime 和 crc32）
     * @param updateTime 统一更新时间戳
     */
    @Update("<script>UPDATE lanting_file_index SET mtime = CASE path " +
            "<foreach collection='list' item='e'>WHEN #{e.path} THEN #{e.mtime} </foreach>" +
            "END, crc32 = CASE path " +
            "<foreach collection='list' item='e'>WHEN #{e.path} THEN #{e.crc32} </foreach>" +
            "END, update_time = #{updateTime} WHERE path IN " +
            "<foreach collection='list' item='e' open='(' separator=',' close=')'>#{e.path}</foreach></script>")
    void batchUpdateMtimeAndCrc32(@Param("list") List<FileIndexEntity> list, @Param("updateTime") long updateTime);
}

