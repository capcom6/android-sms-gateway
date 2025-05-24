package com.example.routes

import com.example.auth.AgentAuthService
import com.example.model.RegisteredAgent
import com.example.model.api.*
import com.example.services.AgentService
import com.example.services.AgentTaskService
import com.example.services.IncomingMessageService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.Instant
import java.util.UUID

const val AGENT_API_KEY_HEADER = "X-Agent-API-Key"

// Simplified authentication helper for this MVP
private suspend fun ApplicationCall.authenticateAgent(
    agentService: AgentService,
    agentAuthService: AgentAuthService
): RegisteredAgent? {
    val apiKey = request.header(AGENT_API_KEY_HEADER)
    if (apiKey == null) {
        respond(HttpStatusCode.Unauthorized, mapOf("error" to "Missing $AGENT_API_KEY_HEADER header"))
        return null
    }
    val hashedApiKey = agentAuthService.hashApiKey(apiKey)
    val agent = agentService.findByApiKeyHash(hashedApiKey)
    if (agent == null) {
        respond(HttpStatusCode.Forbidden, mapOf("error" to "Invalid API Key"))
        return null
    }
    if (!agent.isEnabled) {
        respond(HttpStatusCode.Forbidden, mapOf("error" to "Agent account disabled"))
        return null
    }
    return agent
}

fun Routing.agentApiV1Routes(
    agentService: AgentService,
    agentAuthService: AgentAuthService,
    agentTaskService: AgentTaskService,
    incomingMessageService: IncomingMessageService
) {
    route("/agent/v1") {
        post("/register") {
            val request = call.receive<RegisterAgentRequest>()
            val apiKey = agentAuthService.generateApiKey()
            val hashedApiKey = agentAuthService.hashApiKey(apiKey)

            try {
                val newAgentEntity = agentService.createAgent(
                    name = request.name,
                    apiKeyHash = hashedApiKey
                    // dailySmsLimit and smsPrefix can be set to defaults by AgentService.createAgent
                )
                if (newAgentEntity != null) {
                    call.respond(
                        HttpStatusCode.Created,
                        RegisterAgentResponse(
                            id = newAgentEntity.id,
                            apiKey = apiKey, // Return unhashed key ONCE
                            name = newAgentEntity.name
                        )
                    )
                } else {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to create agent"))
                }
            } catch (e: Exception) {
                // Catch potential unique constraint violations on apiKeyHash if UUIDs somehow collide (highly unlikely)
                // or other DB errors
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Error creating agent: ${e.message}"))
            }
        }

        // Authenticated routes below
        post("/heartbeat") {
            val agent = call.authenticateAgent(agentService, agentAuthService) ?: return@post
            agentService.updateHeartbeat(agent.id)
            call.respond(HttpStatusCode.OK)
        }

        get("/config") {
            val agent = call.authenticateAgent(agentService, agentAuthService) ?: return@get
            // AgentService.findById should be used if authenticateAgent doesn't return the full agent object
            // or if we need to be absolutely sure we have the latest version.
            // For MVP, the agent object from authenticateAgent is likely fresh enough.
            call.respond(
                AgentConfigResponse(
                    isEnabled = agent.isEnabled,
                    dailySmsLimit = agent.dailySmsLimit,
                    smsPrefix = agent.smsPrefix,
                    name = agent.name
                )
            )
        }

        get("/tasks/sms/outgoing") {
            val agent = call.authenticateAgent(agentService, agentAuthService) ?: return@get
            // Retrieve tasks assigned to this agent that are in "ASSIGNED" status
            val tasks = agentTaskService.outgoingSmsTaskService.findPendingTasksForAgent(agent.id, 10) // Limit to 10 for now
            call.respond(tasks.map {
                AgentSmsTaskResponse(
                    taskId = it.id,
                    recipient = it.recipient,
                    messageText = it.messageContent
                )
            })
        }

        post("/tasks/sms/outgoing/{taskId}/status") {
            val agent = call.authenticateAgent(agentService, agentAuthService) ?: return@post
            val taskIdStr = call.parameters["taskId"]
            if (taskIdStr == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing taskId parameter"))
                return@post
            }
            val taskId = try {
                UUID.fromString(taskIdStr)
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid taskId format"))
                return@post
            }

            val request = call.receive<TaskStatusUpdateRequest>()
            if (request.status !in listOf("SENT", "FAILED")) {
                 call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid status. Must be SENT or FAILED."))
                 return@post
            }
            if (request.status == "FAILED" && request.failureReason.isNullOrBlank()) {
                 call.respond(HttpStatusCode.BadRequest, mapOf("error" to "failureReason is required when status is FAILED."))
                 return@post
            }

            agentTaskService.processTaskStatusUpdate(
                taskId = taskId,
                agentId = agent.id,
                status = request.status,
                failureReason = request.failureReason
            )
            call.respond(HttpStatusCode.OK, mapOf("message" to "Status updated for task $taskId"))
        }

        post("/messages/incoming") {
            val agent = call.authenticateAgent(agentService, agentAuthService) ?: return@post
            val request = call.receive<IncomingSmsRequest>()

            incomingMessageService.handleIncomingAgentSms(
                agentId = agent.id,
                originalSender = request.sender,
                messageText = request.messageText,
                receivedAtAgentAt = Instant.ofEpochMilli(request.timestamp)
            )
            call.respond(HttpStatusCode.Accepted, mapOf("message" to "Incoming SMS processed"))
        }
    }
}
