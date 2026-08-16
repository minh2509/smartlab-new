package com.smartlab.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class ProjectResearchFieldId implements Serializable {
    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "field_id")
    private Long fieldId;
}
