package com.lanting.admin.common.basic;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * 扩展的 {@link BaseMapper}，提供物理删除和包含已删除记录的查询方法。
 *
 * @param <T> 实体类型
 * @author wangzhao
 * @since 2025-12-26
 */
public interface BasicEntityMapper<T> extends BaseMapper<T> {

    /**
     * 物理删除：{@code DELETE FROM table WHERE id IN (...)}。
     */
    int deletePhysicallyByIds(@Param(Constants.COLL) Collection<?> idList);

    /**
     * 查询包含逻辑删除的行；如果 {@code queryWrapper == null}，不添加额外条件（仅查已删除行）。
     */
    List<T> selectListIncludeDeleted(@Param(Constants.WRAPPER) Wrapper<T> queryWrapper);
}
