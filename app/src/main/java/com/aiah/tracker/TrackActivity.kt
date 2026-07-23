package com.aiah.tracker

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
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

    companion object {
        const val TAG = "TrackerMap"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(applicationContext, getSharedPreferences("osmdroid", MODE_PRIVATE))

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

        lifecycleScope.launch {
            try {
                val track = api.getTrack(device, date)
                progressBar.visibility = View.GONE
                drawTrack(track)
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                infoText.text = "Ошибка: ${e.message}"
                Log.e(TAG, "Failed to load track", e)
            }
        }
    }

    private fun drawTrack(track: TrackResponse) {
        mapView.overlays.clear()

        val points = mutableListOf<GeoPoint>()
        var maxSpeed = 0.0
        var avgSpeed = 0.0
        var count = 0

        for (feature in track.features) {
            val geom = feature.geometry ?: continue

            if (geom.type == "LineString") continue
            if (geom.type != "Point") continue

            @Suppress("UNCHECKED_CAST")
            val coords = geom.coordinates as? List<Double> ?: continue
            if (coords.size < 2) continue

            val lat = coords[1]
            val lon = coords[0]
            val geoPoint = GeoPoint(lat, lon)
            points.add(geoPoint)
            count++

            val speedKmh = (feature.properties?.get("speed_kmh") as? Number)?.toDouble() ?: 0.0
            if (speedKmh > maxSpeed) maxSpeed = speedKmh
            avgSpeed += speedKmh

            val time = feature.properties?.get("time") as? String ?: ""
            val marker = Marker(mapView).apply {
                position = geoPoint
                title = "$time • ${speedKmh} км/ч"
                snippet = "lat: ${"%.4f".format(lat)}, lon: ${"%.4f".format(lon)}"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
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

        // Bounding box
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
        val center = GeoPoint((minLat + maxLat) / 2, (minLon + maxLon) / 2)
        mapView.controller.setCenter(center)
        val spanLat = (maxLat - minLat).coerceAtLeast(0.01)
        val spanLon = (maxLon - minLon).coerceAtLeast(0.01)
        mapView.controller.zoomToSpan(spanLat, spanLon)
        mapView.controller.zoomOut()

        infoText.text = "Точек: $count • Макс: ${"%.0f".format(maxSpeed)} км/ч • Средн: ${"%.0f".format(avgSpeed)} км/ч"
        mapView.invalidate()
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

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}