package com.lanting.admin.common.mybatis;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.lanting.admin.common.mybatis.method.DeletePhysicallyByIds;
import com.lanting.admin.common.mybatis.method.SelectListIncludeDeleted;
import org.apache.ibatis.session.Configuration;

import java.util.List;

/**
 * 自定义 MyBatis-Plus {@link DefaultSqlInjector}，注册物理删除和包含已删除记录的查询方法。
 *
 * @author wangzhao
 * @since 2025-12-26
 */
public class DeletePhysicallySqlInjector extends DefaultSqlInjector {

    @Override
    public List<AbstractMethod> getMethodList(
            Configuration configuration, Class<?> mapperClass, TableInfo tableInfo) {
        List<AbstractMethod> methodList =
                super.getMethodList(configuration, mapperClass, tableInfo);
        // 自定义按 ID 物理删除
        methodList.add(new DeletePhysicallyByIds());
        // 查询包含逻辑删除的行
        methodList.add(new SelectListIncludeDeleted());
        return methodList;
    }
}
