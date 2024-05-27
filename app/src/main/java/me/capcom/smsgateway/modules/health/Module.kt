import me.capcom.smsgateway.modules.health.HealthService
import org.koin.dsl.module

val healthModule = module {
    single { HealthService(get()) }
}