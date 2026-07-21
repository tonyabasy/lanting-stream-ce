package com.lanting.admin.module.file.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.eclipse.jgit.revwalk.RevCommit;

/**
 * 文件历史 VO。
 *
 * @author wangzhao
 */
@Data
@Schema(description = "文件历史记录")
public class FileHistoryVO {

    /** commit SHA */
    @Schema(description = "commit SHA")
    private String commitHash;

    /** commit message */
    @Schema(description = "commit message")
    private String message;

    /** 操作人 username */
    @Schema(description = "操作人 username")
    private String author;

    /** commit 时间（毫秒时间戳） */
    @Schema(description = "commit 时间（毫秒时间戳）")
    private Long timestamp;

    public static FileHistoryVO of(RevCommit commit) {
        FileHistoryVO vo = new FileHistoryVO();
        vo.setCommitHash(commit.getName());
        vo.setMessage(commit.getFullMessage());
        vo.setAuthor(commit.getAuthorIdent().getName());
        // JGit 的 commitTime 是秒级时间戳，项目统一使用毫秒，需 *1000
        vo.setTimestamp(commit.getCommitTime() * 1000L);
        return vo;
    }
}
