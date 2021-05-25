package dev.neeffect.example.todo

import dev.neeffect.nee.Nee
import dev.neeffect.nee.ctx.web.JDBCBasedWebContextProvider
import dev.neeffect.nee.ctx.web.WebContext
import dev.neeffect.nee.effects.jdbc.JDBCProvider
import dev.neeffect.nee.withErrorType
import io.vavr.Tuple2
import io.vavr.collection.Seq
import io.vavr.control.Option
import java.sql.Connection
import java.time.Instant

typealias WebCtx = WebContext<Connection, JDBCProvider>
typealias TodoIO<A> = Nee<WebCtx, TodoError, A>

data class TodoService(
    val timeProvider: Nee<WebCtx, TodoError, Instant>,
    val ctxProvider: JDBCBasedWebContextProvider
) {
    val dao = TodoDAO(ctxProvider)

    fun addItem(title: String): TodoIO<TodoId> =
        timeProvider.flatMap { time ->
            dao.saveNewItem(title, time)
        }

    fun findActive(): TodoIO<Seq<Tuple2<TodoId, TodoItem>>> =
        dao.findActive()

    fun markDone(id: TodoId): TodoIO<TodoItem> =
        findItem(id).flatMap { maybeItem ->
            maybeItem.map { todoItem ->
                when (todoItem) {
                    is TodoItem.Active -> dao.saveDone(id, todoItem)
                    else -> Nee.fail(TodoError.InvalidState)
                }
            }.getOrElse {
                Nee.failOnly<WebCtx, TodoError, TodoItem>(TodoError.NotFound)
            }
        }

    data class TodoRecord(val id: Long, val title: String, val created: Instant)

    fun findItem(id: TodoId): TodoIO<Option<TodoItem>> =
        dao.findItem(id).map { maybeItem ->
            maybeItem.map { record ->
                val item = TodoItem.Active(record.title, record.created.toInstant())
                if (record.done != null) {
                    TodoItem.Done(item)
                } else {
                    item
                }
            }
        }

    fun cancelItem(id: TodoId) =
        findItem(id).flatMap { maybeItem ->
            maybeItem.map { todoItem ->
                when (todoItem) {
                    is TodoItem.Active -> dao.saveCancelled(id, todoItem)
                    else -> Nee.fail(TodoError.InvalidState)
                }
            }.getOrElse {
                Nee.failOnly<WebCtx, TodoError, TodoItem>(TodoError.NotFound)
            }
        }

    fun findCancelled() = dao.findCancelled()

    fun findDone() = dao.findDone()
}

fun <A> Nee<WebCtx, Nothing, A>.e() = this.withErrorType<WebCtx, TodoError, A>()
