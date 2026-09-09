package com.smartlab.repo;

import com.smartlab.entity.EmailTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailTemplateRepository extends JpaRepository<EmailTemplateEntity, Long> {
    Optional<EmailTemplateEntity> findByCodeAndIsActiveTrue(String code);
}
