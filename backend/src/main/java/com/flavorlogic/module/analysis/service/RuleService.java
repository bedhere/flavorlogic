package com.flavorlogic.module.analysis.service;

import com.flavorlogic.module.analysis.model.FlavorRule;
import com.flavorlogic.common.util.PageResult;
import java.util.List;

/** Rule 业务接口。 */
public interface RuleService {
    List<FlavorRule> listRules();

    PageResult<FlavorRule> adminPageRules(String keyword, String goalType, Integer status,
                                                 int page, int pageSize);

    FlavorRule adminUpdateRule(FlavorRule rule);

    void adminUpdateRuleStatus(Long id, int status);
}
