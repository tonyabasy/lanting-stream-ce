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
     * 按父路径查询直接子节点。
     *
     * @param parentPath 父路径
     * @return 子节点列表
     */
    @Select("SELECT * FROM lanting_file_index WHERE parent_path = #{parentPath} ORDER BY name ASC")
    List<FileIndexEntity> selectByParentPath(@Param("parentPath") String parentPath);

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

