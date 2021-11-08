package dev.neeffect.example.todo

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.core.type.TypeReference
import dev.neeffect.nee.ctx.web.DefaultJacksonMapper
import dev.neeffect.nee.effects.time.HasteTimeProvider
import dev.neeffect.nee.web.test.testApplication
import io.haste.Haste
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.vavr.Tuple2
import io.vavr.collection.Seq
import pl.setblack.nee.example.todolist.TodoItem
import pl.setblack.nee.example.todolist.TodoServer
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

@Suppress("ReturnUnit")
class TodoServerTest : StringSpec({

    "add item should give an id" {
        withTestApplication({
            todoRest()
        }) {
            with(handleRequest(HttpMethod.Post, "/todo?title=hello")) {
                val addedId = DefaultJacksonMapper.mapper.readValue(response.byteContent, Int::class.java)
                addedId shouldBe (1)
            }
        }
    }
    "added item should be returned by id" {
        withTestApplication({
            todoRest()
        }) {
            addItemUsingPOST("hello")
            with(handleRequest(HttpMethod.Get, "/todo/1")) {
                val item = DefaultJacksonMapper.mapper.readValue(response.byteContent, TodoItem::class.java)
                item.title shouldBe "hello"
            }
        }
    }
    "calling get with text id returns error" {
        withTestApplication({
            todoRest()
        }) {
            addItemUsingPOST("hello")
            with(handleRequest(HttpMethod.Get, "/todo/zupka")) {
                response.status() shouldBe HttpStatusCode.BadRequest
            }
        }
    }
    "added item should be in find all" {
        withTestApplication({
            todoRest()
        }) {
            addItemUsingPOST("hello")
            with(handleRequest(HttpMethod.Get, "/todo")) {
                val items = DefaultJacksonMapper.mapper.readValue(response.byteContent,
                    object : TypeReference<Seq<Tuple2<TodoIdAlt, TodoItem>>>() {})
                items.size() shouldBe 1
                items[0]._2.title shouldBe ("hello")
            }
        }
    }

    "added  multiple items should be in find all" {
        withTestApplication({
            todoRest()
        }) {
            (0 until 3).forEach {
                addItemUsingPOST("hello_$it")
            }
            with(handleRequest(HttpMethod.Get, "/todo")) {
                val items = DefaultJacksonMapper.mapper.readValue(response.byteContent,
                    object : TypeReference<Seq<Tuple2<TodoIdAlt, TodoItem>>>() {})
                items.size() shouldBe 3
                items.map { it._2.title } shouldContainAll ((0 until 3).map { "hello_$it" })
            }
        }
    }
    "done should mark as done" {
        withTestApplication({
            todoRest()
        }) {
            val id = addItemUsingPOST("hello")
            handleRequest(HttpMethod.Post, "/todo/done?id=$id")
            with(handleRequest(HttpMethod.Get, "/todo/$id")) {
                val content = response.byteContent
                val item = DefaultJacksonMapper.mapper.readValue(content, TodoItem::class.java)
                item.title shouldBe "hello"
                item.shouldBeTypeOf<TodoItem.Done>()
            }
        }
    }
    "done twice on same item should lead to 409" {
        withTestApplication({
            todoRest()
        }) {
            val id = addItemUsingPOST("hello")
            handleRequest(HttpMethod.Post, "/todo/done?id=$id")
            with(handleRequest(HttpMethod.Post, "/todo/done?id=$id")) {
                response.status().shouldBe(HttpStatusCode.Conflict)
            }
        }
    }
    "done should not be on todo list" {
        withTestApplication({
            todoRest()
        }) {
            val id = addItemUsingPOST("hello")
            handleRequest(HttpMethod.Post, "/todo/done?id=$id")
            with(handleRequest(HttpMethod.Get, "/todo")) {
                val items = DefaultJacksonMapper.mapper.readValue(response.byteContent,
                    object : TypeReference<Seq<Tuple2<TodoIdAlt, TodoItem>>>() {})
                items.size() shouldBe 0
            }
        }
    }
    "done should  be on done list" {
        withTestApplication({
            todoRest()
        }) {
            val id = addItemUsingPOST("hello")
            handleRequest(HttpMethod.Post, "/todo/done?id=$id")
            with(handleRequest(HttpMethod.Get, "/todo/done")) {
                val items = DefaultJacksonMapper.mapper.readValue(response.byteContent,
                    object : TypeReference<Seq<Tuple2<TodoIdAlt, TodoItem>>>() {})
                items.size() shouldBe 1
            }
        }
    }

    "cancelled should not be on todo list" {
        withTestApplication({
            todoRest()
        }) {
            val id = addItemUsingPOST("hello")
            handleRequest(HttpMethod.Delete, "/todo/$id")
            with(handleRequest(HttpMethod.Get, "/todo")) {
                val items = DefaultJacksonMapper.mapper.readValue(response.byteContent,
                    object : TypeReference<Seq<Tuple2<TodoIdAlt, TodoItem>>>() {})
                items.size() shouldBe 0
            }
        }
    }
    "cancelled should be on cancelled list" {
        withTestApplication({
            todoRest()
        }) {
            val id = addItemUsingPOST("hello")
            handleRequest(HttpMethod.Delete, "/todo/$id")
            with(handleRequest(HttpMethod.Get, "/todo/cancelled")) {
                val items = DefaultJacksonMapper.mapper.readValue(response.byteContent,
                    object : TypeReference<Seq<Tuple2<TodoIdAlt, TodoItem>>>() {})
                items.size() shouldBe 1
            }
        }
    }
}) {
    companion object {
        val constTime = HasteTimeProvider(Haste.TimeSource.withFixedClock(Clock.fixed(
            LocalDateTime.parse("2021-04-10T10:10:10").toInstant(ZoneOffset.UTC),
            ZoneId.of("UTC")
        )))

        val ctxProvider = webContextProvider
        val todoRest = testApplication(DefaultJacksonMapper.mapper, webContextProvider) {
            TodoServer.defineRouting(ctxProvider, constTime)
        }
    }
}

private fun TestApplicationEngine.addItemUsingPOST(title: String): Int =
    with(handleRequest(HttpMethod.Post, "/todo?title=$title")) {
        val addedId = DefaultJacksonMapper.mapper.readValue(response.byteContent, Int::class.java)
        addedId
    }

data class TodoIdAlt @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(val id: Int)
