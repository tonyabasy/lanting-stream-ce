package com.lanting.admin.module.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 提交文件 DTO。
 *
 * @author wangzhao
 */
@Data
@Schema(description = "提交文件请求")
public class CommitFileDTO {

    /** 待提交文件相对路径列表 */
    @NotEmpty(message = "提交文件列表不能为空")
    @Schema(description = "待提交文件相对路径列表")
    private List<@NotBlank String> paths;

    /** commit message */
    @NotBlank(message = "commit message 不能为空")
    @Schema(description = "commit message")
    private String message;
}
