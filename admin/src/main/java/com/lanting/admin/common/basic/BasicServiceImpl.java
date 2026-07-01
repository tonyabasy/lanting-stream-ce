package com.lanting.admin.common.basic;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.util.Collection;
import java.util.List;

/**
 * @param <M> Mapper type
 * @param <T> Entity type
 * @author wangzhao
 * @since 2025-12-26
 */
public class BasicServiceImpl<M extends BasicEntityMapper<T>, T> extends ServiceImpl<M, T> {

    /**
     * 根据 ID 物理删除。
     */
    public int removePhysicallyByIds(Collection<?> idList) {
        if (CollectionUtils.isEmpty(idList)) {
            return 0;
        }
        return baseMapper.deletePhysicallyByIds(idList);
    }

    /**
     * 查询结果中包含已删除的数据
     */
    public List<T> listIncludeDeleted(Wrapper<T> queryWrapper) {
        return baseMapper.selectListIncludeDeleted(queryWrapper);
    }
}
