package com.file.whatsapp.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.file.whatsapp.R

class ModeSelectionActivity : AppCompatActivity() {

    private lateinit var btnUsbMode: Button
    private lateinit var btnWifiMode: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mode_selection)

        btnUsbMode = findViewById(R.id.btnUsbMode)
        btnWifiMode = findViewById(R.id.btnWifiMode)

        btnUsbMode.setOnClickListener {
            // الانتقال لنمط النقل عبر التخزين المباشر / OTG
            startActivity(Intent(this, MainActivity::class.java).putExtra("TRANSFER_MODE", "USB"))
        }

        btnWifiMode.setOnClickListener {
            // الانتقال لنمط الشبكة المحلية اللاسلكية
            startActivity(Intent(this, MainActivity::class.java).putExtra("TRANSFER_MODE", "WIFI"))
        }
    }
}
