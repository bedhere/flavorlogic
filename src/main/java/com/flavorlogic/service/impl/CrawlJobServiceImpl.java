package com.flavorlogic.service.impl;

import com.flavorlogic.dao.CrawlJobDao;
import com.flavorlogic.dao.SourceSiteDao;
import com.flavorlogic.dao.impl.CrawlJobDaoImpl;
import com.flavorlogic.dao.impl.SourceSiteDaoImpl;
import com.flavorlogic.model.CrawlJob;
import com.flavorlogic.model.SourceDocument;
import com.flavorlogic.model.SourceSite;
import com.flavorlogic.service.BaseService;
import com.flavorlogic.service.CrawlJobService;
import com.flavorlogic.service.Services;
import com.flavorlogic.util.BizException;
import com.flavorlogic.util.DateTimeUtil;
import com.flavorlogic.util.PublicDocumentFetcher;
import com.flavorlogic.util.PublicUrlGuard;
import com.flavorlogic.util.RobotsPolicy;
import com.flavorlogic.util.TextUtil;
import com.flavorlogic.util.ValidationUtil;

import java.io.IOException;
import java.util.List;

/**
 * 受控资料采集任务：每次只获取一个白名单 URL，不递归跟踪链接，不自动调度。
 */
public class CrawlJobServiceImpl extends BaseService implements CrawlJobService {

    private final CrawlJobDao jobDao = new CrawlJobDaoImpl();
    private final SourceSiteDao siteDao = new SourceSiteDaoImpl();

    @Override
    public List<CrawlJob> list(Long siteId, String status) {
        return read(conn -> jobDao.list(conn, siteId, TextUtil.isBlank(status) ? null : status.trim()));
    }

    @Override
    public CrawlJob detail(Long id) {
        if (id == null) {
            throw BizException.badRequest("缺少采集任务编号");
        }
        return read(conn -> {
            CrawlJob job = jobDao.findById(conn, id);
            if (job == null) {
                throw BizException.notFound("采集任务不存在");
            }
            return job;
        });
    }

    @Override
    public CrawlJob create(Long operatorId, Long siteId, String seedUrl) {
        if (operatorId == null || siteId == null) {
            throw BizException.badRequest("缺少操作人或来源站点编号");
        }
        return write(conn -> {
            SourceSite site = siteDao.findById(conn, siteId);
            if (site == null) {
                throw BizException.notFound("来源站点不存在");
            }
            ensureRunnable(site);
            String target = TextUtil.isBlank(seedUrl) ? site.getBaseUrl()
                    : ValidationUtil.optionalUrl(seedUrl, "采集地址");
            try {
                if (!RobotsPolicy.sameHost(site.getBaseUrl(), target)) {
                    throw BizException.badRequest("采集地址必须属于来源站点根域名");
                }
            } catch (IOException e) {
                throw BizException.badRequest(e.getMessage());
            }
            CrawlJob job = new CrawlJob();
            job.setSiteId(siteId);
            job.setSeedUrl(target);
            job.setCreatedBy(operatorId);
            job.setId(jobDao.insert(conn, job));
            return jobDao.findById(conn, job.getId());
        });
    }

    @Override
    public CrawlJob run(Long operatorId, Long id) {
        if (operatorId == null || id == null) {
            throw BizException.badRequest("缺少操作人或采集任务编号");
        }
        if (!"true".equalsIgnoreCase(System.getenv("FLAVORLOGIC_CRAWL_ENABLED"))) {
            throw BizException.forbidden("自动采集默认关闭；需由部署环境显式启用 FLAVORLOGIC_CRAWL_ENABLED=true");
        }
        CrawlJob job = detail(id);
        if (!CrawlJob.PENDING.equals(job.getStatus())) {
            throw BizException.conflict("只有 PENDING 状态的任务可以执行");
        }
        SourceSite site = read(conn -> siteDao.findById(conn, job.getSiteId()));
        if (site == null) {
            throw BizException.notFound("来源站点不存在");
        }
        ensureRunnable(site);
        String startedAt = DateTimeUtil.now();
        int marked = write(conn -> jobDao.markRunning(conn, id, startedAt));
        if (marked != 1) {
            throw BizException.conflict("采集任务状态已改变，请刷新后重试");
        }

        try {
            PublicUrlGuard.requirePublicHttpUrl(job.getSeedUrl());
            if (!TextUtil.isBlank(site.getRobotsUrl())) {
                PublicUrlGuard.requirePublicHttpUrl(site.getRobotsUrl());
            }
            RobotsPolicy.ensureAllowed(site, job.getSeedUrl());
            PublicDocumentFetcher.FetchedDocument fetched = PublicDocumentFetcher.fetch(job.getSeedUrl());
            SourceDocument document = new SourceDocument();
            document.setSiteId(site.getId());
            document.setCrawlJobId(id);
            document.setUrl(job.getSeedUrl());
            document.setTitle(fetched.getTitle());
            document.setSummary(fetched.getSummary());
            document.setContentText(fetched.getContent());
            document.setSourceType(site.getSourceType());
            document.setLicenseType(site.getLicenseType());
            document.setEvidenceLevel("UNVERIFIED");
            document.setFetchedAt(DateTimeUtil.now());
            Services.sourceDocument().importDraft(operatorId, document);
            finish(id, CrawlJob.COMPLETED, 1, 1, 0, null);
        } catch (Exception e) {
            String message = TextUtil.truncate(e.getMessage(), 500);
            finish(id, CrawlJob.FAILED, 1, 0, 1, TextUtil.defaultIfBlank(message, "采集失败"));
        }
        return detail(id);
    }

    private void finish(Long id, String status, int fetched, int success, int failed, String message) {
        write(conn -> jobDao.finish(conn, id, status, DateTimeUtil.now(), fetched, success, failed, message));
    }

    private void ensureRunnable(SourceSite site) {
        if (!Integer.valueOf(1).equals(site.getStatus())) {
            throw BizException.conflict("来源站点已停用");
        }
        if (!Integer.valueOf(1).equals(site.getCrawlAllowed())) {
            throw BizException.forbidden("来源站点尚未通过自动获取许可审核");
        }
    }
}
