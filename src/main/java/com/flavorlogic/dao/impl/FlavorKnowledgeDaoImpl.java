package com.flavorlogic.dao.impl;

import com.flavorlogic.dao.FlavorKnowledgeDao;
import com.flavorlogic.dao.JdbcHelper;
import com.flavorlogic.model.FlavorKnowledge;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 配料调配知识卡数据访问实现（表 {@code flavor_knowledge}）。
 *
 * <p>全部 SQL 通过 {@link JdbcHelper} 参数化执行；连接、事务由 Service 层管理，
 * 本类不关闭、不提交、不回滚传入的 {@link Connection}。</p>
 */
public class FlavorKnowledgeDaoImpl implements FlavorKnowledgeDao {

    /** 查询列：显式列出，不使用通配符取列。 */
    private static final String COLUMNS = "id, knowledge_code, title, dimension, goal_type, trigger_constraint, "
            + "scene, direction, candidate_ingredient, candidate_category, amount_min, amount_max, amount_unit, "
            + "ratio_note, mechanism, risk_note, allergen_tags, evidence_source, keywords, priority, status";

    @Override
    public List<FlavorKnowledge> listEnabled(Connection conn) {
        // priority 升序即匹配优先级（数值越小越优先），与建表注释一致
        String sql = "SELECT " + COLUMNS + " FROM flavor_knowledge "
                + "WHERE status = 1 ORDER BY dimension, priority, id";
        return JdbcHelper.queryList(conn, sql, FlavorKnowledgeDaoImpl::mapRow);
    }

    /**
     * 将结果集当前行映射为知识卡对象。
     *
     * @param rs 结果集
     * @return 知识卡对象
     * @throws SQLException 读取失败
     */
    private static FlavorKnowledge mapRow(ResultSet rs) throws SQLException {
        FlavorKnowledge card = new FlavorKnowledge();
        card.setId(rs.getLong("id"));
        card.setKnowledgeCode(rs.getString("knowledge_code"));
        card.setTitle(rs.getString("title"));
        card.setDimension(rs.getString("dimension"));
        card.setGoalType(rs.getString("goal_type"));
        card.setTriggerConstraint(rs.getString("trigger_constraint"));
        card.setScene(rs.getString("scene"));
        card.setDirection(rs.getString("direction"));
        card.setCandidateIngredient(rs.getString("candidate_ingredient"));
        card.setCandidateCategory(rs.getString("candidate_category"));
        // 用量区间允许为空（部分卡片只给方向不给幅度），因此这里不做零值兜底
        card.setAmountMin(rs.getBigDecimal("amount_min"));
        card.setAmountMax(rs.getBigDecimal("amount_max"));
        card.setAmountUnit(rs.getString("amount_unit"));
        card.setRatioNote(rs.getString("ratio_note"));
        card.setMechanism(rs.getString("mechanism"));
        card.setRiskNote(rs.getString("risk_note"));
        card.setAllergenTags(rs.getString("allergen_tags"));
        card.setEvidenceSource(rs.getString("evidence_source"));
        card.setKeywords(rs.getString("keywords"));
        card.setPriority(JdbcHelper.getInteger(rs, "priority"));
        card.setStatus(JdbcHelper.getInteger(rs, "status"));
        return card;
    }
}
