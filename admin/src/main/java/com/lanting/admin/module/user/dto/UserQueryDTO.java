package com.lanting.admin.module.user.dto;

import com.lanting.admin.common.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户分页查询 DTO。
 *
 * @author wangzhao
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserQueryDTO extends PageQuery {

    /** 搜索关键词 */
    private String keyword;

    /** 搜索字段：username / nickname / email，为空时模糊匹配全部字段 */
    private String searchField;
}
