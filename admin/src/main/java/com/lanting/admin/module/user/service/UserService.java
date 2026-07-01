package com.lanting.admin.module.user.service;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lanting.admin.common.basic.BasicServiceImpl;
import com.lanting.admin.common.exception.BusinessException;
import com.lanting.admin.common.page.PageResult;
import com.lanting.admin.module.user.UserConstants;
import com.lanting.admin.module.user.dto.*;
import com.lanting.admin.module.user.entity.LoginToken;
import com.lanting.admin.module.user.entity.UserEntity;
import com.lanting.admin.module.user.mapper.UserMapper;
import com.lanting.admin.module.user.result.UserResultCode;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户服务。
 * <p>
 * Service 层只抛 {@link BusinessException}，不返回 {@link com.lanting.admin.common.result.Result}。
 * 参数格式校验由上层的 DTO {@code @Valid} 注解负责，本层仅处理业务规则。
 * <p>
 * {@code password} 和 {@code authSource} 字段在 {@link UserEntity} 上标注了 {@code @JsonIgnore}，
 * 序列化时天然不会出现在响应中，无需额外转换。
 *
 * @author wangzhao
 */
@Service
public class UserService extends BasicServiceImpl<UserMapper, UserEntity> {

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    public UserEntity getUserByName(String username) {
        return getOne(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, username));
    }

    /**
     * 登录。
     * <p>
     * 验证用户名和密码，通过后签发 Sa-Token 令牌，并将令牌信息写入返回的用户对象。
     */
    public UserEntity login(LoginDTO dto) {
        UserEntity user = getUserByName(dto.getUsername());
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(UserResultCode.PASSWORD_WRONG);
        }
        StpUtil.login(user.getId());
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        user.setTokenInfo(new LoginToken(
                tokenInfo.getTokenValue(),
                tokenInfo.getTokenTimeout(),
                tokenInfo.getTokenTimeout() == -1 ? -1L
                        : System.currentTimeMillis() + tokenInfo.getTokenTimeout() * 1000
        ));
        return user;
    }

    /**
     * 获取当前登录用户信息，附带令牌信息。
     */
    public UserEntity getCurrentUser() {
        Long userId = StpUtil.getLoginIdAsLong();
        UserEntity user = getById(userId);
        if (user == null) {
            throw new BusinessException(UserResultCode.USER_NOT_FOUND);
        }
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        user.setTokenInfo(new LoginToken(
                tokenInfo.getTokenValue(),
                tokenInfo.getTokenTimeout(),
                tokenInfo.getTokenTimeout() == -1 ? -1L
                        : System.currentTimeMillis() + tokenInfo.getTokenTimeout() * 1000
        ));
        return user;
    }

    /**
     * 根据用户名、昵称或邮箱模糊搜索用户。关键词为空时返回所有用户。
     */
    public List<UserEntity> searchUsers(String keyword) {
        return list(buildSearchWrapper(keyword, null));
    }

    /**
     * 分页用户列表，按更新时间降序排列。
     */
    public PageResult<UserEntity> searchUsersPage(UserQueryDTO query) {
        LambdaQueryWrapper<UserEntity> wrapper = buildSearchWrapper(query.getKeyword(), query.getSearchField());
        wrapper.orderByDesc(UserEntity::getUpdateTime);
        Page<UserEntity> rawPage = page(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        return PageResult.of(rawPage);
    }

    /**
     * 构建搜索条件。
     * <p>
     * 关键词为空时返回空 wrapper（查全部）；否则根据 searchField 模糊匹配指定字段，
     * searchField 为空时同时匹配用户名、昵称、邮箱。
     */
    LambdaQueryWrapper<UserEntity> buildSearchWrapper(String keyword, String searchField) {
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isBlank(keyword)) {
            return wrapper;
        }
        String kw = keyword.trim();
        if (StringUtils.isNotBlank(searchField)) {
            switch (searchField) {
                case "username" -> wrapper.like(UserEntity::getUsername, kw);
                case "nickname" -> wrapper.like(UserEntity::getNickname, kw);
                case "email"    -> wrapper.like(UserEntity::getEmail, kw);
                default         -> wrapper.and(w -> w
                        .like(UserEntity::getUsername, kw)
                        .or().like(UserEntity::getNickname, kw)
                        .or().like(UserEntity::getEmail, kw));
            }
        } else {
            wrapper.and(w -> w
                    .like(UserEntity::getUsername, kw)
                    .or().like(UserEntity::getNickname, kw)
                    .or().like(UserEntity::getEmail, kw));
        }
        return wrapper;
    }

    /**
     * 删除用户（逻辑删除）。
     * <p>
     * 内置的 {@value UserConstants#PROTECTED_USERNAME} 账号不可删除；不可删除自己。
     */
    public void deleteUser(Long userId) {
        UserEntity user = getById(userId);
        if (user == null) {
            throw new BusinessException(UserResultCode.USER_NOT_FOUND);
        }
        if (UserConstants.PROTECTED_USERNAME.equals(user.getUsername())) {
            throw new BusinessException(UserResultCode.SUPER_ADMIN_PROTECTED);
        }
        String currentUser = StpUtil.getLoginIdAsString();
        if (currentUser.equals(user.getUsername())) {
            throw new BusinessException(UserResultCode.CANNOT_DELETE_SELF);
        }
        removeById(userId);
    }

    /**
     * 创建用户。
     * <p>
     * 密码在持久化前进行 BCrypt 哈希处理。
     */
    public UserEntity createUser(CreateUserDTO dto) {
        if (getUserByName(dto.getUsername()) != null) {
            throw new BusinessException(UserResultCode.USERNAME_DUPLICATE);
        }
        UserEntity user = new UserEntity();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(StringUtils.isNotBlank(dto.getNickname()) ? dto.getNickname() : dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setSuperAdminFlag(dto.getSuperAdminFlag() != null ? dto.getSuperAdminFlag() : false);
        user.setAuthSource(UserConstants.AUTH_SOURCE_LOCAL);
        save(user);
        return user;
    }

    /**
     * 设置用户的超级管理员标记。
     * <p>
     * 内置管理员不可修改。
     */
    public void setSuperAdmin(Long userId, boolean superAdmin) {
        if (Long.valueOf(UserConstants.PROTECTED_USER_ID).equals(userId)) {
            throw new BusinessException(UserResultCode.SUPER_ADMIN_PROTECTED);
        }
        boolean updated = update(
                new LambdaUpdateWrapper<UserEntity>()
                        .eq(UserEntity::getId, userId)
                        .set(UserEntity::getSuperAdminFlag, superAdmin));
        if (!updated) {
            throw new BusinessException(UserResultCode.USER_NOT_FOUND);
        }
    }

    /**
     * 管理员编辑用户信息。
     * <p>
     * 用户名不可修改；仅当提供新密码时才重新编码。
     */
    public UserEntity updateUser(UpdateUserDTO dto) {
        UserEntity exist = getById(dto.getId());
        if (exist == null) {
            throw new BusinessException(UserResultCode.USER_NOT_FOUND);
        }
        LambdaUpdateWrapper<UserEntity> wrapper = new LambdaUpdateWrapper<UserEntity>()
                .eq(UserEntity::getId, dto.getId());
        if (dto.getNickname() != null) {
            wrapper.set(UserEntity::getNickname, dto.getNickname());
        }
        if (dto.getEmail() != null) {
            wrapper.set(UserEntity::getEmail, dto.getEmail());
        }
        if (dto.getAvatarUrl() != null) {
            wrapper.set(UserEntity::getAvatarUrl, dto.getAvatarUrl());
        }
        if (StringUtils.isNotBlank(dto.getPassword())) {
            wrapper.set(UserEntity::getPassword, passwordEncoder.encode(dto.getPassword()));
        }
        update(wrapper);
        return getById(dto.getId());
    }

    /**
     * 更新当前用户的公开资料。
     * <p>
     * 用户身份从 Sa-Token 会话获取，仅更新非空字段，防止空值覆盖。
     */
    public UserEntity updateCurrentProfile(UpdateProfileDTO dto) {
        String currentUser = StpUtil.getLoginIdAsString();
        LambdaUpdateWrapper<UserEntity> wrapper = new LambdaUpdateWrapper<UserEntity>()
                .eq(UserEntity::getUsername, currentUser);
        boolean needUpdate = false;
        if (StringUtils.isNotBlank(dto.getNickname())) {
            wrapper.set(UserEntity::getNickname, dto.getNickname());
            needUpdate = true;
        }
        if (dto.getEmail() != null) {
            wrapper.set(UserEntity::getEmail, dto.getEmail());
            needUpdate = true;
        }
        if (dto.getAvatarUrl() != null) {
            wrapper.set(UserEntity::getAvatarUrl, dto.getAvatarUrl());
            needUpdate = true;
        }
        if (needUpdate) {
            update(wrapper);
        }
        return getUserByName(currentUser);
    }

    /**
     * 修改当前用户的密码。
     */
    public void updateCurrentPassword(ChangePasswordDTO dto) {
        String currentUser = StpUtil.getLoginIdAsString();
        UserEntity user = getUserByName(currentUser);
        if (user == null) {
            throw new BusinessException(UserResultCode.USER_NOT_FOUND);
        }
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException(UserResultCode.PASSWORD_WRONG);
        }
        resetPasswordById(user.getId(), dto.getNewPassword());
    }

    /**
     * 重置用户密码（不验证旧密码）。
     * <p>
     * 仅供超级管理员使用；权限控制在 Controller 层。
     */
    public void resetPasswordById(Long userId, String newPassword) {
        boolean updated = update(
                new LambdaUpdateWrapper<UserEntity>()
                        .eq(UserEntity::getId, userId)
                        .set(UserEntity::getPassword, passwordEncoder.encode(newPassword)));
        if (!updated) {
            throw new BusinessException(UserResultCode.USER_NOT_FOUND);
        }
    }
}
