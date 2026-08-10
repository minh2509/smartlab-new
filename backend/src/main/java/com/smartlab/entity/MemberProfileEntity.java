package com.smartlab.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Table(name = "member_profiles")
public class MemberProfileEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserEntity user;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "avatar_file_id")
    private StoredFileEntity avatarFile;

    @Column(length = 40)
    private String phone;

    @Column(name = "public_email", length = 190)
    private String publicEmail;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(name = "joined_lab_at")
    private LocalDate joinedLabAt;

    @Column(name = "active_status", nullable = false, length = 20)
    private String activeStatus;

    @Column(name = "is_featured", nullable = false)
    private Boolean isFeatured;

    @Column(name = "featured_order")
    private Integer featuredOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @ManyToMany
    @JoinTable(
            name = "member_research_fields",
            joinColumns = @JoinColumn(name = "member_profile_id"),
            inverseJoinColumns = @JoinColumn(name = "field_id")
    )
    @Builder.Default
    private Set<ResearchFieldEntity> researchFields = new LinkedHashSet<>();

    public static MemberProfileEntity create(UserEntity user) {
        return MemberProfileEntity.builder()
                .user(user)
                .activeStatus(Boolean.TRUE.equals(user.getIsActive())
                        && Boolean.TRUE.equals(user.getIsAccountVerified()) ? "ACTIVE" : "INACTIVE")
                .isFeatured(false)
                .researchFields(new LinkedHashSet<>())
                .build();
    }
}
