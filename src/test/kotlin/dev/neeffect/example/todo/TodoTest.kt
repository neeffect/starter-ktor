package dev.neeffect.example.todo

import com.fasterxml.jackson.core.type.TypeReference
import dev.neeffect.nee.ctx.web.DefaultJacksonMapper
import dev.neeffect.nee.ctx.web.ErrorHandler
import dev.neeffect.nee.ctx.web.JDBCBasedWebContextProvider
import dev.neeffect.nee.effects.jdbc.JDBCConfig
import dev.neeffect.nee.effects.jdbc.JDBCProvider
import dev.neeffect.nee.effects.time.HasteTimeProvider
import dev.neeffect.nee.web.test.testApplication
import io.haste.Haste
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.ktor.application.Application
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.vavr.Tuple2
import io.vavr.collection.Seq
import org.flywaydb.core.Flyway
import java.sql.Connection
import java.sql.Driver
import java.sql.DriverManager
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class TodoServerTest : StringSpec({
    "add item should give an id" {
        usingTestDb { testCtx ->
            withTestApplication({
                todoRest(testCtx)()
            }) {
                with(handleRequest(HttpMethod.Post, "/todo?title=hello")) {
                    val addedId = DefaultJacksonMapper.mapper.readValue(response.byteContent!!, TodoIdAlt::class.java)
                    addedId shouldBe (TodoIdAlt(1))
                }
            }
        }
    }
    "added item should be returned by id" {
        usingTestDb() { testCtx ->
            withTestApplication({
                todoRest(testCtx)()
            }) {
                addItemUsingPOST("hello")
                with(handleRequest(HttpMethod.Get, "/todo/1")) {
                    println("content: ${response.content}")
                    val item = DefaultJacksonMapper.mapper.readValue(response.byteContent!!, TodoItem::class.java)
                    item.title shouldBe "hello"
                }
            }
        }
    }
    "calling get with text id returns error" {
        usingTestDb() { testCtx ->
            withTestApplication({
                todoRest(testCtx)()
            }) {
                addItemUsingPOST("hello")
                with(handleRequest(HttpMethod.Get, "/todo/zupka")) {
                    response.status() shouldBe HttpStatusCode.BadRequest
                }
            }
        }
    }
    "added item should be in find active" {
        usingTestDb() { testCtx ->
            withTestApplication({
                todoRest(testCtx)()
            }) {
                addItemUsingPOST("hello")
                with(handleRequest(HttpMethod.Get, "/todo")) {
                    val items = DefaultJacksonMapper.mapper.readValue(response.byteContent!!,
                        object : TypeReference<Seq<Tuple2<TodoIdAlt, TodoItem>>>() {})
                    items.size() shouldBe 1
                    items[0]._2.title shouldBe ("hello")
                }
            }
        }
    }
    "added  multiple items should be in find all" {
        usingTestDb() { testCtx ->
            withTestApplication({
                todoRest(testCtx)()
            }) {
                (0 until 3).forEach {
                    addItemUsingPOST("hello_$it")
                }
                with(handleRequest(HttpMethod.Get, "/todo")) {
                    val items = DefaultJacksonMapper.mapper.readValue(response.byteContent!!,
                        object : TypeReference<Seq<Tuple2<TodoIdAlt, TodoItem>>>() {})
                    items.size() shouldBe 3
                    items.map { it._2.title } shouldContainAll ((0 until 3).map { "hello_$it" })
                }
            }
        }
    }
    "done should mark as done"{
        usingTestDb() { testCtx ->
            withTestApplication({
                todoRest(testCtx)()
            }) {
                val id = addItemUsingPOST("hello")
                handleRequest(HttpMethod.Post, "/todo/done?id=$id")
                with(handleRequest(HttpMethod.Get, "/todo/${id}")) {
                    val content = response.byteContent!!
                    val item = DefaultJacksonMapper.mapper.readValue(content, TodoItem::class.java)
                    item.title shouldBe "hello"
                    item.shouldBeTypeOf<TodoItem.Done>()
                }
            }
        }
    }
//    "done twice on same item should lead to 409"{
//        withTestApplication({
//            todoRest()
//        }) {
//            val id = addItemUsingPOST("hello")
//            handleRequest(HttpMethod.Post, "/todo/done?id=$id")
//            with(handleRequest(HttpMethod.Post, "/todo/done?id=$id")) {
//                response.status().shouldBe(HttpStatusCode.Conflict)
//            }
//        }
//    }
//    "done should not be on todo list" {
//        withTestApplication({
//            todoRest()
//        }) {
//            val id = addItemUsingPOST("hello")
//            handleRequest(HttpMethod.Post, "/todo/done?id=$id")
//            with(handleRequest(HttpMethod.Get, "/todo")) {
//                val items = DefaultJacksonMapper.mapper.readValue(response.byteContent!!,
//                    object : TypeReference<Seq<Tuple2<TodoIdAlt, TodoItem>>>() {})
//                items.size() shouldBe 0
//            }
//        }
//    }
//    "done should  be on done list" {
//        withTestApplication({
//            todoRest()
//        }) {
//            val id = addItemUsingPOST("hello")
//            handleRequest(HttpMethod.Post, "/todo/done?id=$id")
//            with(handleRequest(HttpMethod.Get, "/todo/done")) {
//                val items = DefaultJacksonMapper.mapper.readValue(response.byteContent!!,
//                    object : TypeReference<Seq<Tuple2<TodoIdAlt, TodoItem>>>() {})
//                items.size() shouldBe 1
//            }
//        }
//    }
//
//    "cancelled should not be on todo list" {
//
//        withTestApplication({
//            todoRest()
//        }) {
//            val id = addItemUsingPOST("hello")
//            handleRequest(HttpMethod.Delete, "/todo/$id")
//            with(handleRequest(HttpMethod.Get, "/todo")) {
//                val items = DefaultJacksonMapper.mapper.readValue(response.byteContent!!,
//                    object : TypeReference<Seq<Tuple2<TodoIdAlt, TodoItem>>>() {})
//                items.size() shouldBe 0
//            }
//        }
//    }
//    "cancelled should be on cancelled list" {
//        withTestApplication({
//            todoRest()
//        }) {
//            val id = addItemUsingPOST("hello")
//            handleRequest(HttpMethod.Delete, "/todo/$id")
//            with(handleRequest(HttpMethod.Get, "/todo/cancelled")) {
//                val items = DefaultJacksonMapper.mapper.readValue(response.byteContent!!,
//                    object : TypeReference<Seq<Tuple2<TodoIdAlt, TodoItem>>>() {})
//                items.size() shouldBe 1
//            }
//        }
//    }
}) {
    companion object {
        val constTime = HasteTimeProvider(Haste.TimeSource.withFixedClock(Clock.fixed(
            LocalDateTime.parse("2021-04-10T10:10:10").toInstant(ZoneOffset.UTC),
            ZoneId.of("UTC")
        )))

//        val ctxProvider = webContextProvider
        fun todoRest(testContextProvider: JDBCBasedWebContextProvider) = run {
            testApplication(DefaultJacksonMapper.mapper, testContextProvider) {
                TodoServer.defineRouting(testContextProvider, constTime)
            }
        }
    }
}

private fun TestApplicationEngine.addItemUsingPOST(title: String): Int =
    with(handleRequest(HttpMethod.Post, "/todo?title=$title")) {
        val addedId = DefaultJacksonMapper.mapper.readValue(response.byteContent!!, TodoIdAlt::class.java)
        addedId.id
    }

data class TodoIdAlt(val id: Int)

fun usingTestDb(spec: (JDBCBasedWebContextProvider) -> Unit) = run {
    TestDB().use { testDb ->
        spec(testDb.testContext)
    }
}

class TestDB : AutoCloseable {

    private val testJdbc = JDBCConfig(
        driverClassName = "org.h2.Driver",
        url = "jdbc:h2:mem:test",
        user = "sa",
        password = "notVerySecret"
    )

    private val connection: Connection = Class.forName(testJdbc.driverClassName).let {
        DriverManager.getConnection(testJdbc.url, testJdbc.user, testJdbc.password)
    }

    val testContext = object : JDBCBasedWebContextProvider() {
        private val jdbcConfig = testJdbc

        @Suppress("ReturnUnit")
        override val jdbcProvider: JDBCProvider by lazy {
            JDBCProvider(connection).also { jdbc ->
                val flyway = Flyway.configure().dataSource(jdbc.dataSource()).load()
                flyway.migrate()
            }
        }
        override val errorHandler: ErrorHandler by lazy {
            todoErrorHandler
        }
    }

    override fun close() {
        connection.createStatement().use { dropAll ->
            val result = dropAll.execute("drop all objects")
        }
        connection.close()
    }

}
