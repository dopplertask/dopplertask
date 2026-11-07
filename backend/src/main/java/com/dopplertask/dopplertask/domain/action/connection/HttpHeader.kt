package com.dopplertask.dopplertask.domain.action.connection

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Lob
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

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