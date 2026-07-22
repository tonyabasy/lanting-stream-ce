package com.lanting.admin.module.publish.controller;

import com.lanting.admin.common.page.PageQuery;
import com.lanting.admin.common.page.PageResult;
import com.lanting.admin.common.result.Result;
import com.lanting.admin.module.publish.dto.PublishDTO;
import com.lanting.admin.module.publish.dto.ReviewDTO;
import com.lanting.admin.module.publish.service.PublishService;
import com.lanting.admin.module.publish.service.ReviewService;
import com.lanting.admin.module.publish.vo.CommitVO;
import com.lanting.admin.module.publish.vo.PublishVO;
import com.lanting.admin.module.publish.vo.ReviewVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.lanting.admin.common.util.SecurityUtils.currentUser;

/**
 * 发布与评审接口（v2）。前缀统一为 /api/publish。
 *
 * @author wangzhao
 */
@Tag(name = "发布与评审")
@RestController
@RequestMapping("/api/publish")
public class PublishController {

    private final PublishService publishService;
    private final ReviewService reviewService;

    public PublishController(PublishService publishService, ReviewService reviewService) {
        this.publishService = publishService;
        this.reviewService = reviewService;
    }

    @Operation(summary = "提交文件（git commit + 加入待发布池）")
    @PostMapping("/committed")
    public Result<Void> addCommittedList(@Valid @RequestBody PublishDTO dto) {
        publishService.addCommittedList(dto.getFileIds(), dto.getMessage());
        return Result.success();
    }

    @Operation(summary = "取消提交（撤销候选）")
    @PostMapping("/committed/cancel")
    public Result<Void> cancelCommitted(@Valid @RequestBody PublishDTO dto) {
        publishService.cancelPublish(dto.getFileIds());
        return Result.success();
    }

    @Operation(summary = "待发布列表")
    @GetMapping("/committed")
    public Result<PageResult<CommitVO>> listCommitted(PageQuery query) {
        return Result.success(publishService.listCommittedPages(query));
    }

    @Operation(summary = "发布场景 diff：当前 vs 上次发布版本")
    @GetMapping("/diff/{fileId}")
    public Result<String> diff(@PathVariable Long fileId) {
        return Result.success(publishService.diff(fileId));
    }

    @Operation(summary = "执行发布")
    @PostMapping
    public Result<PublishVO> publish(@Valid @RequestBody PublishDTO dto) {
        return Result.success(publishService.publish(dto.getFileIds(), dto.getDisplayName()));
    }

    @Operation(summary = "已发布批次列表")
    @GetMapping
    public Result<PageResult<PublishVO>> listPublished(PageQuery query,
            @RequestParam(required = false) String keyword) {
        return Result.success(publishService.listPublished(query, keyword));
    }

    @Operation(summary = "发布批次详情")
    @GetMapping("/{id}")
    public Result<PublishVO> getPublish(@PathVariable String id) {
        return Result.success(publishService.getPublish(id));
    }

    @Operation(summary = "提交 CR 评审")
    @PostMapping("/reviews/{fileId}")
    public Result<Void> addReview(@PathVariable Long fileId, @Valid @RequestBody ReviewDTO dto) {
        reviewService.review(fileId, dto.getCommitHash(), dto.getResult(), dto.getComment());
        return Result.success();
    }

    @Operation(summary = "删除自己的 CR 评审")
    @DeleteMapping("/reviews/{reviewId}")
    public Result<Void> deleteReview(@PathVariable Long reviewId) {
        reviewService.delete(reviewId);
        return Result.success();
    }

    @Operation(summary = "查询 CR 评审记录")
    @GetMapping("/reviews/{fileId}")
    public Result<List<ReviewVO>> listReviews(@PathVariable Long fileId,
            @RequestParam(required = false) String commitHash) {
        return Result.success(reviewService.list(fileId, commitHash));
    }
}
