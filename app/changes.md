# Performance Optimizations – PlanScreen MapLibre

## Summary of Changes

This document details the optimizations made to the display of transport stops on the MapLibre map.

---

## 1. GeoJSON Cache for Stops

### Before
- The GeoJSON for stops was recalculated every time the map was displayed
- Unnecessary repeated serialization operations

### After
- The GeoJSON is cached in `TransportViewModel` via `_stopsGeoJsonCache`
- Automatic cache invalidation when data is reloaded

### Performance Gain
- Avoids repeated serialization of ~14,000 stops
- Reduces map loading time during repeated navigation

---

## 2. Bitmap Icon Cache

### Before
```kotlin
// Each icon was loaded every time
val bitmap = loadBitmapFromResources(context, R.drawable.icon_stop)
style.addImage("stop-icon", bitmap)
```

### After
```kotlin
// Cache at the composable level
val iconBitmapCache = remember { mutableMapOf<String, Bitmap>() }

// Reuse bitmaps
val bitmap = iconBitmapCache.getOrPut(iconId) {
    loadBitmapFromResources(context, resourceId)
}
```

### Performance Gain
- Icons loaded once per session
- Reduced memory allocations and GC workload
- ~10-15 different icons loaded once instead of multiple times

---

## 3. GeoJSON Update Without Source Recreation

### Before
```kotlin
// Complete removal and recreation
style.removeLayer("line-stops-circles")
style.removeSource("line-stops")
style.addSource(GeoJsonSource("line-stops", FeatureCollection.fromJson(geoJson)))
style.addLayer(CircleLayer("line-stops-circles", "line-stops"))
```

### After
```kotlin
// In-place data update
val existingSource = style.getSourceAs<GeoJsonSource>("line-stops")
if (existingSource != null) {
    existingSource.setGeoJson(FeatureCollection.fromJson(geoJson))
} else {
    // Create only if necessary
    style.addSource(GeoJsonSource("line-stops", FeatureCollection.fromJson(geoJson)))
    style.addLayer(CircleLayer("line-stops-circles", "line-stops"))
}
```

### Performance Gain
- Avoids destruction/recreation of native MapLibre objects
- Smoother updates when changing the selected line
- Reduced GPU operations

---

## 4. Layer Creation Only for Used Slots

### Before
```kotlin
// Systematic creation of 51 layers (slots -25 to 25)
for (idx in -25..25) {
    style.addLayer(SymbolLayer("all-stops-$idx", "all-stops").apply {
        iconOffset = arrayOf(0f, idx * 13f)
        // ...
    })
}
```

### After
```kotlin
// Collect only actually used slots
val usedSlots = mutableSetOf<Int>()
stops.forEach { stop ->
    stop.stopsPerLine.forEach { (_, slot) ->
        usedSlots.add(slot)
    }
}

// Create only necessary layers
usedSlots.forEach { idx ->
    style.addLayer(SymbolLayer("all-stops-$idx", "all-stops").apply {
        iconOffset = arrayOf(0f, idx * 13f)
        // ...
    })
}
```

### Performance Gain
- Typical reduction from 51 layers to ~10-15 layers (depending on data)
- Less GPU rendering work
- Reduced memory usage by MapLibre

---

## 5. Attempt to Consolidate Layers (Not Retained)

### Tested Approach
Using a dynamic MapLibre expression to calculate the offset:
```kotlin
Expression.raw("""["literal", [0, ["*", ["get", "slot"], 13]]]""")
```

### Result
**Failure** – MapLibre does not support nested arrays in `Expression.raw()` for `iconOffset`.

### Retained Alternative
The multi-slot approach was retained as it is the only method compatible with MapLibre for having different offsets per feature.

---

## Summary Table

| Optimization | Memory Impact | CPU Impact | GPU Impact |
|--------------|---------------|------------|------------|
| GeoJSON Cache | ⬇️ Medium | ⬇️ High | - |
| Bitmap Cache | ⬇️ Medium | ⬇️ Medium | - |
| setGeoJson() | ⬇️ Low | ⬇️ Medium | ⬇️ Medium |
| Only Used Slots | ⬇️ High | ⬇️ Medium | ⬇️ High |

---

## Technical Notes

### MapLibre Limitation
`iconOffset` requires an array `[x, y]` that cannot be dynamically generated via an expression. This is a known limitation of the MapLibre Android SDK.

### Compatibility
These optimizations are compatible with:
- MapLibre Android SDK
- Jetpack Compose
- Kotlin Coroutines

### Modified Files
- `app/src/main/java/com/pelotcl/app/ui/screens/PlanScreen.kt`
- `app/src/main/java/com/pelotcl/app/viewmodel/TransportViewModel.kt`
