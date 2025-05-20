package com.example.todolist

import android.content.Context
import com.google.gson.Gson
import java.io.FileNotFoundException

object StorageHelper {
    private const val FILE_NAME = "app_data.json"

    fun saveAppData(context: Context, data: AppData) {
        val json = Gson().toJson(data)
        context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).use {
            it.write(json.toByteArray())
        }
    }

    fun loadAppData(context: Context): AppData {
        return try {
            val json = context.openFileInput(FILE_NAME).bufferedReader().use { it.readText() }
            Gson().fromJson(json, AppData::class.java)
        } catch (e: FileNotFoundException) {
            // Plik jeszcze nie istnieje — zwracamy dane domyślne
            AppData(
                tasks = emptyList(),
                categories = listOf("Szkoła", "Praca", "Zakupy", "Inne")
            )
        } catch (e: Exception) {
            e.printStackTrace()
            AppData(emptyList(), listOf("Szkoła", "Praca", "Zakupy", "Inne"))
        }
    }
}
