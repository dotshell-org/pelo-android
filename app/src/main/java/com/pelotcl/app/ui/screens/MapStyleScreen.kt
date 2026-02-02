package com.pelotcl.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Satellite
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pelotcl.app.data.repository.MapStyle
import com.pelotcl.app.data.repository.MapStyleCategory
import com.pelotcl.app.data.repository.MapStyleRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapStyleScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mapStyleRepository = remember { MapStyleRepository(context) }
    var selectedStyle by remember { mutableStateOf(mapStyleRepository.getSelectedStyle()) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Fond de carte",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = Color.White,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black
                )
            )
        },
        containerColor = Color.Black
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            
            // Standard maps category
            MapStyleCategorySection(
                category = MapStyleCategory.STANDARD,
                icon = Icons.Default.Map,
                styles = MapStyle.getByCategory(MapStyleCategory.STANDARD),
                selectedStyle = selectedStyle,
                onStyleSelected = { style ->
                    selectedStyle = style
                    mapStyleRepository.saveSelectedStyle(style)
                }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Satellite category
            MapStyleCategorySection(
                category = MapStyleCategory.SATELLITE,
                icon = Icons.Default.Satellite,
                styles = MapStyle.getByCategory(MapStyleCategory.SATELLITE),
                selectedStyle = selectedStyle,
                onStyleSelected = { style ->
                    selectedStyle = style
                    mapStyleRepository.saveSelectedStyle(style)
                }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Information text
            Text(
                text = "Les fonds de carte sont fournis par OpenFreeMap et OpenMapTiles. " +
                        "La vue satellite utilise ESRI World Imagery.",
                color = Color.Gray,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MapStyleCategorySection(
    category: MapStyleCategory,
    icon: ImageVector,
    styles: List<MapStyle>,
    selectedStyle: MapStyle,
    onStyleSelected: (MapStyle) -> Unit
) {
    Column {
        // Category header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = category.displayName,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        
        // Style options card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1C1C1E)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column {
                styles.forEachIndexed { index, style ->
                    MapStyleItem(
                        style = style,
                        isSelected = style == selectedStyle,
                        onClick = { onStyleSelected(style) }
                    )
                    
                    // Add divider between items (but not after the last one)
                    if (index < styles.size - 1) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 56.dp)
                                .height(0.5.dp)
                                .background(Color(0xFF3A3A3C))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MapStyleItem(
    style: MapStyle,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Selection indicator
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .border(
                    width = if (isSelected) 0.dp else 2.dp,
                    color = if (isSelected) Color.Transparent else Color(0xFF8E8E93),
                    shape = CircleShape
                )
                .background(
                    if (isSelected) Color(0xFFE60000) else Color.Transparent,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Sélectionné",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Style name
        Text(
            text = style.displayName,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
        )
    }
}
