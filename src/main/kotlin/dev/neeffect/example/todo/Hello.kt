package dev.neeffect.example.todo

import dev.neeffect.nee.Nee
import dev.neeffect.nee.ctx.web.DefaultErrorHandler
import dev.neeffect.nee.ctx.web.DefaultJacksonMapper
import dev.neeffect.nee.ctx.web.ErrorHandler
import dev.neeffect.nee.ctx.web.JDBCBasedWebContextProvider
import dev.neeffect.nee.ctx.web.pure.get
import dev.neeffect.nee.ctx.web.pure.startNettyServer
import dev.neeffect.nee.effects.jdbc.JDBCConfig
import dev.neeffect.nee.effects.jdbc.JDBCProvider
import dev.neeffect.nee.effects.time.HasteTimeProvider
import io.haste.Haste
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import org.flywaydb.core.Flyway

const val SERVER_PORT = 7777

val todoErrorHandler: ErrorHandler = { error ->
    when (error) {
        is TodoError -> handleTodoError(error)
        else -> DefaultErrorHandler(error)
    }
}
val timeProvider = HasteTimeProvider(Haste.TimeSource.systemTimeSource())

val webContextProvider = object : JDBCBasedWebContextProvider() {
    private val jdbcConfig = JDBCConfig(
        driverClassName = "org.h2.Driver",
        url = "jdbc:h2:mem:todo;DB_CLOSE_DELAY=-1",
        user = "sa",
        password = "notVerySecret"
    )

    @Suppress("ReturnUnit")
    override val jdbcProvider: JDBCProvider by lazy {
        JDBCProvider(jdbcConfig).also { jdbc ->
            val flyway = Flyway.configure().dataSource(jdbc.dataSource()).load()
            flyway.migrate()
        }
    }
    override val errorHandler: ErrorHandler by lazy {
        todoErrorHandler
    }
}

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
        is TodoError.InternalError -> HttpStatusCode.InternalServerError
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
