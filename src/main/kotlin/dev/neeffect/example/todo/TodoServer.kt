package dev.neeffect.example.todo

import dev.neeffect.nee.Nee
import dev.neeffect.nee.ctx.web.JDBCBasedWebContextProvider
import dev.neeffect.nee.ctx.web.pure.get
import dev.neeffect.nee.ctx.web.pure.nested
import dev.neeffect.nee.ctx.web.pure.post
import dev.neeffect.nee.effects.time.TimeProvider
import dev.neeffect.nee.effects.utils.merge
import io.vavr.control.Either

const val REST_LISTENING_PORT = 8000

object TodoServer {
    fun defineRouting(ctx: JDBCBasedWebContextProvider, timeProvider: TimeProvider) = run {
        val time = Nee.success { timeProvider.getTimeSource().now().toInstant() }
        val rb = ctx.routeBuilder()
        val service = TodoService(time, ctx)
        rb.nested("/todo") {
            it + rb.get("/{id}") {
                it.parameters["id"].asId().map {
                    Nee.success { it }.e().flatMap { id ->
                        service.findItem(TodoId(id))
                    }
                }.mapLeft {
                    Nee.fail(it) as TodoIO<Nothing>
                }.neeMerge()
            } + rb.nested("/done") {
                it + rb.post {
                    it.parameters["id"].asId().map {
                        Nee.success { it }.e().flatMap { id ->
                            service.markDone(TodoId(id))
                        }
                    }.mapLeft {
                        Nee.fail(it) as TodoIO<Nothing>
                    }.neeMerge()
                }

            }+ rb.post {
                it.parameters["title"]?.let { title ->
                    service.addItem(title)
                } ?: Nee.fail(TodoError.InvalidParam)
            } + rb.get {
                service.findActive()
            }
        }
    }
}

fun String?.asId(): Either<TodoError, Long> =
    if (this != null && this.matches(Regex("-?[0-9]+"))) {
        Either.right(this.toLong())
    } else {
        Either.left(TodoError.InvalidParam)
    }

@Suppress("USELESS_CAST")
fun <R, E, A> Either<Nee<R, E, Nothing>, Nee<R, E, A>>.neeMerge(): Nee<R, E, A> =
    map { it as Nee<R, E, A> }.mapLeft { it as Nee<R, E, A> }.merge()
