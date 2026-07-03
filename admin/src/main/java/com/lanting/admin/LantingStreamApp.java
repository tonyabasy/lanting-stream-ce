package com.lanting.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

import java.io.File;

/**
 * @author wangzhao
 */
@EnableAsync
@SpringBootApplication
public class LantingStreamApp {

    public static void main(String[] args) {
        // SQLite/Flyway 不会自动创建数据库文件所在的父目录，
        // 必须在 Spring 上下文初始化（数据源连接）之前手动确保目录存在。
        initDataDirs();
        SpringApplication.run(LantingStreamApp.class, args);
    }

    private static void initDataDirs() {
        String dataDir = System.getenv("LANTING_DATA_DIR");
        if (dataDir == null || dataDir.isBlank()) {
            dataDir = "./.data";
        }
        // 需要与 application.yml 中 lanting.data.* 配置项保持一致
        mkdirs(dataDir);
        mkdirs(dataDir + "/workspaces");
        mkdirs(dataDir + "/udfs");
    }

    private static void mkdirs(String path) {
        File dir = new File(path);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("无法创建数据目录: " + dir.getAbsolutePath());
        }
    }
}