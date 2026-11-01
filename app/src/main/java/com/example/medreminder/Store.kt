package com.example.medreminder

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File

object Store {
    var data by mutableStateOf(AppData())
        private set

    private var loaded = false

    @Synchronized
    fun ensureLoaded(ctx: Context) {
        if (!loaded) reload(ctx)
    }

    @Synchronized
    fun reload(ctx: Context) {
        val f = File(ctx.filesDir, "data.json")
        data = try {
            AppData.fromJson(JSONObject(f.readText()))
        } catch (e: Exception) {
            AppData()
        }
        loaded = true
    }

    @Synchronized
    fun update(ctx: Context, change: (AppData) -> AppData) {
        ensureLoaded(ctx)
        data = change(data)
        val tmp = File(ctx.filesDir, "data.json.tmp")
        tmp.writeText(data.toJson().toString())
        tmp.renameTo(File(ctx.filesDir, "data.json"))
    }
}
