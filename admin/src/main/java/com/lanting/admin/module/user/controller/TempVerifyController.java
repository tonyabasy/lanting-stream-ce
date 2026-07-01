package com.lanting.admin.module.user.controller;

import com.lanting.admin.common.result.Result;
import com.lanting.admin.module.user.entity.PublicUser;
import com.lanting.admin.module.user.entity.UserEntity;
import com.lanting.admin.module.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 临时验证接口：验证 Jackson 序列化机制，确认后删除。
 */
@RestController
@RequestMapping("/test")
public class TempVerifyController {

    @Autowired
    private UserService userService;

    /**
     * 验证一：Controller 返回 PublicUser，Jackson 是否只序列化 PublicUser 字段。
     * 预期响应：无 password、无 authSource 字段。
     */
    @GetMapping("/user-public")
    public Result<PublicUser> getPublicUser() {
        UserEntity user = userService.getUserByName("admin");
        // 直接把 UserEntity 赋值给 PublicUser 引用，不调用 toPublic()
        return Result.success(user);
    }

    /**
     * 验证二：Controller 返回 UserEntity，Jackson 是否序列化全部字段。
     * 预期响应：包含 password（BCrypt 哈希）、authSource 字段。
     * 对比验证一，确认区别。
     */
    @GetMapping("/user-full")
    public Result<UserEntity> getFullUser() {
        return Result.success(userService.getUserByName("admin"));
    }
}
