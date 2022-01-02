package com.dopplertask.dopplertask.domain.action.common

import com.fasterxml.jackson.annotation.JsonIgnore
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "SetVariable")
class SetVariable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    private val id: Long? = null
    var name: String? = null

    @Lob
    var value: String? = null

    @ManyToOne
    @JoinColumn(nullable = false)
    @JsonIgnore
    var setVariableAction: SetVariableAction? = null

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val that = o as SetVariable
        return name == that.name &&
                value == that.value
    }

    override fun hashCode(): Int {
        return Objects.hash(name, value)
    }
}