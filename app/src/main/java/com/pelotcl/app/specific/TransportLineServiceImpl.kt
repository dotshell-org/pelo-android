package com.pelotcl.app.specific

import com.google.gson.JsonObject
import com.pelotcl.app.generic.data.model.FeatureCollection
import com.pelotcl.app.generic.data.model.StopCollection
import com.pelotcl.app.generic.data.network.TransportLineService
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Lyon-specific implementation of TransportLineService
 * Handles line data operations for the Lyon transport network
 */
class TransportLineServiceImpl : TransportLineService {
    
    private val transportConfig = TransportConfigImpl
    private val retrofit = Retrofit.Builder()
        .baseUrl(transportConfig.baseUrl)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    private val apiService = retrofit.create(TransportApiImpl::class.java)
    
    override suspend fun getMetroLines(): FeatureCollection {
        return apiService.getMetroLines(
            service = "WFS",
            version = "2.0.0",
            request = "GetFeature",
            typename = "sytral:tcl_sytral.tcllignemf_2_0_0",
            outputFormat = "application/json",
            srsName = "EPSG:4171",
            startIndex = 0,
            sortBy = "gid",
            count = 1000
        )
    }
    
    override suspend fun getTramLines(): FeatureCollection {
        return apiService.getTramLines(
            service = "WFS",
            version = "2.0.0",
            request = "GetFeature",
            typename = "sytral:tcl_sytral.tcllignetram_2_0_0",
            outputFormat = "application/json",
            srsName = "EPSG:4171",
            startIndex = 0,
            sortBy = "gid",
            count = 1000
        )
    }
    
    override suspend fun getBusLines(): FeatureCollection {
        return apiService.getBusLines(
            service = "WFS",
            version = "2.0.0",
            request = "GetFeature",
            typename = "sytral:tcl_sytral.tcllignebus_2_0_0",
            outputFormat = "application/json",
            srsName = "EPSG:4171",
            startIndex = 0,
            sortBy = "gid",
            count = 10000,
            cqlFilter = null
        )
    }
    
    override suspend fun getBusLineByName(lineName: String): FeatureCollection {
        val cqlFilter = "nomligne = '$lineName'"
        return apiService.getBusLineByName(
            service = "WFS",
            version = "2.0.0",
            request = "GetFeature",
            typename = "sytral:tcl_sytral.tcllignebus_2_0_0",
            outputFormat = "application/json",
            srsName = "EPSG:4171",
            sortBy = "gid",
            count = 10,
            cqlFilter = cqlFilter
        )
    }
    
    override suspend fun getNavigoneLines(): FeatureCollection {
        return apiService.getNavigoneLines(
            service = "WFS",
            version = "2.0.0",
            request = "GetFeature",
            typename = "sytral:tcl_sytral.tcllignefluv",
            outputFormat = "application/json",
            srsName = "EPSG:4171",
            startIndex = 0,
            sortBy = "gid",
            count = 1000
        )
    }
    
    override suspend fun getTrambusLines(): FeatureCollection {
        return apiService.getTrambusLines(
            service = "WFS",
            version = "2.0.0",
            request = "GetFeature",
            typename = "sytral:tcl_sytral.tcllignebus_2_0_0",
            outputFormat = "application/json",
            srsName = "EPSG:4171",
            startIndex = 0,
            sortBy = "gid",
            count = 1000,
            cqlFilter = "ligne LIKE 'TB%'"
        )
    }
    
    override suspend fun getTransportStops(): StopCollection {
        return apiService.getTransportStops(
            service = "WFS",
            version = "2.0.0",
            request = "GetFeature",
            typename = "sytral:tcl_sytral.tclarret",
            outputFormat = "application/json",
            srsName = "EPSG:4171",
            startIndex = 0,
            sortBy = "gid",
            count = 10000
        )
    }
    
    override suspend fun getStrongLines(): FeatureCollection {
        // Lyon strong lines: Metro (A, B, C, D), Tram (T1, T2, T3, T4, T5, T6)
        val metroLines = getMetroLines()
        val tramLines = getTramLines()
        
        // Combine features from both collections
        val allFeatures = mutableListOf<com.pelotcl.app.generic.data.model.Feature>().apply {
            addAll(metroLines.features)
            addAll(tramLines.features)
        }
        
        return FeatureCollection(
            type = "FeatureCollection",
            features = allFeatures
        )
    }
    
    override suspend fun getLineGeometry(lineName: String): FeatureCollection {
        // Try to get the line by name from different types
        return when {
            lineName.startsWith("T") -> getTramLines() // Tram lines start with T
            lineName in listOf("A", "B", "C", "D") -> getMetroLines() // Metro lines
            else -> getBusLineByName(lineName) // Assume it's a bus line
        }
    }
    
    override suspend fun getLinesByType(type: String): FeatureCollection {
        return when (type.lowercase()) {
            "metro" -> getMetroLines()
            "tram" -> getTramLines()
            "bus" -> getBusLines()
            "navigone" -> getNavigoneLines()
            "trambus" -> getTrambusLines()
            else -> throw IllegalArgumentException("Unknown transport type: $type")
        }
    }
    
    /**
     * Get special line geometry (like Rhonexpress)
     * @return JsonObject containing the special line geometry
     */
    suspend fun getSpecialLineGeometry(): JsonObject {
        return apiService.getSpecialLineRaw(
            service = "WFS",
            version = "2.0.0",
            request = "GetFeature",
            typename = "sytral:tcl_sytral.tcllignebus_2_0_0",
            outputFormat = "application/json",
            srsName = "EPSG:4326",
            startIndex = 0,
            sortBy = "gid",
            count = 1000
        )
    }
}