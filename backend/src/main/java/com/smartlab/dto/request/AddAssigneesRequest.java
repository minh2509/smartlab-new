package com.smartlab.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class AddAssigneesRequest {

    @NotEmpty
    private List<Long> userIds;
}
