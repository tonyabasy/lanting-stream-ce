package com.lanting.admin.module.file.service;

import com.lanting.admin.common.exception.BusinessException;
import com.lanting.admin.module.file.entity.WorkspaceEntity;
import com.lanting.admin.module.file.mapper.WorkspaceMapper;
import com.lanting.admin.module.file.result.FileResultCode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.Git;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 工作空间服务。
 *
 * @author wangzhao
 */
@Slf4j
@Service
public class WorkspaceService {

    @Autowired
    private WorkspaceMapper workspaceMapper;

    @Autowired
    private FileIndexService fileIndexService;

    @Value("${lanting.data.workspace-dir}")
    private String workspaceDir;

    /**
     * 默认工作空间 ID。社区版只支持一个工作空间。
     */
    private static final Long DEFAULT_WORKSPACE_ID = 1L;

    /**
     * 默认子目录。
     */
    private static final List<String> DEFAULT_DIRS = List.of("ddl", "jobs", "docs");

    /**
     * 应用启动时初始化默认工作空间。
     */
    @PostConstruct
    public void initDefaultWorkspace() {
        WorkspaceEntity workspace = workspaceMapper.selectById(DEFAULT_WORKSPACE_ID);
        if (workspace == null) {
            workspace = new WorkspaceEntity();
            workspace.setId(DEFAULT_WORKSPACE_ID);
            workspace.setName("default");
            workspace.setGitPath(resolveRootPath());
            workspace.setDescription("默认工作空间");
            workspace.setCreatedBy("admin");
            workspaceMapper.insert(workspace);
        }
        initializeWorkspace(workspace);
    }

    /**
     * 获取默认工作空间。
     *
     * @return 工作空间
     */
    public WorkspaceEntity getDefaultWorkspace() {
        WorkspaceEntity workspace = workspaceMapper.selectById(DEFAULT_WORKSPACE_ID);
        if (workspace == null) {
            throw new BusinessException(FileResultCode.WORKSPACE_NOT_FOUND);
        }
        return workspace;
    }

    /**
     * 获取默认工作空间根目录路径。
     *
     * @return 根目录 Path
     */
    public Path getDefaultWorkspaceRoot() {
        String gitPath = getDefaultWorkspace().getGitPath();
        Path path = Path.of(gitPath);
        if (!path.isAbsolute()) {
            // 如果 gitPath 配置的是相对路径（比如 ../data/workspace 或 data/workspace），就
            // 以 JVM 启动时的工作目录 user.dir 为基准，解析出绝对路径。
            path = Path.of(System.getProperty("user.dir")).resolve(path);
        }
        return path.toAbsolutePath().normalize();
    }

    /**
     * 初始化工作空间：创建目录、git init、初始空 commit、建立文件索引。
     *
     * @param workspace 工作空间
     */
    public void initializeWorkspace(WorkspaceEntity workspace) {
        Path root = Path.of(workspace.getGitPath());
        try {
            Files.createDirectories(root);

            // 创建默认子目录，目录结构由 DB 索引维护，不再写入 .gitkeep
            for (String dir : DEFAULT_DIRS) {
                Path dirPath = root.resolve(dir);
                Files.createDirectories(dirPath);
            }

            Path gitDir = root.resolve(".git");
            if (!Files.exists(gitDir)) {
                Git git = Git.init().setDirectory(root.toFile()).call();
                // 空目录无法产生有意义 commit，做空提交保证 HEAD 存在
                git.commit()
                        .setMessage("init: workspace initialized")
                        .setAuthor("system", "system@lanting.io")
                        .setAllowEmpty(true)
                        .call();
                git.close();
                log.info("工作空间 Git 初始化完成：{}，workspaceId: {}", root, workspace.getId());
            }

            // 扫描磁盘建立文件索引
            fileIndexService.scanAndIndex(root);
            log.info("工作空间索引初始化完成：{}，workspaceId: {}", root, workspace.getId());
        } catch (Exception e) {
            log.error("工作空间初始化失败：{}", root, e);
            throw new BusinessException(FileResultCode.GIT_OPERATION_FAILED, e.getMessage());
        }
    }

    /**
     * 解析默认工作空间根目录。基于配置项 {@code lanting.data.workspace-dir}，
     * 默认值为 {@code ./data/workspaces/default}。
     *
     * @return 根目录路径
     */
    private String resolveRootPath() {
        return workspaceDir + "/default";
    }

    /**
     * 读取默认工作空间配置。
     *
     * @return 配置 JSON 字符串，未设置时返回空对象字符串
     */
    public String getDefaultWorkspaceConfig() {
        String config = getDefaultWorkspace().getConfig();
        return StringUtils.isBlank(config) ? "{}" : config;
    }

    /**
     * 保存默认工作空间配置。
     *
     * @param config 配置 JSON 字符串
     */
    public void updateDefaultWorkspaceConfig(String config) {
        WorkspaceEntity workspace = getDefaultWorkspace();
        workspace.setConfig(StringUtils.isBlank(config) ? "{}" : config);
        workspaceMapper.updateById(workspace);
    }
}
