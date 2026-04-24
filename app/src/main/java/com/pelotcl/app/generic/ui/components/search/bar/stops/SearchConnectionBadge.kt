package com.pelotcl.app.generic.ui.components.search.bar.stops

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pelotcl.app.R
import com.pelotcl.app.generic.utils.BusIconHelper

@Composable
fun SearchConnectionBadge(lineName: String, sizeDp: Int = 30) {
    val context = LocalContext.current
    val resourceId = BusIconHelper.getResourceIdForLine(context, lineName)

    if (resourceId != 0) {
        Image(
            painter = painterResource(id = resourceId),
            contentDescription = stringResource(R.string.line_icon, lineName),
            modifier = Modifier.size(sizeDp.dp)
        )
    }
}