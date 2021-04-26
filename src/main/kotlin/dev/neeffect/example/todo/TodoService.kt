package pl.setblack.nee.example.todolist

import dev.neeffect.nee.Nee
import dev.neeffect.nee.atomic.AtomicRef
import dev.neeffect.nee.withErrorType
import io.vavr.Tuple2
import io.vavr.collection.Seq
import io.vavr.control.Option
import java.time.Instant

typealias TodoIO<A> = Nee<Any, TodoError, A>

data class TodoService(
    val timeProvider: Nee<Any, Nothing, Instant>,
    val state: AtomicRef<TodoState> = AtomicRef(TodoState())
) {

    fun addItem(title: String): TodoIO<TodoId> =
        timeProvider.e().flatMap { time ->
            state.modifyGet { s ->
                val item = TodoItem.Active(title, time)
                s.addItem(item)
            }.map { it.second }
        }

    fun markDone(id: TodoId): TodoIO<TodoItem> =
        state.modifyGet { s ->
            val res = s.markDone(id)
            res
        }.e().flatMap {
           it.second.mapLeft { error ->
                Nee.fail(error)
            }.map { item ->
                Nee.success { item }.e()
            }.neeMerge()
        }
    fun findActive(): TodoIO<Seq<Tuple2<TodoId, TodoItem>>> =
        state.get().e().map {
            it.getAll()
                .filter { tuple ->
                    tuple._2.isActive()
                }.map { tuple -> tuple }
        }

    fun findItem(id: TodoId): TodoIO<Option<TodoItem>> =
        state.get().map { it.getItem(id) }

    fun cancelItem(id: TodoId) = state.modifyGet { s ->
        s.markCancelled(id)
    }.map { it.second }.e()

    fun findCancelled() = state.get().map {
        it.getAll()
            .filter { tuple ->
                tuple._2 is TodoItem.Cancelled
            }.map { tuple -> tuple }
    }

    fun findDone() = state.get().map {
        it.getAll()
            .filter { tuple ->
                tuple._2 is TodoItem.Done
            }.map { tuple -> tuple }
    }
}

fun <A> Nee<Any, Nothing, A>.e() = this.withErrorType<Any, TodoError, A>()
