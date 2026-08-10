package com.smartlab.service;

import java.util.List;
import java.util.Set;

/**
 * Integration contract for the D2 research-field module.
 * The D3 core does not call it until the research-field endpoint is implemented.
 */
public interface ProjectResearchFieldGateway {
    List<ResearchFieldReference> getProjectFields(Long projectId);

    void replaceProjectFields(Long projectId, Set<Long> fieldIds);

    record ResearchFieldReference(Long id, String code, String name) {
    }
}
