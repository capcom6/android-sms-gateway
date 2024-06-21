package me.capcom.smsgateway.modules.webhooks.workers

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.userAgent
import io.ktor.serialization.gson.gson
import me.capcom.smsgateway.BuildConfig
import me.capcom.smsgateway.extensions.configure
import me.capcom.smsgateway.modules.webhooks.domain.WebHookEventDTO
import org.json.JSONException
import org.koin.core.component.KoinComponent
import java.util.concurrent.TimeUnit

class SendWebhookWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params), KoinComponent {
    override suspend fun doWork(): Result {
        try {
            val url = inputData.getString(INPUT_URL) ?: return Result.failure()
            val data = inputData.getString(INPUT_DATA)
                ?.let { gson.fromJson(it, JsonObject::class.java) }
                ?: return Result.failure()
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(data)
            }

            if (response.status.value !in 200..299) {
                return Result.retry()
            }
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            return Result.failure()
        } catch (e: JSONException) {
            e.printStackTrace()
            return Result.failure()
        } catch (e: Throwable) {
            e.printStackTrace()
            return when (runAttemptCount >= MAX_RETRIES) {
                false -> Result.retry()
                else -> Result.failure()
            }
        } finally {
            client.close()
        }
        return Result.success()
    }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            gson {
                configure()
            }
        }
        install(DefaultRequest) {
            userAgent("${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME}")
        }
    }

    companion object {
        fun start(
            context: Context,
            url: String,
            data: WebHookEventDTO
        ) {
            val work = OneTimeWorkRequestBuilder<SendWebhookWorker>()
                .setInputData(
                    workDataOf(
                        INPUT_URL to url,
                        INPUT_DATA to gson.toJson(data),
                    )
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context)
                .enqueue(work)
        }

        private val gson = GsonBuilder().configure().create()

        private const val MAX_RETRIES = 14

        private const val INPUT_URL = "url"
        private const val INPUT_DATA = "data"
    }
}