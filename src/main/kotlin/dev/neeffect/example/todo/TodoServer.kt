package pl.setblack.nee.example.todolist

import dev.neeffect.nee.Nee
import dev.neeffect.nee.ctx.web.BaseWebContextProvider
import dev.neeffect.nee.ctx.web.pure.delete
import dev.neeffect.nee.ctx.web.pure.get
import dev.neeffect.nee.ctx.web.pure.nested
import dev.neeffect.nee.ctx.web.pure.post
import dev.neeffect.nee.effects.time.TimeProvider
import dev.neeffect.nee.effects.tx.DummyTxProvider
import dev.neeffect.nee.effects.utils.merge
import io.vavr.control.Either

const val REST_LISTENING_PORT = 8000

object TodoServer {
    fun defineRouting(ctx: BaseWebContextProvider<Nothing, DummyTxProvider>, timeProvider: TimeProvider) = run {
        val time = Nee.success { timeProvider.getTimeSource().now().toInstant() }
        val rb = ctx.routeBuilder()
        val service = TodoService(time)
        rb.nested("/todo") {
            it + rb.nested("/done") {
                it + rb.post {
                    it.parameters["id"].asId().map { id ->
                        service.markDone(TodoId(id))
                    }.mapLeft { error ->
                        Nee.fail(error)
                    }.neeMerge()
                } + rb.get {
                    service.findDone()
                }
            } + rb.get("/{id}") {
                it.parameters["id"].asId().map {
                    Nee.success { it }.e().flatMap { id ->
                        service.findItem(TodoId(id))
                    }
                }.mapLeft {
                    Nee.fail(it)
                }.neeMerge()
            } + rb.get("/cancelled") {
                service.findCancelled()
            } + rb.get {
                service.findActive()
            } + rb.post {
                it.parameters["title"]?.let { title ->
                    service.addItem(title)
                } ?: Nee.fail(TodoError.InvalidParam)
            } + rb.delete("/{id}") {
                it.parameters["id"].asId().map {
                    Nee.success { it }.e().flatMap { id ->
                        service.cancelItem(TodoId(id))
                    }
                }.mapLeft { error ->
                    Nee.fail(error)
                }.neeMerge()
            }
        }
    }
}

fun String?.asId(): Either<TodoError, Int> =
    if (this != null && this.matches(Regex("-?[0-9]+"))) {
        Either.right(this.toInt())
    } else {
        Either.left(TodoError.InvalidParam)
    }

@Suppress("USELESS_CAST")
fun <R, E, A> Either<Nee<R, E, Nothing>, Nee<R, E, A>>.neeMerge(): Nee<R, E, A> =
    map { it as Nee<R, E, A> }.mapLeft { it as Nee<R, E, A> }.merge()
