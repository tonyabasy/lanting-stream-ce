package com.lanting.admin.module.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 当前用户更新个人资料请求 DTO。
 * <p>
 * 用户身份从 Sa-Token 会话获取，无需传 id。
 *
 * @author wangzhao
 */
@Schema(description = "更新个人资料请求")
@Data
public class UpdateProfileDTO {

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "头像 URL")
    private String avatarUrl;
}
