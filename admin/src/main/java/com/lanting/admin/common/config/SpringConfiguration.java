package com.lanting.admin.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.core.injector.ISqlInjector;
import com.lanting.admin.common.mybatis.DeletePhysicallySqlInjector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Spring 及 MyBatis-Plus 核心配置。
 *
 * @author wangzhao
 */
@Configuration
public class SpringConfiguration {

    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 自定义 MyBatis-Plus {@link ISqlInjector}，用于注册全局自定义 SQL 方法。
     */
    @Bean
    public ISqlInjector sqlInjector() {
        return new DeletePhysicallySqlInjector();
    }

    /**
     * MyBatis-Plus 拦截器链。
     * <p>
     * 包含分页插件（SQLite 方言），单页最多返回 500 条作为系统兜底红线；
     * 业务层通过 {@link com.lanting.admin.common.page.PageQuery} 的 {@code @Max(100)} 校验
     * 提前拦截，两道防线互相补充。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.SQLITE);
        pagination.setMaxLimit(500L);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
