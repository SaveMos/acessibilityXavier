package it.unipi.dii.xavier

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.YuvImage
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/*
class MainActivity : AppCompatActivity() {

    private lateinit var pointer: ImageView
    private lateinit var previewView: PreviewView
    private val cameraExecutor: ExecutorService by lazy {
        Executors.newSingleThreadExecutor()
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // init Chaquopy
        if (!Python.isStarted()) Python.start(AndroidPlatform(this))

        setContentView(R.layout.activity_main)
        previewView = findViewById(R.id.previewView)
        pointer     = findViewById(R.id.pointer)

        startCamera()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(Runnable {
            val cameraProvider = cameraProviderFuture.get()

            // Usa solo la front camera
            val selector = CameraSelector.DEFAULT_FRONT_CAMERA

            // Preview (opzionale, per vedere cosa "vede" la camera)
            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            // ImageAnalysis
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, { imageProxy ->
                        processFrame(imageProxy)
                    })
                }

            // bind
            cameraProvider.bindToLifecycle(this, selector, preview, analysis)

        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun processFrame(imageProxy: ImageProxy) {
        // 1) prendi i dati YUV e converti in JPEG
        val image = imageProxy.image
        if (image != null) {
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer
            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            // NV21 array
            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            // comprimi in JPEG
            val yuv = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream().apply {
                yuv.compressToJpeg(Rect(0, 0, image.width, image.height), 90, this)
            }
            val jpegBytes = out.toByteArray()

            // 2) chiama Python
            val metrics = Resources.getSystem().displayMetrics
            // 1) Ottieni l’istanza di Python e i builtins
            val py       = Python.getInstance()
            val builtins = py.getBuiltins()

            // 2) Crea un oggetto Python bytes dal Java ByteArray
            //    Nota: getBuiltins().callAttr("bytes", ...) invoca il costruttore Python bytes()
            val pyBytes = builtins.callAttr("bytes", jpegBytes)

            // 3) Passa pyBytes al modulo coords
            val module  = py.getModule("coords")
            val coordsPy = module.callAttr(
                "get_coords",
                pyBytes,
                metrics.widthPixels,
                metrics.heightPixels
            )

            Log.d("DEBUG_COORDS", "Python ha risposto: $coordsPy")

            // 4) Estrai la lista e convertila in interi
            val coords = coordsPy.asList()
            val x = coords[0].toInt()
            val y = coords[1].toInt()

            Log.d("DEBUG_COORDS", "Ricevute coords: x=$x, y=$y")


            // 3) aggiorna UI sul main thread
            runOnUiThread {
                pointer.x = x.toFloat()
                pointer.y = y.toFloat()
            }
        }

        // non dimenticare di rilasciare
        imageProxy.close()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
*/

class MainActivity : AppCompatActivity() {

    private val CAMERA_PERMISSION_REQUEST_CODE = 1001

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

        if (!Python.isStarted()) Python.start(AndroidPlatform(this))
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

    private fun rotateImage(jpegBytes: ByteArray, degrees: Int): ByteArray {
        val bmp = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
        val matrix = Matrix()
        matrix.postRotate(degrees.toFloat())
        val rotatedBmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)

        val outStream = ByteArrayOutputStream()
        rotatedBmp.compress(Bitmap.CompressFormat.JPEG, 100, outStream)
        return outStream.toByteArray()
    }


    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

