package com.aiah.tracker

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
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

class TrackActivity : AppCompatActivity() {

    private lateinit var api: ApiClient
    private lateinit var mapView: MapView
    private lateinit var progressBar: ProgressBar
    private lateinit var infoText: TextView

    companion object {
        const val TAG = "TrackerMap"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(applicationContext, getSharedPreferences("osmdroid", MODE_PRIVATE))

        val device = intent.getStringExtra(MainActivity.EXTRA_DEVICE) ?: ""
        val date = intent.getStringExtra(MainActivity.EXTRA_DATE) ?: ""
        title = "$device — $date"

        api = ApiClient(MainActivity.BASE_URL)

        // Контейнер
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        progressBar = ProgressBar(this).apply {
            visibility = View.GONE
            setPadding(48, 48, 48, 48)
        }
        container.addView(progressBar)

        infoText = TextView(this).apply {
            setPadding(48, 24, 48, 24)
            textSize = 14f
            setTextColor(Color.DKGRAY)
        }
        container.addView(infoText)

        mapView = MapView(this).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setUseDataConnection(true)
        }
        container.addView(mapView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        setContentView(container)

        loadTrack(device, date)
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

            if (geom.type == "LineString") {
                // Линия трека — пропускаем, рисуем сами
                continue
            }

            if (geom.type == "Point") {
                @Suppress("UNCHECKED_CAST")
                val coords = geom.coordinates as? List<Double> ?: continue
                if (coords.size < 2) continue

                val lat = coords[1]
                val lon = coords[0]
                val geoPoint = GeoPoint(lat, lon)
                points.add(geoPoint)
                count++

                // Скорость из properties
                val speedKmh = (feature.properties?.get("speed_kmh") as? Number)?.toDouble() ?: 0.0
                if (speedKmh > maxSpeed) maxSpeed = speedKmh
                avgSpeed += speedKmh

                // Маркер с всплывашкой
                val time = feature.properties?.get("time") as? String ?: ""
                val marker = Marker(mapView).apply {
                    position = geoPoint
                    title = "$time • ${speedKmh} км/ч"
                    snippet = "lat: ${"%.4f".format(lat)}, lon: ${"%.4f".format(lon)}"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                mapView.overlays.add(marker)
            }
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

        // Центрируем карту
        val controller = mapView.controller
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
        controller.setCenter(center)
        val spanLat = (maxLat - minLat).coerceAtLeast(0.01)
        val spanLon = (maxLon - minLon).coerceAtLeast(0.01)
        controller.zoomToSpan(spanLat, spanLon)
        // Чуть отодвинем зум
        controller.zoomOut()

        infoText.text = "Точек: $count • Макс: ${"%.0f".format(maxSpeed)} км/ч • Средн: ${"%.0f".format(avgSpeed)} км/ч"

        mapView.invalidate()
    }

    private fun speedToColor(speedKmh: Double): Int {
        // 0 км/ч — синий, 60 — зелёный, 100 — жёлтый, 130+ — красный
        return when {
            speedKmh < 10 -> Color.parseColor("#1565C0")   // тёмно-синий (стоит)
            speedKmh < 40 -> Color.parseColor("#42A5F5")   // синий (медленно)
            speedKmh < 70 -> Color.parseColor("#66BB6A")   // зелёный
            speedKmh < 100 -> Color.parseColor("#FFA726")  // жёлтый
            else -> Color.parseColor("#EF5350")           // красный (быстро)
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