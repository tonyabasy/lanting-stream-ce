package com.lanting.admin.module.user.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.lanting.admin.common.result.Result;
import com.lanting.admin.module.user.dto.LoginDTO;
import com.lanting.admin.module.user.entity.UserEntity;
import com.lanting.admin.module.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 认证接口：登录、登出、当前用户。
 * <p>
 * 放行规则（见 {@link com.lanting.admin.common.config.SaTokenConfig}）：
 * <ul>
 *   <li>{@code /api/auth/login}：无需鉴权</li>
 *   <li>{@code /api/auth/logout}：无需鉴权</li>
 *   <li>{@code /api/auth/current}：需要登录</li>
 * </ul>
 *
 * @author wangzhao
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    /**
     * 登录。
     * 返回用户信息及 token，token 同时通过响应头 {@code lanting-token} 下发（Sa-Token 自动处理）。
     */
    @PostMapping("/login")
    public Result<UserEntity> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success(userService.login(dto));
    }

    /**
     * 登出。
     * 销毁当前会话 token，未登录时 Sa-Token 静默处理不报错。
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        StpUtil.logout();
        return Result.success();
    }

    /**
     * 获取当前登录用户信息，附带最新 token 信息（过期时间等）。
     * 需要登录，由 Sa-Token 拦截器统一保障。
     */
    @GetMapping("/current")
    public Result<UserEntity> current() {
        return Result.success(userService.getCurrentUser());
    }
}
