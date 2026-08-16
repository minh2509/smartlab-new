package com.smartlab.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "project_research_fields")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectResearchFieldEntity {
    @EmbeddedId
    private ProjectResearchFieldId id;

    @MapsId("projectId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectEntity project;

    @MapsId("fieldId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "field_id", nullable = false)
    private ResearchFieldEntity researchField;

    private ProjectResearchFieldEntity(ProjectEntity project, ResearchFieldEntity researchField) {
        this.id = new ProjectResearchFieldId(project.getId(), researchField.getId());
        this.project = project;
        this.researchField = researchField;
    }

    public static ProjectResearchFieldEntity create(
            ProjectEntity project,
            ResearchFieldEntity researchField
    ) {
        return new ProjectResearchFieldEntity(project, researchField);
    }
}
