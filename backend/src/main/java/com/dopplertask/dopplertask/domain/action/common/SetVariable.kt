package com.dopplertask.dopplertask.domain.action.common

import com.fasterxml.jackson.annotation.JsonIgnore
import java.util.*
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.Lob
import javax.persistence.ManyToOne
import javax.persistence.Table

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

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        val that = other as SetVariable
        return name == that.name &&
                value == that.value
    }

    override fun hashCode(): Int {
        return Objects.hash(name, value)
    }
}