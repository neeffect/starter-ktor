package dev.neeffect.example.todo

import dev.neeffect.example.todo.db.Sequences
import dev.neeffect.example.todo.db.Tables
import dev.neeffect.example.todo.db.tables.ActiveItemsView
import dev.neeffect.example.todo.db.tables.AllItemsView
import dev.neeffect.example.todo.db.tables.TodoItems
import dev.neeffect.example.todo.db.tables.records.AllItemsViewRecord
import dev.neeffect.nee.Nee
import dev.neeffect.nee.ctx.web.JDBCBasedWebContextProvider
import io.vavr.Tuple
import io.vavr.Tuple2
import io.vavr.collection.Seq
import io.vavr.control.Option
import io.vavr.kotlin.toVavrList
import org.jooq.impl.DSL
import java.math.BigDecimal
import java.time.Instant

@Suppress("MutableCollections")
class TodoDAO(
    val ctxProvider: JDBCBasedWebContextProvider
) {
    val tx = ctxProvider.fx().tx.handleError { e -> TodoError.InternalError(e.toString()) }

    @Suppress("ReturnUnit") // seems to be an error (access to generated sources)
    internal fun saveNewItem(title: String, time: Instant): TodoIO<TodoId> = Nee.with(tx) { r ->
        val dsl = DSL.using(r.getConnection().getResource())
        dsl.nextval(Sequences.TODOSEQ)
    }.flatMap { key: Long ->
        Nee.with(tx) { r ->
            val dsl = DSL.using(r.getConnection().getResource())
            val todoItem = TodoItem.Active(title, time)
            val inserted = dsl.insertInto(TodoItems.TODO_ITEMS).set(
                dsl.newRecord(TodoItems.TODO_ITEMS, todoItem).also {
                    val keyDecimal = BigDecimal.valueOf(key)
                    it.set(TodoItems.TODO_ITEMS.ID, keyDecimal)
                }
            ).execute()
            assert(inserted == 1)
            TodoId(key)
        }
    }

    internal fun findActive(): TodoIO<Seq<Tuple2<TodoId, TodoItem>>> =
        Nee.with(tx) { r ->
            val dsl = DSL.using(r.getConnection().getResource())
            dsl.selectFrom(ActiveItemsView.ACTIVE_ITEMS_VIEW)
                .fetchInto(TodoService.TodoRecord::class.java)
                .map { record ->
                    Tuple.of(TodoId(record.id), TodoItem.Active(record.title, record.created) as TodoItem)
                }.toVavrList()
        }

    internal fun saveDone(id: TodoId, item: TodoItem.Active): TodoIO<TodoItem> =
        Nee.with(tx) { r ->
            val dsl = DSL.using(r.getConnection().getResource())
            val inserted = dsl.insertInto(Tables.DONE_ITEMS).values(id.id)
                .execute()
            assert(inserted == 1)
            TodoItem.Done(item)
        }

    internal fun saveCancelled(id: TodoId, item: TodoItem.Active): TodoIO<TodoItem> =
        Nee.with(tx) { r ->
            val dsl = DSL.using(r.getConnection().getResource())
            val inserted = dsl.insertInto(Tables.CANCELLED_ITEMS).values(id.id)
                .execute()
            assert(inserted == 1)
            TodoItem.Cancelled(item)
        }

    internal fun findItem(id: TodoId): TodoIO<Option<AllItemsViewRecord>> = Nee.with(tx) { r ->
        val dsl = DSL.using(r.getConnection().getResource())
        Option.ofOptional(dsl.selectFrom(AllItemsView.ALL_ITEMS_VIEW)
            .where(AllItemsView.ALL_ITEMS_VIEW.ID.eq(id.id.toBigDecimal()))
            .fetchOptionalInto(AllItemsViewRecord::class.java))
    }

    internal fun findCancelled() = Nee.with(tx) { r ->
        val dsl = DSL.using(r.getConnection().getResource())
        dsl.selectFrom(Tables.CANCELLED_ITEMS_VIEW).fetchInto(TodoService.TodoRecord::class.java).map { record ->
            Tuple.of(TodoId(record.id), TodoItem.Active(record.title, record.created).cancel() as TodoItem)
        }.toVavrList()
    }

    internal fun findDone() = Nee.with(tx) { r ->
        val dsl = DSL.using(r.getConnection().getResource())
        dsl.selectFrom(Tables.DONE_ITEMS_VIEW).fetchInto(TodoService.TodoRecord::class.java).map { record ->
            Tuple.of(TodoId(record.id), TodoItem.Active(record.title, record.created).done() as TodoItem)
        }.toVavrList()
    }
}
