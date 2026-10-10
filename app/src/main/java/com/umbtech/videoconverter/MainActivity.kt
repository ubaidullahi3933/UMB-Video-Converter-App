package com.umbtech.videoconverter

import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = TextView(this).apply {
            text = "UMB Video Converter\n\nSetup complete."
            textSize = 24f
            gravity = Gravity.CENTER
        }

        setContentView(text)
    }
}