package com.smartlab.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.sql.Timestamp;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "roles")
@Schema(description = "Role that groups permissions. Admin, Leader, and Member are seeded by default.")
public class RoleEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    @Schema(description = "Stable role code", example = "LEADER")
    private String code;

    @Column(nullable = false)
    @Schema(description = "Role display name", example = "Leader")
    private String name;

    @Column(columnDefinition = "TEXT")
    @Schema(description = "Role description", example = "Project leader")
    private String description;

    @Column(nullable = false)
    @Schema(description = "True for seeded system roles", example = "true")
    private Boolean isSystem;

    @Column(nullable = false)
    @Schema(description = "If false, users owning this role cannot log in", example = "true")
    private Boolean isActive;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Timestamp createdAt;

    @UpdateTimestamp
    private Timestamp updatedAt;
}
