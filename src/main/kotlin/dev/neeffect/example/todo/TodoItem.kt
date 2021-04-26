package pl.setblack.nee.example.todolist

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.Instant

inline class TodoId @JsonCreator constructor(val id: Int) {
    fun next() =
        TodoId(id + 1)
}

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type")
@JsonSubTypes(JsonSubTypes.Type(TodoItem.Active::class))
sealed class TodoItem(val title: String, val created: Instant) {

    @JsonIgnore
    open fun isActive() = false

    class Active @JsonCreator constructor(title: String, created: Instant) : TodoItem(title, created) {
        fun done() = Done(this)
        fun cancel() = Cancelled(this)
        override fun isActive() = true
    }

    class Done(todoItem: TodoItem) : TodoItem(todoItem.title, todoItem.created) {
        @JsonCreator
        private constructor(title: String, created: Instant) : this(Active(title, created))
    }

    class Cancelled(todoItem: TodoItem) : TodoItem(todoItem.title, todoItem.created) {
        @JsonCreator
        private constructor(title: String, created: Instant) : this(Active(title, created))
    }
}
