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
@Table(name = "permissions")
@Schema(description = "Permission code that backend security checks can use.")
public class PermissionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    @Schema(description = "Stable permission code", example = "PROJECT_MANAGE")
    private String code;

    @Column(nullable = false)
    @Schema(description = "Permission display name", example = "Manage projects")
    private String name;

    @Schema(description = "Feature/module grouping", example = "PROJECT")
    private String module;

    @Column(columnDefinition = "TEXT")
    @Schema(description = "Permission description", example = "Create and update project data")
    private String description;

    @Column(nullable = false)
    @Schema(description = "Inactive permissions are ignored in effective permission calculation", example = "true")
    private Boolean isActive;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Timestamp createdAt;

    @UpdateTimestamp
    private Timestamp updatedAt;
}
