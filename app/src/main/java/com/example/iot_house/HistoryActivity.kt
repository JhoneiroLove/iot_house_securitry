package com.example.iot_house

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class HistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        val historyTextView: TextView = findViewById(R.id.historyTextView)
        val backButton: Button = findViewById(R.id.backButton)

        // Obtener los datos del historial de la actividad principal
        val history = intent.getStringArrayListExtra("history") ?: arrayListOf()

        // Mostrar el historial en el TextView
        historyTextView.text = history.joinToString("\n")

        // Configurar el botón para regresar a la actividad anterior
        backButton.setOnClickListener {
            finish() // Esto finaliza la actividad actual y regresa a la anterior
        }
    }
}
