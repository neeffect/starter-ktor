package pl.setblack.nee.example.todolist

import dev.neeffect.nee.effects.utils.merge
import io.vavr.collection.Map
import io.vavr.control.Either
import io.vavr.control.Option
import io.vavr.kotlin.hashMap

data class TodoState(
    private val nextId: TodoId = TodoId(1),
    private val items: Map<TodoId, TodoItem> = hashMap()
) {
    fun addItem(item: TodoItem): Pair<TodoState, TodoId> =
        nextId.let { id ->
            Pair(this.copy(nextId = id.next(), items = items.put(id, item)), id)
        }

    fun markDone(id: TodoId): Pair<TodoState, Either<TodoError, TodoItem>> = changeItem(id) { item ->
        when (item) {
            is TodoItem.Active -> Either.right(item.done())
            else -> Either.left(TodoError.InvalidState)
        }
    }

    fun markCancelled(id: TodoId): Pair<TodoState, Either<TodoError, TodoItem>> = changeItem(id) { item ->
        when (item) {
            is TodoItem.Active -> Either.right(item.cancel())
            else -> Either.left(TodoError.InvalidState)
        }
    }

    fun getItem(id: TodoId): Option<TodoItem> = this.items[id]

    fun getAll() = this.items

    private fun changeItem(id: TodoId, change: (TodoItem) -> Either<TodoError, TodoItem>):
            Pair<TodoState, Either<TodoError, TodoItem>> =
        items[id].toEither<TodoError> { TodoError.NotFound }
            .flatMap {
                change(it)
            }.map {
                Pair(this.copy(items = items.put(id, it)), Either.right<TodoError, TodoItem>(it))
            }.mapLeft {
                Pair(this, Either.left<TodoError, TodoItem>(it))
            }.merge()
}
