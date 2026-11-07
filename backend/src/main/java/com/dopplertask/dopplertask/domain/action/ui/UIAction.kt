package com.dopplertask.dopplertask.domain.action.ui

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "UIAction")
class UIAction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    var id: Long? = null

    @ManyToOne
    @JoinColumn(nullable = false)
    @JsonIgnore
    var browseWebAction: BrowseWebAction? = null

    @Column
    var fieldName: String? = null

    @Enumerated(EnumType.STRING)
    @Column
    var action = UIActionType.PRESS

    @Enumerated(EnumType.STRING)
    @Column
    var findByType = UIFieldFindByType.ID

    @Column
    var value: String? = null

}