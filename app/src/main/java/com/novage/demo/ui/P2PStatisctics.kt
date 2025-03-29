package com.novage.demo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun InfoCard(
    title: String,
    content: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = content, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun P2PStatistics(
    totalHttpDownloaded: Double,
    totalP2PDownloaded: Double,
    totalP2PUploaded: Double,
    activePeers: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        InfoCard(
            title = "Downloaded through HTTP",
            content = "${"%.2f".format(totalHttpDownloaded)} MiB",
            icon = Icons.Default.Info
        )
        InfoCard(
            title = "Downloaded through P2P",
            content = "${"%.2f".format(totalP2PDownloaded)} MiB",
            icon = Icons.Default.Info
        )
        InfoCard(
            title = "Uploaded through P2P",
            content = "${"%.2f".format(totalP2PUploaded)} MiB",
            icon = Icons.Default.Info
        )
        InfoCard(
            title = "Active Peers",
            content = activePeers.toString(),
            icon = Icons.Default.Info
        )
    }
}
