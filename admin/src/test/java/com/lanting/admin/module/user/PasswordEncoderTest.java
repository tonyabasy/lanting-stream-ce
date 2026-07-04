package com.lanting.admin.module.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * @author wangzhao
 */
public class PasswordEncoderTest {

    @Test
    @Disabled("仅用于生成测试数据")
    @DisplayName("生成 BCrypt 密码")
    void encodePassword() {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        String encoded = encoder.encode("admin");
        System.out.println(encoded);
    }
}
