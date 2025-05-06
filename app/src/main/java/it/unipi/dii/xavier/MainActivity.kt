package it.unipi.dii.xavier

import GazeTrackerSingleton
import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
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
import androidx.core.content.edit


class MainActivity : AppCompatActivity() {

    private val cameraPermissionRequestCode = 1000
    //values for camera position coordinates to be inserted in the initial form
    private lateinit var initialForm: LinearLayout
    private lateinit var etX: EditText
    private lateinit var etY: EditText
    private lateinit var btnSet: Button
    //default values
    private var paramAngle = 0f
    private var paramScale = 0f

    //manages the result of the overlay permission request
    private lateinit var overlayPermissionLauncher: ActivityResultLauncher<Intent>

    //main class that captures the user's face through the camera of the device, processes it, and provides data related to the gaze
    private var gazeTracker: GazeTracker? = null

    //root layout object
    private lateinit var rootLayout: FrameLayout
    //width and height of the root layout
    private var w = 0
    private var h = 0

    //pointer object
    private lateinit var pointer: ImageView

    //point shown for calibration
    private var currentCalibrationPoint: View? = null
    //flag to check if calibration is done
    private var isCalibrated = false

    //ExecutorService is a thread pool interface that is used to manage the pool of threads, used to serialize operations such as camera frames
    private val cameraExecutor: ExecutorService by lazy {
        //returns an ExecutorService that executes tasks in a queue
        Executors.newSingleThreadExecutor()
    }

    // Handler to schedule tasks on the main thread
    private val handler = Handler(Looper.getMainLooper())

    @SuppressLint("MissingInflatedId", "DiscouragedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //hide action bar
        supportActionBar?.hide()

        setContentView(R.layout.activity_main)

        //retrieve the root layout and its dimensions
        rootLayout = findViewById(R.id.root)
        rootLayout.post {
            w = rootLayout.width
            h = rootLayout.height
        }

        //method to manage the activity results
        overlayPermissionLauncher = registerForActivityResult(
            //start a new activity and manage the result (response to overlay permission)
            ActivityResultContracts.StartActivityForResult()
        ) {
            //manages the result of the user interaction with the permission window
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "You have to allow overlay permission", Toast.LENGTH_LONG).show()
            }
        }

        //verifying if the app has the overlay permission
        if (!Settings.canDrawOverlays(this)) {
            //new intent to request overlay permission
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri()
            )
            overlayPermissionLauncher.launch(intent)
        }

        //views initialization
        initialForm = findViewById(R.id.initialForm)
        etX          = findViewById(R.id.etX)
        etY          = findViewById(R.id.etY)
        btnSet       = findViewById(R.id.btnSet)

        //read values from shared preferences
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val firstRun = prefs.getBoolean("first_run", true)

        //check if it is the first run ever
        if (!firstRun) {
            //not for the first time, hide the form, values already set
            initialForm.visibility = View.GONE
            paramAngle = prefs.getFloat("pref_angle", paramAngle)
            paramScale = prefs.getFloat("pref_scale", paramScale)
        } else {
            //first run, show form
            initialForm.visibility = View.VISIBLE
        }

        //set listener to "Set" Button
        btnSet.setOnClickListener {
            //read values from EditText
            val xStr = etX.text.toString().ifEmpty { "0" }
            val yStr = etY.text.toString().ifEmpty { "0" }
            //save values in global variables to be used in the gazeTracker
            paramAngle = xStr.toFloat()
            paramScale = yStr.toFloat()

            //not the first run anymore (used to save values in shared preferences)
            prefs.edit {
                putBoolean("first_run", false)
            }

            //hide form
            initialForm.visibility = View.GONE

            //check if camera permission is granted
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
                //request permission
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.CAMERA),
                    cameraPermissionRequestCode)
            }else{
                pointer = findViewById(R.id.pointer)
                cameraExecutor.execute{startCamera()}
            }
        }
        //check if it is the first run ever, if not, start the gazeTracker
        if(!firstRun){
            pointer = findViewById(R.id.pointer)
            cameraExecutor.execute{startCamera()}
        }
    }

    override fun onPause() {
        super.onPause()
        //calibration ended, send message to the service to start gaze
        val intent = Intent(GazeTrackerService.ACTION_START_GAZE)
        sendBroadcast(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == cameraPermissionRequestCode &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            pointer = findViewById(R.id.pointer)
            cameraExecutor.execute{startCamera()}

        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCamera() {
        initGaze()
    }

    private fun initGaze() {
        val licenseKey = "dev_ptq5nrn1bep16ykwwlwlag5n6u3hz6q7vj0sbcxc"
        //a GazeTracker option instance is created thorugh build method
        val options = GazeTrackerOptions.Builder().setUseGazeFilter(true).setUseBlink(true).build()

        GazeTracker.initGazeTracker(applicationContext, licenseKey, initializationCallback, options)
    }

    private val initializationCallback =
        InitializationCallback { gazeTracker, error ->

            if (gazeTracker != null) {
                initSuccess(gazeTracker)

                //set status callback
                gazeTracker.setStatusCallback(statusCallback)

                //-22.5f 0.5f for Huawei Y6 2018 used for testing
                Log.i("X", "x: $paramAngle ")
                Log.i("Y", "y: $paramScale")

                //set camera position respect to the highest left corner of the screen
                //to make eye tracking more precise
                val cameraPosition =
                    CameraPosition("Huawei Y6 2018",
                        w.toFloat(), h.toFloat(), paramAngle, paramScale, false)

                gazeTracker.addCameraPosition(cameraPosition)

                //open the camera and start tracking the gaze
                gazeTracker.startTracking()

            } else {
                initFail(error)
            }
        }

    private val statusCallback = object : StatusCallback {
        override fun onStarted() {
            //set calibration function
            gazeTracker?.setCalibrationCallback(calibrationCallback)
            //start the most accurate calibration process
            gazeTracker?.startCalibration(CalibrationModeType.FIVE_POINT, AccuracyCriteria.HIGH)
        }

        override fun onStopped(error: StatusErrorType) {
            //handle status error
        }
    }

    private val calibrationCallback = object : CalibrationCallback {

        override fun onCalibrationProgress(progress: Float) {
            runOnUiThread {
                // process calibration point UI
            }
        }

        //use the calibration points coordinates to draw the point in the UI
        override fun onCalibrationNextPoint(x: Float, y: Float) {
            runOnUiThread {
                // draw calibration point to (x, y)
                drawPointAt(x, y)
            }
            //after drawing the point, wait 1 second and execute startCollectSamples()
            handler.postDelayed({ gazeTracker?.startCollectSamples() }, 1000)
        }

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

        // remove the previous point if it exists
        currentCalibrationPoint?.let { rootLayout.removeView(it) }

        val pointView = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(20, 20).apply {
                leftMargin = x.toInt() - 10  // center the point
                topMargin = y.toInt() - 10
            }
            setBackgroundColor(Color.RED)
        }
        //draw point
        rootLayout.addView(pointView)
        currentCalibrationPoint = pointView
    }

    private fun initSuccess(gazeTracker: GazeTracker) {

        //assign gazeTracker
        this.gazeTracker = gazeTracker

        //save gazeTracker in Singleton
        GazeTrackerSingleton.tracker = gazeTracker

        //register the tracking callback, triggered when certain events occur
        this.gazeTracker!!.setTrackingCallback(trackingCallback)
    }

    private fun initFail(error: InitializationErrorType) {
        val err = when (error) {
            InitializationErrorType.ERROR_INIT -> "Initialization failed"
            InitializationErrorType.ERROR_CAMERA_PERMISSION -> "Required permission not granted"
            else -> "Eyedid SDK initialization failed"
        }
        Log.w("Eyedid SDK", "Error description: $err")
    }


    private val trackingCallback: TrackingCallback = object : TrackingCallback {
        override fun onMetrics(
            timestamp: Long,
            gazeInfo: GazeInfo,
            faceInfo: FaceInfo,
            blinkInfo: BlinkInfo,
            userStatusInfo: UserStatusInfo
        ) {

            //update the pointer position
            runOnUiThread {
                pointer.x = gazeInfo.x
                pointer.y = gazeInfo.y
            }

            if (isCalibrated) {
                if (blinkInfo.isBlinkRight && !blinkInfo.isBlinkLeft) {
                    //Right blind --> Restart the calibration process
                    isCalibrated = false
                    gazeTracker?.startCalibration(
                        CalibrationModeType.FIVE_POINT,
                        AccuracyCriteria.HIGH
                    )

                } else if (blinkInfo.isBlinkLeft && !blinkInfo.isBlinkRight) {
                    //Left blink --> go to the home screen
                    isCalibrated = false
                    val i = Intent("it.unipi.dii.xavier.BACK")
                    sendBroadcast(i)
                }
            }
        }

        override fun onDrop(timestamp: Long) {
            //handle tracking frame drop
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

