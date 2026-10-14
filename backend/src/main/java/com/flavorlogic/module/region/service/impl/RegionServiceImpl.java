package com.flavorlogic.module.region.service.impl;

import com.flavorlogic.module.region.dao.RegionProfileDao;
import com.flavorlogic.module.region.dao.impl.RegionProfileDaoImpl;
import com.flavorlogic.module.region.model.RegionProfile;
import com.flavorlogic.common.service.BaseService;
import com.flavorlogic.module.region.service.RegionService;
import java.util.List;

/** Region 业务：从原 MetaService 按实际职责拆分，保留原有校验和事务。 */
public class RegionServiceImpl extends BaseService implements RegionService {
    private final RegionProfileDao regionDao = new RegionProfileDaoImpl();

    @Override
    public List<RegionProfile> listRegions() {
        return read(regionDao::listEnabled);
    }
}
