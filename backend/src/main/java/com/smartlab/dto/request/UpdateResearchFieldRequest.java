package com.smartlab.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateResearchFieldRequest {
    @Size(max = 150)
    private String name;

    @Size(max = 5000)
    private String description;

    private Boolean isActive;
}
