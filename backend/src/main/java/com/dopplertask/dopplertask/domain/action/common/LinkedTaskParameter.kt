package com.dopplertask.dopplertask.domain.action.common

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "linkedtask_parameter_action")
class LinkedTaskParameter {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    @JsonIgnore
    private var id: Long? = null

    @ManyToOne
    @JoinColumn(nullable = false)
    @JsonIgnore
    var linkedTaskAction: LinkedTaskAction? = null
    var parameterName: String? = null
    var parameterValue: String? = null
}
