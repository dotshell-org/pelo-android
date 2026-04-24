package com.pelotcl.app.generic.data.models

/**
 * API response containing all stop alerts
 * Structure: { "stopId1": { "karma_below_threshold": [...], "karma_at_or_above_threshold": [...] }, ... }
 */
typealias UserStopAlertsResponse = Map<String, StopAlertsStatus>