package com.flavorlogic.dao;

import com.flavorlogic.model.FlavorKnowledge;

import java.sql.Connection;
import java.util.List;

/**
 * 配料调配知识卡数据访问（表 {@code flavor_knowledge}）。
 *
 * <p>本接口只提供风味分析链路当前需要的读取能力；管理端的增删改在启用后台维护功能时再补充，
 * 避免留下没有调用方的空方法。</p>
 */
public interface FlavorKnowledgeDao {

    /**
     * 查询全部启用中的知识卡，按维度与优先级排序。
     *
     * <p>知识卡总量在课设阶段为数十条量级，分析时一次加载并在内存中按
     * 「维度 + 目标类型」建索引即可，无需逐维度发 SQL。</p>
     *
     * @param conn 数据库连接
     * @return 知识卡列表，按 dimension、priority、id 升序
     */
    List<FlavorKnowledge> listEnabled(Connection conn);
}
