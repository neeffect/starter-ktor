package dev.neeffect.example.todo

import dev.neeffect.nee.Nee
import dev.neeffect.nee.atomic.AtomicRef
import dev.neeffect.nee.ctx.web.JDBCBasedWebContextProvider
import dev.neeffect.nee.ctx.web.WebContext
import dev.neeffect.nee.effects.jdbc.JDBCProvider
import dev.neeffect.nee.withErrorType
import dev.neeffect.example.todo.db.Sequences
import dev.neeffect.example.todo.db.tables.AllItemsView
import dev.neeffect.example.todo.db.tables.DoneItems
import dev.neeffect.example.todo.db.tables.TodoItems
import dev.neeffect.example.todo.db.tables.records.AllItemsViewRecord
import dev.neeffect.example.todo.db.tables.records.DoneItemsRecord
import dev.neeffect.nee.widerError
import io.vavr.Tuple
import io.vavr.Tuple2
import io.vavr.collection.Seq
import io.vavr.control.Option
import io.vavr.kotlin.option
import io.vavr.kotlin.toVavrList
import org.jooq.impl.DSL
import java.sql.Connection
import java.time.Instant

typealias WebCtx = WebContext<Connection, JDBCProvider>
typealias TodoIO<A> = Nee<WebCtx, TodoError, A>

data class TodoService(
    val timeProvider: Nee<WebCtx, TodoError, Instant>,
    val ctxProvider: JDBCBasedWebContextProvider,
    val state: AtomicRef<TodoState> = AtomicRef(TodoState())
) {

    val tx = ctxProvider.fx().tx.handleError { e -> TodoError.InternalError(e.toString()) }

    @Suppress("ReturnUnit") // seems to be an error (access to generated sources)
    fun addItem(title: String): TodoIO<TodoId> =
        timeProvider.flatMap { time ->
            Nee.with(tx) { r ->
                val dsl = DSL.using(r.getConnection().getResource())
                dsl.nextval(Sequences.TODOSEQ)
            }.flatMap { key ->
                Nee.with(tx) { r ->
                    val dsl = DSL.using(r.getConnection().getResource())
                    val todoItem = TodoItem.Active(title, time)
                    val inserted = dsl.insertInto(TodoItems.TODO_ITEMS).set(
                        dsl.newRecord(TodoItems.TODO_ITEMS, todoItem).also {
                            it.set(TodoItems.TODO_ITEMS.ID, key.toBigDecimal())
                        }
                    ).execute()
                    assert(inserted == 1)
                    TodoId(key)
                }
            }
        }

    fun findActive(): TodoIO<Seq<Tuple2<TodoId, TodoItem>>> =
        Nee.with(tx) { r ->
            val dsl = DSL.using(r.getConnection().getResource())
            val z: MutableList<TodoRecord> = dsl.selectFrom(TodoItems.TODO_ITEMS).fetchInto(TodoRecord::class.java)
            z.map { record ->
                Tuple.of(TodoId(record.id), TodoItem.Active(record.title, record.created) as TodoItem)
            }.toVavrList()
        }

    fun markDone(id: TodoId): TodoIO<TodoItem> =
        findItem(id).flatMap { maybeItem ->
            maybeItem.map {todoItem ->
                Nee.with(tx) { r ->
                    val dsl = DSL.using(r.getConnection().getResource())
                    val inserted = dsl.insertInto(DoneItems.DONE_ITEMS).values(id.id)
                        .execute()
                    assert( inserted == 1)
                    TodoItem.Done(todoItem)
                } as TodoIO<TodoItem>
            }.getOrElse {
                Nee.failOnly<WebCtx, TodoError, TodoItem>(TodoError.NotFound)
            }
        }



    data class TodoRecord(val id: Long, val title: String, val created: Instant)

    fun findItem(id: TodoId): TodoIO<Option<TodoItem>> = Nee.with(tx) { r ->
        val dsl = DSL.using(r.getConnection().getResource())
        val z: Option<AllItemsViewRecord> =
            Option.ofOptional(dsl.selectFrom(AllItemsView.ALL_ITEMS_VIEW)
                .where(AllItemsView.ALL_ITEMS_VIEW.ID.eq(id.id.toBigDecimal()))
                .fetchOptionalInto(AllItemsViewRecord::class.java))
        z.map { record ->
            val item = TodoItem.Active(record.title, record.created.toInstant())
            if (record.done != null) {
                TodoItem.Done(item)
            } else {
                item
            }
        }
    }


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

fun <A> Nee<WebCtx, Nothing, A>.e() = this.withErrorType<WebCtx, TodoError, A>()
