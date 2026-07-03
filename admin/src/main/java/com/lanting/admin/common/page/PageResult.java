package com.lanting.admin.common.page;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.util.List;

/**
 * 统一分页响应结构。
 * <p>
 * 不直接暴露 MyBatis-Plus 的 {@link IPage} 对象，屏蔽 ORM 框架实现细节，
 * 保证 API 契约与底层框架解耦。
 * <p>
 * 通过 {@link #of(IPage)} 从 MyBatis-Plus 分页结果转换，转换收口在 Service 层，
 * {@link IPage} 对象不跨出 Service 层。
 *
 * @param <T> 列表元素类型
 * @author wangzhao
 */
@Schema(description = "分页响应")
@Getter
public class PageResult<T> {

    /** 当前页数据列表 */
    @Schema(description = "当前页数据列表")
    private final List<T> records;

    /** 总记录数 */
    @Schema(description = "总记录数")
    private final long total;

    /** 当前页码 */
    @Schema(description = "当前页码")
    private final long pageNum;

    /** 每页条数 */
    @Schema(description = "每页条数")
    private final long pageSize;

    /** 总页数 */
    @Schema(description = "总页数")
    private final long totalPages;

    private PageResult(List<T> records, long total, long pageNum, long pageSize, long totalPages) {
        this.records = records;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.totalPages = totalPages;
    }

    /**
     * 从 MyBatis-Plus 分页结果转换，统一转换入口。
     */
    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(
                page.getRecords(),
                page.getTotal(),
                page.getCurrent(),
                page.getSize(),
                page.getPages()
        );
    }
}
