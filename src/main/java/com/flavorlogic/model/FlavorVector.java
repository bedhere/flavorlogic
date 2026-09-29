package com.flavorlogic.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 八维风味向量：一份配方在八个维度上的加权得分（0-100）。
 *
 * <p>计算口径见项目介绍 7.3 / 12.1：
 * 单一维度得分 = Σ（食材占比 × 该食材的维度属性值），
 * 其中食材占比 = 该食材用量 / 配方总用量，用于消除总量差异带来的不可比性。</p>
 */
public class FlavorVector implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 展示精度：保留两位小数 */
    public static final int SCALE = 2;

    /** 维度得分，顺序与 {@link FlavorDimensions#ALL} 一致 */
    private Map<String, BigDecimal> scores = new LinkedHashMap<>();
    /** 配方总用量 */
    private BigDecimal totalAmount = BigDecimal.ZERO;
    /** 参与计算的配料条数 */
    private Integer itemCount = 0;

    public FlavorVector() {
        for (String dimension : FlavorDimensions.ALL) {
            scores.put(dimension, BigDecimal.ZERO);
        }
    }

    /**
     * 归一化到两位小数。
     *
     * @param value 原始值
     * @return 展示值
     */
    public static BigDecimal normalize(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 取维度得分。
     *
     * @param dimension 维度编码
     * @return 得分，缺失返回 0
     */
    public BigDecimal get(String dimension) {
        BigDecimal value = scores.get(dimension);
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 设置维度得分（自动归一化）。
     *
     * @param dimension 维度编码
     * @param value     得分
     */
    public void put(String dimension, BigDecimal value) {
        scores.put(dimension, normalize(value));
    }

    /**
     * 累加维度得分。
     *
     * @param dimension 维度编码
     * @param value     增量
     */
    public void add(String dimension, BigDecimal value) {
        BigDecimal current = get(dimension);
        scores.put(dimension, normalize(current.add(value == null ? BigDecimal.ZERO : value)));
    }

    /**
     * 按区域权重加权（区域适配场景）。
     *
     * @param weights 维度 → 权重
     * @return 新的加权向量，不修改当前对象
     */
    public FlavorVector weighted(Map<String, BigDecimal> weights) {
        FlavorVector result = new FlavorVector();
        result.setTotalAmount(totalAmount);
        result.setItemCount(itemCount);
        if (weights == null || weights.isEmpty()) {
            for (String dimension : FlavorDimensions.ALL) {
                result.put(dimension, get(dimension));
            }
            return result;
        }
        for (String dimension : FlavorDimensions.ALL) {
            BigDecimal weight = weights.get(dimension);
            result.put(dimension, get(dimension).multiply(weight == null ? BigDecimal.ONE : weight));
        }
        return result;
    }

    /**
     * 深拷贝。
     *
     * @return 新的向量对象
     */
    public FlavorVector copy() {
        FlavorVector copy = new FlavorVector();
        copy.setTotalAmount(totalAmount);
        copy.setItemCount(itemCount);
        for (String dimension : FlavorDimensions.ALL) {
            copy.put(dimension, get(dimension));
        }
        return copy;
    }

    /**
     * 八维得分之和，用于粗略判断整体浓度。
     *
     * @return 合计值
     */
    public BigDecimal sum() {
        BigDecimal total = BigDecimal.ZERO;
        for (String dimension : FlavorDimensions.ALL) {
            total = total.add(get(dimension));
        }
        return normalize(total);
    }

    public Map<String, BigDecimal> getScores() { return scores; }
    public void setScores(Map<String, BigDecimal> scores) { this.scores = scores; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public Integer getItemCount() { return itemCount; }
    public void setItemCount(Integer itemCount) { this.itemCount = itemCount; }
}
