package com.lanting.admin.common.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

/**
 * MyBatis-Plus {@link MetaObjectHandler}，用于在插入/更新时自动填充审计字段。
 *
 * @author wangzhao
 * @since 2026-02-11
 */
@Component
public class BasicEntityFillHandler implements MetaObjectHandler {
    @Override
    public void insertFill(MetaObject metaObject) {
        long currentTime = System.currentTimeMillis();
        this.strictInsertFill(metaObject, "createTime", Long.class, currentTime);
        this.strictInsertFill(metaObject, "updateTime", Long.class, currentTime);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        long currentTime = System.currentTimeMillis();
        this.strictInsertFill(metaObject, "updateTime", Long.class, currentTime);
    }
}
