package me.capcom.smsgateway.modules.localserver.routes

import android.content.Context
import com.google.gson.JsonParser
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.gson.gson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.basic
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.TestApplicationResponse
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import io.mockk.every
import io.mockk.mockk
import me.capcom.smsgateway.data.entities.Message
import me.capcom.smsgateway.data.entities.MessageRecipient
import me.capcom.smsgateway.data.entities.MessageWithRecipients
import me.capcom.smsgateway.domain.EntitySource
import me.capcom.smsgateway.domain.MessageContent
import me.capcom.smsgateway.domain.ProcessingState
import me.capcom.smsgateway.modules.localserver.LocalServerSettings
import me.capcom.smsgateway.modules.messages.MessagesService
import me.capcom.smsgateway.modules.messages.data.SendRequest
import me.capcom.smsgateway.modules.messages.exceptions.ConflictException
import me.capcom.smsgateway.modules.receiver.ReceiverService
import me.capcom.smsgateway.modules.settings.KeyValueStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Type
import java.util.Base64

/**
 * Route-level tests for the localserver MessagesRoutes POST /messages handler.
 *
 * Mirrors the WebService bootstrapping (Authentication basic + ContentNegotiation
 * + StatusPages) that the real server applies around MessagesRoutes; the test
 * installs only the "auth-basic" provider because the JWT provider needs a
 * real verifier and is orthogonal to the Location header behavior.
 */
class MessagesRoutesTest {

    companion object {
        private const val TEST_USER = "sms"
        private const val TEST_PASSWORD = "password"
        private const val DEVICE_ID = "device-1"
        private const val MESSAGE_ID = "msg-123"
    }

    private fun settings(): LocalServerSettings {
        val storage = object : KeyValueStorage {
            val values = mutableMapOf<String, Any?>()
            override fun <T> set(key: String, value: T) {
                values[key] = value
            }

            override fun <T> get(key: String, typeOfT: Type): T? {
                @Suppress("UNCHECKED_CAST")
                return values[key] as T?
            }

            override fun remove(key: String) {
                values.remove(key)
            }
        }
        return LocalServerSettings(storage).apply {
            deviceId = DEVICE_ID
        }
    }

    private fun message(id: String = MESSAGE_ID) = MessageWithRecipients(
        Message(
            id = id,
            withDeliveryReport = true,
            simNumber = null,
            validUntil = null,
            scheduleAt = null,
            isEncrypted = false,
            skipPhoneValidation = false,
            priority = Message.PRIORITY_DEFAULT,
            source = EntitySource.Local,
            content = MessageContent.Text("hello"),
            createdAt = System.currentTimeMillis(),
        ),
        listOf(
            MessageRecipient(id, "+79991234567", ProcessingState.Pending)
        ),
    )

    private fun module(
        messagesService: MessagesService,
    ): Application.() -> Unit = {
        install(Authentication) {
            basic("auth-basic") {
                realm = "Access to SMS Gateway"
                validate { credentials ->
                    when {
                        credentials.name == TEST_USER && credentials.password == TEST_PASSWORD ->
                            UserIdPrincipal(credentials.name)

                        else -> null
                    }
                }
            }
        }
        install(ContentNegotiation) {
            gson()
        }
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                call.respond(
                    when (cause) {
                        is IllegalArgumentException -> HttpStatusCode.BadRequest
                        is BadRequestException -> HttpStatusCode.BadRequest
                        is NotFoundException -> HttpStatusCode.NotFound
                        else -> HttpStatusCode.InternalServerError
                    },
                    mapOf(
                        "message" to ((cause.localizedMessage ?: cause.message) ?: cause.toString())
                    )
                )
            }
        }
        routing {
            authenticate("auth-basic") {
                route("/messages") {
                    MessagesRoutes(
                        context = mockk<Context>(relaxed = true),
                        messagesService = messagesService,
                        receiverService = mockk<ReceiverService>(relaxed = true),
                        settings = settings(),
                    ).register(this)
                }
            }
        }
    }

    private fun basicAuth(): String {
        return "Basic " + Base64.getEncoder()
            .encodeToString("$TEST_USER:$TEST_PASSWORD".toByteArray(Charsets.UTF_8))
    }

    private fun TestApplicationEngine.postMessage(
        body: String,
        authorized: Boolean = true,
    ): TestApplicationResponse {
        return handleRequest(HttpMethod.Post, "/messages") {
            if (authorized) {
                addHeader(HttpHeaders.Authorization, basicAuth())
            }
            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(body)
        }.response
    }

    private fun TestApplicationResponse.assertAbsolutePathLocation(expected: String) {
        val location = headers[HttpHeaders.Location]
        assertNotNull("Location header missing on 202", location)
        assertEquals("Location must be exactly $expected", expected, location)
        // AC-GAP4-4: absolute path only - single leading '/', no scheme,
        // no host, no query string, no double slash.
        assertTrue("Location must start with a single '/'", location!!.startsWith("/"))
        assertFalse("Location must not start with '//'", location.startsWith("//"))
        assertFalse("Location must not contain a scheme/host", location.contains("://"))
        assertFalse("Location must not contain a query string", location.contains("?"))
        assertFalse("Location must not contain a fragment", location.contains("#"))
    }

    @Test
    fun postMessagesReturns202WithLocationHeaderForEnqueuedId() {
        var enqueuedId: String? = null
        val service = mockk<MessagesService>()
        every { service.enqueueMessage(any()) } answers {
            firstArg<SendRequest>().let { request ->
                enqueuedId = request.message.id
                message(request.message.id)
            }
        }

        withTestApplication(module(service)) {
            val response = postMessage(
                """{"textMessage":{"text":"hello"},"phoneNumbers":["+79991234567"]}"""
            )

            assertEquals(HttpStatusCode.Accepted, response.status())
            assertNotNull("enqueued id should have been captured", enqueuedId)
            response.assertAbsolutePathLocation("/messages/$enqueuedId")
        }
    }

    @Test
    fun postMessagesUsesClientProvidedIdInLocation() {
        val customId = "custom-id-42"
        val service = mockk<MessagesService>()
        every { service.enqueueMessage(any()) } answers {
            firstArg<SendRequest>().let { request ->
                message(request.message.id)
            }
        }

        withTestApplication(module(service)) {
            val response = postMessage(
                """{"id":"$customId","textMessage":{"text":"hello"},"phoneNumbers":["+79991234567"]}"""
            )

            assertEquals(HttpStatusCode.Accepted, response.status())
            response.assertAbsolutePathLocation("/messages/$customId")
        }
    }

    @Test
    fun getLocationPathResolvesToSameMessageId() {
        val service = mockk<MessagesService>()
        every { service.enqueueMessage(any()) } returns message(MESSAGE_ID)
        every { service.getMessage(MESSAGE_ID) } returns message(MESSAGE_ID)

        withTestApplication(module(service)) {
            val post = postMessage(
                """{"textMessage":{"text":"hello"},"phoneNumbers":["+79991234567"]}"""
            )
            assertEquals(HttpStatusCode.Accepted, post.status())

            val location = post.headers[HttpHeaders.Location]
            assertNotNull("Location header missing on 202", location)

            val get = handleRequest(HttpMethod.Get, location!!) {
                addHeader(HttpHeaders.Authorization, basicAuth())
            }.response

            // AC-GAP4-2: GET on the Location path returns the same message id.
            assertEquals(HttpStatusCode.OK, get.status())
            val body = requireNotNull(get.content) { "GET body must not be null" }
            val id = JsonParser.parseString(body).asJsonObject.get("id").asString
            assertEquals(MESSAGE_ID, id)
        }
    }

    @Test
    fun postMessagesEncodesSpecialCharsInClientIdInLocation() {
        val specialId = "part/123"
        val service = mockk<MessagesService>()
        every { service.enqueueMessage(any()) } answers {
            firstArg<SendRequest>().let { request ->
                message(request.message.id)
            }
        }

        withTestApplication(module(service)) {
            val response = postMessage(
                """{"id":"$specialId","textMessage":{"text":"hello"},"phoneNumbers":["+79991234567"]}"""
            )

            // AC-GAP4-6: path delimiters in client IDs are percent-encoded so the
            // Location stays a single path segment.
            assertEquals(HttpStatusCode.Accepted, response.status())
            response.assertAbsolutePathLocation("/messages/${specialId.replace("/", "%2F")}")
        }
    }

    @Test
    fun getLocationWithEncodedSpecialIdResolvesToSameMessageId() {
        val specialId = "part/123"
        val service = mockk<MessagesService>()
        every { service.enqueueMessage(any()) } returns message(specialId)
        every { service.getMessage(specialId) } returns message(specialId)

        withTestApplication(module(service)) {
            val post = postMessage(
                """{"id":"part/123","textMessage":{"text":"hello"},"phoneNumbers":["+79991234567"]}"""
            )
            assertEquals(HttpStatusCode.Accepted, post.status())

            val location = post.headers[HttpHeaders.Location]
            assertNotNull("Location header missing on 202", location)
            assertTrue("Location must encode path delimiters", location!!.contains("%2F"))

            // AC-GAP4-7: GET on the encoded Location path returns the same message id.
            val get = handleRequest(HttpMethod.Get, location) {
                addHeader(HttpHeaders.Authorization, basicAuth())
            }.response

            assertEquals(HttpStatusCode.OK, get.status())
            val body = requireNotNull(get.content) { "GET body must not be null" }
            val id = JsonParser.parseString(body).asJsonObject.get("id").asString
            assertEquals(specialId, id)
        }
    }

    @Test
    fun postMessagesConflictReturns409WithoutLocation() {
        val service = mockk<MessagesService>()
        every { service.enqueueMessage(any()) } throws ConflictException()

        withTestApplication(module(service)) {
            val response = postMessage(
                """{"textMessage":{"text":"hello"},"phoneNumbers":["+79991234567"]}"""
            )

            // AC-GAP4-3: conflict path unchanged - no Location on error.
            assertEquals(HttpStatusCode.Conflict, response.status())
            assertNull(response.headers[HttpHeaders.Location])
        }
    }

    @Test
    fun postMessagesUnauthenticatedReturns401WithoutLocation() {
        val service = mockk<MessagesService>()

        withTestApplication(module(service)) {
            val response = postMessage(
                """{"textMessage":{"text":"hello"},"phoneNumbers":["+79991234567"]}""",
                authorized = false,
            )

            // AC-GAP4-5: 401 on unauthenticated POST, Location absent.
            assertEquals(HttpStatusCode.Unauthorized, response.status())
            assertNull(response.headers[HttpHeaders.Location])
        }
    }

    @Test
    fun postMessagesInvalidPayloadReturns400WithoutLocation() {
        val service = mockk<MessagesService>()

        withTestApplication(module(service)) {
            val response = postMessage(
                """{"textMessage":{"text":""},"phoneNumbers":[]}"""
            )

            assertEquals(HttpStatusCode.BadRequest, response.status())
            assertNull(response.headers[HttpHeaders.Location])
        }
    }
}