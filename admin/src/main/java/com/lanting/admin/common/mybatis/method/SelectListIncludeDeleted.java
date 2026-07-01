package com.lanting.admin.common.mybatis.method;

import com.baomidou.mybatisplus.core.enums.SqlMethod;
import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.toolkit.sql.SqlScriptUtils;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;

/**
 * MyBatis-Plus 自定义方法：查询所有行（包含逻辑删除的行）。
 *
 * @author wangzhao
 * @since 2026-03-19
 */
public class SelectListIncludeDeleted extends AbstractMethod {
    private static final String AND_SQL_SEGMENT =
            SqlScriptUtils.convertIf(
                    " AND ${" + WRAPPER_SQLSEGMENT + "}",
                    "_sgEs_ and " + WRAPPER_NONEMPTYOFNORMAL,
                    true);
    private static final String BIND_SQL_SEGMENT =
            "<bind name=\"_sgEs_\" value=\"ew.sqlSegment != null and ew.sqlSegment != ''\"/>";
    private static final String LAST_SQL_SEGMENT =
            SqlScriptUtils.convertIf(
                    " ${" + WRAPPER_SQLSEGMENT + "}", "_sgEs_ and " + WRAPPER_EMPTYOFNORMAL, true);

    public SelectListIncludeDeleted() {
        super("selectListIncludeDeleted");
    }

    @Override
    public MappedStatement injectMappedStatement(
            Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
        SqlMethod sqlMethod = SqlMethod.SELECT_LIST;
        String sql =
                String.format(
                        sqlMethod.getSql(),
                        sqlFirst(),
                        sqlSelectColumns(tableInfo, true),
                        tableInfo.getTableName(),
                        sqlWhereEntityWrapper(true, tableInfo),
                        sqlOrderBy(tableInfo),
                        sqlComment());
        SqlSource sqlSource = super.createSqlSource(configuration, sql, modelClass);
        return this.addSelectMappedStatementForTable(mapperClass, methodName, sqlSource, tableInfo);
    }

    @Override
    protected String sqlWhereEntityWrapper(boolean newLine, TableInfo table) {
        /*
         * 标准 SQL 片段注入（Wrapper 条件）。
         */
        String sqlScript = table.getAllSqlWhere(false, false, true, WRAPPER_ENTITY_DOT);
        sqlScript = SqlScriptUtils.convertIf(sqlScript, WRAPPER_ENTITY + " != null", true);
        sqlScript =
                SqlScriptUtils.convertWhere(sqlScript + NEWLINE + AND_SQL_SEGMENT)
                        + NEWLINE
                        + LAST_SQL_SEGMENT;
        sqlScript =
                SqlScriptUtils.convertIf(
                        BIND_SQL_SEGMENT + NEWLINE + sqlScript, WRAPPER + " != null", true);
        return newLine ? NEWLINE + sqlScript : sqlScript;
    }
}
