package com.lanting.admin.module.table.controller;

import com.lanting.admin.common.result.Result;
import com.lanting.admin.module.table.dto.CreateTableRequest;
import com.lanting.admin.module.table.dto.UpdateTableRequest;
import com.lanting.admin.module.table.service.TableService;
import com.lanting.admin.module.table.vo.TableCreatedVO;
import com.lanting.admin.module.table.vo.TableVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.lanting.admin.common.util.SecurityUtils.currentUser;

/**
 * 表管理接口。
 *
 * @author wangzhao
 */
@Tag(name = "表管理")
@Validated
@RestController
@RequestMapping("/api/tables")
public class TableController {

    private final TableService tableService;

    public TableController(TableService tableService) {
        this.tableService = tableService;
    }

    @Operation(summary = "创建表")
    @PostMapping
    public Result<TableCreatedVO> create(@RequestBody @Valid CreateTableRequest request) {
        return Result.success(tableService.createTable(request.getPath(), request.getDdl(), currentUser()));
    }

    @Operation(summary = "更新表")
    @PutMapping("/{tableId}")
    public Result<Void> update(@PathVariable @NotNull Long tableId,
                               @RequestBody @Valid UpdateTableRequest request) {
        tableService.updateTable(tableId, request.getDdl(), currentUser());
        return Result.success();
    }

    @Operation(summary = "删除表")
    @DeleteMapping("/{tableId}")
    public Result<Void> delete(@PathVariable @NotNull Long tableId) {
        tableService.deleteTable(tableId, currentUser());
        return Result.success();
    }

    @Operation(summary = "查询全部表")
    @GetMapping
    public Result<List<TableVO>> list() {
        return Result.success(tableService.listTables());
    }

    @Operation(summary = "查询表详情")
    @GetMapping("/{tableId}")
    public Result<TableVO> get(@PathVariable @NotNull Long tableId) {
        return Result.success(tableService.getTable(tableId));
    }

    @Operation(summary = "搜索表")
    @GetMapping("/search")
    public Result<List<TableVO>> search(@RequestParam("q") @NotBlank String keyword) {
        return Result.success(tableService.searchTables(keyword));
    }

    @Operation(summary = "表检查（占位）")
    @PostMapping("/{tableId}/check")
    public Result<Void> check(@PathVariable @NotNull Long tableId) {
        return Result.success();
    }

    @Operation(summary = "表拉取（占位）")
    @PostMapping("/pull")
    public Result<Void> pull() {
        return Result.success();
    }
}
