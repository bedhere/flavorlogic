package com.flavorlogic.model;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 单维度偏移明细：基准值、目标值、绝对差值、变化比例与状态。
 *
 * <p>为防止基准值接近零时比例异常，比值计算使用最小阈值
 * {@link #MIN_BASELINE}，同时始终展示绝对差值。</p>
 */
public class FlavorDelta implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态：下降 */
    public static final String STATUS_DOWN = "DOWN";
    /** 状态：稳定 */
    public static final String STATUS_STABLE = "STABLE";
    /** 状态：上升 */
    public static final String STATUS_UP = "UP";
    /** 状态：超出建议范围（由规则命中标记） */
    public static final String STATUS_OUT_OF_RANGE = "OUT_OF_RANGE";

    /** 变化比例绝对值小于该值视为稳定（%） */
    public static final BigDecimal STABLE_THRESHOLD = new BigDecimal("5");
    /** 计算比例时的基准最小阈值，避免除零 */
    public static final BigDecimal MIN_BASELINE = new BigDecimal("1");
    /**
     * 基准值低于该值时，相对变化比例不具参考意义。
     * 0-100 量表上低于 5 基本等于「感知不到」：基准 1.58 → 目标 10.95 会算成 +593%，
     * 但语义只是「从几乎不甜变成有点甜」。此常量供摘要文案与前端判读统一使用。
     */
    public static final BigDecimal LOW_BASELINE = new BigDecimal("5");

    private String dimension;
    private String dimensionLabel;
    private BigDecimal baseline = BigDecimal.ZERO;
    private BigDecimal target = BigDecimal.ZERO;
    private BigDecimal diff = BigDecimal.ZERO;
    private BigDecimal changePercent = BigDecimal.ZERO;
    private String status = STATUS_STABLE;
    private String statusText = "稳定";
    /** 变化方向：下降 / 稳定 / 上升（超出建议范围时仍保留方向，便于界面同时展示） */
    private String direction = "稳定";
    /** 主要贡献说明，例如“鸡胸肉占比 45%，脂香 25” */
    private String note;

    public FlavorDelta() {
    }

    public FlavorDelta(String dimension, BigDecimal baseline, BigDecimal target) {
        this.dimension = dimension;
        this.dimensionLabel = FlavorDimensions.label(dimension);
        this.baseline = FlavorVector.normalize(baseline);
        this.target = FlavorVector.normalize(target);
        this.diff = FlavorVector.normalize(this.target.subtract(this.baseline));
        BigDecimal base = this.baseline.abs().compareTo(MIN_BASELINE) < 0 ? MIN_BASELINE : this.baseline.abs();
        this.changePercent = this.diff.multiply(new BigDecimal("100"))
                .divide(base, 2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * 根据变化比例刷新状态与状态文案。
     */
    public void refreshStatus() {
        if (changePercent == null) {
            status = STATUS_STABLE;
            statusText = "稳定";
            direction = "稳定";
            return;
        }
        if (changePercent.abs().compareTo(STABLE_THRESHOLD) < 0) {
            status = STATUS_STABLE;
            statusText = "稳定";
            direction = "稳定";
        } else if (changePercent.signum() < 0) {
            status = STATUS_DOWN;
            statusText = "下降";
            direction = "下降";
        } else {
            status = STATUS_UP;
            statusText = "上升";
            direction = "上升";
        }
    }

    /**
     * 标记为超出建议范围。
     *
     * @param note 说明
     */
    public void markOutOfRange(String note) {
        this.status = STATUS_OUT_OF_RANGE;
        this.statusText = "超出建议范围";
        this.note = note;
    }

    /**
     * 基准值是否过低（低于 {@link #LOW_BASELINE}），此时相对变化比例会被放大。
     *
     * @return 是否属于「基准极低」
     */
    public boolean isLowBaseline() {
        return baseline != null && baseline.abs().compareTo(LOW_BASELINE) < 0;
    }

    public String getDimension() { return dimension; }
    public void setDimension(String dimension) { this.dimension = dimension; }

    public String getDimensionLabel() { return dimensionLabel; }
    public void setDimensionLabel(String dimensionLabel) { this.dimensionLabel = dimensionLabel; }

    public BigDecimal getBaseline() { return baseline; }
    public void setBaseline(BigDecimal baseline) { this.baseline = baseline; }

    public BigDecimal getTarget() { return target; }
    public void setTarget(BigDecimal target) { this.target = target; }

    public BigDecimal getDiff() { return diff; }
    public void setDiff(BigDecimal diff) { this.diff = diff; }

    public BigDecimal getChangePercent() { return changePercent; }
    public void setChangePercent(BigDecimal changePercent) { this.changePercent = changePercent; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getStatusText() { return statusText; }
    public void setStatusText(String statusText) { this.statusText = statusText; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
