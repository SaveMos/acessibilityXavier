package it.unipi.dii.xavier

import GazeTrackerSingleton
import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import camp.visual.eyedid.gazetracker.GazeTracker
import camp.visual.eyedid.gazetracker.callback.CalibrationCallback
import camp.visual.eyedid.gazetracker.callback.InitializationCallback
import camp.visual.eyedid.gazetracker.callback.StatusCallback
import camp.visual.eyedid.gazetracker.callback.TrackingCallback
import camp.visual.eyedid.gazetracker.constant.AccuracyCriteria
import camp.visual.eyedid.gazetracker.constant.CalibrationModeType
import camp.visual.eyedid.gazetracker.constant.GazeTrackerOptions
import camp.visual.eyedid.gazetracker.constant.InitializationErrorType
import camp.visual.eyedid.gazetracker.constant.StatusErrorType
import camp.visual.eyedid.gazetracker.device.CameraPosition
import camp.visual.eyedid.gazetracker.metrics.BlinkInfo
import camp.visual.eyedid.gazetracker.metrics.FaceInfo
import camp.visual.eyedid.gazetracker.metrics.GazeInfo
import camp.visual.eyedid.gazetracker.metrics.UserStatusInfo
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {

    private val cameraPermissionRequestCode = 1000

    //gestisce il risultato della richiesta di permesso di overlay
    private lateinit var overlayPermissionLauncher: ActivityResultLauncher<Intent>

    //classe principale che cattura la faccia dell'utente attraverso la fotocamera del dispositivo
    //la processa e fornisce i dati relativi allo sguardo
    private var gazeTracker: GazeTracker? = null

    //oggetto layout di root
    private lateinit var rootLayout: FrameLayout
    private var w = 0
    private var h = 0

    //oggetto layout che contiene la freccetta
    private lateinit var pointer: ImageView

    //punto per calibrazione
    private var currentCalibrationPoint: View? = null
    //booleano per calibrazione
    private var isCalibrated = false

    //ExecutorService è l'interfaccia per gestire il pool di thread
    //utile pe serializare le operazioni come ad esempio i frame della camera
    private val cameraExecutor: ExecutorService by lazy {
        //restituisce un ExecutorService che esegue i task in coda
        Executors.newSingleThreadExecutor()
    }

    // Handler to schedule tasks on the main thread
    private val handler = Handler(Looper.getMainLooper())

    @SuppressLint("MissingInflatedId", "DiscouragedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //nascondi action bar
        supportActionBar?.hide()
        setContentView(R.layout.activity_main)

        rootLayout = findViewById(R.id.root)
        rootLayout.post {
            w = rootLayout.width
            h = rootLayout.height
        }

        //--------------------------------------------------------------------
        //metodo per gestire i risultati dele attività
        overlayPermissionLauncher = registerForActivityResult(
            //avvia una nuova attività e gestisce il risultato (risposta al permesso di overlay)
            ActivityResultContracts.StartActivityForResult()
        ) {
            //gestisce il risultato dell'interazione dell'utente con la finestra di permesso
            if (Settings.canDrawOverlays(this)) {
                startGazeTrackerService()
            } else {
                Toast.makeText(this, "Devi consentire il permesso per continuare", Toast.LENGTH_LONG).show()
            }
        }

        //verifica se l'app ha il permesso di overlay
        if (!Settings.canDrawOverlays(this)) {
            //nuovo intento per chiedere il permesso
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri()
            )
            overlayPermissionLauncher.launch(intent)
        } else {
            startGazeTrackerService()
        }
        //------------------------------------------------------------------------

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.CAMERA),
                cameraPermissionRequestCode)
        }else{

            pointer = findViewById(R.id.pointer)
            // il nuovo thread inizia dopo delay secondi dalla fine del precedente
            //cameraExecutor.scheduleWithFixedDelay({startCamera()}, 0, 1, TimeUnit.SECONDS)
            cameraExecutor.execute{startCamera()}
        }
    }

    override fun onPause() {
        super.onPause()
        // finita calibrazione!
        val intent = Intent(GazeTrackerService.ACTION_START_GAZE)
        sendBroadcast(intent)
        Log.d("DOPO SEND BROADCAST", "dopo send broadcast")
    }
    //viene selezionato il servizio e parte il foreground service
    private fun startGazeTrackerService() {

    }
    //-----------------------------------------------------------------------------
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == cameraPermissionRequestCode &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            // L'utente ha concesso il permesso: avvia la fotocamera
            pointer = findViewById(R.id.pointer)
            // il nuovo thread inizia dopo delay secondi dalla fine del precedente
            //cameraExecutor.scheduleWithFixedDelay({startCamera()}, 0, 1, TimeUnit.SECONDS)
            cameraExecutor.execute{startCamera()}

        } else {
            Toast.makeText(this, "Permesso fotocamera negato", Toast.LENGTH_SHORT).show()
        }
    }

    private fun permissionGranted() {
        initGaze()
    }

    private fun initGaze() {
        val licenseKey = "dev_ptq5nrn1bep16ykwwlwlag5n6u3hz6q7vj0sbcxc"
        //un'istanza GazeTrackerOptions viene creata tramite il metodo build
        val options = GazeTrackerOptions.Builder().setUseGazeFilter(true).setUseBlink(true).build()

        Log.d("DENTRO INIT GAZE", "siamo entrati in initGaze")

        GazeTracker.initGazeTracker(applicationContext, licenseKey, initializationCallback, options)
    }

    private val initializationCallback =
        InitializationCallback { gazeTracker, error ->
            Log.d("DENTRO ON INITIALIZED", "siamo entrati in onInitialized")

            if (gazeTracker != null) {
                initSuccess(gazeTracker)

                //Log.d("PRIMA DI STATUS CALLBACK", "siamo prima di statusCallback")

                gazeTracker.setStatusCallback(statusCallback)

                //Log.d("DOPO STATUS CALLBACK", "siamo dopo statusCallback")
                val metrics = Resources.getSystem().displayMetrics
                Log.i("DIMENSIONI SCHERMO", "dimensioni schermo: " +  metrics.widthPixels.toFloat() + " : " + metrics.heightPixels.toFloat())
                Log.i("DIMENSIONI LAYOUT", "dimensioni layout: $w : $h")

                val cameraPosition =
                    CameraPosition("redmi note 8 pro",
                        w.toFloat(), h.toFloat(), -34.5f, 0f, false)

                //aggiungiamo la posizione della fotocamera al gazeTracker
                gazeTracker.addCameraPosition(cameraPosition)
                //apre fotocamera e inizia il tracking dello sguardo
                gazeTracker.startTracking()

            } else {
                initFail(error)
            }
        }

    private val statusCallback = object : StatusCallback {
        override fun onStarted() {
            // gazeTracker.startTracking() Success
            Log.d("DENTRO ON STARTED", "dentro on started")

            gazeTracker?.setCalibrationCallback(calibrationCallback)
            //start the calibration process
            gazeTracker?.startCalibration(CalibrationModeType.FIVE_POINT, AccuracyCriteria.HIGH)

        }

        override fun onStopped(error: StatusErrorType) {
            // gazeTracker.startTracking() Fail
            Log.d("DENTRO ON STOPPED", "error: $error")
        }
    }

    private val calibrationCallback = object : CalibrationCallback {

        override fun onCalibrationProgress(progress: Float) {
            runOnUiThread {
                // process calibration point UI
                //Log.d("CALIBRATION PROGRESS", "calibration progress: "+ progress)
            }
        }

        //utilizza le coordinate di ogni punto di calibrazione  per disegnare il punto nella UI
        override fun onCalibrationNextPoint(x: Float, y: Float) {
            runOnUiThread {
                // draw calibration point to (x, y)
                drawPointAt(x, y)
            }
            //dopo aver disegnato il punto, attende 1 secondo ed esegue startCollectSamples()
            handler.postDelayed({ gazeTracker?.startCollectSamples() }, 1000)
        }

        //chiamata solo uan volta alla fine
        override fun onCalibrationFinished(calibrationData: DoubleArray) {
            // saveCalibration(calibrationData)
            isCalibrated = true
            runOnUiThread {
                // remove calibration UI
                currentCalibrationPoint?.let { rootLayout.removeView(it) }
                currentCalibrationPoint = null
            }
        }

        override fun onCalibrationCanceled(calibrationData: DoubleArray) {
            runOnUiThread {
                // handle calibration cancel UI
            }
        }
    }

    private fun drawPointAt(x: Float, y: Float) {

    Log.d("COORDINATE PUNTI CALIBRAZIONE", "coordinate calibrazione: $x : $y")
    // Rimuove il punto precedente se esiste
    currentCalibrationPoint?.let { rootLayout.removeView(it) }

    val pointView = View(this).apply {
        layoutParams = FrameLayout.LayoutParams(20, 20).apply {
            leftMargin = x.toInt() - 10  // Centrare il punto
            topMargin = y.toInt() - 10
        }
        setBackgroundColor(Color.RED)
    }
    rootLayout.addView(pointView)
    currentCalibrationPoint = pointView  // Salva il riferimento al punto
}

    private fun initSuccess(gazeTracker: GazeTracker) {

        Log.d("DENTRO INIT SUCCESS", "siamo entrati in initSuccess")

        this.gazeTracker = gazeTracker

        GazeTrackerSingleton.tracker = gazeTracker // Salviamo nel Singleton

        //registra una funzione che riceve gli eventi di tracking, triggerata quando succedono delle cose
        this.gazeTracker!!.setTrackingCallback(trackingCallback)
        //Log.d("DOPO GAZE TRACKER", "siamo dopo gazeTracker")
    }

    private fun initFail(error: InitializationErrorType) {
        val err = when (error) {
            InitializationErrorType.ERROR_INIT -> "Initialization failed"
            InitializationErrorType.ERROR_CAMERA_PERMISSION -> "Required permission not granted"
            else -> "Eyedid SDK initialization failed"
        }
        Log.w("Eyedid SDK", "Error description: $err")
    }

    private fun startCamera() {
        permissionGranted()
        //GazeTrackerSingleton.tracker = gazeTracker // Salviamo nel Singleton
    }

    private val trackingCallback: TrackingCallback = object : TrackingCallback {
        override fun onMetrics(
            timestamp: Long,
            gazeInfo: GazeInfo,
            faceInfo: FaceInfo,
            blinkInfo: BlinkInfo,
            userStatusInfo: UserStatusInfo
        ) {
            //dimensioni schermo 1080x2134
            //val metrics = Resources.getSystem().displayMetrics
            //Log.i("DIMENSIONI SCHERMO", "dimensioni schermo: " +  metrics.widthPixels + "x" + metrics.heightPixels)

            //aggiorna la posizione dell'immagine del puntatore
            runOnUiThread {
                pointer.x = gazeInfo.x
                pointer.y = gazeInfo.y
            }
            if (isCalibrated) {
                if (blinkInfo.isBlinkRight && !blinkInfo.isBlinkLeft) {
                    Log.d("BLINK DESTRO RILEVATO", "blink destro rilevato: $blinkInfo")
                    //Restart the calibration process
                    isCalibrated = false
                    gazeTracker?.startCalibration(
                        CalibrationModeType.FIVE_POINT,
                        AccuracyCriteria.HIGH
                    )

                } else if (blinkInfo.isBlinkLeft && !blinkInfo.isBlinkRight) {
                    Log.d("BLINK SINISTRO RILEVATO", "blink rilevato")
                    isCalibrated = false
                    val i = Intent("it.unipi.dii.xavier.BACK")
                    sendBroadcast(i)
                }
            }
        }

        override fun onDrop(timestamp: Long) {
            Log.d("DENTRO ON DROP", "drop frame : $timestamp")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

