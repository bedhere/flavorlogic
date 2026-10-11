package com.flavorlogic.service.impl;

import com.flavorlogic.dao.IngredientAttributeVersionDao;
import com.flavorlogic.dao.IngredientDao;
import com.flavorlogic.dao.SourceDocumentDao;
import com.flavorlogic.dao.impl.IngredientAttributeVersionDaoImpl;
import com.flavorlogic.dao.impl.IngredientDaoImpl;
import com.flavorlogic.dao.impl.SourceDocumentDaoImpl;
import com.flavorlogic.model.FlavorDimensions;
import com.flavorlogic.model.Ingredient;
import com.flavorlogic.model.IngredientAttributeVersion;
import com.flavorlogic.model.SourceDocument;
import com.flavorlogic.service.BaseService;
import com.flavorlogic.service.IngredientAttributeVersionService;
import com.flavorlogic.util.BizException;
import com.flavorlogic.util.TextUtil;
import com.flavorlogic.util.ValidationUtil;

import java.math.BigDecimal;
import java.util.List;

/** 食材属性版本业务：审核与应用分开，避免批准动作自动改变主表。 */
public class IngredientAttributeVersionServiceImpl extends BaseService implements IngredientAttributeVersionService {

    private final IngredientAttributeVersionDao versionDao = new IngredientAttributeVersionDaoImpl();
    private final IngredientDao ingredientDao = new IngredientDaoImpl();
    private final SourceDocumentDao documentDao = new SourceDocumentDaoImpl();

    @Override
    public List<IngredientAttributeVersion> list(Long ingredientId, String dimension, String reviewStatus) {
        return read(conn -> versionDao.list(conn, ingredientId,
                TextUtil.isBlank(dimension) ? null : dimension.trim(),
                TextUtil.isBlank(reviewStatus) ? null : reviewStatus.trim()));
    }

    @Override
    public IngredientAttributeVersion create(IngredientAttributeVersion version) {
        normalize(version);
        return write(conn -> {
            if (ingredientDao.findById(conn, version.getIngredientId()) == null) {
                throw BizException.notFound("食材不存在");
            }
            SourceDocument document = documentDao.findById(conn, version.getSourceDocumentId());
            if (document == null || !SourceDocument.APPROVED.equals(document.getReviewStatus())) {
                throw BizException.conflict("属性版本必须引用已审核通过的来源文档");
            }
            version.setVersionNo(versionDao.nextVersionNo(conn, version.getIngredientId(), version.getDimension()));
            version.setId(versionDao.insert(conn, version));
            return versionDao.findById(conn, version.getId());
        });
    }

    @Override
    public IngredientAttributeVersion review(Long operatorId, Long id, String status, String note) {
        if (operatorId == null) {
            throw BizException.unauthorized("请先登录");
        }
        String safeStatus = ValidationUtil.requireOneOf(status, "审核状态",
                IngredientAttributeVersion.REVIEW_PENDING, IngredientAttributeVersion.APPROVED,
                IngredientAttributeVersion.REJECTED, IngredientAttributeVersion.ARCHIVED);
        String safeNote = ValidationUtil.optionalText(note, 1000, "审核意见");
        return write(conn -> {
            IngredientAttributeVersion version = versionDao.findById(conn, id);
            if (version == null) {
                throw BizException.notFound("属性版本不存在");
            }
            if (IngredientAttributeVersion.APPROVED.equals(safeStatus)) {
                SourceDocument document = documentDao.findById(conn, version.getSourceDocumentId());
                if (document == null || !SourceDocument.APPROVED.equals(document.getReviewStatus())) {
                    throw BizException.conflict("来源文档尚未审核通过，属性版本不能批准");
                }
            }
            versionDao.updateReview(conn, id, safeStatus, operatorId, safeNote);
            return versionDao.findById(conn, id);
        });
    }

    @Override
    public IngredientAttributeVersion apply(Long operatorId, Long id) {
        if (operatorId == null) {
            throw BizException.unauthorized("请先登录");
        }
        return write(conn -> {
            IngredientAttributeVersion version = versionDao.findById(conn, id);
            if (version == null) {
                throw BizException.notFound("属性版本不存在");
            }
            if (!IngredientAttributeVersion.APPROVED.equals(version.getReviewStatus())) {
                throw BizException.conflict("只有 APPROVED 属性版本才能应用到食材");
            }
            SourceDocument document = documentDao.findById(conn, version.getSourceDocumentId());
            if (document == null || !SourceDocument.APPROVED.equals(document.getReviewStatus())) {
                throw BizException.conflict("来源文档当前不是 APPROVED");
            }
            Ingredient ingredient = ingredientDao.findById(conn, version.getIngredientId());
            if (ingredient == null) {
                throw BizException.notFound("食材不存在");
            }
            setDimension(ingredient, version.getDimension(), version.getValue());
            ingredient.setDataSource(TextUtil.truncate(
                    "证据版本#" + id + "；来源文档#" + version.getSourceDocumentId(), 255));
            ingredientDao.update(conn, ingredient);
            return version;
        });
    }

    private void normalize(IngredientAttributeVersion version) {
        if (version == null) {
            throw BizException.badRequest("缺少属性版本内容");
        }
        if (version.getIngredientId() == null || version.getSourceDocumentId() == null) {
            throw BizException.badRequest("食材编号和来源文档编号不能为空");
        }
        version.setDimension(ValidationUtil.requireOneOf(version.getDimension(), "风味维度",
                FlavorDimensions.ALL.toArray(new String[0])));
        if (version.getValue() == null || version.getValue().compareTo(BigDecimal.ZERO) < 0
                || version.getValue().compareTo(new BigDecimal("100")) > 0) {
            throw BizException.badRequest("属性值必须在 0-100 之间");
        }
        version.setValueUnit(ValidationUtil.optionalText(version.getValueUnit(), 30, "数值单位"));
        version.setBasis(ValidationUtil.optionalText(version.getBasis(), 50, "数据口径"));
        version.setConcentration(ValidationUtil.optionalText(version.getConcentration(), 100, "测试浓度"));
        version.setTemperature(ValidationUtil.optionalText(version.getTemperature(), 50, "测试温度"));
        version.setMeasurementMethod(ValidationUtil.optionalText(version.getMeasurementMethod(), 255, "测量方法"));
        version.setUncertainty(ValidationUtil.optionalText(version.getUncertainty(), 100, "不确定性"));
        version.setEvidenceLevel(ValidationUtil.requireOneOf(
                TextUtil.defaultIfBlank(version.getEvidenceLevel(), "UNVERIFIED"), "证据等级",
                "UNVERIFIED", "LITERATURE", "STANDARD", "EXPERIMENT", "EXPERIENCE", "DEMO"));
    }

    private void setDimension(Ingredient ingredient, String dimension, BigDecimal value) {
        switch (dimension) {
            case FlavorDimensions.SWEET: ingredient.setSweet(value); break;
            case FlavorDimensions.SALTY: ingredient.setSalty(value); break;
            case FlavorDimensions.SOUR: ingredient.setSour(value); break;
            case FlavorDimensions.BITTER: ingredient.setBitter(value); break;
            case FlavorDimensions.UMAMI: ingredient.setUmami(value); break;
            case FlavorDimensions.SPICY: ingredient.setSpicy(value); break;
            case FlavorDimensions.NUMBING: ingredient.setNumbing(value); break;
            case FlavorDimensions.FAT_AROMA: ingredient.setFatAroma(value); break;
            default: throw BizException.badRequest("未知风味维度");
        }
    }
}
