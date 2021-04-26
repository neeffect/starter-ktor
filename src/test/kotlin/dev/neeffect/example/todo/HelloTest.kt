package dev.neeffect.example.todo

import dev.neeffect.nee.ctx.web.DefaultJacksonMapper
import dev.neeffect.nee.web.test.testApplication
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.application.Application
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import java.time.LocalDateTime
import java.time.ZoneOffset

class HelloTest : StringSpec({
    "server should return hello" {
        withTestApplication (rest )  {
            with(handleRequest(HttpMethod.Get, "/hello")) {
                response.content shouldBe "\"hello world\""
            }
        }
        withTestApplication (rest )  {
            with(handleRequest(HttpMethod.Get, "/error")) {
                response.status() shouldBe HttpStatusCode.NotFound
            }
        }
    }
}) {
    companion object {
        val constTime = suspend { LocalDateTime.parse("2021-03-14T10:10:10").toInstant(ZoneOffset.UTC) }

        val rest: Application.() -> Unit = testApplication(
            DefaultJacksonMapper.mapper,
            webContextProvider
        ) {
            it + routing
        }
    }
}