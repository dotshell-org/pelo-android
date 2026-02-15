package com.pelotcl.app.data.model

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Représente une alerte de trafic pour une ligne de transport
 */
@Immutable
@Serializable
data class TrafficAlert(
    @SerializedName("cause")
    @SerialName("cause")
    val cause: String,

    @SerializedName("debut")
    @SerialName("debut")
    val startDate: String,

    @SerializedName("fin")
    @SerialName("fin")
    val endDate: String,

    @SerializedName("last_update_fme")
    @SerialName("last_update_fme")
    val lastUpdate: String,

    @SerializedName("ligne_cli")
    @SerialName("ligne_cli")
    val lineCode: String,

    @SerializedName("ligne_com")
    @SerialName("ligne_com")
    val lineName: String,

    @SerializedName("listeobjet")
    @SerialName("listeobjet")
    val objectList: String,

    @SerializedName("message")
    @SerialName("message")
    val message: String,

    @SerializedName("mode")
    @SerialName("mode")
    val mode: String,

    @SerializedName("n")
    @SerialName("n")
    val alertNumber: Int,

    @SerializedName("niveauseverite")
    @SerialName("niveauseverite")
    val severityLevel: Int,

    @SerializedName("titre")
    @SerialName("titre")
    val title: String,

    @SerializedName("type")
    @SerialName("type")
    val alertType: String,

    @SerializedName("typeobjet")
    @SerialName("typeobjet")
    val objectType: String,

    @SerializedName("typeseverite")
    @SerialName("typeseverite")
    val severityType: String
)

/**
 * Représente la réponse API pour les alertes de trafic
 */
data class TrafficAlertsResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("data")
    val alerts: List<TrafficAlert>,
    
    @SerializedName("timestamp")
    val timestamp: String,
    
    @SerializedName("lastUpdated")
    val lastUpdated: String
)

/**
 * Représente la réponse API pour le statut du trafic
 */
data class TrafficStatusResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("data")
    val data: TrafficStatusData,
    
    @SerializedName("timestamp")
    val timestamp: String,
    
    @SerializedName("lastUpdated")
    val lastUpdated: String
)

data class TrafficStatusData(
    @SerializedName("count")
    val alertCount: Int,
    
    @SerializedName("lastUpdated")
    val lastUpdated: String
)

/**
 * Enumération des types de sévérité des alertes
 */
enum class AlertSeverity(val level: Int, val displayName: String, val color: Long) {
    SIGNIFICANT_DELAYS(20, "Retards significatifs", 0xFFFF5722), // Orange
    OTHER_EFFECT(30, "Autres effets", 0xFF2196F3), // Blue
    INFORMATION(40, "Information", 0xFF4CAF50), // Green
    UNKNOWN(0, "Inconnu", 0xFF9E9E9E); // Gray

    companion object {
        fun fromSeverityType(severityType: String, severityLevel: Int): AlertSeverity {
            return when (severityType) {
                "SIGNIFICANT_DELAYS" -> SIGNIFICANT_DELAYS
                "OTHER_EFFECT" -> OTHER_EFFECT
                "INFORMATION" -> INFORMATION
                else -> values().firstOrNull { it.level == severityLevel } ?: UNKNOWN
            }
        }
    }
}