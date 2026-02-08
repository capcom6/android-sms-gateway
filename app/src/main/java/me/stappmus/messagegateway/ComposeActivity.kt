package me.stappmus.messagegateway

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class ComposeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(android.content.Intent(this, MainActivity::class.java))
        finish()
    }
}
