package com.savr.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.savr.app.data.ServiceLocator
import kotlinx.coroutines.*

class App : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate(); ServiceLocator.app = this
        CoroutineScope(Dispatchers.IO).launch { ServiceLocator.db.dao().pauseStale() } // interrupted downloads show as Paused
    }
    // Video frames as thumbnails: works offline from the saved file
    override fun newImageLoader() = ImageLoader.Builder(this).components { add(VideoFrameDecoder.Factory()) }.crossfade(true).build()
}
