package com.lanting.admin.module.test;

import com.lanting.admin.common.exception.BusinessException;
import com.lanting.admin.common.result.Result;
import com.lanting.admin.module.user.entity.PublicUser;
import com.lanting.admin.module.user.entity.UserEntity;
import com.lanting.admin.module.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试接口：用于集成测试及开发验证。
 */
@RestController
@RequestMapping("/api/test")
public class TestController {

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

    /**
     * 验证三：业务异常国际化及 MessageFormat 占位符填充。
     */
    @GetMapping("/business-exception")
    public Result<Void> throwBusinessException() {
        throw BusinessException.of(TestResultCode.TEST_MESSAGE_FORMAT, "Alice", 42);
    }
}
