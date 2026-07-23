package com.aiah.tracker

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var api: ApiClient
    private lateinit var adapter: DevicesAdapter

    companion object {
        const val TAG = "TrackerMain"
        const val BASE_URL = "http://100.72.117.127:18080"
        const val EXTRA_DEVICE = "device"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Контейнер, центрирующий список по середине экрана
        val container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val rv = RecyclerView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL
            )
            layoutManager = LinearLayoutManager(this@MainActivity)
            clipToPadding = false
            setPadding(48, 96, 48, 96)
        }
        container.addView(rv)
        setContentView(container)

        api = ApiClient(BASE_URL)
        adapter = DevicesAdapter(this) { item ->
            val intent = Intent(this, TrackActivity::class.java).apply {
                putExtra(EXTRA_DEVICE, item.name)
            }
            startActivity(intent)
        }
        rv.adapter = adapter

        loadDevices()
    }

    private fun loadDevices() {
        lifecycleScope.launch {
            try {
                val tracks = api.getTracks()
                adapter.update(tracks.keys.toList())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load devices", e)
            }
        }
    }
}