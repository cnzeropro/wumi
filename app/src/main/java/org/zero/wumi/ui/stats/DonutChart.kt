package org.zero.wumi.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt
import org.zero.wumi.data.local.dao.CategorySum
import org.zero.wumi.util.Money

/**
 * 环形图几何纯函数（无 Android 依赖，可 JVM 单测）。
 * 角度体系与 Compose [Canvas] 的 drawArc 一致：-90°（12 点钟）为起点，顺时针为正，单位度。
 */
object DonutMath {

    /**
     * 扇区角度分配：每个非零切片一段（起始角，扫过角）。
     * 0 值扇区跳过不占角；[total] <= 0 或无非零切片返回空列表；各段扫过角之和恰为 360°。
     */
    fun angles(total: Long, parts: List<Long>): List<Pair<Float, Float>> {
        if (total <= 0L) return emptyList()
        var start = -90f
        val out = ArrayList<Pair<Float, Float>>(parts.size)
        for (p in parts) {
            if (p <= 0L) continue
            val sweep = p.toFloat() / total * 360f
            out += start to sweep
            start += sweep
        }
        return out
    }

    /**
     * 点击命中反解：[angle] 为绘制绝对角（度，与 drawArc 同体系），归一化到自 -90° 起顺时针的 [0,360)
     * 后线性扫描累加角定位扇区，返回原始序号；0 值扇区不占角不可命中。
     * 仅一个非零扇区时无判别意义（整环即它），返回 null；无法命中亦返回 null。
     */
    fun sliceAt(angle: Float, parts: List<Long>): Int? {
        val total = parts.sum()
        if (total <= 0L || parts.count { it > 0L } < 2) return null
        val norm = ((angle + 90f) % 360f + 360f) % 360f
        var acc = 0f
        for ((i, p) in parts.withIndex()) {
            if (p <= 0L) continue
            val sweep = p.toFloat() / total * 360f
            if (norm >= acc && norm < acc + sweep) return i
            acc += sweep
        }
        return null
    }
}

/** 环形图 12 色切片序列：由品牌米金种子色 HSL 色相旋转派生（hue += i*30），保持米金和谐。 */
object WumiPalette {
    private const val SEED_HUE = 41.8f   // 0xFFC8A24B 的色相
    private const val SATURATION = 0.52f
    private const val LIGHTNESS = 0.56f

    /** 12 色序列，供 DonutChart 切片着色（超过 12 个分类时按序取模复用）。 */
    fun slices(): List<Color> = List(12) { i ->
        Color.hsl(hue = (SEED_HUE + i * 30f) % 360f, saturation = SATURATION, lightness = LIGHTNESS)
    }
}

/** 环形图默认尺寸与环宽。 */
private val DonutSize = 220.dp
private val DonutStrokeWidth = 28.dp
/** 选中扇区加粗量（向两侧扩展，半径侧预留该空间避免裁剪）。 */
private val SelectedExtraStroke = 4.dp

/**
 * 自绘环形图：逐扇 drawArc，中心显 [centerText]；点击环带反解角度回调 [onSelect]（点中心/环外回调 null）。
 * [slices] 为空时显示灰色整环与「暂无数据」。选中扇区加粗 [SelectedExtraStroke] 高亮。
 */
@Composable
fun DonutChart(
    slices: List<CategorySum>,
    colors: List<Color>,
    centerText: String,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
) {
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    val strokePx = with(LocalDensity.current) { DonutStrokeWidth.toPx() }
    val extraPx = with(LocalDensity.current) { SelectedExtraStroke.toPx() }
    // angles 跳过 0 值扇区，这里同步记录「第 n 段角度 → 原始切片序号」的映射供绘制与选中比对
    val parts = remember(slices) { slices.map { it.total } }
    val segIndices = remember(parts) {
        val total = parts.sum()
        ArrayList<Int>().also { out ->
            if (total > 0L) parts.forEachIndexed { i, p -> if (p > 0L) out += i }
        }
    }
    val segments = remember(parts) { DonutMath.angles(parts.sum(), parts) }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(DonutSize)) {
        Canvas(
            modifier = Modifier
                .size(DonutSize)
                .pointerInput(parts) {
                    detectTapGestures { offset ->
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val dx = offset.x - cx
                        val dy = offset.y - cy
                        val dist = sqrt(dx * dx + dy * dy)
                        val outerR = min(size.width, size.height) / 2f - strokePx / 2f - extraPx / 2f
                        val innerR = outerR - strokePx
                        if (dist >= innerR && dist <= outerR + extraPx) {
                            // 屏幕 atan2：y 向下、顺时针为正，恰与 drawArc 起角体系一致
                            val deg = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                            onSelect(DonutMath.sliceAt(deg, parts))
                        } else {
                            onSelect(null)
                        }
                    }
                },
        ) {
            // 半径向内让出加粗余量，选中扇区外扩后仍贴画布边不裁剪
            val outer = min(size.width, size.height) / 2f
            val radius = outer - strokePx / 2f - extraPx / 2f
            val arcSize = Size(radius * 2f, radius * 2f)
            val topLeft = Offset(outer - radius, outer - radius)
            if (slices.isEmpty()) {
                drawArc(
                    color = emptyColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx),
                )
            } else {
                segments.forEachIndexed { seg, (start, sweep) ->
                    val raw = segIndices[seg]
                    val width = if (selectedIndex == raw) strokePx + extraPx else strokePx
                    drawArc(
                        color = colors[raw % colors.size],
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = width),
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (slices.isEmpty()) "暂无数据" else centerText,
                style = MaterialTheme.typography.headlineSmall,
            )
        }
    }
}

/**
 * 分类排行列表：每行「emoji 名称｜¥合计 (占比%)｜进度条」，与环形图共用 [selectedIndex] 双向高亮。
 * 占比 = total/合计；进度条进度 = total/最大 total（最大项拉满，视觉区分度更高）。
 */
@Composable
fun CategoryRankList(
    slices: List<CategorySum>,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
) {
    val totalSum = slices.sumOf { it.total }
    val maxTotal = slices.maxOfOrNull { it.total } ?: 0L
    val highlight = MaterialTheme.colorScheme.secondaryContainer
    Column {
        slices.forEachIndexed { i, s ->
            val selected = selectedIndex == i
            val pct = if (totalSum > 0L) (s.total * 100 / totalSum).toInt() else 0
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) highlight else Color.Transparent)
                    .clickable { onSelect(if (selected) null else i) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.emoji ?: "❓", fontSize = 20.sp)
                    Spacer(Modifier.size(8.dp))
                    Text(s.name ?: "未分类", modifier = Modifier.weight(1f))
                    Text("${Money.format(s.total, "CNY")} ($pct%)")
                }
                Spacer(Modifier.size(4.dp))
                LinearProgressIndicator(
                    progress = { if (maxTotal > 0L) s.total.toFloat() / maxTotal else 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
