package com.smartlab.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Set;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "tbl_user")
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true)
    private String userId;
    private String name;
    @Column(unique = true)
    private String email;
    private String password;
    @Column(nullable = false)
    private Boolean isActive;
    private Boolean isAccountVerified;
    private String resetOtp;
    private Long resetOtpExpireAt;
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Timestamp createdAt;
    @UpdateTimestamp
    private Timestamp updatedAt;

    @OneToMany(mappedBy = "user")
    private Set<UserRoleEntity> roles = new HashSet<>();

}
