import android.annotation.SuppressLint
import camp.visual.eyedid.gazetracker.GazeTracker

object GazeTrackerSingleton {
    @SuppressLint("StaticFieldLeak")
    var tracker: GazeTracker? = null
}