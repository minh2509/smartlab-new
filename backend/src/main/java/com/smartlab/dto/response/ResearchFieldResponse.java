package com.smartlab.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ResearchFieldResponse {
    private Long id;
    private String code;
    private String name;
    private String description;
    private Long coverFileId;
    private Boolean isActive;
}
