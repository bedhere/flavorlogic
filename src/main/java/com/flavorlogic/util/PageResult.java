package com.flavorlogic.util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 通用分页结果。
 *
 * @param <T> 行数据类型
 */
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页数据 */
    private List<T> list = new ArrayList<>();
    /** 总记录数 */
    private long total;
    /** 当前页码（从 1 开始） */
    private int page = 1;
    /** 每页条数 */
    private int pageSize = 10;
    /** 总页数 */
    private int totalPages;

    public PageResult() {
    }

    public PageResult(List<T> list, long total, int page, int pageSize) {
        this.list = list == null ? Collections.emptyList() : list;
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
        this.totalPages = pageSize <= 0 ? 0 : (int) ((total + pageSize - 1) / pageSize);
    }

    /**
     * 空分页结果。
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @param <T>      行数据类型
     * @return 空结果
     */
    public static <T> PageResult<T> empty(int page, int pageSize) {
        return new PageResult<>(Collections.emptyList(), 0L, page, pageSize);
    }

    public List<T> getList() {
        return list;
    }

    public void setList(List<T> list) {
        this.list = list;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }
}
