package it.andrea.wanbonight

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*

@SuppressLint("SetTextI18n") // Personal prototype with an intentionally Italian-only UI.
class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var compare: Button
    private var threshold = -30
    private var ratio = 4
    private var makeup = 0
    private val refresh = object : Runnable {
        override fun run() {
            status.text = NightState.message
            status.setTextColor(if (NightState.active) Color.rgb(99, 229, 192) else Color.LTGRAY)
            compare.text = if (NightState.mode == "compress") "Confronta: ascolta l’originale" else "Confronta: riattiva il compressore"
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("controls", MODE_PRIVATE)
        threshold = prefs.getFloat("threshold", -30f).toInt().coerceIn(-60, 0)
        ratio = prefs.getFloat("ratio", 4f).toInt().coerceIn(1, 10)
        makeup = prefs.getFloat("makeup", 0f).toInt().coerceIn(0, 12)
        val density = resources.displayMetrics.density
        fun dp(n: Int) = (n * density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(44), dp(24), dp(44), dp(24))
            setBackgroundColor(Color.rgb(20, 38, 56))
        }
        fun label(text: String, size: Float = 17f): TextView = TextView(this).apply {
            this.text = text; textSize = size; setTextColor(Color.WHITE)
            setPadding(0, dp(5), 0, dp(5))
            layout.addView(this)
        }
        label("Wanbo Night", 28f)
        label("Riduci gli sbalzi tra dialoghi ed effetti.")
        status = label(NightState.message, 17f)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        layout.addView(row)
        fun rowButton(text: String, action: () -> Unit): Button = Button(this).apply {
            this.text = text; isAllCaps = false
            row.addView(this, LinearLayout.LayoutParams(0, dp(54), 1f))
            setOnClickListener { action() }
        }
        rowButton("Applica e attiva") {
            prefs.edit().putFloat("threshold", threshold.toFloat()).putFloat("ratio", ratio.toFloat()).putFloat("makeup", makeup.toFloat()).apply()
            command("compress", true)
        }.requestFocus()
        rowButton("Disattiva") { stopService(Intent(this, NightService::class.java)) }
        rowButton("Apri Prime Video") {
            val launch = packageManager.getLeanbackLaunchIntentForPackage("com.amazon.amazonvideo.livingroom")
                ?: packageManager.getLaunchIntentForPackage("com.amazon.amazonvideo.livingroom")
            if (launch != null) startActivity(launch)
            else Toast.makeText(this, "Apri Prime Video dalla schermata Home", Toast.LENGTH_LONG).show()
        }
        compare = Button(this).apply {
            isAllCaps = false
            setOnClickListener { command(if (NightState.mode == "compress") "bypass" else "compress", false) }
        }
        layout.addView(compare)
        fun slider(title: String, min: Int, max: Int, initial: Int, unit: String, changed: (Int) -> Unit) {
            val value = label("$title: $initial$unit")
            val seek = SeekBar(this).apply {
                this.max = max - min; progress = initial - min
                keyProgressIncrement = 1
                contentDescription = title
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                        val n = progress + min; value.text = "$title: $n$unit"; changed(n)
                    }
                    override fun onStartTrackingTouch(bar: SeekBar?) {}
                    override fun onStopTrackingTouch(bar: SeekBar?) {}
                })
            }
            layout.addView(seek, LinearLayout.LayoutParams(-1, dp(40)))
        }
        slider("Soglia", -60, 0, threshold, " dB", { threshold = it })
        slider("Rapporto", 1, 10, ratio, ":1", { ratio = it })
        slider("Recupero del volume", 0, 12, makeup, " dB", { makeup = it })
        label("Una soglia più bassa e un rapporto maggiore comprimono di più. Parti da −30 dB e 4:1; aumenta il recupero solo se necessario. Premi Applica dopo le modifiche.", 15f)
        label("Attacco 10 ms · rilascio 250 ms · ginocchio 6 dB · limiter stereo a −3 dB. La soglia utile può variare con il volume del proiettore.", 14f)
        label("Prototipo 0.2 · Non parte al riavvio. Attiva il compressore e torna al film. La prova riuscita riguarda Prime Video in PCM stereo su questo dispositivo.", 14f)
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(layout) })
    }

    private fun command(action: String, useSliders: Boolean) {
        val intent = Intent(this, NightService::class.java).setAction(action)
        if (useSliders) intent.putExtra("threshold", threshold.toFloat()).putExtra("ratio", ratio.toFloat()).putExtra("makeup", makeup.toFloat())
        try { startForegroundService(intent) }
        catch (e: Exception) { NightState.message = "Avvio non riuscito: ${e.message}" }
    }

    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }
}
