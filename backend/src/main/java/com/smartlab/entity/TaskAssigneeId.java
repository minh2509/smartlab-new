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
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TaskAssigneeId implements Serializable {

    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "user_id")
    private Long userId;
}
