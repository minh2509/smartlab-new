package com.smartlab.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Admin request for replacing all project leaders, or clearing them with an empty list.")
public class ChangeProjectLeadersRequest {
    @NotNull(message = "Leader user ids are required")
    @Size(max = 100, message = "At most 100 project leaders may be selected")
    @Schema(description = "Complete leader set. Omitting the current primary clears that selection; an empty list removes every leader.")
    private List<
            @NotBlank(message = "Leader user id must not be blank")
            @Size(max = 36, message = "Leader user id must not exceed 36 characters") String> leaderUserIds;
}
