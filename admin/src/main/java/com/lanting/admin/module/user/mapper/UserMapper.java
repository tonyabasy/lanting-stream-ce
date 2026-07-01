package com.lanting.admin.module.user.mapper;

import com.lanting.admin.common.basic.BasicEntityMapper;
import com.lanting.admin.module.user.entity.PublicUser;
import com.lanting.admin.module.user.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户表 Mapper（基本资料和登录相关字段）。
 *
 * @author wangzhao
 * @since 2025-11-24
 */
@Mapper
public interface UserMapper extends BasicEntityMapper<UserEntity> {

    /**
     * 组内未删除且尚未成为管理员的用户（可晋升候选），按 {@code update_time} 降序排列。
     */
    List<PublicUser> listUsersNotGroupAdmin(@Param("groupName") String groupName);
}
