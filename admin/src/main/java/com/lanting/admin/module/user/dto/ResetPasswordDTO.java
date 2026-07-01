package com.lanting.admin.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 管理员重置用户密码请求 DTO。
 * <p>
 * 不需要旧密码，仅超管可调用，权限控制在 Controller 层。
 *
 * @author wangzhao
 */
@Data
public class ResetPasswordDTO {

    @NotBlank(message = "新密码不能为空")
    private String newPassword;
}
