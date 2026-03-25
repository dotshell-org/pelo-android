package com.pelotcl.app.specific.data.mapper

import android.util.Log
import com.pelotcl.app.generic.data.model.StopCollection
import com.pelotcl.app.generic.data.model.StopFeature
import com.pelotcl.app.generic.data.model.StopGeometry
import com.pelotcl.app.generic.data.model.StopProperties
import com.pelotcl.app.specific.data.model.LyonStopCollection
import com.pelotcl.app.specific.data.model.LyonStopFeature

/**
 * Mapper to convert between Lyon-specific stop models and generic models
 */
object StopMapper {

    /**
     * Convert Lyon-specific stop properties to generic properties
     */
    fun mapToGeneric(properties: com.pelotcl.app.specific.data.model.LyonStopProperties): StopProperties {
        // Debug logging to understand what the WFS API is returning
        val desserteRaw = properties.desserte
        val desserteArretRaw = properties.desserteArret
        
        Log.d("StopMapper", "Stop ${properties.stopName} (gid=${properties.gid}): desserte='${desserteRaw}', desserteArret='${desserteArretRaw}'")

        // Ensure desserte is never empty by using a fallback value
        val desserteValue = (desserteRaw ?: desserteArretRaw).orEmpty()
        val finalDesserte = if (desserteValue.isNotBlank()) desserteValue else "UNKNOWN"
        
        Log.d("StopMapper", "  -> final desserte: '$finalDesserte'")

        return StopProperties(
            id = properties.gid,
            nom = properties.stopName,
            desserte = finalDesserte,
            ascenseur = false, // Default value
            escalator = false, // Default value
            gid = properties.gid,
            lastUpdate = null,
            lastUpdateFme = null,
            adresse = null,
            localiseFaceAAdresse = false,
            commune = properties.city,
            insee = properties.inseeCode,
            zone = null
        )
    }

    /**
     * Convert Lyon-specific stop feature to generic feature
     */
    fun mapToGeneric(feature: LyonStopFeature): StopFeature {
        return StopFeature(
            type = feature.type,
            id = feature.id,
            geometry = StopGeometry(
                type = feature.geometry.type,
                coordinates = feature.geometry.coordinates
            ),
            geometryName = feature.geometryName,
            properties = mapToGeneric(feature.properties),
            bbox = feature.bbox
        )
    }

    /**
     * Convert Lyon-specific stop collection to generic collection
     */
    fun mapToGeneric(collection: LyonStopCollection): StopCollection {
        return StopCollection(
            type = collection.type,
            features = collection.features.map { mapToGeneric(it) },
            totalFeatures = collection.totalFeatures,
            numberMatched = collection.numberMatched,
            numberReturned = collection.numberReturned,
            timeStamp = collection.timeStamp,
            crs = collection.crs?.let {
                com.pelotcl.app.generic.data.model.CRS(
                    type = it.type,
                    properties = com.pelotcl.app.generic.data.model.CRSProperties(
                        name = it.properties.name
                    )
                )
            },
            bbox = collection.bbox
        )
    }
}
