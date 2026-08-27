package com.file.whatsapp.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import com.file.whatsapp.R
import com.file.whatsapp.core.ConnectionManager

class RoleSelectionActivity : AppCompatActivity() {

    private lateinit var radioGroupRole: RadioGroup
    private lateinit var radioGroupMode: RadioGroup
    private lateinit var edtTargetIp: EditText
    private lateinit var btnProceed: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_role_selection)

        initViews()
        setupListeners()
    }

    private fun initViews() {
        radioGroupRole = findViewById(R.id.radioGroupRole)
        radioGroupMode = findViewById(R.id.radioGroupMode)
        edtTargetIp = findViewById(R.id.edtTargetIp)
        btnProceed = findViewById(R.id.btnProceed)

        // إعداد الحالة الافتراضية
        radioGroupRole.check(R.id.radioSender)
        radioGroupMode.check(R.id.radioWifi)
        edtTargetIp.visibility = View.VISIBLE
    }

    private fun setupListeners() {
        radioGroupMode.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.radioWifi) {
                edtTargetIp.visibility = View.VISIBLE
            } else {
                edtTargetIp.visibility = View.GONE
            }
        }

        btnProceed.setOnClickListener {
            val role = if (radioGroupRole.checkedRadioButtonId == R.id.radioSender) {
                ConnectionManager.Role.SENDER
            } else {
                ConnectionManager.Role.RECEIVER
            }

            val mode = if (radioGroupMode.checkedRadioButtonId == R.id.radioWifi) {
                ConnectionManager.Mode.WIFI
            } else {
                ConnectionManager.Mode.USB
            }

            val targetIp = edtTargetIp.text.toString().trim()

            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("TRANSFER_ROLE", role.name)
                putExtra("TRANSFER_MODE", mode.name)
                putExtra("TARGET_IP", targetIp)
            }
            startActivity(intent)
        }
    }
}
