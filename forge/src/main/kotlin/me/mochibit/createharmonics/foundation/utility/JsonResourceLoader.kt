package me.mochibit.createharmonics.foundation.utility

import com.google.gson.Gson
import com.google.gson.JsonElement
import me.mochibit.createharmonics.foundation.err
import java.io.BufferedReader
import java.io.InputStreamReader

object JsonResourceLoader {
    private val gson = Gson()

    fun loadJsonResource(path: String): JsonElement? {
        return try {
            val inputStream = JsonResourceLoader::class.java.classLoader.getResourceAsStream(path)
            if (inputStream == null) {
                "Could not find resource: $path".err()
                return null
            }

            val reader = BufferedReader(InputStreamReader(inputStream))
            val json = gson.fromJson(reader, JsonElement::class.java)
            reader.close()
            json
        } catch (e: Exception) {
            "Error loading JSON resource $path: ${e.message}".err()
            null
        }
    }
}
