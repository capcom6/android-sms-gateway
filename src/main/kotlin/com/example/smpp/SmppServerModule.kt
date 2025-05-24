package com.example.smpp

import com.example.services.OutgoingSmsTaskService
import org.jsmpp.bean.BindType
import org.jsmpp.bean.DeliverSm
import org.jsmpp.bean.InterfaceVersion
import org.jsmpp.bean.NumberingPlanIndicator
import org.jsmpp.bean.TypeOfNumber
import org.jsmpp.extra.ProcessRequestException
import org.jsmpp.session.BindRequest
import org.jsmpp.session.SMPPServerSession
import org.jsmpp.session.SMPPServerSessionListener
import org.jsmpp.session.ServerMessageReceiverListener
import org.jsmpp.session.ServerResponseDeliveryAdapter
import org.jsmpp.session.Session
import org.jsmpp.util.DefaultSMPPServer
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.Executors

data class SmppConfig(
    val host: String = "0.0.0.0", // Bind to all interfaces
    val port: Int = 2775,
    val systemId: String, // Expected client systemId
    val password: String, // Expected client password
    val interfaceVersion: InterfaceVersion = InterfaceVersion.IF_34 // Default to SMPP 3.4
)

class SmppServerService(
    private val outgoingSmsTaskService: OutgoingSmsTaskService
) {
    private val logger = LoggerFactory.getLogger(SmppServerService::class.java)
    private var smppServer: DefaultSMPPServer? = null
    private var serverSession: SMPPServerSession? = null
    private val singleExecutor = Executors.newSingleThreadExecutor() // For server operations

    private val sessionListener = object : SMPPServerSessionListener {
        override fun sessionCreated(session: SMPPServerSession?) {
            logger.info("SMPP Server Session created: ${session?.sessionId}")
            serverSession = session
            serverSession?.messageReceiverListener = object : ServerMessageReceiverListener {
                override fun onAcceptDeliverSm(deliverSm: DeliverSm?, session: SMPPServerSession?): org.jsmpp.bean.SubmitSmResult {
                    if (deliverSm == null) {
                        logger.warn("Received null DeliverSm from session ${session?.sessionId}")
                        // Returning a generic error; specific error codes can be used.
                        return org.jsmpp.bean.SubmitSmResult(null, org.jsmpp.SMPPConstant.STAT_ESME_RDELIVERYFAILURE)
                    }
                    logger.info("Received DeliverSm: ID=${deliverSm.id}, From=${deliverSm.sourceAddr}, To=${deliverSm.destAddress}, Text='${String(deliverSm.shortMessage)}'")

                    // For MVP, assume deliverSm.shortMessage is the payload and deliverSm.sourceAddr is the recipient for the OutgoingSmsTask.
                    val messageContent = String(deliverSm.shortMessage)
                    val recipient = deliverSm.sourceAddr // This is the phone number of the original sender

                    // Enqueue the message using OutgoingSmsTaskService
                    // This is an async operation, but onAcceptDeliverSm expects a synchronous result.
                    // We'll enqueue and immediately return success to the SMSC.
                    // Real error handling for task creation would need to be more robust.
                    try {
                        // This should be run in a non-blocking way if possible,
                        // but for now, directly calling for MVP simplicity.
                        // Consider launching a coroutine if outgoingSmsTaskService.createTask is suspend.
                        // For now, assume it's a blocking call or can be made blocking for this listener.
                        
                        // If createTask is suspend, it can't be called directly here.
                        // This is a common challenge with jSMPP's synchronous listeners.
                        // For MVP, we might log and acknowledge, or use a temporary blocking context.
                        // Let's assume for now that a non-suspend wrapper or a way to block is used.
                        // For this subtask, we'll just log the intention.
                        
                        logger.info("Intending to create task: Recipient=$recipient, Message='$messageContent'")
                        // Placeholder for actual task creation:
                        // val task = outgoingSmsTaskService.createTask(
                        //    messageContent = messageContent,
                        //    recipient = recipient,
                        //    receivedFromSmppAt = Instant.now()
                        // )
                        // logger.info("OutgoingSmsTask created with ID: ${task?.id}")

                        // Respond to SMSC that the message was accepted
                        // The messageId for SubmitSmResult should be the one from the SMSC if it's a response to a submit_sm.
                        // For a deliver_sm that we receive, we generate a message_id for our system.
                        // The String messageId in SubmitSmResult is the one that will be returned in the submit_sm_resp PDU.
                        // For deliver_sm_resp, the message_id is not usually that significant beyond acknowledging receipt.
                        return org.jsmpp.bean.SubmitSmResult(java.util.UUID.randomUUID().toString(), org.jsmpp.SMPPConstant.STAT_ESME_ROK)
                    } catch (e: Exception) {
                        logger.error("Error creating OutgoingSmsTask from DeliverSm: ${e.message}", e)
                        return org.jsmpp.bean.SubmitSmResult(null, org.jsmpp.SMPPConstant.STAT_ESME_RUNKNOWNERR) // Unknown error
                    }
                }

                override fun onAcceptSubmitSm(submitSm: org.jsmpp.bean.SubmitSm?, session: SMPPServerSession?): org.jsmpp.bean.SubmitSmResult {
                    // This method is for when the Ktor server *receives* a submit_sm from a client (acting as an SMSC).
                    // For this MVP, we are primarily acting as an ESME client to an external SMSC via SmppService (from Android app),
                    // and this Ktor server is more for agent interaction and potentially receiving MO messages via SMPP (as above).
                    // If this Ktor server itself needs to act like an SMSC to some clients, this logic would be filled.
                    logger.warn("onAcceptSubmitSm called, but Ktor server is not primarily acting as an SMSC endpoint for external ESMEs in this MVP. Original Sender: ${submitSm?.sourceAddr}, Recipient: ${submitSm?.destAddress}")
                    // Rejecting by default as this flow isn't the primary focus of the MVP server.
                    return org.jsmpp.bean.SubmitSmResult(null, org.jsmpp.SMPPConstant.STAT_ESME_RSUBMITFAIL) // Submit failed
                }
            }
        }

        override fun sessionDestroyed(session: SMPPServerSession?) {
            logger.info("SMPP Server Session destroyed: ${session?.sessionId}")
            if (serverSession?.sessionId == session?.sessionId) {
                serverSession = null
            }
        }
    }

    fun start(config: SmppConfig) {
        if (smppServer != null && smppServer?.isListening == true) {
            logger.warn("SMPP server is already running on port ${config.port}")
            return
        }
        logger.info("Starting SMPP server on ${config.host}:${config.port} with System ID: ${config.systemId}")

        try {
            smppServer = DefaultSMPPServer(
                singleExecutor, // Executor for session creation
                sessionListener,
                config.port,
                null, // Server session factory, null for default
                null, // PDU processor, null for default
                null, // Connection properties, null for default
                singleExecutor // Executor for PDU processing
            )

            // Set a BindRequestListener to authenticate incoming bind requests
            smppServer?.bindRequestListener = BindRequest -> {
                logger.info("Received bind request: SystemId=${BindRequest.systemId}, BindType=${BindRequest.bindType}")
                if (BindRequest.systemId == config.systemId && BindRequest.password == config.password) {
                    logger.info("Client ${BindRequest.systemId} authenticated successfully.")
                } else {
                    logger.warn("Client ${BindRequest.systemId} authentication failed. Expected SystemId: ${config.systemId}")
                    throw ProcessRequestException("Invalid systemId or password", org.jsmpp.SMPPConstant.STAT_ESME_RINVPASWD)
                }
            }
            
            smppServer?.start() // This method is blocking in older jSMPP, ensure it's non-blocking or run in a separate thread/coroutine
            logger.info("SMPP server started and listening on port ${config.port}.")

        } catch (e: Exception) {
            logger.error("Failed to start SMPP server: ${e.message}", e)
            // Ensure cleanup if start fails partially
            smppServer?.stop()
            smppServer = null
        }
    }

    fun stop() {
        logger.info("Stopping SMPP server...")
        try {
            smppServer?.stop()
            logger.info("SMPP server stopped.")
        } catch (e: Exception) {
            logger.error("Error stopping SMPP server: ${e.message}", e)
        } finally {
            smppServer = null
            serverSession = null // Clear any active session reference
            singleExecutor.shutdown() // Shutdown the executor
        }
    }
}
