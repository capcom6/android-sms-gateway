package me.capcom.smsgateway.ui

import android.app.Activity
import android.os.Bundle

/**
 * Stub compose activity registered to satisfy the default-SMS-app
 * requirement (the system checks that we can handle `SENDTO` for sms/mms
 * schemes). We have no user-facing compose UI — this activity just
 * finishes immediately.
 */
class ComposeSmsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
