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
    private lateinit var adapter: TracksAdapter

    companion object {
        const val TAG = "TrackerMain"
        const val BASE_URL = "http://100.72.117.127:18080"
        const val EXTRA_DEVICE = "device"
        const val EXTRA_DATE = "date"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rv = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            setPadding(48, 48, 48, 48)
        }
        setContentView(rv)

        api = ApiClient(BASE_URL)
        adapter = TracksAdapter(this) { item ->
            val intent = Intent(this, TrackActivity::class.java).apply {
                putExtra(EXTRA_DEVICE, item.device)
                putExtra(EXTRA_DATE, item.date)
            }
            startActivity(intent)
        }
        rv.adapter = adapter

        loadTracks()
    }

    private fun loadTracks() {
        lifecycleScope.launch {
            try {
                val tracks = api.getTracks()
                val items = mutableListOf<TrackItem>()
                for ((device, dates) in tracks) {
                    for (date in dates) {
                        items.add(TrackItem(device = device, date = date))
                    }
                }
                items.sortByDescending { "${it.device}-${it.date}" }
                adapter.update(items)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load tracks", e)
            }
        }
    }
}