package com.fieldpin.data.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.fieldpin.core.AppJson
import com.fieldpin.domain.model.Site
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File

/** Writes a site as a GeoJSON FeatureCollection and returns a shareable content:// Uri. */
class GeoJsonExporter(private val context: Context) {

    fun toGeoJson(site: Site): String {
        val ring = site.boundary + site.boundary.first() // GeoJSON rings are explicitly closed
        val json = buildJsonObject {
            put("type", "FeatureCollection")
            putJsonArray("features") {
                add(
                    buildJsonObject {
                        put("type", "Feature")
                        putJsonObject("properties") {
                            put("id", site.id)
                            put("name", site.name)
                            put("category", site.category.label)
                            put("status", site.status.label)
                            put("areaSqm", site.areaSqm)
                            put("perimeterM", site.perimeterM)
                        }
                        putJsonObject("geometry") {
                            put("type", "Polygon")
                            putJsonArray("coordinates") {
                                add(
                                    buildJsonArray {
                                        ring.forEach { p ->
                                            add(buildJsonArray { add(p.lng); add(p.lat) }) // [lng, lat]
                                        }
                                    }
                                )
                            }
                        }
                    }
                )
            }
        }
        return AppJson.json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), json)
    }

    fun export(site: Site): Uri {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeName = site.name.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').ifEmpty { "site" }
        val file = File(dir, "$safeName.geojson")
        file.writeText(toGeoJson(site))
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
