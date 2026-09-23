package com.callflow.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

val PremiumShape = RoundedCornerShape(16.dp)
val PremiumBorderStroke = BorderStroke(1.dp, Color(0xFF242E40))

@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = PremiumShape,
    border: BorderStroke? = PremiumBorderStroke,
    content: @Composable () -> Unit
) = Card(
    modifier = modifier,
    shape = shape,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = border,
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
) { content() }

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) = Row(
    Modifier.fillMaxWidth().padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = 8.dp)) {
        Box(
            Modifier
                .padding(end = 8.dp)
                .size(width = 3.5.dp, height = 16.dp)
                .background(Indigo, RoundedCornerShape(2.dp))
        )
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
    action?.let {
        if (onAction != null) TextButton(onClick = onAction) { Text(it, color = Indigo, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) }
        else Text(it, modifier = Modifier.weight(1f), color = Indigo, style = MaterialTheme.typography.labelLarge, textAlign = androidx.compose.ui.text.style.TextAlign.End, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun KpiCard(label: String, value: String, accent: Color, modifier: Modifier = Modifier) = PremiumCard(modifier) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .background(accent.copy(alpha = .15f), RoundedCornerShape(99.dp))
                .border(1.dp, accent.copy(alpha = .25f), RoundedCornerShape(99.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(label, color = accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun ActivityChart(values: List<Float>, modifier: Modifier = Modifier, labels: List<String> = listOf("M", "T", "W", "T", "F", "S", "S"), chartHeight: androidx.compose.ui.unit.Dp = 140.dp) {
    val safe = values.ifEmpty { listOf(0f) }
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(chartHeight)) {
            val max = safe.maxOrNull()?.coerceAtLeast(1f) ?: 1f
            val step = if (safe.size <= 1) size.width else size.width / (safe.size - 1)
            val points = safe.mapIndexed { index, value -> Offset(index * step, size.height - (value / max * size.height * .80f) - 8.dp.toPx()) }
            val area = Path().apply {
                moveTo(points.first().x, size.height)
                points.forEach { lineTo(it.x, it.y) }
                lineTo(points.last().x, size.height)
                close()
            }
            drawPath(
                area,
                brush = Brush.verticalGradient(
                    colors = listOf(Indigo.copy(alpha = 0.28f), Color.Transparent),
                    startY = 0f,
                    endY = size.height
                )
            )
            val line = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(line, Indigo, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            points.forEach {
                drawCircle(Indigo.copy(alpha = 0.35f), 7.dp.toPx(), it)
                drawCircle(Indigo, 4.dp.toPx(), it)
                drawCircle(Color.White, 2.dp.toPx(), it)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = Slate) }
        }
    }
}

@Composable
fun DonutChart(percent: Int, modifier: Modifier = Modifier) = Box(modifier, contentAlignment = Alignment.Center) {
    Canvas(Modifier.matchParentSize()) {
        drawArc(Color(0xFF1E2536), -90f, 360f, false, style = Stroke(12.dp.toPx(), cap = StrokeCap.Round))
        drawArc(Emerald, -90f, 360f * percent.coerceIn(0, 100) / 100f, false, style = Stroke(12.dp.toPx(), cap = StrokeCap.Round))
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$percent%", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text("connected", style = MaterialTheme.typography.labelSmall, color = Slate)
    }
}

@Composable
fun BarChart(values: List<Float>, modifier: Modifier = Modifier) = Canvas(modifier) {
    val max = values.maxOrNull()?.coerceAtLeast(1f) ?: 1f
    val peak = values.indices.maxByOrNull { values[it] }
    val slot = size.width / values.size.coerceAtLeast(1)
    values.forEachIndexed { index, value ->
        val barHeight = (size.height * value / max).coerceAtLeast(4.dp.toPx())
        val isPeak = index == peak && value > 0
        drawRoundRect(
            brush = if (isPeak) Brush.verticalGradient(
                listOf(Indigo, Indigo.copy(alpha = 0.7f)),
                startY = size.height - barHeight,
                endY = size.height
            ) else Brush.verticalGradient(
                listOf(Indigo.copy(alpha = 0.35f), Indigo.copy(alpha = 0.15f)),
                startY = size.height - barHeight,
                endY = size.height
            ),
            topLeft = Offset(index * slot + slot * .18f, size.height - barHeight),
            size = Size(slot * .64f, barHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(7.dp.toPx(), 7.dp.toPx()),
        )
    }
}
