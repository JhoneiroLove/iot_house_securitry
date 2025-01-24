package com.example.iot_house

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.widget.*
import androidx.activity.ComponentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val esp32BaseUrl = "http://192.168.1.48" // URL base del ESP32
    private val client = OkHttpClient() // Cliente HTTP para manejar solicitudes
    private val handler = Handler(Looper.getMainLooper()) // Handler para tareas periódicas

    private lateinit var systemStatus: TextView // Estado del sistema
    private lateinit var sensorsStatus: TextView // Estado de los sensores
    private lateinit var alarmStatus: TextView // Estado de la alarma
    private lateinit var activityLogButton: Button // Botón para ver el historial
   // private lateinit var cameraView: VideoView // Vista previa de la cámara
    private lateinit var temperatureText: TextView // Texto para la temperatura
    private lateinit var humidityText: TextView // Texto para la humedad
    private lateinit var disableBuzzer2Button: Button // Botón para desactivar buzzer 2
    private lateinit var vibrator: Vibrator // Servicio de vibración
    private val wsClient = OkHttpClient() // Cliente WebSocket

    private val activityLog = mutableListOf<String>() // Lista para el registro de actividad

    private lateinit var database: FirebaseDatabase
    private lateinit var databaseReference: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        database = FirebaseDatabase.getInstance()
        databaseReference = database.reference.child("sensors_activity") // Nodo raíz para tus datos

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Vincular elementos de la interfaz
        systemStatus = findViewById(R.id.systemStatus)
        sensorsStatus = findViewById(R.id.sensorsStatus)
        alarmStatus = findViewById(R.id.alarmStatus)
        activityLogButton = findViewById(R.id.activityLogButton)
      //  cameraView = findViewById(R.id.cameraView)
        temperatureText = findViewById(R.id.temperatureText)
        humidityText = findViewById(R.id.humidityText)
        disableBuzzer2Button = findViewById(R.id.disableBuzzer2Button)
        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator

        // Configurar botones
        setupButtons()

        // Iniciar monitoreo de los sensores
        monitorSystem()

        // Iniciar monitoreo de temperatura y humedad
        monitorTemperature()

        // Configurar WebSocket
        setupWebSocket()
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.startSystemButton).setOnClickListener { controlSystem("on") }
        findViewById<Button>(R.id.stopSystemButton).setOnClickListener { controlSystem("off") }
        findViewById<Button>(R.id.activateAlarmButton).setOnClickListener { controlAlarm("on") }
        findViewById<Button>(R.id.deactivateAlarmButton).setOnClickListener { controlAlarm("off") }
        findViewById<Button>(R.id.stopAlarmButton).setOnClickListener { resetAlarm() }
        disableBuzzer2Button.setOnClickListener { disableBuzzer2() }

        // Botones de control general de LEDs
        findViewById<Button>(R.id.turnOnAllLEDsButton).setOnClickListener { controlAllLEDs("on") }
        findViewById<Button>(R.id.turnOffAllLEDsButton).setOnClickListener { controlAllLEDs("off") }

        // Botones de control para la Sala
        findViewById<Button>(R.id.ledsalaOnButton).setOnClickListener { controlSpecificLED("ledsala", "on") }
        findViewById<Button>(R.id.ledsalaOffButton).setOnClickListener { controlSpecificLED("ledsala", "off") }

        // Botones de control para Dormitorio 1
        findViewById<Button>(R.id.ledDormitorio1OnButton).setOnClickListener { controlSpecificLED("ledDormitorio1", "on") }
        findViewById<Button>(R.id.ledDormitorio1OffButton).setOnClickListener { controlSpecificLED("ledDormitorio1", "off") }

        // Botones de control para Dormitorio 2
        findViewById<Button>(R.id.ledDormitorio2OnButton).setOnClickListener { controlSpecificLED("ledDormitorio2", "on") }
        findViewById<Button>(R.id.ledDormitorio2OffButton).setOnClickListener { controlSpecificLED("ledDormitorio2", "off") }

        // Botones de control para Dormitorio 3
        findViewById<Button>(R.id.ledDormitorio3OnButton).setOnClickListener { controlSpecificLED("ledDormitorio3", "on") }
        findViewById<Button>(R.id.ledDormitorio3OffButton).setOnClickListener { controlSpecificLED("ledDormitorio3", "off") }

        // Botones de control para Cocina
        findViewById<Button>(R.id.ledCocinaOnButton).setOnClickListener { controlSpecificLED("ledCocina", "on") }
        findViewById<Button>(R.id.ledCocinaOffButton).setOnClickListener { controlSpecificLED("ledCocina", "off") }

        // Botones de control para Baño 1
        findViewById<Button>(R.id.baño1OnButton).setOnClickListener { controlSpecificLED("baño1", "on") }
        findViewById<Button>(R.id.baño1OffButton).setOnClickListener { controlSpecificLED("baño1", "off") }

        // Botones de control para Baño 2
        findViewById<Button>(R.id.baño2OnButton).setOnClickListener { controlSpecificLED("baño2", "on") }
        findViewById<Button>(R.id.baño2OffButton).setOnClickListener { controlSpecificLED("baño2", "off") }

        activityLogButton.setOnClickListener {
            if (activityLog.isEmpty()) {
                Toast.makeText(this, "No hay actividad registrada aún.", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(this, HistoryActivity::class.java)
                intent.putStringArrayListExtra("history", ArrayList(activityLog))
                startActivity(intent)
            }
        }
    }

    private fun monitorSystem() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                updateSensors()
                handler.postDelayed(this, 1000)
            }
        }, 1000)
    }

    private fun updateSensors() {
        sendHttpRequest("$esp32BaseUrl/sensor-status") { response ->
            try {
                val json = JSONObject(response)
                val pir1 = json.getInt("pir1")
                val pir2 = json.getInt("pir2")
                val pir3 = json.getInt("pir3")

                // Obtener la fecha y hora actual
                val currentDateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                runOnUiThread {
                    // Actualizar el estado de los sensores en la interfaz con nombres descriptivos
                    sensorsStatus.text = "Sala de estar: ${if (pir1 == 1) "Activo" else "Inactivo"}, " +
                            "Dormitorio 1: ${if (pir2 == 1) "Activo" else "Inactivo"}, " +
                            "Dormitorio 2: ${if (pir3 == 1) "Activo" else "Inactivo"}"

                    // Registrar en Firebase y en el historial local si un sensor está activo
                    if (pir1 == 1) {
                        val log = "$currentDateTime - Sensor Sala de estar activado"
                        activityLog.add(log)
                        saveToFirebase("Sala de estar", log)
                        vibratePhone()
                    }
                    if (pir2 == 1) {
                        val log = "$currentDateTime - Sensor Dormitorio 1 activado"
                        activityLog.add(log)
                        saveToFirebase("Dormitorio 1", log)
                        vibratePhone()
                    }
                    if (pir3 == 1) {
                        val log = "$currentDateTime - Sensor Dormitorio 2 activado"
                        activityLog.add(log)
                        saveToFirebase("Dormitorio 2", log)
                        vibratePhone()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Error al obtener el estado de los sensores", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveToFirebase(sensorName: String, log: String) {
        val sensorLog = mapOf(
            "sensor" to sensorName,
            "log" to log,
            "timestamp" to System.currentTimeMillis()
        )
        databaseReference.push().setValue(sensorLog)
            .addOnSuccessListener {
                Toast.makeText(this, "Datos enviados a Firebase", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al guardar en Firebase: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun logSystemEvent(event: String) {
        val logEntry = mapOf(
            "event" to event,
            "timestamp" to System.currentTimeMillis()
        )
        databaseReference.child("system_events").push().setValue(logEntry)
            .addOnSuccessListener {
                Toast.makeText(this, "Evento registrado en Firebase", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al guardar el evento: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun vibratePhone() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(android.os.VibrationEffect.createOneShot(500, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(500) // Vibración de 500ms para versiones anteriores a Android O
        }
    }

    private fun disableBuzzer2() {
        sendHttpRequest("$esp32BaseUrl/disable-buzzer2") { response ->
            runOnUiThread {
                Toast.makeText(this@MainActivity, response, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupWebSocket() {
        val wsUrl = "ws://192.168.1.48/ws"
        val request = Request.Builder().url(wsUrl).build()

        val webSocket = wsClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Conexión WebSocket establecida", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runOnUiThread {
                    activityLog.add("${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())} - $text")
                    sensorsStatus.text = text // Actualizar el estado en tiempo real
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Error en WebSocket: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            }
        })
        wsClient.dispatcher.executorService.shutdown()
    }

    private fun monitorTemperature() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                sendHttpRequest("$esp32BaseUrl/temperature") { response ->
                    try {
                        val json = JSONObject(response)
                        val temperature = json.getDouble("temperature")
                        val humidity = json.getDouble("humidity")

                        runOnUiThread {
                            temperatureText.text = "Temperatura: $temperature °C"
                            humidityText.text = "Humedad: $humidity %"
                        }

                        // Registrar los datos en Firebase solo si la temperatura excede 35°C
                        if (temperature > 35) {
                            logTemperatureAndHumidity(temperature, humidity)
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Error al obtener temperatura/humedad", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                handler.postDelayed(this, 5000) // Actualizar cada 5 segundos
            }
        }, 5000)
    }

    private fun logTemperatureAndHumidity(temperature: Double, humidity: Double) {
        val dataEntry = mapOf(
            "temperature" to temperature,
            "humidity" to humidity,
            "timestamp" to System.currentTimeMillis()
        )
        databaseReference.child("temperature_humidity_logs").push().setValue(dataEntry)
            .addOnSuccessListener {
                Toast.makeText(this, "Temperatura registrada en Firebase", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al guardar datos: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun controlSystem(command: String) {
        sendHttpRequest("$esp32BaseUrl/control?state=$command") { response ->
            runOnUiThread {
                activityLog.add(response)
                updateSystemStatus(command)
                val event = if (command == "on") "Sistema encendido" else "Sistema apagado"
                logSystemEvent(event) // Registrar en Firebase
            }
        }
    }

    private fun updateSystemStatus(command: String) {
        systemStatus.text = if (command == "on") "Encendido" else "Apagado"
    }

    private fun controlAlarm(command: String) {
        sendHttpRequest("$esp32BaseUrl/alarm-control?state=$command") { response ->
            runOnUiThread {
                activityLog.add(response)
                alarmStatus.text = if (command == "on") "Activada" else "Desactivada"
                val event = if (command == "on") "Alarma activada" else "Alarma desactivada"
                logSystemEvent(event) // Registrar en Firebase
            }
        }
    }

    private fun resetAlarm() {
        sendHttpRequest("$esp32BaseUrl/reset-alarm") { response ->
            runOnUiThread {
                activityLog.add(response)
                Toast.makeText(this, response, Toast.LENGTH_SHORT).show()
                logSystemEvent("Alarma restablecida") // Registrar en Firebase
            }
        }
    }

    /*private fun loadCameraStream() {
        val videoUrl = "https://presumably-legible-terrier.ngrok-free.app/stream" // URL del stream
        runOnUiThread {
            cameraView.setVideoPath(videoUrl) // Configurar la URL del video
            cameraView.setOnPreparedListener { mediaPlayer ->
                mediaPlayer.isLooping = true // Configurar para que el video se repita
                cameraView.start() // Iniciar reproducción
            }
            cameraView.setOnErrorListener { _, _, _ ->
                Toast.makeText(this, "Error al cargar el video", Toast.LENGTH_SHORT).show()
                true
            }
        }
    }*/

    private fun sendHttpRequest(url: String, callback: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    callback(response.body?.string() ?: "")
                } else {
                    callback("Error: ${response.code}")
                }
            } catch (e: Exception) {
                callback("Error: ${e.message}")
            }
        }
    }

    private fun controlAllLEDs(command: String) {
        sendHttpRequest("$esp32BaseUrl/control-all-leds?state=$command") { response ->
            runOnUiThread {
                Toast.makeText(this, response, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun controlSpecificLED(led: String, state: String) {
        sendHttpRequest("$esp32BaseUrl/led-control?led=$led&state=$state") { response ->
            runOnUiThread {
                Toast.makeText(this, response, Toast.LENGTH_SHORT).show()
            }
        }
    }

}
