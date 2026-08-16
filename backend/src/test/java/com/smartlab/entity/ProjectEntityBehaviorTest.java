package com.smartlab.entity;

import com.smartlab.enums.ProjectMemberStatus;
import com.smartlab.enums.ProjectRole;
import com.smartlab.enums.ProjectStatus;
import com.smartlab.enums.ProjectType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectEntityBehaviorTest {

    @Test
    void createsLeaderAsAnActiveProjectMember() {
        UserEntity leader = user(2L, "leader-user");
        ProjectEntity project = project(leader);

        ProjectMemberEntity membership = ProjectMemberEntity.createLeader(project, leader);

        assertThat(membership.getProject()).isSameAs(project);
        assertThat(membership.getUser()).isSameAs(leader);
        assertThat(membership.getProjectRole()).isEqualTo(ProjectRole.LEADER);
        assertThat(membership.getStatus()).isEqualTo(ProjectMemberStatus.ACTIVE);
        assertThat(membership.getRemovedAt()).isNull();
    }

    @Test
    void demotesOldLeaderWithoutRemovingThemFromProject() {
        UserEntity leader = user(2L, "leader-user");
        ProjectMemberEntity membership = ProjectMemberEntity.createLeader(project(leader), leader);

        membership.activateAsMember();

        assertThat(membership.getProjectRole()).isEqualTo(ProjectRole.MEMBER);
        assertThat(membership.getStatus()).isEqualTo(ProjectMemberStatus.ACTIVE);
        assertThat(membership.getRemovedAt()).isNull();
    }

    @Test
    void reactivatesRemovedMembershipWhenUserBecomesLeaderAgain() {
        UserEntity user = user(2L, "leader-user");
        ProjectMemberEntity membership = ProjectMemberEntity.createMember(project(user), user);
        membership.remove();

        membership.activateAsLeader();

        assertThat(membership.getProjectRole()).isEqualTo(ProjectRole.LEADER);
        assertThat(membership.getStatus()).isEqualTo(ProjectMemberStatus.ACTIVE);
        assertThat(membership.getRemovedAt()).isNull();
    }

    @Test
    void softDeleteKeepsProjectDataAndMarksDeletedAt() {
        UserEntity leader = user(2L, "leader-user");
        ProjectEntity project = project(leader);

        project.softDelete();

        assertThat(project.getDeletedAt()).isNotNull();
        assertThat(project.getCode()).isEqualTo("SL-AI");
        assertThat(project.getLeader()).isSameAs(leader);
    }

    private static ProjectEntity project(UserEntity leader) {
        return ProjectEntity.create(
                "SL-AI",
                "Smart Lab AI",
                "Description",
                "Goal",
                ProjectType.RESEARCH,
                leader,
                ProjectStatus.PROPOSED,
                LocalDate.of(2026, 8, 15),
                LocalDate.of(2027, 1, 31),
                null,
                false,
                false,
                user(1L, "admin-user")
        );
    }

    private static UserEntity user(Long id, String userId) {
        return UserEntity.builder()
                .id(id)
                .userId(userId)
                .name(userId)
                .isActive(true)
                .build();
    }
}
