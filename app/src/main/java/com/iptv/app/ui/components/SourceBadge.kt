package com.iptv.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iptv.app.core.metadata.MetadataSource

@Composable
fun SourceBadge(
    source: MetadataSource = MetadataSource.AUTO,
    rating: Double? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (badgeBgColor, badgeTextColor, badgeLabel) = when (source) {
        MetadataSource.IMDB -> Triple(Color(0xFFF5C518), Color(0xFF000000), "IMDb")
        MetadataSource.TRAKT -> Triple(Color(0xFFED1C24), Color(0xFFFFFFFF), "Trakt")
        MetadataSource.SRATIM -> Triple(Color(0xFF169DFF), Color(0xFFFFFFFF), "סרטים")
        MetadataSource.TVDB -> Triple(Color(0xFF388E3C), Color(0xFFFFFFFF), "TVDB")
        MetadataSource.AUTO -> Triple(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, "INFO")
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(badgeBgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = badgeLabel,
                color = badgeTextColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )

            if (rating != null && rating > 0.0) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = badgeTextColor,
                    modifier = Modifier.size(10.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = String.format(java.util.Locale.US, "%.1f", rating),
                    color = badgeTextColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
