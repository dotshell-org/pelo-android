package com.pelotcl.app.specific.data.mapper

import com.pelotcl.app.generic.data.model.Feature
import com.pelotcl.app.generic.data.model.FeatureCollection
import com.pelotcl.app.generic.data.model.Geometry
import com.pelotcl.app.generic.data.model.TransportLineProperties
import com.pelotcl.app.specific.data.model.LyonFeature
import com.pelotcl.app.specific.data.model.LyonFeatureCollection
import com.pelotcl.app.specific.data.model.LyonTransportLineProperties

/**
 * Mapper to convert between Lyon-specific transport line models and generic models
 */
object TransportLineMapper {

    /**
     * Convert Lyon-specific transport line properties to generic properties
     */
    fun mapToGeneric(properties: LyonTransportLineProperties): TransportLineProperties {
        return TransportLineProperties(
            lineName = properties.ligne,
            traceCode = properties.codeTrace,
            lineId = properties.codeLigne,
            traceType = properties.typeTrace,
            traceName = properties.nomTrace,
            direction = properties.sens,
            origin = properties.origine,
            destination = properties.destination,
            originName = properties.nomOrigine,
            destinationName = properties.nomDestination,
            transportType = properties.familleTransport,
            startDate = properties.dateDebut,
            endDate = properties.dateFin,
            lineTypeCode = properties.codeTypeLigne,
            lineTypeName = properties.nomTypeLigne,
            sortCode = properties.codeTriLigne,
            versionName = properties.nomVersion,
            lastUpdate = properties.lastUpdate,
            lastUpdateFme = properties.lastUpdateFme,
            gid = properties.gid,
            color = properties.couleur
        )
    }

    /**
     * Convert Lyon-specific feature to generic feature
     */
    fun mapToGeneric(feature: LyonFeature): Feature {
        return Feature(
            type = feature.type,
            id = feature.id,
            geometry = Geometry(
                type = feature.geometry.type,
                coordinates = feature.geometry.coordinates
            ),
            geometryName = feature.geometryName,
            properties = mapToGeneric(feature.properties),
            bbox = feature.bbox
        )
    }

    /**
     * Convert Lyon-specific feature collection to generic feature collection
     */
    fun mapToGeneric(collection: LyonFeatureCollection): FeatureCollection {
        return FeatureCollection(
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

    /**
     * Convert generic transport line properties to Lyon-specific properties
     */
    fun mapToLyon(properties: TransportLineProperties): LyonTransportLineProperties {
        return LyonTransportLineProperties(
            ligne = properties.lineName,
            codeTrace = properties.traceCode,
            codeLigne = properties.lineId,
            typeTrace = properties.traceType,
            nomTrace = properties.traceName,
            sens = properties.direction,
            origine = properties.origin,
            destination = properties.destination,
            nomOrigine = properties.originName,
            nomDestination = properties.destinationName,
            familleTransport = properties.transportType,
            dateDebut = properties.startDate,
            dateFin = properties.endDate,
            codeTypeLigne = properties.lineTypeCode,
            nomTypeLigne = properties.lineTypeName,
            codeTriLigne = properties.sortCode,
            nomVersion = properties.versionName,
            lastUpdate = properties.lastUpdate,
            lastUpdateFme = properties.lastUpdateFme,
            gid = properties.gid,
            couleur = properties.color
        )
    }
}
