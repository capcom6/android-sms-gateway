package com.example.services

import com.example.model.RegisteredAgent
import org.slf4j.LoggerFactory
import java.util.UUID

class AgentTaskService(
    private val agentService: AgentService,
    private val outgoingSmsTaskService: OutgoingSmsTaskService
) {
    private val logger = LoggerFactory.getLogger(AgentTaskService::class.java)
    private var roundRobinAgentIndex = 0 // Simple round-robin counter

    suspend fun assignTasksToAvailableAgents(batchSizePerAgent: Int = 5) {
        val enabledAgents = agentService.getAllAgents().filter { it.isEnabled }
        if (enabledAgents.isEmpty()) {
            logger.info("No enabled agents available to assign tasks.")
            return
        }
        
        logger.info("Attempting to assign tasks to ${enabledAgents.size} enabled agents.")

        // Round-robin assignment for this run. A more sophisticated scheduler might be needed for fairness over time.
        // This approach assigns a batch to one agent, then moves to the next.
        for (i in enabledAgents.indices) {
            val agentIndex = (roundRobinAgentIndex + i) % enabledAgents.size
            val agent = enabledAgents[agentIndex]

            if (agent.dailySmsLimit != -1 && agent.currentDailySmsCount >= agent.dailySmsLimit) {
                logger.info("Agent ${agent.name} (ID: ${agent.id}) has reached daily SMS limit (${agent.currentDailySmsCount}/${agent.dailySmsLimit}). Skipping task assignment.")
                continue
            }

            val tasksToAssign = outgoingSmsTaskService.findUnassignedTasks(batchSizePerAgent)
            if (tasksToAssign.isEmpty()) {
                logger.info("No unassigned tasks available to assign for agent ${agent.name} (ID: ${agent.id}).")
                // If no tasks for this agent, we might as well stop trying for others in this run if task fetching is global.
                // Or, if findUnassignedTasks is cheap, we can continue for other agents.
                // For MVP, let's assume we can break if no tasks are found for one agent.
                break 
            }

            logger.info("Found ${tasksToAssign.size} tasks to potentially assign to agent ${agent.name} (ID: ${agent.id}).")

            var assignedCount = 0
            for (task in tasksToAssign) {
                // Double check limit before assigning each task
                if (agent.dailySmsLimit != -1 && (agent.currentDailySmsCount + assignedCount) >= agent.dailySmsLimit) {
                    logger.info("Agent ${agent.name} (ID: ${agent.id}) would exceed daily limit with next task. Stopping assignment for this agent.")
                    break // Stop assigning to this agent
                }

                if (outgoingSmsTaskService.assignTaskToAgent(task.id, agent.id)) {
                    logger.info("Assigned task ${task.id} to agent ${agent.name} (ID: ${agent.id}).")
                    assignedCount++
                } else {
                    logger.warn("Failed to assign task ${task.id} to agent ${agent.name} (ID: ${agent.id}). Task might have been assigned by another process.")
                }
            }
            if (assignedCount == 0 && tasksToAssign.isNotEmpty()) {
                 logger.info("Could not assign any of the ${tasksToAssign.size} pending tasks to agent ${agent.name} (ID: ${agent.id}), possibly due to limit or concurrent assignment.")
            }
        }
        // Update round-robin index for next call, ensuring it wraps around.
        // This simple round-robin might not be perfectly fair if batch sizes vary or agents have different capacities.
        if (enabledAgents.isNotEmpty()) {
            roundRobinAgentIndex = (roundRobinAgentIndex + 1) % enabledAgents.size
        }
    }

    suspend fun processTaskStatusUpdate(taskId: UUID, agentId: UUID, status: String, failureReason: String?) {
        logger.info("Processing task status update: TaskID=$taskId, AgentID=$agentId, Status=$status, Reason=${failureReason ?: "N/A"}")
        
        val task = outgoingSmsTaskService.getTaskById(taskId)
        if (task == null) {
            logger.warn("Task with ID $taskId not found. Cannot update status from agent $agentId.")
            return
        }
        if (task.agentId != agentId) {
            logger.warn("Agent $agentId reported status for task $taskId, but task is assigned to agent ${task.agentId}. Ignoring.")
            return
        }
        if (task.status !in listOf("ASSIGNED", "RETRY")) { // Only allow updates for tasks that are actively assigned or pending retry by agent
            logger.warn("Task $taskId is in status ${task.status}, not ASSIGNED or RETRY. Agent $agentId update to $status ignored.")
            return
        }

        val success = outgoingSmsTaskService.updateTaskStatus(taskId, status.uppercase(), failureReason, agentId)
        if (success) {
            logger.info("Task $taskId status updated to $status by agent $agentId.")
            if (status.equals("SENT", ignoreCase = true)) {
                val agentIncrementSuccess = agentService.incrementSmsCount(agentId)
                if (agentIncrementSuccess) {
                    logger.info("Incremented SMS count for agent $agentId.")
                } else {
                    logger.error("Failed to increment SMS count for agent $agentId after task $taskId sent.")
                }
            }
            // TODO: Trigger webhooks or further processing based on status (e.g., for DLR handling if status is SENT)
        } else {
            logger.error("Failed to update status for task $taskId by agent $agentId.")
        }
    }
}
