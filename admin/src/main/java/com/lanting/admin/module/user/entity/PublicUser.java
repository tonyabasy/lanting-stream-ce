package com.lanting.admin.module.user.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.lanting.admin.common.basic.BasicEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 可安全公开暴露的用户字段。
 *
 * @author wangzhao
 */
@Getter
@Setter
@ToString
@Schema(description = "公开用户视图（不包含密码等敏感信息）")
public class PublicUser extends BasicEntity {

    /** 用户名（全局唯一）。 */
    @Schema(description = "用户名（全局唯一）", example = "admin")
    private String username;

    /** 显示名称。 */
    @Schema(description = "昵称", example = "super_admin")
    private String nickname;

    /** 头像 URL。 */
    @Schema(description = "头像 URL", example = "/uploads/avatars/abc123.jpg")
    private String avatarUrl;

    /** 邮箱地址。 */
    @Schema(description = "邮箱", example = "admin@example.com")
    private String email;

    /** 超级管理员标记。 */
    @Schema(description = "超级管理员标记", example = "true")
    private Boolean superAdminFlag;

    /** 登录令牌值。 */
    @TableField(exist = false)
    @Schema(
            description = "登录令牌；仅在登录/当前用户响应中返回",
            accessMode = Schema.AccessMode.READ_ONLY)
    private LoginToken tokenInfo;
}
