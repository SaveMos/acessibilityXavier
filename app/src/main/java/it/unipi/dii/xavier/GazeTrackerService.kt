package it.unipi.dii.xavier

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import camp.visual.eyedid.gazetracker.GazeTracker
import camp.visual.eyedid.gazetracker.callback.InitializationCallback
import camp.visual.eyedid.gazetracker.callback.TrackingCallback
import camp.visual.eyedid.gazetracker.constant.GazeTrackerOptions
import camp.visual.eyedid.gazetracker.constant.InitializationErrorType
import camp.visual.eyedid.gazetracker.metrics.BlinkInfo
import camp.visual.eyedid.gazetracker.metrics.FaceInfo
import camp.visual.eyedid.gazetracker.metrics.GazeInfo
import camp.visual.eyedid.gazetracker.metrics.UserStatusInfo
import kotlin.io.path.Path
import kotlin.math.abs

class GazeTrackerService : AccessibilityService() {

    //gestisce visualizzazione di view sopra l'interfaccia utente
    private lateinit var windowManager: WindowManager
    //rappresenta la freccia
    private lateinit var pointerView: ImageView
    //parametri per posizionare il puntatore nell'interfaccia utente
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var gazeTracker: GazeTracker? = null

    // per dwell-click
    private var lastX = 0f
    private var lastY = 0f
    private var dwellStart: Long = 0
    private val DWELL_THRESHOLD = 1500L // ms

    //-------------------------------------------------------------------------
    companion object {
        const val ACTION_START_GAZE = "it.unipi.dii.xavier.START_GAZE"
        // (eventualmente) const val ACTION_STOP_GAZE = "…STOP_GAZE"
    }

    private val startReceiver = object: BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == ACTION_START_GAZE) {
                startGazeTracking()
            }
        }
    }
    //-------------------------------------------------------------------------
    @SuppressLint("ForegroundServiceType")
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()

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
            setColorFilter(Color.parseColor("#39FF14"), PorterDuff.Mode.SRC_ATOP)
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
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            //il puntatore non ruba il focus alle app sottostanti
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            //puntatore parte in alto a sinistra
            gravity = Gravity.TOP or Gravity.START
        }

        // CONTROLLO PERMESSO PRIMA DI ADD VIEW
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.e("GazeTrackerService", "Permesso SYSTEM_ALERT_WINDOW mancante. Stoppo il servizio.")
            stopSelf()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
        }


        Log.d("DENTRO ON CREATE GTS 2", "dentro on create gts 2")
        //mostra a schermo il puntatore con i parametri appena creati
        //-------------------------------------------------------------------------
        //windowManager.addView(pointerView, layoutParams)
        //-------------------------------------------------------------------------
        //recupero del gazeTracker già calibrato ed inizializzato
        gazeTracker = GazeTrackerSingleton.tracker

        Log.d("DENTRO ON CREATE GTS 3", "dentro on create gts 3")
        //-------------------------------------------------------------------------
        /*
        //utilizza il trackingCallback per il tracciamento
        if (gazeTracker != null) {
            gazeTracker?.setTrackingCallback(trackingCallback)
            gazeTracker?.startTracking()
        } else {
            Log.e("GazeService", "GazeTracker è null, inizializzazione fallita!")
            stopSelf() // chiude il servizio se qualcosa è andato storto
        }
         */
        //-------------------------------------------------------------------------

    }
    //-------------------------------------------------------------------------
    private fun startGazeTracking() {
        // 1) aggiungi l’overlay (se già aggiunto, integralo con try/catch)
        try { windowManager.addView(pointerView, layoutParams) } catch (_: Exception) {}
        // 2) parti col tracking
        gazeTracker?.apply {
            setTrackingCallback(trackingCallback)
            startTracking()
        }
    }
    //-------------------------------------------------------------------------

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

            handleDwellClick(gazeInfo.x, gazeInfo.y)
        }
        override fun onDrop(timestamp: Long) { /*…*/ }
    }

    private fun handleDwellClick(x: Float, y: Float) {
        //Log.d("DENTRO HANDLE CLICK GTS", "dentro handle click gts")
        if (abs(x - lastX) < 20 && abs(y - lastY) < 20) {
            // non si è mosso quasi
            if (dwellStart == 0L) dwellStart = System.currentTimeMillis()
            else if (System.currentTimeMillis() - dwellStart > DWELL_THRESHOLD) {
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

        Log.d("SCHERMO ATTIVO", "AccessibilityService connesso — flags: ${serviceInfo.flags}")
    }

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
    //-------------------------------------------------------------------------
    /*
    override fun onDestroy() {
        gazeTracker?.stopTracking()
        try {
            windowManager.removeView(pointerView)
        } catch (e: Exception) {
            Log.w("GazeTrackerService", "Pointer view non rimossa correttamente: ${e.message}")
        }
        super.onDestroy()
    }

    */
    //-------------------------------------------------------------------------
    override fun onDestroy() {
        unregisterReceiver(startReceiver)
        gazeTracker?.stopTracking()
        try { windowManager.removeView(pointerView) } catch (_: Exception) {}
        super.onDestroy()
    }

}
