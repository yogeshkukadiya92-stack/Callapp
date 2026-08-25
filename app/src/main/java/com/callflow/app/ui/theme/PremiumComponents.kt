package com.callflow.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

val PremiumShape = RoundedCornerShape(20.dp)

@Composable
fun PremiumCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) = Card(
    modifier = modifier, shape = PremiumShape,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
) { content() }

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) = Row(
    Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
) {
    Text(title, style = MaterialTheme.typography.titleLarge)
    action?.let {
        if (onAction != null) TextButton(onClick = onAction) { Text(it, color = Indigo, style = MaterialTheme.typography.labelLarge) }
        else Text(it, color = Indigo, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun KpiCard(label: String, value: String, accent: Color, modifier: Modifier = Modifier) = PremiumCard(modifier) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.background(accent.copy(alpha = .12f), RoundedCornerShape(99.dp)).padding(horizontal = 9.dp, vertical = 4.dp)) {
            Text(label, color = accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ActivityChart(values: List<Float>, modifier: Modifier = Modifier, labels: List<String> = listOf("M", "T", "W", "T", "F", "S", "S")) {
    val safe = values.ifEmpty { listOf(0f) }
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val max = safe.maxOrNull()?.coerceAtLeast(1f) ?: 1f
            val step = if (safe.size <= 1) size.width else size.width / (safe.size - 1)
            val points = safe.mapIndexed { index, value -> Offset(index * step, size.height - (value / max * size.height * .82f) - 8.dp.toPx()) }
            val area = Path().apply { moveTo(points.first().x, size.height); points.forEach { lineTo(it.x, it.y) }; lineTo(points.last().x, size.height); close() }
            drawPath(area, Indigo.copy(alpha = .10f))
            val line = Path().apply { moveTo(points.first().x, points.first().y); points.drop(1).forEach { lineTo(it.x, it.y) } }
            drawPath(line, Indigo, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            points.forEach { drawCircle(Indigo, 4.dp.toPx(), it); drawCircle(Color.White, 2.dp.toPx(), it) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = Slate) }
        }
    }
}

@Composable
fun DonutChart(percent: Int, modifier: Modifier = Modifier) = Box(modifier, contentAlignment = Alignment.Center) {
    Canvas(Modifier.matchParentSize()) {
        drawArc(IndigoSoft, -90f, 360f, false, style = Stroke(12.dp.toPx(), cap = StrokeCap.Round))
        drawArc(Emerald, -90f, 360f * percent.coerceIn(0, 100) / 100f, false, style = Stroke(12.dp.toPx(), cap = StrokeCap.Round))
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$percent%", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("connected", style = MaterialTheme.typography.labelSmall, color = Slate)
    }
}

@Composable
fun BarChart(values: List<Float>, modifier: Modifier = Modifier) = Canvas(modifier) {
    val max = values.maxOrNull()?.coerceAtLeast(1f) ?: 1f
    val peak = values.indices.maxByOrNull { values[it] }
    val slot = size.width / values.size.coerceAtLeast(1)
    values.forEachIndexed { index, value ->
        val barHeight = size.height * value / max
        drawRoundRect(
            color = if (index == peak) Indigo else Indigo.copy(alpha = .28f),
            topLeft = Offset(index * slot + slot * .18f, size.height - barHeight), size = Size(slot * .64f, barHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(7.dp.toPx(), 7.dp.toPx()),
        )
    }
}
