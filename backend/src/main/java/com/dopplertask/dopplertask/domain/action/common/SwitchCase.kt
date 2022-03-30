package com.dopplertask.dopplertask.domain.action.common

import com.fasterxml.jackson.annotation.JsonIgnore
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table

@Entity
@Table(name = "SwitchCase")
class SwitchCase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    @JsonIgnore
    var id: Long? = null

    @ManyToOne
    @JoinColumn(nullable = false)
    @JsonIgnore
    var switchAction: SwitchAction? = null
    var currentCase: String? = null

}