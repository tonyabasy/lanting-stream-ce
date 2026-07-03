package com.lanting.admin.module.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 管理员重置用户密码请求 DTO。
 * <p>
 * 不需要旧密码，仅超管可调用，权限控制在 Controller 层。
 *
 * @author wangzhao
 */
@Schema(description = "重置密码请求")
@Data
public class ResetPasswordDTO {

    @Schema(description = "新密码")
    @NotBlank(message = "新密码不能为空")
    private String newPassword;
}
