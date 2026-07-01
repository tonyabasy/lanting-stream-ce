package com.lanting.admin.common.page;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 分页查询请求基类。
 * <p>
 * 各业务模块的查询 DTO 继承此类，统一分页参数的命名和校验规则。
 *
 * @author wangzhao
 */
@Getter
@Setter
public class PageQuery {

    /**
     * 当前页码，从 1 开始。
     */
    @NotNull
    @Min(value = 1, message = "页码最小为 1")
    private Integer pageNum = 1;

    /**
     * 每页条数，业务上限 100，防止前端误传超大值。
     * 插件层另有 500 的兜底硬限制（见 MybatisPlusConfig）。
     */
    @NotNull
    @Min(value = 1, message = "每页条数最小为 1")
    @Max(value = 100, message = "每页条数最大为 100")
    private Integer pageSize = 10;
}
