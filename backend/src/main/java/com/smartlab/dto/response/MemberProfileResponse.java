package com.smartlab.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class MemberProfileResponse {
    private String userId;
    private String name;
    private String email;
    private String publicEmail;
    private String phone;
    private String bio;
    private LocalDate joinedLabAt;
    private String activeStatus;
    private Boolean isFeatured;
    private Integer featuredOrder;
    private FileResponse avatar;
    private List<ResearchFieldResponse> researchFields;
}
