package com.smartlab.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Table(name = "document_categories")
public class DocumentCategoryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static DocumentCategoryEntity create(String code, String name, String description, Integer displayOrder, Boolean isActive) {
        return DocumentCategoryEntity.builder()
                .code(code)
                .name(name)
                .description(description)
                .displayOrder(displayOrder != null ? displayOrder : 0)
                .isActive(isActive != null ? isActive : true)
                .build();
    }

    public void update(String name, String description, Integer displayOrder, Boolean isActive) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
        this.description = description;
        if (displayOrder != null) {
            this.displayOrder = displayOrder;
        }
        if (isActive != null) {
            this.isActive = isActive;
        }
    }

    public void setActive(boolean active) {
        this.isActive = active;
    }

    public void setDisplayOrder(int order) {
        this.displayOrder = order;
    }
}
