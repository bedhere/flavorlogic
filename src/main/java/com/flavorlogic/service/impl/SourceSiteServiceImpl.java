package com.flavorlogic.service.impl;

import com.flavorlogic.dao.SourceSiteDao;
import com.flavorlogic.dao.impl.SourceSiteDaoImpl;
import com.flavorlogic.model.SourceSite;
import com.flavorlogic.service.BaseService;
import com.flavorlogic.service.SourceSiteService;
import com.flavorlogic.util.BizException;
import com.flavorlogic.util.TextUtil;
import com.flavorlogic.util.ValidationUtil;

import java.util.List;

/** 来源站点白名单业务：默认只允许登记，不自动开启抓取。 */
public class SourceSiteServiceImpl extends BaseService implements SourceSiteService {

    private static final String DEFAULT_SOURCE_TYPE = "WEB";
    private static final int DEFAULT_INTERVAL_MS = 2000;
    private final SourceSiteDao siteDao = new SourceSiteDaoImpl();

    @Override
    public List<SourceSite> list(boolean enabledOnly) {
        return read(conn -> siteDao.list(conn, enabledOnly));
    }

    @Override
    public SourceSite detail(Long id) {
        if (id == null) {
            throw BizException.badRequest("缺少来源站点编号");
        }
        return read(conn -> {
            SourceSite site = siteDao.findById(conn, id);
            if (site == null) {
                throw BizException.notFound("来源站点不存在");
            }
            return site;
        });
    }

    @Override
    public SourceSite create(SourceSite site) {
        SourceSite normalized = normalize(site);
        return write(conn -> {
            if (siteDao.findByBaseUrl(conn, normalized.getBaseUrl()) != null) {
                throw BizException.conflict("相同根地址的来源站点已存在");
            }
            normalized.setId(siteDao.insert(conn, normalized));
            return siteDao.findById(conn, normalized.getId());
        });
    }

    @Override
    public SourceSite update(Long id, SourceSite site) {
        if (id == null) {
            throw BizException.badRequest("缺少来源站点编号");
        }
        SourceSite normalized = normalize(site);
        normalized.setId(id);
        return write(conn -> {
            if (siteDao.findById(conn, id) == null) {
                throw BizException.notFound("来源站点不存在");
            }
            SourceSite sameUrl = siteDao.findByBaseUrl(conn, normalized.getBaseUrl());
            if (sameUrl != null && !id.equals(sameUrl.getId())) {
                throw BizException.conflict("相同根地址的来源站点已存在");
            }
            siteDao.update(conn, normalized);
            return siteDao.findById(conn, id);
        });
    }

    private SourceSite normalize(SourceSite source) {
        if (source == null) {
            throw BizException.badRequest("缺少来源站点内容");
        }
        SourceSite site = new SourceSite();
        site.setSiteName(ValidationUtil.requireText(source.getSiteName(), "站点名称", 120));
        site.setBaseUrl(requireHttpUrl(source.getBaseUrl(), "站点根地址"));
        site.setRobotsUrl(ValidationUtil.optionalUrl(source.getRobotsUrl(), "robots 地址"));
        site.setTermsUrl(ValidationUtil.optionalUrl(source.getTermsUrl(), "条款地址"));
        if (site.getRobotsUrl() != null) {
            try {
                if (!com.flavorlogic.util.RobotsPolicy.sameHost(site.getBaseUrl(), site.getRobotsUrl())) {
                    throw BizException.badRequest("robots 地址必须属于站点根域名");
                }
            } catch (java.io.IOException e) {
                throw BizException.badRequest("robots 地址格式不合法");
            }
        }
        site.setLicenseType(ValidationUtil.optionalText(source.getLicenseType(), 80, "许可类型"));
        site.setSourceType(ValidationUtil.requireOneOf(TextUtil.defaultIfBlank(
                ValidationUtil.optionalText(source.getSourceType(), 30, "来源类型"), DEFAULT_SOURCE_TYPE),
                "来源类型", "API", "PAPER", "STANDARD", "WEB", "DATASET"));
        site.setCrawlAllowed(oneOrZero(source.getCrawlAllowed(), "自动获取开关"));
        site.setRequestIntervalMs(interval(source.getRequestIntervalMs()));
        site.setStatus(oneOrZero(source.getStatus(), "站点状态"));
        site.setNotes(ValidationUtil.optionalText(source.getNotes(), 500, "站点备注"));
        return site;
    }

    private String requireHttpUrl(String value, String field) {
        String url = ValidationUtil.requireText(value, field, 500);
        url = ValidationUtil.optionalUrl(url, field);
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            throw BizException.badRequest(field + "必须使用 http:// 或 https://");
        }
        return url;
    }

    private Integer interval(Integer value) {
        int result = value == null ? DEFAULT_INTERVAL_MS : value;
        if (result < 500 || result > 600000) {
            throw BizException.badRequest("请求间隔必须在 500 到 600000 毫秒之间");
        }
        return result;
    }

    private Integer oneOrZero(Integer value, String field) {
        int result = value == null ? ("站点状态".equals(field) ? 1 : 0) : value;
        if (result != 0 && result != 1) {
            throw BizException.badRequest(field + "只能是 0 或 1");
        }
        return result;
    }
}
