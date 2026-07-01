package com.lanting.admin.module.user.entity;

import cn.dev33.satoken.stp.StpUtil;
import lombok.*;

/**
 * 登录令牌载荷。
 *
 * @author wangzhao
 * @since 2025-11-25
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class LoginToken {
    /**
     * 令牌值。
     */
    private String token;

    /**
     * 令牌 TTL（秒）：{@code -1} 永不过期，{@code -2} 无有效令牌。
     *
     * <p>参见 {@link StpUtil#getTokenTimeout()}。
     */
    private Long tokenTtl;

    /**
     * 令牌到期时间（毫秒时间戳）。
     */
    private Long tokenExpireAt;
}
