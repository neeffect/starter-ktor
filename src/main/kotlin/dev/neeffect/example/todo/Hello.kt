package dev.neeffect.example.todo

import dev.neeffect.nee.Nee
import dev.neeffect.nee.ctx.web.BaseWebContextProvider
import dev.neeffect.nee.ctx.web.DefaultErrorHandler
import dev.neeffect.nee.ctx.web.DefaultJacksonMapper
import dev.neeffect.nee.ctx.web.ErrorHandler
import dev.neeffect.nee.ctx.web.pure.get
import dev.neeffect.nee.ctx.web.pure.startNettyServer
import dev.neeffect.nee.effects.time.HasteTimeProvider
import dev.neeffect.nee.effects.tx.DummyTxProvider
import io.haste.Haste
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import pl.setblack.nee.example.todolist.TodoError
import pl.setblack.nee.example.todolist.TodoServer

const val SERVER_PORT = 7777

val errorHandler: ErrorHandler = { error ->
    when (error) {
        is TodoError -> handleTodoError(error)
        else -> DefaultErrorHandler(error)
    }
}
val timeProvider = HasteTimeProvider(Haste.TimeSource.systemTimeSource())

val webContextProvider: BaseWebContextProvider<Nothing, DummyTxProvider> = BaseWebContextProvider.createTransient(
    customErrorHandler = errorHandler
)
val routeBuilder = webContextProvider.routeBuilder()
val routing = routeBuilder.get("/hello") {
    Nee.success { "hello world" }
} + routeBuilder.get("/error") {
    Nee.fail(TodoError.NotFound)
} + TodoServer.defineRouting(webContextProvider, timeProvider)

fun handleTodoError(error: TodoError): OutgoingContent =
    when (error) {
        is TodoError.InvalidState -> HttpStatusCode.Conflict
        is TodoError.NotFound -> HttpStatusCode.NotFound
        is TodoError.InvalidParam -> HttpStatusCode.BadRequest
    }.let { code ->
        TextContent(error.toString(),
            contentType = ContentType.Text.Plain,
            status = code
        )
    }

fun main() {

    startNettyServer(SERVER_PORT, DefaultJacksonMapper.mapper, webContextProvider) {
        it + routing
    }.perform(Unit)
}
