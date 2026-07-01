package com.lanting.admin.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建用户请求 DTO。
 *
 * @author wangzhao
 */
@Data
public class CreateUserDTO {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    /** 昵称，不填时默认使用用户名 */
    private String nickname;

    private String email;

    /** 超级管理员标记 */
    private Boolean superAdminFlag;
}
