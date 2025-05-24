package com.example.routes

import com.example.services.AgentService
import com.example.services.OutgoingSmsTaskService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

fun Route.dashboardWebRoutes(
    agentService: AgentService,
    outgoingSmsTaskService: OutgoingSmsTaskService
) {
    val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

    authenticate("dashboardAuth") {
        route("/dashboard") {

            fun HTML.commonHead(title: String) {
                head {
                    title { +title }
                    style {
                        unsafe {
                            raw("""
                                body { font-family: sans-serif; margin: 20px; background-color: #f4f4f4; color: #333; }
                                table { border-collapse: collapse; width: 100%; box-shadow: 0 2px 3px #ccc; background-color: white; }
                                th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
                                th { background-color: #e9e9e9; }
                                tr:nth-child(even) { background-color: #f9f9f9; }
                                h1, h2 { color: #333; }
                                nav { margin-bottom: 20px; background-color: #333; padding: 10px; }
                                nav a { color: white; padding: 10px; text-decoration: none; }
                                nav a:hover { background-color: #555; }
                                .container { padding: 20px; background-color: white; box-shadow: 0 2px 3px #ccc; margin-bottom: 20px; }
                                form { margin-bottom: 5px; }
                                input[type="text"], input[type="number"], textarea, select { width: calc(100% - 22px); padding: 10px; margin-bottom: 10px; border: 1px solid #ccc; border-radius: 4px; }
                                input[type="submit"], button { background-color: #5cb85c; color: white; padding: 10px 15px; border: none; border-radius: 4px; cursor: pointer; }
                                input[type="submit"]:hover, button:hover { background-color: #4cae4c; }
                                .action-button-disable { background-color: #d9534f; }
                                .action-button-disable:hover { background-color: #c9302c; }
                                .action-button-reset { background-color: #f0ad4e; }
                                .action-button-reset:hover { background-color: #ec971f; }
                                .error { color: red; margin-bottom: 10px; }
                                .success { color: green; margin-bottom: 10px; }
                            """.trimIndent())
                        }
                    }
                }
            }

            fun BODY.pageLayout(title: String, block: BODY.() -> Unit) {
                h1 { +title }
                nav {
                    a(href = "/dashboard/agents") { +"Agents" }
                    a(href = "/dashboard/tasks") { +"Tasks" }
                    a(href = "/dashboard/submit") { +"Submit SMS" }
                }
                div(classes = "container") {
                    block()
                }
            }
            
            get {
                call.respondRedirect("/dashboard/agents")
            }

            // Agent List Page
            get("/agents") {
                val agents = agentService.getAllAgents()
                call.respondHtml {
                    commonHead("Agents Dashboard")
                    body {
                        pageLayout("Registered Agents") {
                            table {
                                thead {
                                    tr {
                                        th { +"ID" }
                                        th { +"Name" }
                                        th { +"Enabled" }
                                        th { +"API Key (Hash)" } // Show full for MVP, normally masked
                                        th { +"SMS Count/Limit" }
                                        th { +"Last Heartbeat" }
                                        th { +"Actions" }
                                    }
                                }
                                tbody {
                                    agents.forEach { agent ->
                                        tr {
                                            td { +agent.id.toString() }
                                            td { +agent.name }
                                            td { if (agent.isEnabled) b { +"Yes" } else { +"No" } }
                                            td { code { +agent.apiKeyHash } }
                                            td { +"${agent.currentDailySmsCount} / ${if(agent.dailySmsLimit == -1) "Unlimited" else agent.dailySmsLimit.toString()}" }
                                            td { +(agent.lastHeartbeatAt?.let { timeFormatter.format(it) } ?: "Never") }
                                            td {
                                                form(action = "/dashboard/agents/${agent.id}/status", method = FormMethod.post) {
                                                    hiddenInput { name = "isEnabled"; value = (!agent.isEnabled).toString() }
                                                    submitInput(classes = if (agent.isEnabled) "action-button-disable" else "") {
                                                        value = if (agent.isEnabled) "Disable" else "Enable"
                                                    }
                                                }
                                                form(action = "/dashboard/agents/${agent.id}/reset_count_web", method = FormMethod.post) {
                                                    submitInput(classes = "action-button-reset") { value = "Reset Count" }
                                                }
                                            }
                                        }
                                    }
                                    if (agents.isEmpty()) {
                                        tr { td { attributes["colspan"] = "7"; +"No agents found." } }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Task Queue Page
            get("/tasks") {
                val statusFilter = call.request.queryParameters["status"]
                val tasks = outgoingSmsTaskService.getTasks(page = 1, pageSize = 50, statusFilter = statusFilter)
                call.respondHtml {
                    commonHead("Tasks Dashboard")
                    body {
                        pageLayout("Outgoing SMS Tasks") {
                             form(method = FormMethod.get) {
                                label { +"Filter by Status: " }
                                select {
                                    name = "status"
                                    option { value = ""; +"All" }
                                    listOf("PENDING", "ASSIGNED", "SENT", "FAILED", "RETRY").forEach { s ->
                                        option { value = s; if (s == statusFilter) selected = true; +s }
                                    }
                                }
                                submitInput { value = "Filter" }
                            }
                            br {}
                            table {
                                thead {
                                    tr {
                                        th { +"ID" }
                                        th { +"Recipient" }
                                        th { +"Message (Snippet)" }
                                        th { +"Status" }
                                        th { +"Agent ID" }
                                        th { +"Created At" }
                                        th { +"Updated At" }
                                        th { +"Sent At" }
                                        th { +"Failure Reason" }
                                    }
                                }
                                tbody {
                                    tasks.forEach { task ->
                                        tr {
                                            td { code { +task.id.toString() } }
                                            td { +task.recipient }
                                            td { +task.messageContent.take(50) }
                                            td { +task.status }
                                            td { code { +(task.agentId?.toString() ?: "N/A") } }
                                            td { +timeFormatter.format(task.createdAt) }
                                            td { +timeFormatter.format(task.updatedAt) }
                                            td { +(task.sentByAgentAt?.let { timeFormatter.format(it) } ?: "N/A") }
                                            td { +(task.failureReason ?: "N/A") }
                                        }
                                    }
                                    if (tasks.isEmpty()) {
                                        tr { td { attributes["colspan"] = "9"; +"No tasks found." } }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Submit SMS Page
            get("/submit") {
                val agents = agentService.getAllAgents().filter { it.isEnabled }
                call.respondHtml {
                    commonHead("Submit SMS")
                    body {
                        pageLayout("Submit New SMS") {
                            form(action = "/dashboard/submit", method = FormMethod.post) {
                                div {
                                    label { +"Recipient:" }
                                    textInput(name = "recipient") { required = true; placeholder = "e.g., +1234567890" }
                                }
                                div {
                                    label { +"Message:" }
                                    textArea(name = "messageContent", rows = "5") { required = true; placeholder = "Enter SMS content" }
                                }
                                div {
                                    label { +"Agent ID (Optional):" }
                                    select {
                                        name = "agentId"
                                        option { value = ""; +"Any (Automatic)" }
                                        agents.forEach { agent ->
                                            option { value = agent.id.toString(); +"${agent.name} (${agent.id.toString().take(8)}...)" }
                                        }
                                    }
                                }
                                div {
                                    submitInput { value = "Send SMS" }
                                }
                            }
                        }
                    }
                }
            }

            post("/submit") {
                val params = call.receiveParameters()
                val recipient = params["recipient"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing recipient")
                val messageContent = params["messageContent"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing messageContent")
                val agentId = params["agentId"]?.takeIf { it.isNotBlank() }

                // Note: OutgoingSmsTaskService.createTask does not directly take agentId.
                // It's assigned by AgentTaskService. For MVP, we'll create the task
                // and if an agentId is specified, it's more of a preference for the background assignment logic.
                val task = outgoingSmsTaskService.createTask(
                    messageContent = messageContent,
                    recipient = recipient,
                    receivedFromSmppAt = Instant.now() // Or mark as API source
                )
                if (task != null) {
                    // Log preference if agentId was submitted
                    agentId?.let { application.log.info("Dashboard SMS submission for task ${task.id} requested agent $it") }
                    call.respondRedirect("/dashboard/tasks", permanent = false)
                } else {
                    // Could show an error page or redirect with error message
                    call.respondHtml {
                        commonHead("Submit SMS Failed")
                        body {
                            pageLayout("Submit SMS Failed") {
                                p(classes = "error") { +"Failed to create SMS task." }
                                p { a(href = "/dashboard/submit") { +"Try again" } }
                            }
                        }
                    }
                }
            }

            // Handler for agent status toggle
            post("/agents/{agentId}/status") {
                val agentIdStr = call.parameters["agentId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                val agentId = UUID.fromString(agentIdStr)
                val params = call.receiveParameters()
                val isEnabled = params["isEnabled"]?.toBooleanStrictOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)

                agentService.setEnabled(agentId, isEnabled)
                call.respondRedirect("/dashboard/agents")
            }

            // Handler for agent count reset
            post("/agents/{agentId}/reset_count_web") {
                val agentIdStr = call.parameters["agentId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                val agentId = UUID.fromString(agentIdStr)
                
                // TODO: Need a specific method in AgentService for resetting count of a single agent.
                // AgentService.resetAllCounts() resets for all.
                // For now, this is a placeholder action.
                // A proper implementation: agentService.resetSmsCountForAgent(agentId)
                application.log.info("Web dashboard request to reset count for agent $agentId (requires AgentService method).")
                
                call.respondRedirect("/dashboard/agents")
            }
        }
    }
}
