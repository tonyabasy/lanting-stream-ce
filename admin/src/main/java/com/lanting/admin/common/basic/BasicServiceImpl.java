package com.lanting.admin.common.basic;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Collection;
import java.util.List;

/**
 * @param <M> Mapper type
 * @param <T> Entity type
 * @author wangzhao
 * @since 2025-12-26
 */
@Slf4j
public class BasicServiceImpl<M extends BasicEntityMapper<T>, T> extends ServiceImpl<M, T> {

    @Autowired
    protected ApplicationEventPublisher eventPublisher;

    /**
     * 安全发布事件，捕获异常仅记录日志，不中断业务流程。
     */
    protected void publishEventSafely(ApplicationEvent event) {
        try {
            eventPublisher.publishEvent(event);
        } catch (Exception e) {
            log.warn("发布事件失败 {}", event, e);
        }
    }

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
