package com.flavorlogic.service;

import com.flavorlogic.model.AnalysisFeedback;
import com.flavorlogic.model.AnalysisRequest;
import com.flavorlogic.model.AnalysisTask;
import com.flavorlogic.util.PageResult;

/**
 * 风味分析业务接口：项目主功能。
 */
public interface AnalysisService {

    /**
     * 创建并执行一次风味分析。
     *
     * <p>任务先以 PENDING 落库，随后流转为 RUNNING 并写入结果；
     * 计算失败时任务标记为 FAILED 并保留失败原因，便于在历史记录中排查。</p>
     *
     * @param userId  当前用户
     * @param request 分析请求
     * @return 分析任务详情（含结果）
     */
    AnalysisTask analyze(Long userId, AnalysisRequest request);

    /**
     * 分析历史分页查询。
     *
     * @param userId   当前用户
     * @param keyword  任务或配方名称关键字
     * @param goalType 分析类型筛选
     * @param status   状态筛选
     * @param page     页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    PageResult<AnalysisTask> history(Long userId, String keyword, String goalType, String status,
                                     int page, int pageSize);

    /**
     * 分析详情（含快照、偏移明细与建议）。
     *
     * @param userId 当前用户
     * @param taskId 任务编号
     * @return 任务详情
     */
    AnalysisTask detail(Long userId, Long taskId);

    /**
     * 删除历史任务（结果与反馈级联删除）。
     *
     * @param userId 当前用户
     * @param taskId 任务编号
     */
    void delete(Long userId, Long taskId);

    /**
     * 保存或更新试产反馈。
     *
     * @param userId   当前用户
     * @param taskId   任务编号
     * @param feedback 反馈内容
     * @return 保存后的反馈
     */
    AnalysisFeedback saveFeedback(Long userId, Long taskId, AnalysisFeedback feedback);

    /**
     * 管理端分页查询全部分析任务。
     *
     * @param keyword  关键字
     * @param goalType 分析类型筛选
     * @param status   状态筛选
     * @param page     页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    PageResult<AnalysisTask> adminPage(String keyword, String goalType, String status, int page, int pageSize);

    /**
     * 用户分析任务总数。
     *
     * @param userId 用户编号
     * @return 任务总数
     */
    long countByUser(Long userId);

    /**
     * 用户已完成任务数。
     *
     * @param userId 用户编号
     * @return 已完成任务数
     */
    long countCompletedByUser(Long userId);

    /**
     * 用户最近一次分析完成时间。
     *
     * @param userId 用户编号
     * @return 时间字符串，无记录返回 null
     */
    String lastAnalysisAt(Long userId);

    /**
     * 平台任务总数（管理端）。
     *
     * @return 任务总数
     */
    long countAll();
}
