package com.lanting.admin.module.user.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.lanting.admin.common.page.PageResult;
import com.lanting.admin.common.result.Result;
import com.lanting.admin.module.user.dto.*;
import com.lanting.admin.module.user.entity.UserEntity;
import com.lanting.admin.module.user.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 用户管理接口。
 * <p>
 * 所有接口需要登录（由 Sa-Token 拦截器统一保障）；
 * 管理员操作额外需要 {@code user:admin} 权限（{@link SaCheckPermission}）；
 * 当前用户自身操作仅需登录，身份校验在 Service 层通过 Sa-Token 会话完成。
 *
 * @author wangzhao
 */
@Validated
@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    // ==================== 普通用户接口 ====================

    /**
     * 分页查询用户列表。
     * 所有登录用户可访问，用于"@成员"等功能。
     */
    @GetMapping
    public Result<PageResult<UserEntity>> listUsers(@Valid UserQueryDTO query) {
        return Result.success(userService.searchUsersPage(query));
    }

    /**
     * 更新当前用户个人资料。
     */
    @PutMapping("/me/profile")
    public Result<UserEntity> updateProfile(@Valid @RequestBody UpdateProfileDTO dto) {
        return Result.success(userService.updateCurrentProfile(dto));
    }

    /**
     * 修改当前用户密码。
     */
    @PutMapping("/me/password")
    public Result<Void> updatePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        userService.updateCurrentPassword(dto);
        return Result.success();
    }

    // ==================== 管理员接口 ====================

    /**
     * 创建用户。
     */
    @SaCheckPermission("user:admin")
    @PostMapping
    public Result<UserEntity> createUser(@Valid @RequestBody CreateUserDTO dto) {
        return Result.success(userService.createUser(dto));
    }

    /**
     * 编辑用户信息。
     */
    @SaCheckPermission("user:admin")
    @PutMapping("/{id}")
    public Result<UserEntity> updateUser(@PathVariable Long id,
                                         @Valid @RequestBody UpdateUserDTO dto) {
        dto.setId(id);
        return Result.success(userService.updateUser(dto));
    }

    /**
     * 删除用户（逻辑删除）。
     */
    @SaCheckPermission("user:admin")
    @DeleteMapping("/{id}")
    public Result<Void> deleteUser(@PathVariable @NotNull Long id) {
        userService.deleteUser(id);
        return Result.success();
    }

    /**
     * 设置超管标记。
     */
    @SaCheckPermission("user:admin")
    @PutMapping("/{id}/super-admin")
    public Result<Void> setSuperAdmin(@PathVariable Long id,
                                      @RequestParam boolean superAdmin) {
        userService.setSuperAdmin(id, superAdmin);
        return Result.success();
    }

    /**
     * 重置指定用户密码（不需要旧密码，仅管理员可操作）。
     */
    @SaCheckPermission("user:admin")
    @PutMapping("/{id}/password")
    public Result<Void> resetPassword(@PathVariable Long id,
                                      @Valid @RequestBody ResetPasswordDTO dto) {
        userService.resetPasswordById(id, dto.getNewPassword());
        return Result.success();
    }
}
