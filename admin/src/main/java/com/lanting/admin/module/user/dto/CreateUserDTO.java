package com.lanting.admin.module.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建用户请求 DTO。
 *
 * @author wangzhao
 */
@Schema(description = "创建用户请求")
@Data
public class CreateUserDTO {

    @Schema(description = "用户名（全局唯一）")
    @NotBlank(message = "用户名不能为空")
    private String username;

    @Schema(description = "密码")
    @NotBlank(message = "密码不能为空")
    private String password;

    /** 昵称，不填时默认使用用户名 */
    @Schema(description = "昵称，不填时默认使用用户名")
    private String nickname;

    @Schema(description = "邮箱")
    private String email;

    /** 超级管理员标记 */
    @Schema(description = "超级管理员标记")
    private Boolean superAdminFlag;
}
