package com.pelotcl.app.specific.data.mapper

import com.pelotcl.app.generic.data.models.StopCollection
import com.pelotcl.app.generic.data.models.StopFeature
import com.pelotcl.app.generic.data.models.StopGeometry
import com.pelotcl.app.generic.data.models.StopProperties
import com.pelotcl.app.generic.data.repository.itinerary.RaptorRepository
import com.pelotcl.app.specific.data.model.LyonStopCollection
import com.pelotcl.app.specific.data.model.LyonStopFeature

/**
 * Mapper to convert between Lyon-specific stop models and generic models.
 * Handles the case where WFS stop names are missing by enriching from Raptor/GTFS.
 */
object StopMapper {

    /**
     * Enrich stop names using Raptor/GTFS data by matching gid to Raptor stop id.
     * This should be called after loading stops to fill in missing names.
     * 
     * @param stops List of stops to enrich
     * @param raptorRepository Raptor repository to get stop names from
     * @return List of stops with enriched names
     */
    fun enrichStopNamesFromRaptor(
        stops: List<StopFeature>,
        raptorRepository: RaptorRepository
    ): List<StopFeature> {
        return stops.map { stop ->
            // Only update if the name is a fallback (contains "Arrondissement" or "Arret")
            val isFallbackName = stop.properties.nom.contains("Arrondissement") ||
                                 stop.properties.nom.contains("Arret ")
            
            if (isFallbackName) {
                // Try to find the stop in Raptor by gid
                // Raptor stops have an id field that should match WFS gid
                val raptorStopName = try {
                    raptorRepository.getStopNameById(stop.properties.gid)
                } catch (e: Exception) {
                    null
                }
                
                if (raptorStopName != null && raptorStopName.isNotBlank()) {
                    stop.copy(properties = stop.properties.copy(nom = raptorStopName))
                } else {
                    stop
                }
            } else {
                stop
            }
        }
    }

    /**
     * Convert Lyon-specific stop properties to generic properties.
     * Uses fallback naming if WFS names are missing.
     */
    fun mapToGeneric(properties: com.pelotcl.app.specific.data.model.LyonStopProperties): StopProperties {
        val desserteRaw = properties.desserte
        val desserteArretRaw = properties.desserteArret
        
        // Ensure desserte is never empty by using a fallback value
        val desserteValue = (desserteRaw ?: desserteArretRaw).orEmpty()
        val finalDesserte = if (desserteValue.isNotBlank()) desserteValue else "UNKNOWN"
        
        // Try to get stop name from WFS first
        // Note: Gson may inject null into non-null Kotlin fields, so we use safe calls
        @Suppress("USELESS_ELVIS")
        var finalNom: String = properties.stopName ?: ""
        
        // If still empty, use a temporary placeholder
        // The real name will be enriched later from Raptor
        if (finalNom.isBlank()) {
            finalNom = "Arret ${properties.gid}"
        }

        return StopProperties(
            id = properties.gid,
            nom = finalNom,
            desserte = finalDesserte,
            ascenseur = false,
            escalator = false,
            gid = properties.gid,
            lastUpdate = null,
            lastUpdateFme = null,
            adresse = null,
            localiseFaceAAdresse = false,
            commune = properties.city ?: "",
            insee = properties.inseeCode ?: "",
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
                com.pelotcl.app.generic.data.models.CRS(
                    type = it.type,
                    properties = com.pelotcl.app.generic.data.models.CRSProperties(
                        name = it.properties.name
                    )
                )
            },
            bbox = collection.bbox
        )
    }
}
