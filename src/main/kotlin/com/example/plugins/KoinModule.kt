package com.example.plugins

import com.example.auth.AgentAuthService
import com.example.db.DatabaseFactory
import com.example.jobs.DailyCountResetJob
import com.example.services.AgentService
import com.example.services.AgentTaskService
import com.example.services.IncomingMessageService
import com.example.services.IncomingSmsLogService
import com.example.services.OutgoingSmsTaskService
import com.example.smpp.SmppConfig
import com.example.smpp.SmppServerService
import io.ktor.server.application.*
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

val dbModule = module {
    single { AgentService() }
    single { OutgoingSmsTaskService() }
    single { IncomingSmsLogService() }
    // DatabaseFactory.init() should be called explicitly at application start, not part of Koin module usually
    // unless it's wrapped in a class that Koin can manage. For simplicity, assume it's called in Application.module.
}

val appModule = module {
    single { AgentAuthService() }
    
    // For MVP, SmppConfig can be hardcoded or loaded from environment
    // This assumes you'd load it from environment.config or a dedicated config file in a real app
    single {
        // Example: Fetch from environment properties or a config file
        // For MVP, hardcoding is acceptable if environment config is not set up
        val systemId = System.getenv("SMPP_SERVER_SYSTEM_ID") ?: "testsmsc"
        val password = System.getenv("SMPP_SERVER_PASSWORD") ?: "password"
        val port = System.getenv("SMPP_SERVER_PORT")?.toIntOrNull() ?: 2775
        val host = System.getenv("SMPP_SERVER_HOST") ?: "0.0.0.0"
        SmppConfig(
            host = host,
            port = port,
            systemId = systemId,
            password = password
        )
    }
    
    single { SmppServerService(get()) } // Depends on OutgoingSmsTaskService
    single { AgentTaskService(get(), get()) } // Depends on AgentService, OutgoingSmsTaskService
    single { IncomingMessageService(get()) } // Depends on IncomingSmsLogService
    single { DailyCountResetJob(get()) } // Depends on AgentService
}

// Extension function to install Koin, called from Application.module()
fun Application.configureKoin() {
    DatabaseFactory.init() // Initialize database before Koin setup that might need DB services
    install(Koin) {
        slf4jLogger() // Use SLF4JLogger, ensure you have an SLF4J binding (e.g., logback)
        modules(dbModule, appModule)
    }
}
