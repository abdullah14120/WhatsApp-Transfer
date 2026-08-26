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

        findViewById<Button>(dirBtnId(R.id.btnUsbMode)).setOnClickListener {
            launchMain("USB")
        }

        findViewById<Button>(R.id.btnWifiMode).setOnClickListener {
            launchMain("WIFI")
        }
    }

    private fun dirBtnId(id: Int) = id

    private fun launchMain(mode: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("TRANSFER_MODE", mode)
        }
        startActivity(intent)
    }
}
