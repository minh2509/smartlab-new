package com.smartlab.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ReplaceProjectResearchFieldsRequest {
    @NotNull(message = "Research field list is required")
    @Size(max = 100, message = "At most 100 research fields may be selected")
    private List<@Valid @NotNull(message = "Research field id is required")
            @Positive(message = "Research field id must be positive") Long> fieldIds;
}
