package com.hemant.myapplication.ui

import android.content.Context
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hemant.myapplication.model.ActionEvent
import com.hemant.myapplication.model.ComponentNode
import com.hemant.myapplication.model.ComponentValue
import com.hemant.myapplication.model.RuntimeSnapshot
import com.hemant.myapplication.model.WidgetDocument
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ceil

@Composable
fun GenWidgetSurface(
    document: WidgetDocument,
    surfaceId: String,
    runtimeSnapshot: RuntimeSnapshot?,
    modifier: Modifier = Modifier,
    onAction: (ActionEvent) -> Unit,
) {
    val surface = document.surfaces[surfaceId]
    if (surface == null) {
        Log.e("HEMANT_DBG", "GenWidgetSurface error: No surface found for surfaceId='$surfaceId'. Available surfaces: ${document.surfaces.keys}")
        FallbackSurface("No surface for $surfaceId", modifier)
        return
    }
    val values = remember(document, runtimeSnapshot) {
        val snapValues = runtimeSnapshot?.values() ?: JSONObject()
        val mockData = document.previewMockData
        if (mockData != null) {
            val merged = JSONObject(mockData.toString())
            val snapModel = snapValues.optJSONObject("model")
            if (snapModel != null) {
                val mergedModel = merged.optJSONObject("model") ?: JSONObject()
                val keys = snapModel.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    mergedModel.put(key, snapModel.opt(key))
                }
                merged.put("model", mergedModel)
            }
            merged
        } else {
            snapValues
        }
    }
    val context = remember(document, surface, values) {
        ComposeRenderContext(document, surface, values, onAction)
    }
    val root = surface.root()
    Log.d("HEMANT_DBG", "Rendering GenWidgetSurface: surfaceId=$surfaceId, componentsCount=${surface.components.size}, rootId=${surface.rootComponentId}, rootExists=${root != null}")
    if (root == null) {
        Log.e("HEMANT_DBG", "GenWidgetSurface error: No root component found for surfaceId='$surfaceId'. Root component ID is '${surface.rootComponentId}'")
        FallbackSurface("No root component for $surfaceId", modifier)
        return
    }
    Box(modifier = modifier.fillMaxSize()) {
        GenWidgetComponent(root, context, Modifier.fillMaxSize(), isRoot = true)
    }
}

class ComposeRenderContext(
    val document: WidgetDocument,
    val surface: com.hemant.myapplication.model.Surface,
    val runtimeValues: JSONObject,
    val onAction: (ActionEvent) -> Unit,
)

@Composable
private fun GenWidgetComponent(
    node: ComponentNode,
    context: ComposeRenderContext,
    modifier: Modifier = Modifier,
    isRoot: Boolean = false,
) {
    Log.d("HEMANT_DBG", "GenWidgetComponent: rendering id=${node.id}, component=${node.component}, childrenCount=${node.children.size}")
    val bindings = ComposeBindingResolver(context.runtimeValues)
    if (!bindings.visible(node.fields["visibleWhen"])) {
        Log.d("HEMANT_DBG", "GenWidgetComponent: component id=${node.id} skipped via visibleWhen")
        return
    }
    
    when (node.component) {
        "Column" -> ComposeColumn(node, context, modifier, isRoot)
        "Row" -> ComposeRow(node, context, modifier, isRoot)
        "Text" -> ComposeText(node, context, modifier)
        "Icon" -> ComposeIcon(node, context, modifier)
        "IconButton" -> ComposeIconButton(node, context, modifier)
        "InsightList" -> ComposeInsightList(node, context, modifier)
        "EmptyState" -> ComposeEmptyState(node, context, modifier, "Refresh")
        "Spacer" -> Spacer(modifier.then(Modifier.size(componentSize(node.fieldText("size"), 10.dp))))
        "Divider" -> Box(modifier.then(Modifier.fillMaxWidth().height(1.dp).background(BorderColor)))
        else -> {
            Log.e("HEMANT_DBG", "GenWidgetSurface error: Unsupported component type '${node.component}' for id '${node.id}'")
            FallbackSurface("Unsupported component: ${node.component}", modifier)
        }
    }
}

@Composable
private fun ComposeColumn(node: ComponentNode, context: ComposeRenderContext, modifier: Modifier, isRoot: Boolean) {
    val gap = tokenGap(node.fieldText("gap"))
    Column(
        modifier = modifier
            .then(surfaceModifier(node, isRoot))
            .clickableIfAction(node, context),
        verticalArrangement = columnArrangement(node, isRoot, gap),
    ) {
        node.children.forEach { childId ->
            context.surface.components[childId]?.let { GenWidgetComponent(it, context, Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun ComposeRow(node: ComponentNode, context: ComposeRenderContext, modifier: Modifier, isRoot: Boolean) {
    val gap = tokenGap(node.fieldText("gap"))
    Row(
        modifier = modifier
            .then(surfaceModifier(node, isRoot))
            .clickableIfAction(node, context),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowChildren(node, context)
    }
}

@Composable
private fun RowChildren(node: ComponentNode, context: ComposeRenderContext) {
    val align = node.fieldText("align")
    val horizontalAlignment = when (align) {
        "center" -> Arrangement.Center
        "end" -> Arrangement.End
        "spaceBetween" -> Arrangement.SpaceBetween
        else -> Arrangement.spacedBy(tokenGap(node.fieldText("gap")))
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = horizontalAlignment,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        node.children.forEach { childId ->
            context.surface.components[childId]?.let { child ->
                val childModifier = when (align) {
                    "center", "end", "spaceBetween" -> Modifier
                    else -> Modifier.weight(1f, fill = false)
                }
                GenWidgetComponent(child, context, childModifier)
            }
        }
    }
}

@Composable
private fun ComposeText(node: ComponentNode, context: ComposeRenderContext, modifier: Modifier) {
    val bindings = ComposeBindingResolver(context.runtimeValues)
    val text = bindings.text(node.fields["text"]).ifBlank { bindings.text(node.fields["label"]) }
    Text(
        text = text,
        color = textColor(node.fieldText("color")),
        fontSize = textSize(node.fieldText("style")),
        fontWeight = textWeight(node.fieldText("style")),
        maxLines = node.fieldNumber("maxLines")?.toInt() ?: 3,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.clickableIfAction(node, context),
    )
}

@Composable
private fun ComposeIcon(node: ComponentNode, context: ComposeRenderContext, modifier: Modifier) {
    val bindings = ComposeBindingResolver(context.runtimeValues)
    val resolver = ComposeIconResolver()
    val iconRef = resolver.iconRef(node.fields["icon"], bindings)
    val resource = resolver.resourceFor(LocalContext.current, node.fields["icon"], bindings)
    val iconModifier = modifier
        .size(componentSize(node.fieldText("size"), 24.dp))
        .clickableIfAction(node, context)
        
    if (resolver.isWeatherBitmapRef(iconRef)) {
        Image(
            painter = painterResource(resource),
            contentDescription = null,
            modifier = iconModifier,
            contentScale = ContentScale.Fit,
        )
    } else {
        Icon(
            painter = painterResource(resource),
            contentDescription = null,
            tint = iconTint(node),
            modifier = iconModifier,
        )
    }
}

@Composable
private fun ComposeIconButton(node: ComponentNode, context: ComposeRenderContext, modifier: Modifier) {
    val bindings = ComposeBindingResolver(context.runtimeValues)
    val resolver = ComposeIconResolver()
    val resource = resolver.resourceFor(LocalContext.current, node.fields["icon"], bindings, "ms:info")
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (node.fieldText("background") == "transparent") Color.Transparent else ActionBg)
            .clickableIfAction(node, context),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(resource),
            contentDescription = null,
            tint = TextPrimary,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun ComposeInsightList(node: ComponentNode, context: ComposeRenderContext, modifier: Modifier) {
    val bindings = ComposeBindingResolver(context.runtimeValues)
    val sourceArray = bindings.array(node.fields["source"])
    val itemsList = node.fields["items"] as? ComponentValue.ListValue
    
    val presentation = node.fieldText("presentation")
    val horizontal = presentation.equals("chips", ignoreCase = true) ||
            node.fieldText("layout").equals("horizontal", ignoreCase = true)
            
    if (sourceArray != null) {
        val visibleItems = runtimeInsightItems(sourceArray)
            .take(node.fieldNumber("maxItems")?.toInt() ?: 5)
        if (horizontal) {
            val columns = chipColumns(node, visibleItems.size)
            Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                visibleItems.chunked(columns).forEach { rowItems ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowItems.forEach { item ->
                            RuntimeInsightChip(item, Modifier.weight(1f))
                        }
                        repeat(columns - rowItems.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
            Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                visibleItems.forEach { item ->
                    RuntimeInsightRow(item)
                }
            }
        }
        return
    }
    
    // Fallback if raw list items are supplied
    if (itemsList != null) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsList.values.forEach { raw ->
                val item = raw as? ComponentValue.ObjectValue ?: return@forEach
                if (!bindings.visible(item.values["visibleWhen"])) return@forEach
                val title = bindings.text(item.values["title"])
                val value = bindings.text(item.values["value"])
                val iconVal = item.values["icon"]
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ActionBg).padding(10.dp)
                ) {
                    val iconNode = ComponentNode("insightIcon", "Icon", mapOf("icon" to (iconVal ?: ComponentValue.Text("ms:info"))))
                    ComposeIcon(iconNode, context, Modifier.size(18.dp))
                    Text(title, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun RuntimeInsightChip(item: RuntimeInsightItem, modifier: Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(ActionBg)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RuntimeInsightIcon(item.iconRef, Modifier.size(20.dp))
        Column(Modifier.weight(1f, fill = false)) {
            if (item.title.isNotBlank()) {
                Text(item.title, color = TextSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(item.value.ifBlank { item.title }, color = TextPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RuntimeInsightRow(item: RuntimeInsightItem) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ActionBg).padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), 
        verticalAlignment = Alignment.CenterVertically
    ) {
        RuntimeInsightIcon(item.iconRef, Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            if (item.title.isNotBlank()) {
                Text(item.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (item.subtitle.isNotBlank()) {
                Text(item.subtitle, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(item.value, color = AccentColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RuntimeInsightIcon(iconRef: String, modifier: Modifier) {
    val resolver = ComposeIconResolver()
    val resource = resolver.resourceForRef(iconRef)
    if (resolver.isWeatherBitmapRef(iconRef)) {
        Image(
            painter = painterResource(resource),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )
    } else {
        Icon(
            painter = painterResource(resource),
            contentDescription = null,
            tint = AccentColor,
            modifier = modifier,
        )
    }
}

private fun chipColumns(node: ComponentNode, itemCount: Int): Int {
    val requested = node.fieldNumber("columns")?.toInt()
    if (requested != null) {
        return requested.coerceIn(1, 4)
    }
    if (itemCount <= 0) return 1
    val targetRows = ceil(itemCount / 4.0).toInt().coerceAtLeast(1)
    return ceil(itemCount.toDouble() / targetRows.toDouble()).toInt().coerceIn(1, 4)
}

@Composable
private fun ComposeEmptyState(node: ComponentNode, context: ComposeRenderContext, modifier: Modifier, defaultActionLabel: String) {
    val bindings = ComposeBindingResolver(context.runtimeValues)
    val title = bindings.text(node.fields["title"])
    val message = bindings.text(node.fields["message"])
    val action = ComposeActionResolver().event(node.fields["action"])
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(surfaceModifier(node)),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        if (title.isNotBlank()) Text(title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (message.isNotBlank()) Text(message, color = TextSecondary, fontSize = 13.sp)
        if (action != null) {
            Button(
                onClick = { context.onAction(action) }, 
                colors = ButtonDefaults.buttonColors(containerColor = ActionBg, contentColor = TextPrimary)
            ) {
                Text(bindings.text(node.fields["actionLabel"]).ifBlank { defaultActionLabel })
            }
        }
    }
}

@Composable
private fun FallbackSurface(message: String, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), color = SurfaceColor, shape = RoundedCornerShape(18.dp)) {
        Box(Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
            Text(message, color = TextSecondary, fontSize = 13.sp)
        }
    }
}

private fun Modifier.clickableIfAction(node: ComponentNode, context: ComposeRenderContext): Modifier {
    val event = ComposeActionResolver().event(node.fields["action"]) ?: return this
    return clickable { context.onAction(event) }
}

private fun surfaceModifier(node: ComponentNode, isRoot: Boolean = false): Modifier {
    val padding = tokenPadding(node.fieldText("padding"))
    val radius = tokenRadius(node.fieldText("cornerRadius"))
    val hasSurfaceBackground = isRoot || node.fieldText("background") == "surface"
    val background = if (hasSurfaceBackground) SurfaceColor else Color.Transparent
    val shape = RoundedCornerShape(radius)
    return Modifier
        .clip(shape)
        .background(background)
        .then(if (isRoot) Modifier.border(1.dp, BorderColor, shape) else Modifier)
        .padding(padding)
}

private fun ComponentNode.fieldText(name: String): String = (fields[name] as? ComponentValue.Text)?.value.orEmpty()
private fun ComponentNode.fieldNumber(name: String): Double? = (fields[name] as? ComponentValue.Number)?.value

private fun tokenPadding(token: String): Dp = when (token) {
    "xs" -> 4.dp
    "sm" -> 8.dp
    "lg" -> 18.dp
    else -> 12.dp
}

private fun tokenGap(token: String): Dp = when (token) {
    "xs" -> 4.dp
    "sm" -> 8.dp
    "lg" -> 16.dp
    else -> 12.dp
}

private fun columnArrangement(node: ComponentNode, isRoot: Boolean, gap: Dp): Arrangement.Vertical {
    return when (node.fieldText("verticalAlign").ifBlank { node.fieldText("align") }) {
        "center" -> Arrangement.spacedBy(gap, Alignment.CenterVertically)
        "bottom", "end" -> Arrangement.spacedBy(gap, Alignment.Bottom)
        "spaceBetween" -> Arrangement.SpaceBetween
        else -> Arrangement.spacedBy(gap)
    }
}

private fun tokenRadius(token: String): Dp = when (token) {
    "sm" -> 8.dp
    "md" -> 12.dp
    "lg" -> 18.dp
    else -> 14.dp
}

private fun componentSize(token: String, fallback: Dp): Dp = when (token) {
    "xs" -> 14.dp
    "sm" -> 18.dp
    "md" -> 24.dp
    "lg" -> 34.dp
    "xl" -> 48.dp
    else -> fallback
}

private fun textSize(style: String): androidx.compose.ui.unit.TextUnit = when (style) {
    "displayMedium" -> 38.sp
    "displaySmall" -> 30.sp
    "titleLarge" -> 20.sp
    "titleMedium" -> 17.sp
    "bodySmall" -> 12.sp
    "labelSmall" -> 11.sp
    else -> 14.sp
}

private fun textWeight(style: String): FontWeight = 
    if (style.contains("title", ignoreCase = true)) FontWeight.Bold else FontWeight.Normal

private fun textColor(token: String): Color = when (token) {
    "secondary" -> TextSecondary
    "muted" -> TextMuted
    "warning" -> WarningColor
    "inverse" -> Color.White
    else -> TextPrimary
}

private fun iconTint(node: ComponentNode): Color = when (node.fieldText("color")) {
    "secondary" -> TextSecondary
    "muted" -> TextMuted
    "warning" -> WarningColor
    "inverse" -> Color.White
    else -> AccentColor
}

private data class RuntimeInsightItem(
    val title: String,
    val value: String,
    val subtitle: String,
    val iconRef: String,
)

private fun runtimeInsightItems(source: JSONArray): List<RuntimeInsightItem> {
    val items = ArrayList<RuntimeInsightItem>()
    for (i in 0 until source.length()) {
        val item = source.optJSONObject(i) ?: continue
        val title = item.optString("title", item.optString("label"))
        val value = item.optString("value", item.optString("text"))
        val subtitle = item.optString("subtitle")
        items.add(
            RuntimeInsightItem(
                title = title,
                value = value,
                subtitle = subtitle,
                iconRef = item.optString("icon", "ms:info"),
            ),
        )
    }
    return items
}

private val SurfaceColor = Color(0xFFF8FAFC)
private val TextPrimary = Color(0xFF0F172A)
private val TextSecondary = Color(0xFF475569)
private val TextMuted = Color(0xFF94A3B8)
private val BorderColor = Color(0xFFE2E8F0)
private val ActionBg = Color(0xFFF1F5F9)
private val AccentColor = Color(0xFF0284C7)
private val WarningColor = Color(0xFFEA580C)
