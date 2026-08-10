package com.smartlab.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Admin request for atomically updating the complete project leadership.")
public class UpdateProjectLeadershipRequest {
    @Schema(
            description = "Public user id selected as primary. Null leaves the project without a primary leader.",
            nullable = true
    )
    @Size(max = 36, message = "Primary leader user id must not exceed 36 characters")
    private String primaryLeaderUserId;

    @NotNull(message = "Leader user ids are required")
    @Size(max = 100, message = "At most 100 project leaders may be selected")
    @Schema(description = "Complete active leader set. Use an empty list to remove every leader.")
    private List<
            @NotBlank(message = "Leader user id must not be blank")
            @Size(max = 36, message = "Leader user id must not exceed 36 characters") String> leaderUserIds;

    @JsonIgnore
    @AssertTrue(message = "Primary leader must be included in the leader user ids")
    public boolean isLeadershipSelectionValid() {
        if (leaderUserIds == null || primaryLeaderUserId == null) {
            return true;
        }
        if (primaryLeaderUserId.isBlank()) {
            return false;
        }
        String normalizedPrimaryId = primaryLeaderUserId.trim();
        return leaderUserIds.stream()
                .filter(userId -> userId != null && !userId.isBlank())
                .map(String::trim)
                .anyMatch(normalizedPrimaryId::equals);
    }

    @JsonIgnore
    @AssertTrue(message = "Leader user ids must not contain duplicates")
    public boolean isLeaderUserIdsUnique() {
        if (leaderUserIds == null) {
            return true;
        }
        java.util.Set<String> normalizedIds = new java.util.HashSet<>();
        for (String userId : leaderUserIds) {
            if (userId == null || userId.isBlank()) {
                continue;
            }
            if (!normalizedIds.add(userId.trim())) {
                return false;
            }
        }
        return true;
    }
}
