package com.aiah.tracker

import android.app.AlertDialog
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TrackActivity : AppCompatActivity() {

    private lateinit var api: ApiClient
    private lateinit var mapView: MapView
    private lateinit var progressBar: ProgressBar
    private lateinit var infoText: TextView
    private lateinit var dateSpinner: Spinner

    private var dates: List<String> = emptyList()
    private lateinit var device: String
    private var currentDate: String? = null
    private var lastPointCount = -1
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshIntervalMs = 5000L  // опрос каждые 5 сек

    private val refreshRunnable = object : Runnable {
        override fun run() {
            val date = currentDate ?: return
            refreshTrack(date)
            refreshHandler.postDelayed(this, refreshIntervalMs)
        }
    }

    companion object {
        const val TAG = "TrackerMap"
        private const val PREFS_NAME = "aiah_tracker"
        private const val KEY_TILE_SOURCE = "tile_source"
        private const val TILE_OSM = 0
        private const val TILE_SATELLITE = 1
        private val tileOptions = arrayOf("OSM (Карта)", "Спутник (ESRI)")
        private val tileValues = intArrayOf(TILE_OSM, TILE_SATELLITE)
    }

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    }

    // Yandex Satellite (https://sat0{s}.maps.yandex.net) — российский, не блокируется в РБ/РФ.
    // Формат OSM: x/y/z, в URL как query-параметры: ?l=sat&x={x}&y={y}&z={z}
    // curl тест: HTTP 200, image/png
    private val satelliteSourceXY: ITileSource = object : OnlineTileSourceBase(
        "YandexSat",
        0, 19, 256, "",
        arrayOf("https://sat01.maps.yandex.net/tiles?l=sat&x="),
        "© Яндекс"
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val z = org.osmdroid.util.MapTileIndex.getZoom(pMapTileIndex)
            val x = org.osmdroid.util.MapTileIndex.getX(pMapTileIndex)
            val y = org.osmdroid.util.MapTileIndex.getY(pMapTileIndex)
            val url = "https://sat01.maps.yandex.net/tiles?l=sat&x=" + x + "&y=" + y + "&z=" + z
            android.util.Log.d("AiahTracker", "Yandex sat tile: $url")
            return url
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(applicationContext, getSharedPreferences("osmdroid", MODE_PRIVATE))
        // Установить User-Agent — иначе ESRI возвращает 403 (по умолчанию osmdroid)
        Configuration.getInstance().userAgentValue = "AiahTracker/1.0 (https://github.com/tcrmoz/aiah-tracker)"

        device = intent.getStringExtra(MainActivity.EXTRA_DEVICE) ?: ""
        title = device

        api = ApiClient(MainActivity.BASE_URL)

        setContentView(R.layout.activity_track)

        dateSpinner = findViewById(R.id.dateSpinner)
        progressBar = findViewById(R.id.progressBar)
        infoText = findViewById(R.id.infoText)
        mapView = findViewById(R.id.mapView)

        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.setUseDataConnection(true)

        // Восстановить сохранённый тип карты
        applyTileSource(prefs.getInt(KEY_TILE_SOURCE, TILE_OSM))

        val layersButton = findViewById<ImageButton>(R.id.layersButton)
        layersButton.setOnClickListener { showLayerDialog() }

        dateSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (dates.isNotEmpty()) {
                    loadTrack(device, dates[position])
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        loadDates()
    }

    private fun loadDates() {
        progressBar.visibility = View.VISIBLE
        infoText.text = "Загрузка списка дат..."

        lifecycleScope.launch {
            try {
                val tracks = api.getTracks()
                dates = tracks[device] ?: emptyList()

                if (dates.isEmpty()) {
                    progressBar.visibility = View.GONE
                    infoText.text = "Нет треков для $device"
                    dateSpinner.adapter = null
                    return@launch
                }

                val spinnerAdapter = ArrayAdapter(
                    this@TrackActivity,
                    android.R.layout.simple_spinner_item,
                    dates
                )
                spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                dateSpinner.adapter = spinnerAdapter

                // Дефолт — сегодня, если есть. Иначе последняя доступная.
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val todayIdx = dates.indexOf(today)
                val defaultIdx = if (todayIdx >= 0) todayIdx else dates.size - 1
                dateSpinner.setSelection(defaultIdx)
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                infoText.text = "Ошибка загрузки дат: ${e.message}"
                Log.e(TAG, "Failed to load dates", e)
            }
        }
    }

    private fun loadTrack(device: String, date: String) {
        progressBar.visibility = View.VISIBLE
        infoText.text = "Загрузка трека $device за $date..."

        // Остановить старый polling и сбросить счётчик
        refreshHandler.removeCallbacks(refreshRunnable)
        lastPointCount = -1
        currentDate = date

        lifecycleScope.launch {
            try {
                val track = api.getTrack(device, date)
                progressBar.visibility = View.GONE
                lastPointCount = track.point_count
                drawTrack(track, fitToScreen = true)
                // Запустить авто-рефреш
                refreshHandler.postDelayed(refreshRunnable, refreshIntervalMs)
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                infoText.text = "Ошибка: ${e.message}"
                Log.e(TAG, "Failed to load track", e)
            }
        }
    }

    private fun refreshTrack(date: String) {
        // Тихо опрашиваем сервер в фоне
        lifecycleScope.launch {
            try {
                val track = api.getTrack(device, date)
                if (track.point_count > lastPointCount) {
                    val added = track.point_count - lastPointCount
                    lastPointCount = track.point_count
                    // Перерисовываем без сброса зума
                    drawTrack(track, fitToScreen = false)
                    infoText.text = "${infoText.text} • +$added точек"
                }
            } catch (e: Exception) {
                Log.w(TAG, "Refresh failed: ${e.message}")
                // Тихо — следующая попытка через 5 сек
            }
        }
    }

    private fun drawTrack(track: TrackResponse, fitToScreen: Boolean) {
        mapView.overlays.clear()

        val points = mutableListOf<GeoPoint>()
        var maxSpeed = 0.0
        var avgSpeed = 0.0
        var count = 0
        var totalDistanceKm = 0.0

        // Иконки: маленькая для всех точек, большая для последней
        val smallIcon = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_point_small)
        val largeIcon = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_point_large)
        val pointFeatures = track.features.filter { it.geometry?.type == "Point" }
        val lastPointIdx = pointFeatures.size - 1

        var pointIdx = -1
        for (feature in track.features) {
            val geom = feature.geometry ?: continue

            if (geom.type == "LineString") continue
            if (geom.type != "Point") continue

            pointIdx++
            val isLast = pointIdx == lastPointIdx

            @Suppress("UNCHECKED_CAST")
            val coords = geom.coordinates as? List<Double> ?: continue
            if (coords.size < 2) continue

            val lat = coords[1]
            val lon = coords[0]
            val geoPoint = GeoPoint(lat, lon)
            points.add(geoPoint)
            count++

            // Haversine distance from previous point
            if (points.size >= 2) {
                val prev = points[points.size - 2]
                totalDistanceKm += haversineKm(
                    prev.latitude, prev.longitude,
                    lat, lon
                )
            }

            val speedKmh = (feature.properties?.get("speed_kmh") as? Number)?.toDouble() ?: 0.0
            if (speedKmh > maxSpeed) maxSpeed = speedKmh
            avgSpeed += speedKmh

            val time = feature.properties?.get("time") as? String ?: ""
            val marker = Marker(mapView).apply {
                position = geoPoint
                icon = if (isLast) largeIcon else smallIcon
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                if (isLast) {
                    title = "Финальная точка: $time • ${speedKmh} км/ч"
                    snippet = "lat: ${"%.4f".format(lat)}, lon: ${"%.4f".format(lon)}"
                }
            }
            mapView.overlays.add(marker)
        }

        if (points.isEmpty()) {
            infoText.text = "Нет точек в треке"
            return
        }

        avgSpeed = if (count > 0) avgSpeed / count else 0.0

        // Линия трека — цвет по скорости
        if (points.size >= 2) {
            for (i in 0 until points.size - 1) {
                val speedKmh = ((track.features.getOrNull(i + 1)?.properties?.get("speed_kmh")) as? Number)?.toDouble() ?: 0.0
                val color = speedToColor(speedKmh)
                val line = Polyline(mapView).apply {
                    setPoints(listOf(points[i], points[i + 1]))
                    outlinePaint.color = color
                    outlinePaint.strokeWidth = 8f
                }
                mapView.overlays.add(0, line)
            }
        }

        if (fitToScreen && points.isNotEmpty()) {
            // Bounding box с отступом 10% — только при первой загрузке
            var minLat = points.first().latitude
            var maxLat = points.first().latitude
            var minLon = points.first().longitude
            var maxLon = points.first().longitude
            for (p in points) {
                if (p.latitude < minLat) minLat = p.latitude
                if (p.latitude > maxLat) maxLat = p.latitude
                if (p.longitude < minLon) minLon = p.longitude
                if (p.longitude > maxLon) maxLon = p.longitude
            }
            val latPad = (maxLat - minLat).coerceAtLeast(0.0001) * 0.1
            val lonPad = (maxLon - minLon).coerceAtLeast(0.0001) * 0.1
            val boundingBox = org.osmdroid.util.BoundingBox(
                maxLat + latPad, maxLon + lonPad,
                minLat - latPad, minLon - lonPad
            )
            mapView.post {
                mapView.zoomToBoundingBox(boundingBox, false)
            }
        }

        infoText.text = "Точек: $count • Пробег: ${"%.1f".format(totalDistanceKm)} км • Макс: ${"%.0f".format(maxSpeed)} км/ч • Средн: ${"%.0f".format(avgSpeed)} км/ч"
        mapView.invalidate()
    }

    // Haversine distance in kilometers between two lat/lon points
    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0  // Earth radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2).let { it * it } +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2).let { it * it }
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }

    private fun speedToColor(speedKmh: Double): Int {
        return when {
            speedKmh < 10 -> Color.parseColor("#1565C0")
            speedKmh < 40 -> Color.parseColor("#42A5F5")
            speedKmh < 70 -> Color.parseColor("#66BB6A")
            speedKmh < 100 -> Color.parseColor("#FFA726")
            else -> Color.parseColor("#EF5350")
        }
    }

    private fun applyTileSource(type: Int) {
        // Запоминаем текущий зум и центр, чтобы не сбрасывались при переключении
        val savedZoom = mapView.zoomLevelDouble
        val savedCenter = mapView.mapCenter
        val source = when (type) {
            TILE_SATELLITE -> satelliteSourceXY  // начинаем с z/x/y — ESRI на нашем сервере возвращает 200
            else -> TileSourceFactory.MAPNIK
        }
        mapView.setTileSource(source)
        mapView.controller.setZoom(savedZoom)
        mapView.controller.setCenter(savedCenter)
        prefs.edit().putInt(KEY_TILE_SOURCE, type).apply()

        // Сразу пробуем основной URL и логируем HTTP-статус — увидим 200/403/404 в logcat
        if (type == TILE_SATELLITE) {
            lifecycleScope.launch {
                try {
                    val testUrl = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/10/163/402"
                    val conn = java.net.URL(testUrl).openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    conn.setRequestProperty("User-Agent", "AiahTracker/1.0")
                    conn.connect()
                    val code = conn.responseCode
                    android.util.Log.i("AiahTracker", "Probe satellite URL: $code (${conn.responseMessage})")
                    conn.disconnect()
                } catch (e: Exception) {
                    android.util.Log.w("AiahTracker", "Probe failed: ${e.message}")
                }
            }
        }
    }

    private fun showLayerDialog() {
        val savedType = prefs.getInt(KEY_TILE_SOURCE, TILE_OSM)
        val checkedIdx = tileValues.indexOf(savedType).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Тип карты")
            .setSingleChoiceItems(tileOptions, checkedIdx) { dialog, which ->
                applyTileSource(tileValues[which])
                dialog.dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        // Возобновить авто-рефреш если был активен
        if (currentDate != null) {
            refreshHandler.removeCallbacks(refreshRunnable)
            refreshHandler.postDelayed(refreshRunnable, refreshIntervalMs)
        }
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        refreshHandler.removeCallbacks(refreshRunnable)
    }
}