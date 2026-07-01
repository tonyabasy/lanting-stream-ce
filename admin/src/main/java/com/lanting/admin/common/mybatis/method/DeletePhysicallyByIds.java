package com.lanting.admin.common.mybatis.method;

import com.baomidou.mybatisplus.core.enums.SqlMethod;
import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.toolkit.sql.SqlScriptUtils;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;

/**
 * MyBatis-Plus 自定义方法：按主键物理删除（MySQL）。
 *
 * @author wangzhao
 * @since 2025-12-26
 */
public class DeletePhysicallyByIds extends AbstractMethod {

    /**
     * @since 3.5.0
     */
    public DeletePhysicallyByIds() {
        super("deletePhysicallyByIds");
    }

    @Override
    public MappedStatement injectMappedStatement(
            Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
        SqlMethod sqlMethod = SqlMethod.DELETE_BY_IDS;
        String sql =
                String.format(
                        sqlMethod.getSql(),
                        tableInfo.getTableName(),
                        tableInfo.getKeyColumn(),
                        getConvertForeachScript(tableInfo));
        SqlSource sqlSource = super.createSqlSource(configuration, sql, Object.class);
        return this.addDeleteMappedStatement(mapperClass, methodName, sqlSource);
    }

    protected String getConvertForeachScript(TableInfo tableInfo) {
        return SqlScriptUtils.convertForeach(
                SqlScriptUtils.convertChoose(
                        "item!=null and @org.apache.ibatis.reflection.SystemMetaObject@forObject(item).findProperty('"
                                + tableInfo.getKeyProperty()
                                + "', false) != null",
                        "#{item." + tableInfo.getKeyProperty() + "}",
                        "#{item}"),
                COLL,
                null,
                "item",
                COMMA);
    }
}
