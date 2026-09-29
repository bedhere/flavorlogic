package com.flavorlogic.servlet;

import com.flavorlogic.model.AnalysisFeedback;
import com.flavorlogic.model.AnalysisRequest;
import com.flavorlogic.service.Services;
import com.flavorlogic.util.BizException;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 风味分析接口：{@code /api/analysis/*}（项目主功能）。
 *
 * <ul>
 *   <li>POST   /api/analysis —— 创建并执行一次分析</li>
 *   <li>GET    /api/analysis/history —— 分析历史分页</li>
 *   <li>GET    /api/analysis/{id} —— 分析详情（快照 + 偏移 + 建议）</li>
 *   <li>DELETE /api/analysis/{id} —— 删除历史任务</li>
 *   <li>POST   /api/analysis/{id}/feedback —— 保存试产反馈</li>
 * </ul>
 */
@WebServlet("/api/analysis/*")
public class AnalysisServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        String segment = pathSegment(request, 0);
        if ("history".equals(segment)) {
            handle(request, response, () -> Services.analysis().history(
                    requireUserId(request),
                    param(request, "keyword", null),
                    param(request, "goalType", null),
                    param(request, "status", null),
                    intParam(request, "page", 1),
                    intParam(request, "pageSize", 10)));
            return;
        }
        Long id = pathId(request);
        if (id == null) {
            // 必须走 handle(...)：直接抛 BizException 会冒泡到容器，变成 500 错误页，
            // 而项目约定参数问题统一返回 {code:400} / {code:404} 的 JSON，便于前端就地提示。
            final String unknown = pathSegment(request, 0);
            handle(request, response, () -> {
                if (unknown != null) {
                    throw BizException.notFound("接口不存在：/api/analysis/" + unknown);
                }
                throw BizException.badRequest("缺少分析任务编号");
            });
            return;
        }
        handle(request, response, () -> Services.analysis().detail(requireUserId(request), id));
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        Long id = pathId(request);
        if (id == null) {
            handle(request, response, () -> {
                Long userId = requireUserId(request);
                AnalysisRequest analysisRequest = readBody(request, AnalysisRequest.class);
                return Services.analysis().analyze(userId, analysisRequest);
            });
            return;
        }
        String action = pathSegment(request, 1);
        if ("feedback".equals(action)) {
            handle(request, response, () -> {
                Long userId = requireUserId(request);
                AnalysisFeedback feedback = readBody(request, AnalysisFeedback.class);
                return Services.analysis().saveFeedback(userId, id, feedback);
            });
            return;
        }
        methodNotAllowed(response);
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) {
        handle(request, response, () -> {
            Long userId = requireUserId(request);
            Long id = pathId(request);
            if (id == null) {
                throw BizException.badRequest("缺少分析任务编号");
            }
            Services.analysis().delete(userId, id);
            return null;
        });
    }
}
