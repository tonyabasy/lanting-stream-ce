package com.lanting.admin.module.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理员编辑用户请求 DTO。
 *
 * @author wangzhao
 */
@Data
public class UpdateUserDTO {

    @NotNull(message = "用户 ID 不能为空")
    private Long id;

    private String nickname;

    private String email;

    private String avatarUrl;

    /** 新密码，为空则保留原密码 */
    private String password;
}
