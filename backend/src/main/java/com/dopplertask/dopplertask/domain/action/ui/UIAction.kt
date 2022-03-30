package com.dopplertask.dopplertask.domain.action.ui

import com.fasterxml.jackson.annotation.JsonIgnore
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table

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