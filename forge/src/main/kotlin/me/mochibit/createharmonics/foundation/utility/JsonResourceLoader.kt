package me.mochibit.createharmonics.foundation.utility

import com.google.gson.Gson
import com.google.gson.JsonElement
import me.mochibit.createharmonics.Logger
import java.io.BufferedReader
import java.io.InputStreamReader

object JsonResourceLoader {
    private val gson = Gson()

    fun loadJsonResource(path: String): JsonElement? {
        return try {
            val inputStream = JsonResourceLoader::class.java.classLoader.getResourceAsStream(path)
            if (inputStream == null) {
                Logger.err("Could not find resource: $path")
                return null
            }

            val reader = BufferedReader(InputStreamReader(inputStream))
            val json = gson.fromJson(reader, JsonElement::class.java)
            reader.close()
            json
        } catch (e: Exception) {
            Logger.err("Error loading JSON resource $path: ${e.message}")
            null
        }
    }
}
