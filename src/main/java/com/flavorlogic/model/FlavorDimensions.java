package com.flavorlogic.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 八维风味维度定义。
 *
 * <p>前五个维度是舌头能直接说出的“可见区”（甜、咸、酸、苦、鲜），
 * 后三个是真实存在但说不清的“不可见区”（辣、麻、脂香）。</p>
 */
public final class FlavorDimensions {

    public static final String SWEET = "sweet";
    public static final String SALTY = "salty";
    public static final String SOUR = "sour";
    public static final String BITTER = "bitter";
    public static final String UMAMI = "umami";
    public static final String SPICY = "spicy";
    public static final String NUMBING = "numbing";
    public static final String FAT_AROMA = "fat_aroma";

    /** 全部维度，顺序固定，用于雷达图与表格 */
    public static final List<String> ALL = Collections.unmodifiableList(Arrays.asList(
            SWEET, SALTY, SOUR, BITTER, UMAMI, SPICY, NUMBING, FAT_AROMA));

    /** 可见区维度 */
    public static final List<String> VISIBLE = Collections.unmodifiableList(Arrays.asList(
            SWEET, SALTY, SOUR, BITTER, UMAMI));

    /** 不可见区维度 */
    public static final List<String> INVISIBLE = Collections.unmodifiableList(Arrays.asList(
            SPICY, NUMBING, FAT_AROMA));

    private static final Map<String, String> LABELS;

    static {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(SWEET, "甜");
        labels.put(SALTY, "咸");
        labels.put(SOUR, "酸");
        labels.put(BITTER, "苦");
        labels.put(UMAMI, "鲜");
        labels.put(SPICY, "辣");
        labels.put(NUMBING, "麻");
        labels.put(FAT_AROMA, "脂香");
        LABELS = Collections.unmodifiableMap(labels);
    }

    private FlavorDimensions() {
    }

    /**
     * 维度中文名。
     *
     * @param dimension 维度编码
     * @return 中文名，未知维度返回原编码
     */
    public static String label(String dimension) {
        return LABELS.getOrDefault(dimension, dimension);
    }

    /**
     * 是否为合法维度。
     *
     * @param dimension 维度编码
     * @return 是否合法
     */
    public static boolean isDimension(String dimension) {
        return dimension != null && LABELS.containsKey(dimension);
    }

    /**
     * 维度中文名映射。
     *
     * @return 不可变映射
     */
    public static Map<String, String> labels() {
        return LABELS;
    }
}
