package it.andrea.wanbonight

import android.app.*
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.os.*
import android.util.Log
import java.io.File

class NightService : Service() {
    private var effect: DynamicsProcessing? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { log("Timeout: rilascio effetto"); stopSelf() }
    private var session = 0
    private var failure: String? = null

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("audio", "Elaborazione audio", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 0, Intent(this, NightService::class.java).setAction("stop"), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        startForeground(1, Notification.Builder(this, "audio")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle("Wanbo Night")
            .setContentText("Apri l’app per lo stato. Stop rilascia l’effetto.")
            .setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build()).build())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.removeCallbacks(timeout)
        val command = intent?.action ?: "stop"
        try {
            if (command == "stop") { stopSelf(); return START_NOT_STICKY }
            failure = null
            require(command in listOf("probe", "attenuate", "compress", "bypass")) { "Comando non valido" }
            val prefs = getSharedPreferences("controls", MODE_PRIVATE)
            val threshold = intent?.getFloatExtra("threshold", prefs.getFloat("threshold", -30f)) ?: -30f
            val ratio = intent?.getFloatExtra("ratio", prefs.getFloat("ratio", 4f)) ?: 4f
            val makeup = intent?.getFloatExtra("makeup", prefs.getFloat("makeup", 0f)) ?: 0f
            require(threshold.isFinite() && threshold in -60f..0f) { "Soglia fuori intervallo" }
            require(ratio.isFinite() && ratio in 1f..10f) { "Rapporto fuori intervallo" }
            require(makeup.isFinite() && makeup in 0f..12f) { "Guadagno fuori intervallo" }
            val requestedSession = intent?.getIntExtra("session", 0) ?: 0
            require(requestedSession >= 0) { "Sessione non valida" }
            if (effect == null || session != requestedSession) {
                release()
                session = requestedSession
                AudioEffect.queryEffects()?.forEach { log("AVAILABLE name=${it.name} type=${it.type} uuid=${it.uuid}") }
                val cfg = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2,
                    false, 0, true, 1, false, 0, true
                ).setInputGainAllChannelsTo(0f).build()
                effect = DynamicsProcessing(0, session, cfg)
                effect!!.setControlStatusListener { _, control ->
                    log("CONTROL=$control")
                    if (!control) { failure = "Controllo dell’effetto perso"; stopSelf() }
                }
                check(effect!!.hasControl()) { "Controllo effetto non disponibile" }
                log("CREATED session=$session id=${effect!!.id} channels=${effect!!.channelCount} enabled=${effect!!.enabled}")
            }
            val dp = effect!!
            check(dp.hasControl()) { "Controllo effetto perso" }
            check(dp.setEnabled(false) == AudioEffect.SUCCESS) { "Disattivazione fallita" }
            dp.setInputGainAllChannelsTo(if (command == "attenuate") -12f else 0f)
            val compression = command == "compress"
            // Full-band MBC per channel; user makeup defaults to 0 dB; no expander/noise gate.
            dp.setMbcBandAllChannelsTo(0, DynamicsProcessing.MbcBand(
                compression, 20000f, 10f, 250f, ratio, threshold, 6f, -90f, 1f, 0f, makeup
            ))
            // Linked stereo limiter, no makeup gain.
            dp.setLimiterAllChannelsTo(DynamicsProcessing.Limiter(
                true, compression, 0, 1f, 80f, 20f, -3f, 0f
            ))
            val enabled = command == "attenuate" || compression
            // Read back the full configuration only while the effect is bypassed.
            // Repeated effect queries / dumps during live processing can block audio on slow HALs.
            log("PARAMETERS threshold=$threshold ratio=$ratio makeup=$makeup actualMbc=${dp.getMbcBandByChannelIndex(0, 0)}")
            log("CONFIG ${dp.config}")
            check(dp.setEnabled(enabled) == AudioEffect.SUCCESS) { "Attivazione fallita" }
            NightState.active = enabled
            NightState.mode = command
            NightState.message = when (command) {
                "compress" -> "Attivo · soglia ${threshold.toInt()} dB · rapporto ${ratio.toInt()}:1 · recupero +${makeup.toInt()} dB"
                "attenuate" -> "Test: −12 dB · ripristino automatico dopo 15 secondi"
                "bypass" -> "Confronto · audio originale"
                else -> "Diagnosi · effetto presente ma disattivato"
            }
            log("STATE command=$command session=$session enabled=$enabled gain=${if (command == "attenuate") -12 else 0}")
            // Normal compression stays on until Stop; ADB trials can set a finite duration.
            val duration = if (command == "compress") intent?.getIntExtra("seconds", 0)?.coerceIn(0, 600) ?: 0
                else if (command == "attenuate") 15 else 120
            if (duration > 0) handler.postDelayed(timeout, duration * 1000L)
        } catch (e: Exception) {
            failure = "${e.javaClass.simpleName}: ${e.message}"
            log("ERROR ${e.javaClass.simpleName}: ${e.message}")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun release() {
        effect?.let { dp ->
            try { if (dp.hasControl()) dp.setEnabled(false) } catch (_: Exception) { }
            try { dp.release() } catch (_: Exception) { }
        }
        effect = null
    }

    private fun log(message: String) {
        Log.i("WanboNight", message)
        val line = "${java.time.Instant.now()} $message\n"
        val file = File(filesDir, "diagnostics.txt")
        if (file.length() > 250000) file.writeText("")
        file.appendText(line)
        if (message.startsWith("STATE") || message.startsWith("ERROR") || message.startsWith("STOPPED")) {
            getSharedPreferences("state", MODE_PRIVATE).edit().putString("last", message).apply()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(timeout)
        release()
        NightState.active = false
        NightState.mode = "stop"
        NightState.message = failure?.let { "Non attivo · $it" } ?: "Disattivato · audio originale"
        log("STOPPED: effetto rilasciato")
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
