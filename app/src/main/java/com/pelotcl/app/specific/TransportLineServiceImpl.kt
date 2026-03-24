package com.pelotcl.app.specific

import com.pelotcl.app.generic.data.model.FeatureCollection
import com.pelotcl.app.generic.data.model.StopCollection
import com.pelotcl.app.generic.data.network.TransportLineService
import com.pelotcl.app.specific.data.network.LyonTransportLineApi
import com.pelotcl.app.specific.data.network.LyonTransportLineApiWrapper
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
    
    private val lyonApi = retrofit.create(LyonTransportLineApi::class.java)
    private val apiService = LyonTransportLineApiWrapper(lyonApi)
    
    override suspend fun getMetroLines(): FeatureCollection {
        return apiService.getMetroLines(
            "WFS", "2.0.0", "GetFeature", "sytral:tcl_sytral.tcllignemf_2_0_0",
            "application/json", "EPSG:4171", 0, "gid", 1000
        )
    }
    
    override suspend fun getTramLines(): FeatureCollection {
        return apiService.getTramLines(
            "WFS", "2.0.0", "GetFeature", "sytral:tcl_sytral.tcllignetram_2_0_0",
            "application/json", "EPSG:4171", 0, "gid", 1000
        )
    }
    
    override suspend fun getBusLines(): FeatureCollection {
        return apiService.getBusLines(
            "WFS", "2.0.0", "GetFeature", "sytral:tcl_sytral.tcllignebus_2_0_0",
            "application/json", "EPSG:4171", 0, "gid", 10000, null
        )
    }
    
    override suspend fun getBusLineByName(lineName: String): FeatureCollection {
        val cqlFilter = "nomligne = '$lineName'"
        return apiService.getBusLineByName(
            "WFS", "2.0.0", "GetFeature", "sytral:tcl_sytral.tcllignebus_2_0_0",
            "application/json", "EPSG:4171", "gid", 10, cqlFilter
        )
    }
    
    override suspend fun getNavigoneLines(): FeatureCollection {
        return apiService.getNavigoneLines(
            "WFS", "2.0.0", "GetFeature", "sytral:tcl_sytral.tcllignefluv",
            "application/json", "EPSG:4171", 0, "gid", 1000
        )
    }
    
    override suspend fun getTrambusLines(): FeatureCollection {
        return apiService.getTrambusLines(
            "WFS", "2.0.0", "GetFeature", "sytral:tcl_sytral.tcllignebus_2_0_0",
            "application/json", "EPSG:4171", 0, "gid", 1000, "ligne LIKE 'TB%'"
        )
    }
    
    override suspend fun getTransportStops(): StopCollection {
        return apiService.getTransportStops(
            "WFS", "2.0.0", "GetFeature", "sytral:tcl_sytral.tclarret",
            "application/json", "EPSG:4171", 0, "gid", 10000
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

}