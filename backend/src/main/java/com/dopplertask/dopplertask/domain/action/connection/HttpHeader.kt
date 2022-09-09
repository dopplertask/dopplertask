package com.dopplertask.dopplertask.domain.action.connection

import com.fasterxml.jackson.annotation.JsonIgnore
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.Lob
import javax.persistence.ManyToOne
import javax.persistence.Table

@Entity
@Table(name = "HttpHeader")
class HttpHeader {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    @JsonIgnore
    private var id: Long? = null

    @ManyToOne
    @JoinColumn(nullable = false)
    @JsonIgnore
    var httpAction: HttpAction? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var headerName: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var headerValue: String? = null

}