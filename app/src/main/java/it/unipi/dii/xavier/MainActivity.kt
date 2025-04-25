package it.unipi.dii.xavier

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import camp.visual.eyedid.gazetracker.GazeTracker
import camp.visual.eyedid.gazetracker.callback.InitializationCallback
import camp.visual.eyedid.gazetracker.callback.TrackingCallback
import camp.visual.eyedid.gazetracker.constant.GazeTrackerOptions
import camp.visual.eyedid.gazetracker.constant.InitializationErrorType
import camp.visual.eyedid.gazetracker.metrics.BlinkInfo
import camp.visual.eyedid.gazetracker.metrics.FaceInfo
import camp.visual.eyedid.gazetracker.metrics.GazeInfo
import camp.visual.eyedid.gazetracker.metrics.UserStatusInfo
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {


    private val CAMERA_PERMISSION_REQUEST_CODE = 1000

    //oggetto layout che contiene la freccetta
    private lateinit var pointer: ImageView

    //ExecutorService è l'interfaccia per gestire il pool di thread
    //utile pe serializare le operazioni come ad esempio i frame della camera
    private val cameraExecutor: ExecutorService by lazy {
        //restituisce un ExecutorService che esegue i task in coda
        Executors.newSingleThreadExecutor()
    }

    // Handler to schedule tasks on the main thread
    //private val handler = Handler(Looper.getMainLooper())

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //if (!Python.isStarted()) Python.start(AndroidPlatform(this))
        setContentView(R.layout.activity_main)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE)
        }else{

            pointer = findViewById(R.id.pointer)
            // il nuovo thread inizia dopo delay secondi dalla fine del precedente
            //cameraExecutor.scheduleWithFixedDelay({startCamera()}, 0, 1, TimeUnit.SECONDS)
            cameraExecutor.execute(){startCamera()}
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            // L'utente ha concesso il permesso: avvia la fotocamera
            pointer = findViewById(R.id.pointer)
            // il nuovo thread inizia dopo delay secondi dalla fine del precedente
            //cameraExecutor.scheduleWithFixedDelay({startCamera()}, 0, 1, TimeUnit.SECONDS)
            cameraExecutor.execute(){startCamera()}

        } else {
            Toast.makeText(this, "Permesso fotocamera negato", Toast.LENGTH_SHORT).show()
        }
    }

    private var gazeTracker: GazeTracker? = null

    private fun permissionGranted() {
        initGaze()
    }

    private fun initGaze() {
        val licenseKey = "dev_ptq5nrn1bep16ykwwlwlag5n6u3hz6q7vj0sbcxc"
        val options = GazeTrackerOptions.Builder().build()

        GazeTracker.initGazeTracker(applicationContext, licenseKey, initializationCallback, options)
    }

    private val initializationCallback = object : InitializationCallback {
        override fun onInitialized(gazeTracker: GazeTracker?, error: InitializationErrorType) {
            if (gazeTracker != null) {
                initSuccess(gazeTracker)
            } else {
                initFail(error)
            }
        }
    }

    private fun initSuccess(gazeTracker: GazeTracker) {
        this.gazeTracker = gazeTracker

        this.gazeTracker!!.setTrackingCallback(trackingCallback);
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
        gazeTracker?.startTracking()

        start()



    }

    private fun start() {



    }

    private val trackingCallback: TrackingCallback = object : TrackingCallback {
        override fun onMetrics(
            timestamp: Long,
            gazeInfo: GazeInfo,
            faceInfo: FaceInfo,
            blinkInfo: BlinkInfo,
            userStatusInfo: UserStatusInfo
        ) {
            Log.i("Eyedid SDK", "Gaze coordinates: " + gazeInfo.x + "x" + gazeInfo.y)
            //aggiorna la posizione dell'immagine del puntatore
            runOnUiThread {
                pointer.x = gazeInfo.x
                pointer.y = gazeInfo.y
            }
        }

        override fun onDrop(timestamp: Long) {
        }
    }


    /*
    private fun startCamera() {
        //cameraX libreria Android per gestione fotocamera
        //ProcessCameraProvider è il gestore globale della fotocamera in CameraX
        //getInstance(this) restituisce un oggetto ListenableFuture<ProcessCameraProvider>, cioè una promessa asincrona che verrà completata più tardi
        //ovvero è come chiedere al sistema "avvisami quando la camera è pronta"
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        //listener chiamato quando cameraProviderFuture è pronto, successivamente il codice interno viene eseguito
        cameraProviderFuture.addListener({
            //otteniamo il risultato della promise, l'oggetto ProcessCameraProvider
            val cameraProvider = cameraProviderFuture.get()
            //seleziona telecamera frontale
            val selector = CameraSelector.DEFAULT_FRONT_CAMERA

            // Solo ImageAnalysis, niente Preview
            //costruisce il blocco di analisi dei frame, ImageAnalysis classe che gestisce i frame
            //builder è il costruttore dell'oggetto
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) //strategia che scarta i vecchi frame e prende solo gli ultimi se arrivano troppe immagini
                .build()                                                          //crea l'oggetto ImageAnalysis
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->                  //aggiunge un analizzatore, imageproxy rappresenta il singolo frame
                        processFrame(imageProxy)                                    //per ogni frame viene eseguita la lambda tra le graffe che esegue processFrame
                    }
                }

            // Bind solo analysis
            //collega fotocamera al ciclo di vita dell'activity, viene indicata la camera utilizzata e la funzione da eseguire
            cameraProvider.bindToLifecycle(this, selector, analysis)

        }, ContextCompat.getMainExecutor(this)) //specifica che il listener deve girare sul main thread
    }
    */
    /*
    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun processFrame(imageProxy: ImageProxy) {
        //otteniamo l'oggetto image raw da imageproxy che è un wrapper dell'immagine raw
        val image = imageProxy.image
        if (image != null) {

            Log.d("IMAGE_NOT_NULL", "immagine non null")

            //Quando ottieni un frame in formato YUV_420_888, imageProxy.planes è un array di tre oggetti Image.Plane, indicizzati da 0 a 2
            // il .buffer contiene i singoli byte dei pixel per quel piano
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer

            Log.d("Y_BUFFER", "yBuffer: $yBuffer")
            Log.d("U_BUFFER", "uBuffer: $uBuffer")
            Log.d("V_BUFFER", "vBuffer: $vBuffer")

            //byte contenuti in ciascun buffer disponibili per la lettura
            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            Log.d("Y_BUFFER_SIZE", "ySize: $ySize")
            Log.d("U_BUFFER_SIZE", "uSize: $uSize")
            Log.d("V_BUFFER_SIZE", "vSize: $vSize")

            //array di byte per contenere tutti i byte dei tre piani analizzati
            val nv21 = ByteArray(ySize + uSize + vSize)

            //copia i byte nei posti giusti secondo lo standard nv21
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            //costruzione oggetto YuvImage partendo dall'array di byte per convertirlo in jpeg
            val yuv = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)

            Log.d("YUV", "yuv: $yuv")

            //comprime in jpeg con qualità 100, scrivendo in uno stream di byte al quale viene applicata l'immagine compressa in jpeg
            val out = ByteArrayOutputStream().apply {
                yuv.compressToJpeg(Rect(0, 0, image.width, image.height), 100, this)
            }

            //Log.d("OUT", "out: $out")

            //ottiene l'array di byte in jpeg
            val jpegBytes = out.toByteArray()

            Log.d("JPEG_BYTES", "jpegBytes: $jpegBytes")

            //recupera òe dimensioni dello schermo in pixel
            val metrics = Resources.getSystem().displayMetrics

            //inizializza interprete python
            val py = Python.getInstance()
            val builtins = py.getBuiltins()

            //salva le immagini per verifica---------------------------------------------------------
            val resolver = applicationContext.contentResolver
            val imageName = "latest_frame.jpg"
            val imageCollection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

// 1. Rimuovi immagine precedente con lo stesso nome
            val selection = "${MediaStore.Images.Media.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(imageName)
            resolver.delete(imageCollection, selection, selectionArgs)

// 2. Inserisci nuova immagine
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, imageName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/GazeTracker")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(imageCollection, contentValues)
            var jpegBytesRotated = jpegBytes
            uri?.let {
                // 3. Ruota immagine se necessario
               jpegBytesRotated = rotateImage(jpegBytes, 270) // ruota -90° se orientamento errato

                resolver.openOutputStream(it).use { outStream ->
                    outStream?.write(jpegBytesRotated)
                }

                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(it, contentValues, null, null)

                Log.d("GALLERY_SAVE", "Immagine sovrascritta nella galleria: $uri")
            }


            //------------------------------------------------------------------------


            //converte i byte jpeg in un oggetto python bytes
            val pyBytes = builtins.callAttr("bytes", jpegBytesRotated)

            //carica il modulo python coords
            val module = py.getModule("coords")
            //eseguiamo get_coords
            val coords = module.callAttr(
                "get_coords",
                pyBytes,
                metrics.widthPixels,
                metrics.heightPixels
            ).asList()

            //estrae le coordinate da una lista python
            val x = coords[0]
            val y = coords[1]

            Log.d("PYTHON_COORD", "coord_x: $x, coord_y: $y")

            //aggiorna la posizione dell'immagine del puntatore
            runOnUiThread {
                pointer.x = x.toFloat()
                pointer.y = y.toFloat()
            }
        }

        imageProxy.close()
    }
*/
    /*
    private fun rotateImage(jpegBytes: ByteArray, degrees: Int): ByteArray {
        val bmp = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
        val matrix = Matrix()
        matrix.postRotate(degrees.toFloat())
        val rotatedBmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)

        val outStream = ByteArrayOutputStream()
        rotatedBmp.compress(Bitmap.CompressFormat.JPEG, 100, outStream)
        return outStream.toByteArray()
    }

     */


    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

