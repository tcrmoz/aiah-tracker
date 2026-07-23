package com.aiah.tracker

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

// --- Модели данных ---

data class TracksResponse(
    @SerializedName("carpad") val carpad: List<String>? = null,
    @SerializedName("redmi-note-13") val redmiNote13: List<String>? = null
) {
    fun toMap(): Map<String, List<String>> {
        val map = mutableMapOf<String, List<String>>()
        carpad?.let { map["carpad"] = it }
        redmiNote13?.let { map["redmi-note-13"] = it }
        // Любые другие ключи тоже попадут, если парсить как Map
        return map
    }
}

data class GeoJsonFeature(
    val type: String,
    val geometry: Geometry? = null,
    val properties: Map<String, Any?>? = null
)

data class Geometry(
    val type: String,
    val coordinates: Any? = null  // [lon, lat] или [[lon,lat],...]
)

data class TrackResponse(
    val type: String,
    val device: String,
    val date: String,
    val point_count: Int,
    val features: List<GeoJsonFeature>
)

data class ApkListResponse(val apk: List<ApkInfo>)
data class ApkInfo(
    val filename: String,
    val size: Long,
    val size_mb: Double,
    val modified: String
)

// --- API клиент ---

class ApiClient(private val baseUrl: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    suspend fun getTracks(): Map<String, List<String>> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("$baseUrl/tracks").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            val body = resp.body?.string() ?: throw Exception("Empty body")
            // Парсим как Map напрямую —.gson не справится с dynamic keys в data class
            val type = object : com.google.gson.reflect.TypeToken<Map<String, List<String>>>() {}.type
            gson.fromJson(body, type) ?: emptyMap()
        }
    }

    suspend fun getTrack(device: String, date: String): TrackResponse = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("$baseUrl/tracks/$device/$date").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            val body = resp.body?.string() ?: throw Exception("Empty body")
            gson.fromJson(body, TrackResponse::class.java)
        }
    }

    suspend fun getApkList(): List<ApkInfo> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("$baseUrl/apk").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            val body = resp.body?.string() ?: throw Exception("Empty body")
            gson.fromJson(body, ApkListResponse::class.java)?.apk ?: emptyList()
        }
    }
}