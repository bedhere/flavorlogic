package com.flavorlogic.service;

import com.flavorlogic.model.EvidenceItem;
import com.flavorlogic.model.SourceDocument;
import com.flavorlogic.util.PageResult;

import java.util.List;

/** 外部来源文档的导入、审核和检索业务。 */
public interface SourceDocumentService {

    PageResult<SourceDocument> page(String keyword, String reviewStatus, int page, int pageSize);

    SourceDocument detail(Long id);

    SourceDocument importDraft(Long operatorId, SourceDocument document);

    SourceDocument review(Long operatorId, Long id, String reviewStatus, String note);

    List<EvidenceItem> search(String query, int limit);
}
