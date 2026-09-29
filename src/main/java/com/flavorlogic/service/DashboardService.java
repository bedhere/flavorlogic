package com.flavorlogic.service;

import java.util.Map;

/**
 * 研发工作台业务接口：汇总用户最需要的信息与入口。
 */
public interface DashboardService {

    /**
     * 工作台概览数据。
     *
     * @param userId 当前用户编号
     * @param admin  是否为管理员（管理员额外返回平台级统计与操作日志）
     * @return 概览数据 Map
     */
    Map<String, Object> overview(Long userId, boolean admin);
}
