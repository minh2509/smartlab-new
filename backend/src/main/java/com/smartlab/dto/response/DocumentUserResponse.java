package com.smartlab.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DocumentUserResponse {
    private String userId;
    private String name;
    private String email;
}
