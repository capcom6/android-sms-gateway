package com.example.plugins

import com.example.routes.agentApiV1Routes
import com.example.routes.dashboardApiV1Routes
import com.example.services.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Application.configureRouting() {
    // Basic Auth for Dashboard (Hardcoded for MVP)
    install(Authentication) {
        basic("dashboardAuth") {
            realm = "Ktor Server Dashboard"
            validate { credentials ->
                // Replace with config-based check or more robust user management for non-MVP
                if (credentials.name == "admin" && credentials.password == "password") { // Using "password" as per common examples
                    UserIdPrincipal(credentials.name)
                } else {
                    null
                }
            }
        }
    }
    
    // Inject services using Koin
    val agentService by inject<AgentService>()
    val agentAuthService by inject<com.example.auth.AgentAuthService>() // Explicit import if needed
    val agentTaskService by inject<AgentTaskService>()
    val incomingMessageService by inject<IncomingMessageService>()
    val outgoingSmsTaskService by inject<OutgoingSmsTaskService>()

    routing {
        // Register Agent API routes (publicly accessible for registration, internal routes use header auth)
        agentApiV1Routes(agentService, agentAuthService, agentTaskService, incomingMessageService)
        
        // Register Dashboard API routes (protected by Basic Auth)
        dashboardApiV1Routes(agentService, outgoingSmsTaskService)

        // Register Dashboard Web routes (authentication is handled within the function)
        com.example.routes.dashboardWebRoutes(agentService, outgoingSmsTaskService)
    }
}
