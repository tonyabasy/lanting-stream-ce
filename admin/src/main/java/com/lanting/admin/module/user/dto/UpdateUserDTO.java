package com.lanting.admin.module.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理员编辑用户请求 DTO。
 *
 * @author wangzhao
 */
@Schema(description = "编辑用户请求")
@Data
public class UpdateUserDTO {

    @Schema(description = "用户ID")
    @NotNull(message = "用户 ID 不能为空")
    private Long id;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "头像 URL")
    private String avatarUrl;

    /** 新密码，为空则保留原密码 */
    @Schema(description = "新密码，为空则保留原密码")
    private String password;
}
