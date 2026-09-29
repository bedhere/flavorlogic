package com.flavorlogic.service;

import com.flavorlogic.model.ArticleCategory;
import com.flavorlogic.model.KnowledgeArticle;
import com.flavorlogic.model.User;
import com.flavorlogic.util.PageResult;

import java.util.List;

/**
 * 研发知识库业务接口。
 */
public interface ArticleService {

    /**
     * 全部启用分类。
     *
     * @return 分类列表
     */
    List<ArticleCategory> categories();

    /**
     * 用户端分页查询已发布文章。
     *
     * @param categoryId 分类筛选，可为空
     * @param keyword    关键字，可为空
     * @param page       页码
     * @param pageSize   每页条数
     * @return 分页结果
     */
    PageResult<KnowledgeArticle> pageForUser(Long categoryId, String keyword, int page, int pageSize);

    /**
     * 文章详情：正文安全过滤 + 浏览次数累加。
     *
     * @param id 文章编号
     * @return 文章详情
     */
    KnowledgeArticle detailForUser(Long id);

    /**
     * 最新已发布文章。
     *
     * @param limit 条数
     * @return 文章列表
     */
    List<KnowledgeArticle> recent(int limit);

    /**
     * 相关文章。
     *
     * @param categoryId 分类编号，可为空
     * @param excludeId  排除的文章编号
     * @param limit      条数
     * @return 文章列表
     */
    List<KnowledgeArticle> related(Long categoryId, Long excludeId, int limit);

    /**
     * 管理端分页查询文章（含草稿与下架）。
     *
     * @param categoryId 分类筛选，可为空
     * @param keyword    关键字，可为空
     * @param status     状态筛选，可为空
     * @param page       页码
     * @param pageSize   每页条数
     * @return 分页结果
     */
    PageResult<KnowledgeArticle> adminPage(Long categoryId, String keyword, Integer status, int page, int pageSize);

    /**
     * 管理端新增文章（含重复检测）。
     *
     * @param operator 操作者
     * @param article  文章
     * @return 新增后的文章（可能带重复提示）
     */
    KnowledgeArticle adminCreate(User operator, KnowledgeArticle article);

    /**
     * 管理端编辑文章（含重复检测）。
     *
     * @param operator 操作者
     * @param article  文章（需含编号）
     * @return 更新后的文章（可能带重复提示）
     */
    KnowledgeArticle adminUpdate(User operator, KnowledgeArticle article);

    /**
     * 管理端修改文章状态。
     *
     * @param id     文章编号
     * @param status 目标状态
     */
    void adminUpdateStatus(Long id, int status);

    /**
     * 文章总数。
     *
     * @return 文章总数
     */
    long countAll();

    /**
     * 已发布文章数。
     *
     * @return 已发布文章数
     */
    long countPublished();
}
