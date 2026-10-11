package com.flavorlogic.service;

import com.flavorlogic.model.EvidenceClaim;

import java.util.List;

/** 证据声明录入与审核业务。 */
public interface EvidenceClaimService {

    List<EvidenceClaim> list(Long documentId, Long ingredientId, String reviewStatus);

    EvidenceClaim create(EvidenceClaim claim);

    EvidenceClaim review(Long operatorId, Long id, String status, String note);
}
