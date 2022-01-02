package com.dopplertask.dopplertask.domain.action.connection

import com.fasterxml.jackson.annotation.JsonIgnore
import javax.persistence.*

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
    var headerName: String? = null
    var headerValue: String? = null

}