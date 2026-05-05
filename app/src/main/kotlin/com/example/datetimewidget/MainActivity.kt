package com.example.datetimewidget

import android.os.Bundle
import android.widget.TextView
import android.app.Activity

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = TextView(this).apply {
            text = "ホーム画面を長押し → ウィジェット → DateTimeWidget を追加してください"
            textSize = 16f
            setPadding(48, 48, 48, 48)
        }
        setContentView(text)
    }
}
