package com.stillune.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun ContentPanel(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Surface(modifier, shape = Corners.Panel, color = AppSurface, border = BorderStroke(1.dp, PanelBorder)) {
        Box(content = content)
    }
}

@Composable
internal fun TabStrip(labels: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalAlignment = Alignment.CenterVertically) {
        labels.forEach { label ->
            val active = label == selected
            Box(Modifier.heightIn(min = Space.touch).clip(Corners.Control)
                .background(if (active) SelectedSurface else Color.Transparent)
                .selectable(active, role = Role.Tab, onClick = { onSelect(label) })
                .padding(horizontal = Space.m, vertical = Space.s), contentAlignment = Alignment.Center) {
                Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (active) InkAccent else InkMuted)
            }
        }
    }
}
