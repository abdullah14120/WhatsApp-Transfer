package com.file.whatsapp.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.file.whatsapp.R

class ModeSelectionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mode_selection)

        findViewById<Button>(R.id.btnUsbMode).setOnClickListener {
            launchMain("USB", "SENDER")
        }

        findViewById<Button>(R.id.btnWifiMode).setOnClickListener {
            launchMain("WIFI", "SENDER")
        }
    }

    private fun launchMain(mode: String, role: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("TRANSFER_MODE", mode)
            putExtra("TRANSFER_ROLE", role)
        }
        startActivity(intent)
    }
}
