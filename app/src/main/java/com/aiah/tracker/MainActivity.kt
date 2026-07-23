package com.aiah.tracker

import android.content.Intent
import android.os.Bundle
import android.util.Log
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

        val rv = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            setPadding(48, 48, 48, 48)
        }
        setContentView(rv)

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