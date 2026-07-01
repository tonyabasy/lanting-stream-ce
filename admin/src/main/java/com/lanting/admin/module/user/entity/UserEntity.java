package com.lanting.admin.module.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户表；存储基本资料和登录相关字段。
 *
 * @author wangzhao
 * @since 2025-11-24
 */
@Getter
@Setter
@TableName("lanting_user")
public class UserEntity extends PublicUser {

    /**
     * 认证来源，{@code "local"} 表示本地账号。
     * 内部字段，不对外暴露。
     */
    @JsonIgnore
    private String authSource;

    /**
     * 密码（BCrypt 哈希）。
     * 敏感字段，不对外暴露。
     */
    @JsonIgnore
    private String password;
}
