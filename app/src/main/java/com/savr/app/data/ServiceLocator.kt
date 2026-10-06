package com.savr.app.data

import android.app.Application
import androidx.room.Room
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.savr.app.BuildConfig
import java.util.concurrent.TimeUnit

object ServiceLocator {
    lateinit var app: Application
    val db by lazy { Room.databaseBuilder(app, AppDb::class.java, "library.db").build() } // offline-friendly library
    val http by lazy { OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { c -> c.proceed(c.request().newBuilder().header("User-Agent", "Mozilla/5.0 (Linux; Android ${android.os.Build.VERSION.RELEASE}) Savr/${BuildConfig.VERSION_NAME}").build()) }
        .build() }
    /** Downloads: the backend may merge video+audio before the first byte arrives, so allow a long wait between bytes. */
    val downloadHttp by lazy { http.newBuilder().readTimeout(10, TimeUnit.MINUTES).build() }
    // Extraction asks the backend to resolve the post (yt-dlp): typically 2-10 s, allow 60 s
    val api: ExtractApi by lazy { Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).client(http.newBuilder().readTimeout(60, TimeUnit.SECONDS).build())
        .addConverterFactory(GsonConverterFactory.create()).build().create(ExtractApi::class.java) }
    val settings by lazy { SettingsStore(app) }
}
