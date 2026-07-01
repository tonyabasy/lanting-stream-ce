package com.lanting.admin.module.user.dto;

import lombok.Data;

/**
 * 当前用户更新个人资料请求 DTO。
 * <p>
 * 用户身份从 Sa-Token 会话获取，无需传 id。
 *
 * @author wangzhao
 */
@Data
public class UpdateProfileDTO {

    private String nickname;

    private String email;

    private String avatarUrl;
}
