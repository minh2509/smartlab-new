package com.smartlab.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProjectResearchFieldResponse {
    private Long id;
    private String code;
    private String name;
    private Boolean isActive;
}
