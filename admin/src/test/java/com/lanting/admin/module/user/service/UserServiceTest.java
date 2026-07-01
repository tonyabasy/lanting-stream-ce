package com.lanting.admin.module.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lanting.admin.common.exception.BusinessException;
import com.lanting.admin.module.user.UserConstants;
import com.lanting.admin.module.user.dto.CreateUserDTO;
import com.lanting.admin.module.user.entity.UserEntity;
import com.lanting.admin.module.user.mapper.UserMapper;
import com.lanting.admin.module.user.result.UserResultCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

/**
 * UserService 单元测试。
 * <p>
 * 只覆盖有业务分支、出错难发现的逻辑。
 * 简单 CRUD、框架委托方法不在此测试——由 Controller 切片测试或集成测试覆盖。
 *
 * @author wangzhao
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        // BasicServiceImpl 内部通过 baseMapper 访问数据库，注入 Mock 的 Mapper
        ReflectionTestUtils.setField(userService, "baseMapper", userMapper);
    }

    // ==================== createUser ====================

    /**
     * 用户名已存在时，抛出 USERNAME_DUPLICATE 异常。
     * 这是最核心的业务规则，出错会导致数据重复。
     */
    @Test
    void createUser_shouldThrow_whenUsernameAlreadyExists() {
        CreateUserDTO dto = new CreateUserDTO();
        dto.setUsername("admin");
        dto.setPassword("password123");

        UserEntity existing = new UserEntity();
        existing.setUsername("admin");
        when(userMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(existing);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> userService.createUser(dto));
        assertEquals(UserResultCode.USERNAME_DUPLICATE, ex.getResultCode());
    }

    /**
     * 用户名不存在时，正常创建用户。
     * 验证：昵称默认使用用户名、authSource 默认为 local、密码经过编码。
     */
    @Test
    void createUser_shouldSetDefaults_whenNicknameAndAuthSourceOmitted() {
        CreateUserDTO dto = new CreateUserDTO();
        dto.setUsername("newuser");
        dto.setPassword("password123");

        when(userMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(null);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$encoded");
        when(userMapper.insert(any(UserEntity.class))).thenReturn(1);

        UserEntity result = userService.createUser(dto);

        assertNotNull(result);
        assertEquals("newuser", result.getUsername());
        // 昵称未传时默认使用用户名
        assertEquals("newuser", result.getNickname());
        // authSource 默认 local
        assertEquals(UserConstants.AUTH_SOURCE_LOCAL, result.getAuthSource());
        // 密码已编码，不存储明文
        assertEquals("$2a$encoded", result.getPassword());
    }

    /**
     * superAdminFlag 未传时默认为 false。
     */
    @Test
    void createUser_shouldDefaultSuperAdminFlagToFalse_whenNotProvided() {
        CreateUserDTO dto = new CreateUserDTO();
        dto.setUsername("newuser");
        dto.setPassword("password123");

        when(userMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(null);
        when(passwordEncoder.encode(any())).thenReturn("$2a$encoded");
        when(userMapper.insert(any(UserEntity.class))).thenReturn(1);

        UserEntity result = userService.createUser(dto);

        assertFalse(result.getSuperAdminFlag());
    }

    // ==================== setSuperAdmin ====================

    /**
     * 内置管理员（id=1）不可修改超管标记。
     */
    @Test
    void setSuperAdmin_shouldThrow_whenTargetIsProtectedAdmin() {
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> userService.setSuperAdmin(UserConstants.PROTECTED_USER_ID, false));
        assertEquals(UserResultCode.SUPER_ADMIN_PROTECTED, ex.getResultCode());
    }
}
