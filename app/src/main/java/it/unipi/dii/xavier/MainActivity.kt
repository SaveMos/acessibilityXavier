package it.unipi.dii.xavier

import android.content.res.Resources
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

class MainActivity : AppCompatActivity() {

    // ImageView representing the pointer on the screen
    private lateinit var pointer: ImageView

    // Handler to schedule tasks on the main thread
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize the Python environment if it hasn't started yet
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        // Set the layout defined in activity_main.xml
        setContentView(R.layout.activity_main)

        // Find the ImageView with id "pointer" from the layout
        pointer = findViewById(R.id.pointer)

        // Start a repeated task that polls the Python function every second
        handler.post(updateRunnable)
    }

    // Runnable that updates the pointer position based on Python-generated coordinates
    private val updateRunnable = object : Runnable {
        override fun run() {
            // Get the Python instance
            val py = Python.getInstance()

            val displayMetrics = Resources.getSystem().displayMetrics
            val width = displayMetrics.widthPixels
            val height = displayMetrics.heightPixels

            // Load the Python module named "coords.py"
            val module = py.getModule("coords")

            // Call the function "get_coords" which should return a list of [x, y]
            val coords = module.callAttr("get_coords", width, height).asList()

            // Convert the coordinates from Python objects to integers
            val x = coords[0].toInt()
            val y = coords[1].toInt()

            // Move the ImageView to the specified coordinates
            pointer.x = x.toFloat()
            pointer.y = y.toFloat()

            // Schedule the next update after 1000 milliseconds (1 second)
            handler.postDelayed(this, 1000)
        }
    }
}
