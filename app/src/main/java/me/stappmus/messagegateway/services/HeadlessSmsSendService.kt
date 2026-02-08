package me.stappmus.messagegateway.services

import android.app.IntentService
import android.content.Intent

class HeadlessSmsSendService : IntentService("HeadlessSmsSendService") {
    override fun onHandleIntent(intent: Intent?) {
        // Required for default SMS app role but we don't need to handle quick-reply
    }
}
