package com.lanting.admin.common.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 拦截器配置。
 * <p>
 * 拦截所有 {@code /api/**} 接口，要求登录；
 * 登录和登出接口排除在外，无需鉴权。
 * <p>
 * 更细粒度的权限控制（如管理员权限）通过 Controller 方法上的
 * {@code @SaCheckPermission} 注解实现。
 *
 * @author wangzhao
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handler -> StpUtil.checkLogin()))
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/logout"
                );
    }
}
