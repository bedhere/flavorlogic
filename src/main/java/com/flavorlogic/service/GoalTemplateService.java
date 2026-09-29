package com.flavorlogic.service;

import com.flavorlogic.model.FlavorVector;
import com.flavorlogic.model.GoalVector;
import com.flavorlogic.model.RegionProfile;

/**
 * 目标模板业务接口：把「研发目标」展开为目标向量。
 *
 * <p>这是配方生成器的输入侧（见《项目介绍》4.3）。展开规则全部来自
 * {@code goal_target} 表，本层只负责查表、折算与截断，不写死任何目标语义。</p>
 */
public interface GoalTemplateService {

    /**
     * 展开研发目标为目标向量。
     *
     * @param goalType 研发目标类型（{@code AnalysisTask.GOAL_*}），为空时按 GENERAL 处理
     * @param baseline 基准配方的风味向量，用于把相对倍数折算成具体目标值
     * @param region   所选区域画像，可为 null（未选区域）
     * @return 目标向量：八维各自的意图与目标值，以及成分层动作
     */
    GoalVector expand(String goalType, FlavorVector baseline, RegionProfile region);
}
