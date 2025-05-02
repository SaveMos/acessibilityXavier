package it.unipi.dii.xavier

import GazeTrackerSingleton
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Resources
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import camp.visual.eyedid.gazetracker.GazeTracker
import camp.visual.eyedid.gazetracker.callback.TrackingCallback
import camp.visual.eyedid.gazetracker.metrics.BlinkInfo
import camp.visual.eyedid.gazetracker.metrics.FaceInfo
import camp.visual.eyedid.gazetracker.metrics.GazeInfo
import camp.visual.eyedid.gazetracker.metrics.UserStatusInfo
import kotlin.math.abs


class GazeTrackerService : AccessibilityService() {

    //gestisce visualizzazione di view sopra l'interfaccia utente
    private lateinit var windowManager: WindowManager
    //rappresenta la freccia
    private lateinit var pointerView: ImageView
    //parametri per posizionare il puntatore nell'interfaccia utente
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var gazeTracker: GazeTracker? = null

    private lateinit var startReceiver: BroadcastReceiver

    // per dwell-click
    private var lastX = 0f
    private var lastY = 0f
    private var dwellStart: Long = 0
    private val dwellTreshold = 1500L // ms

    // per swipe
    private var currentZone: String? = null
    private var zoneStart: Long = 0L
    private val zoneTreshold = 1000L  // ms di dwell per zona
    private var screenW = 0
    private var screenH = 0

    //menu personalizzato
    private lateinit var navMenu: View
    private lateinit var navMenuParams: WindowManager.LayoutParams

    //-------------------------------------------------------------------------
    companion object {
        const val ACTION_START_GAZE = "it.unipi.dii.xavier.START_GAZE"
        // (eventualmente) const val ACTION_STOP_GAZE = "…STOP_GAZE"
    }
    //-------------------------------------------------------------------------
    @SuppressLint("ForegroundServiceType", "UnspecifiedRegisterReceiverFlag")
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()

        val metrics = Resources.getSystem().displayMetrics
        screenW = metrics.widthPixels
        screenH = metrics.heightPixels

        //serve per disegnare il puntatore nello schermo, sopra altre app
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        //dimensioni per il nuovo puntatore
        val density = resources.displayMetrics.density
        val sizePx = (32 * density + 0.5f).toInt()

        //crea una nuova imageView del puntatore
        pointerView = ImageView(this).apply {
            setImageResource(R.drawable.mouse_pointer)

            scaleType = ImageView.ScaleType.FIT_CENTER
            // Applica un color filter verde neon (#39FF14) in modalità SRC_ATOP
            setColorFilter("#39FF14".toColorInt(), PorterDuff.Mode.SRC_ATOP)
            visibility = View.INVISIBLE
        }

        Log.d("DENTRO ON CREATE GTS 1", "dentro on create gts 1")
        //crea i parametri che definiscono dove e come deve essere mostrato il puntatore
        layoutParams = WindowManager.LayoutParams().apply {
            //grandezza della view
            width = sizePx
            height = sizePx
            //supporta trasparenze
            format = PixelFormat.TRANSLUCENT
            //serve per disegnare sopra altre app
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            //il puntatore non ruba il focus alle app sottostanti
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            //puntatore parte in alto a sinistra
            gravity = Gravity.TOP or Gravity.START

        }

        // CONTROLLO PERMESSO PRIMA DI ADD VIEW
        if (!Settings.canDrawOverlays(this)) {
            Log.e("GazeTrackerService", "Permesso SYSTEM_ALERT_WINDOW mancante. Stoppo il servizio.")
            stopSelf()
            return
        }

        val channelId = "GazeTrackerChannel"
        val channel = NotificationChannel(channelId, "Gaze Tracker Service", NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Gaze Tracker Attivo")
            .setContentText("Il servizio sta tracciando il tuo sguardo")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // usa una tua icona
            .build()

        startForeground(1, notification)


        Log.d("DENTRO ON CREATE GTS 2", "dentro on create gts 2")
        //mostra a schermo il puntatore con i parametri appena creati

        Log.d("DENTRO ON CREATE GTS 3", "dentro on create gts 3")
        //-------------------------------------------------------------------------
        // registra il receiver che aspetta il “via libera”
        startReceiver = object: BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                Log.d("DENTRO ON RECEIVE", "dentro on receive")
                if (intent.action == ACTION_START_GAZE) {
                    Log.d("DENTRO IF ON RECEIVE", "dentro if on receive")
                    // 1) aggiungi l’overlay se non è già stato fatto
                    try { windowManager.addView(pointerView, layoutParams) } catch (_: Exception) {}
                    pointerView.visibility = View.VISIBLE

                    // 2) avvia gaze-tracker
                    gazeTracker = GazeTrackerSingleton.tracker
                    gazeTracker?.setTrackingCallback(trackingCallback)
                    gazeTracker?.startTracking()
                }
            }
        }
        registerReceiver(startReceiver, IntentFilter(ACTION_START_GAZE))
        val filter = IntentFilter("it.unipi.dii.xavier.BACK")
        registerReceiver(backReceiver, filter)

        val navigationBarHeight = calcNavigationBarHeight()

        // infla il layout
        navMenu = LayoutInflater.from(this).inflate(R.layout.nav_menu, null)

        // posizionalo appena sopra la navigation bar, centrato orizzontalmente
        navMenuParams = WindowManager.LayoutParams().apply {
            //width = WindowManager.LayoutParams.WRAP_CONTENT
            //height = WindowManager.LayoutParams.WRAP_CONTENT
            width = 700
            height = 250
            format = PixelFormat.TRANSLUCENT
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            //flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    //WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            //gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            gravity = Gravity.CENTER
            y = navigationBarHeight + 50  // spostalo 16px sopra la nav bar
        }

        //-------------------------------------------------------------------------

    }
    //-----------------------------------------------------------------------------
    private val backReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "it.unipi.dii.xavier.BACK") {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        }
    }

    private fun calcNavigationBarHeight(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Ottieni le metriche della finestra corrente
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val windowMetrics = wm.currentWindowMetrics
            // Prendi l’inset delle navigation bars (bordo inferiore)
            val insets = windowMetrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars())
            insets.bottom
        } else {
            // Fallback pre-API 30: usa un valore dp standard (es. 48 dp convertiti in pixel)
            val dp = 48
            (dp * resources.displayMetrics.density + 0.5f).toInt()
        }
    }

    private fun showNavMenu() {
        Handler(Looper.getMainLooper()).post {
            navMenu.visibility = View.VISIBLE
        }
    }
    private fun hideNavMenu() {
        Handler(Looper.getMainLooper()).post {
            navMenu.visibility = View.GONE
        }
    }

    //-----------------------------------------------------------------------------

    private val trackingCallback = object : TrackingCallback {
        override fun onMetrics(
            timestamp: Long,
            gazeInfo: GazeInfo,
            faceInfo: FaceInfo,
            blinkInfo: BlinkInfo,
            userStatusInfo: UserStatusInfo
        ) {
            //Log.d("DENTRO ON METRICS GTS", "dentro on metrics gts")
            // aggiorna posizione overlay
            Handler(Looper.getMainLooper()).post {
                layoutParams.x = gazeInfo.x.toInt()
                layoutParams.y = gazeInfo.y.toInt()
                windowManager.updateViewLayout(pointerView, layoutParams)
            }

            //------------------------------------------------------------------
            //handleDwellClick(gazeInfo.x, gazeInfo.y)
            // 2) zone-based actions
            handleZone(gazeInfo.x, gazeInfo.y)

            // 3) solo se non siamo in una zona attiva, permetti il dwell-click
            if (currentZone == null) {
                handleDwellClick(gazeInfo.x, gazeInfo.y)
            }

            if(blinkInfo.isBlinkRight && !blinkInfo.isBlinkLeft && !navMenu.isVisible){
                Log.d("BLINK DESTRO RILEVATO", "blink destro rilevato: $blinkInfo")
                showNavMenu()

            }else if(blinkInfo.isBlinkLeft && !blinkInfo.isBlinkRight && navMenu.isVisible ){
                Log.d("BLINK SINISTRO RILEVATO", "blink rilevato")
                hideNavMenu()
            }

            //------------------------------------------------------------------
        }
        override fun onDrop(timestamp: Long) { /*…*/ }
    }

    private fun handleDwellClick(x: Float, y: Float) {
        //Log.d("DENTRO HANDLE CLICK GTS", "dentro handle click gts")
        if (abs(x - lastX) < 20 && abs(y - lastY) < 20) {
            // non si è mosso quasi
            if (dwellStart == 0L) dwellStart = System.currentTimeMillis()
            else if (System.currentTimeMillis() - dwellStart > dwellTreshold) {
                performClick(x.toInt(), y.toInt())
                dwellStart = 0L  // reset
            }
        } else {
            // si è mosso
            dwellStart = 0L
        }
        lastX = x
        lastY = y
    }

    private fun handleZone(x: Float, y: Float) {
        // determina in quale zona sei
        val newZone = when {
            x < screenW * 0.2f   -> "LEFT"
            x > screenW * 0.8f   -> "RIGHT"
            y <= screenH * 0.05f   -> "UP"
            y > screenH * 0.05f && y <= screenH * 0.15f  -> "SWIPE_UP"
            y > screenH * 0.85f && y < screenH * 0.95f  -> "DOWN"
            else                 -> null
        }

        if (newZone == null) {
            // sei nella zona centrale: reset
            currentZone = null
            zoneStart = 0L
            return
        }

        if (newZone == currentZone) {
            // stai ancora guardando nella stessa zona
            if (zoneStart == 0L) zoneStart = System.currentTimeMillis()
            else if (System.currentTimeMillis() - zoneStart > zoneTreshold) {
                // superata la soglia, esegui l’azione
                when (newZone) {
                    "LEFT"  -> performSwipeLeft()
                    "RIGHT" -> performSwipeRight()
                    "UP"    -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                    "SWIPE_UP"  -> performSwipeUp()
                    "DOWN"  -> performSwipeDown()
                }
                // reset per non ripetere in loop
                zoneStart = 0L
            }
        } else {
            // entri in una nuova zona
            currentZone = newZone
            zoneStart = 0L
        }
    }

    private fun performSwipeRight() {
        val path = android.graphics.Path().apply {
            moveTo(screenW * 0.9f, screenH / 2f)
            lineTo(screenW * 0.1f, screenH / 2f)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 500L)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    private fun performSwipeLeft() {
        val path = android.graphics.Path().apply {
            moveTo(screenW * 0.1f, screenH / 2f)
            lineTo(screenW * 0.9f, screenH / 2f)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 500L)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    private fun performSwipeUp() {
        val path = android.graphics.Path().apply {
            moveTo(screenW / 2f, screenH * 0.1f)
            lineTo(screenW / 2f, screenH * 0.9f)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 500L)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    private fun performSwipeDown() {
        val path = android.graphics.Path().apply {
            moveTo(screenW / 2f, screenH * 0.9f)
            lineTo(screenW / 2f, screenH * 0.1f)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 500L)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    @SuppressLint("ServiceCast")
    private fun performClick(x: Int, y: Int) {
        Log.d("DENTRO PERFORM CLICK GTS", "dentro perform click gts")
        // crea un gesture per simulare tap, crea il punto in cui cliccare
        val path = android.graphics.Path().apply { moveTo(x.toFloat(), y.toFloat()) }

        // 2) Prendi la durata minima di un tap dal sistema (≈100 ms)
        val tapDuration = ViewConfiguration.getTapTimeout().toLong()

        // 3) Crea lo StrokeDescription con quell’intervallo
        val stroke = GestureDescription.StrokeDescription(path, 0, tapDuration)

        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        Handler(Looper.getMainLooper()).post {

            // Solo per debug: controlla se il tuo servizio è fra quelli abilitati
            val enabledServices = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            Log.d("SERVIZI ABILITATI", "Enabled services: $enabledServices")
            // Se non vedi il tuo pacchetto qui dentro, non lo hai abilitato

            val sent = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    Log.d("GazeTrackerService", "Tap simulato con successo")
                }

                override fun onCancelled(gestureDescription: GestureDescription) {
                    Log.w("GazeTrackerService", "Tap annullato")
                }
            }, null)

            Log.d("NON APRE L'APP", "dispatchGesture returns: $sent")
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager.addView(navMenu, navMenuParams)
        navMenu.visibility = View.GONE

        // trova gli ImageView e metti i click listener
        navMenu.findViewById<ImageView>(R.id.btn_back).setOnClickListener {

            Handler(Looper.getMainLooper()).postDelayed({
               performGlobalAction(GLOBAL_ACTION_BACK)
            }, 100)

            hideNavMenu()
        }
        navMenu.findViewById<ImageView>(R.id.btn_home).setOnClickListener {
            performGlobalAction(GLOBAL_ACTION_HOME)
            hideNavMenu()
        }
        navMenu.findViewById<ImageView>(R.id.btn_recents).setOnClickListener {
            performGlobalAction(GLOBAL_ACTION_RECENTS)
            hideNavMenu()
        }

        Log.d("SCHERMO ATTIVO", "AccessibilityService connesso — flags: ${serviceInfo.flags}")
    }

    @SuppressLint("SwitchIntDef")
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                Log.d("FINESTRA CAMBIATA", "Finestra cambiata: ${event.className}")
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                Log.d("VIEW CLICCATA", "View cliccata: ${event.packageName}")
            }

        }
    }

    override fun onInterrupt() {
        Log.w("ACCESSIBILITà INTERROTTA", "Servizio di Accessibilità interrotto dal sistema")
        // qui potresti fermare il gazeTracker, rimuovere overlay, ecc.
        gazeTracker?.stopTracking()
        try { windowManager.removeView(pointerView) } catch (_: Exception) {}
    }

    override fun onDestroy() {
        unregisterReceiver(startReceiver)
        unregisterReceiver(backReceiver)
        gazeTracker?.stopTracking()
        try { windowManager.removeView(pointerView) } catch (_: Exception) {}
        super.onDestroy()
    }


}
