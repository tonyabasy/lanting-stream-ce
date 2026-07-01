package com.lanting.admin.common.config;

import cn.dev33.satoken.stp.StpInterface;
import com.lanting.admin.module.user.entity.UserEntity;
import com.lanting.admin.module.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Sa-Token 权限接口实现。
 * <p>
 * 超管（{@code superAdminFlag = true}）拥有所有模块的管理权限；
 * 普通用户没有任何 {@code *:admin} 权限。
 * <p>
 * EE 可通过 {@code @Primary} 覆盖此 Bean，实现细粒度权限或多租户隔离，CE 代码无需改动。
 * 见 {@code extension-points-watchlist.md}。
 *
 * @author wangzhao
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    @Autowired
    private UserService userService;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        UserEntity user = userService.getById(Long.parseLong(loginId.toString()));
        if (user == null) {
            return Collections.emptyList();
        }
        if (Boolean.TRUE.equals(user.getSuperAdminFlag())) {
            return List.of(
                    "user:admin",
                    "cluster:admin",
                    "datasource:admin",
                    "job:admin",
                    "udf:admin",
                    "file:admin",
                    "monitor:admin"
            );
        }
        return Collections.emptyList();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        // 不使用角色体系
        return Collections.emptyList();
    }
}
