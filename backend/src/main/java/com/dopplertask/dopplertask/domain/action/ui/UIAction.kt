package com.dopplertask.dopplertask.domain.action.ui

import com.fasterxml.jackson.annotation.JsonIgnore
import javax.persistence.*

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