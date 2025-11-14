package com.pelotcl.app.data.gtfs

/**
 * Modèles de données GTFS
 */

data class GtfsRoute(
    val routeId: String,
    val agencyId: String,
    val routeShortName: String,
    val routeLongName: String,
    val routeDesc: String,
    val routeType: Int,
    val routeColor: String,
    val routeTextColor: String
)

data class GtfsTrip(
    val routeId: String,
    val serviceId: String,
    val tripId: String,
    val tripHeadsign: String,
    val tripShortName: String,
    val directionId: Int,
    val shapeId: String,
    val wheelchairAccessible: Int,
    val bikesAllowed: Int
)

data class GtfsStopTime(
    val tripId: String,
    val arrivalTime: String,
    val departureTime: String,
    val stopId: String,
    val stopSequence: Int,
    val stopHeadsign: String,
    val pickupType: Int,
    val dropOffType: Int,
    val shapeDistTraveled: Double
)

data class GtfsStop(
    val stopId: String,
    val stopCode: String,
    val stopName: String,
    val stopLat: Double,
    val stopLon: Double,
    val zoneId: String,
    val locationType: Int,
    val parentStation: String,
    val stopTimezone: String,
    val wheelchairBoarding: Int
)

/**
 * Représente un passage à un arrêt avec les informations de ligne et destination
 */
data class StopDeparture(
    val lineName: String,
    val destination: String,
    val departureTime: String, // HH:MM:SS format
    val routeColor: String,
    val routeTextColor: String
)

/**
 * Représente un arrêt sur une ligne avec son ordre et informations
 */
data class LineStopInfo(
    val stopId: String,
    val stopName: String,
    val stopSequence: Int,
    val isCurrentStop: Boolean = false,
    val connections: List<String> = emptyList() // Liste des lignes de métro/funiculaire en correspondance
)
