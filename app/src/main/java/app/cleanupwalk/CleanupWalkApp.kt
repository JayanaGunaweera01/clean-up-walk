package app.cleanupwalk

import android.app.Application
import app.cleanupwalk.data.CleanupDatabase
import app.cleanupwalk.data.CleanupRepository
import app.cleanupwalk.detection.DetectionModelStore
import app.cleanupwalk.detection.PhotoScanner
import app.cleanupwalk.location.LocationTracker
import app.cleanupwalk.walk.WalkManager

/**
 * Simple manual dependency container - the app is small enough not to need a DI framework
 */
class CleanupWalkApp : Application() {

    val repository by lazy { CleanupRepository(CleanupDatabase.create(this).dao()) }
    val walkManager by lazy { WalkManager(repository) }
    val locationTracker by lazy { LocationTracker(this) }
    val models by lazy { DetectionModelStore(this) }
    val scanner by lazy { PhotoScanner(this, models) }
}
