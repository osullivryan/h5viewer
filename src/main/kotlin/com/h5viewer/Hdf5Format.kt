package com.h5viewer

import io.jhdf.api.Dataset
import java.lang.reflect.Array as JArray

/** Formatting helpers shared by the tree, info header, attributes and table cells. */
object Hdf5Format {

    private const val PER_LEVEL = 100
    private const val MAX_STRING = 8000

    /** e.g. `(100 × 4)` or `scalar`. */
    fun shape(dims: IntArray): String =
        if (dims.isEmpty()) "scalar" else dims.joinToString(" × ", "(", ")")

    /** Short summary shown next to a dataset in the tree, e.g. `(100 × 4) float`. */
    fun shapeAndType(ds: Dataset): String {
        val type = runCatching { ds.javaType?.simpleName }.getOrNull() ?: "?"
        val scalar = runCatching { ds.isScalar || ds.dimensions.isEmpty() }.getOrDefault(false)
        return if (scalar) "scalar $type" else "${shape(ds.dimensions)} $type"
    }

    fun humanBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB", "PB")
        var value = bytes.toDouble()
        var unit = -1
        while (value >= 1024 && unit < units.size - 1) {
            value /= 1024
            unit++
        }
        return String.format("%.2f %s", value, units[unit])
    }

    /** Renders a (possibly nested array) value to a bounded, readable string. */
    fun formatValue(value: Any?): String {
        val text = formatInternal(value)
        return if (text.length > MAX_STRING) text.take(MAX_STRING) + "…" else text
    }

    /** Renders a single table cell; keeps scalars verbatim, truncates arrays. */
    fun formatCell(value: Any?): String = when {
        value == null -> ""
        value.javaClass.isArray -> formatInternal(value).let {
            if (it.length > 500) it.take(500) + "…" else it
        }
        else -> value.toString()
    }

    private fun formatInternal(value: Any?): String {
        if (value == null) return ""
        if (!value.javaClass.isArray) return value.toString()
        val length = JArray.getLength(value)
        val shown = minOf(length, PER_LEVEL)
        val sb = StringBuilder("[")
        for (i in 0 until shown) {
            if (i > 0) sb.append(", ")
            sb.append(formatInternal(JArray.get(value, i)))
        }
        if (length > PER_LEVEL) sb.append(", … ($length)")
        sb.append("]")
        return sb.toString()
    }

    fun escapeHtml(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}

/** Reflection helpers for walking the nested Java arrays that jHDF returns. */
object Arr {

    /** Nesting depth of an array object (0 for a non-array/scalar). */
    fun depth(value: Any?): Int {
        var depth = 0
        var current: Any? = value
        while (current != null && current.javaClass.isArray) {
            depth++
            current = if (JArray.getLength(current) > 0) JArray.get(current, 0) else null
        }
        return depth
    }

    /** Element at the given multidimensional index, or null if out of range. */
    fun element(array: Any?, index: IntArray): Any? {
        var current: Any? = array
        for (i in index) {
            if (current == null || !current.javaClass.isArray) return current
            val length = JArray.getLength(current)
            if (i < 0 || i >= length) return null
            current = JArray.get(current, i)
        }
        return current
    }
}
