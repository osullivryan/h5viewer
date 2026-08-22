package com.h5viewer

import com.intellij.util.ui.UIUtil
import io.jhdf.HdfFile
import io.jhdf.api.Dataset
import io.jhdf.api.Group
import io.jhdf.api.Node
import java.awt.Component
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import java.lang.reflect.Array as JArray

/**
 * Table model for an N-dimensional array dataset.
 *
 * The last one or two axes are shown as the grid; any leading axes are fixed and
 * controlled from the UI. Only the currently visible window is read from disk,
 * via jHDF's hyperslab reads, so huge datasets stay responsive.
 */
class GridTableModel(private val dataset: Dataset) : AbstractTableModel() {

    val dims: IntArray = dataset.dimensions
    private val rank = dims.size
    private val displayAxes = if (rank >= 2) 2 else 1
    val fixedCount = rank - displayAxes
    private val rowAxis = rank - displayAxes
    private val colAxis = rank - 1
    private val leadIndex = IntArray(fixedCount)

    private val rowDim = dims[rowAxis]
    private val colDim = if (rank >= 2) dims[colAxis] else 1

    val colCount: Int = if (rank >= 2) minOf(colDim, MAX_COLS) else 1
    val rowsShown: Int = computeRowCount()
    private val rowTruncated = rowsShown < rowDim
    private val colTruncated = rank >= 2 && colCount < colDim

    private var data: Any? = null
    private var usedFallback = false
    private var dataDepth = 0

    init {
        loadSlice()
    }

    private fun computeRowCount(): Int {
        var rows = minOf(rowDim, MAX_ROWS)
        val cols = colCount.coerceAtLeast(1)
        if (rows.toLong() * cols > MAX_CELLS) {
            rows = maxOf(1, (MAX_CELLS / cols).toInt())
        }
        return rows
    }

    private fun loadSlice() {
        val offset = LongArray(rank)
        val slice = IntArray(rank)
        for (axis in 0 until rank) {
            when {
                axis < fixedCount -> {
                    offset[axis] = leadIndex[axis].toLong()
                    slice[axis] = 1
                }
                rank == 1 || axis == rowAxis -> {
                    offset[axis] = 0
                    slice[axis] = rowsShown
                }
                else -> {
                    offset[axis] = 0
                    slice[axis] = colCount
                }
            }
        }
        data = try {
            usedFallback = false
            dataset.getData(offset, slice)
        } catch (t: Throwable) {
            if (dataset.size <= MAX_FULL_ELEMENTS) {
                usedFallback = true
                dataset.data
            } else {
                throw IllegalStateException(
                    "Partial reads aren't supported for this dataset and it is too large " +
                        "to load fully (${dataset.size} elements).",
                    t,
                )
            }
        }
        dataDepth = Arr.depth(data)
    }

    fun setLead(axis: Int, value: Int) {
        if (axis in leadIndex.indices && leadIndex[axis] != value) {
            leadIndex[axis] = value
            if (!usedFallback) loadSlice()
            fireTableDataChanged()
        }
    }

    override fun getRowCount(): Int = rowsShown

    override fun getColumnCount(): Int = colCount + 1 // +1 for the index column

    override fun getColumnName(column: Int): String = when {
        column == 0 -> "#"
        rank >= 2 -> (column - 1).toString()
        else -> "value"
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
        if (columnIndex == 0) return rowIndex
        val dataCol = columnIndex - 1
        val full = IntArray(rank) { axis ->
            when {
                axis < fixedCount -> if (usedFallback) leadIndex[axis] else 0
                rank == 1 || axis == rowAxis -> rowIndex
                else -> dataCol
            }
        }
        val index = if (dataDepth in 1..rank) full.copyOfRange(rank - dataDepth, rank) else full
        return Arr.element(data, index)
    }

    fun truncationNote(): String? {
        if (!rowTruncated && !colTruncated) return null
        val sb = StringBuilder("Showing rows 0–${rowsShown - 1} of $rowDim")
        if (rank >= 2) sb.append(", cols 0–${colCount - 1} of $colDim")
        return sb.toString()
    }

    companion object {
        const val MAX_ROWS = 200_000
        const val MAX_COLS = 1024
        const val MAX_CELLS = 2_000_000L
        const val MAX_FULL_ELEMENTS = 20_000_000L
    }
}

/**
 * Table model for a compound dataset: one column per member field.
 *
 * [map] holds the records actually read (possibly a bounded prefix of the whole
 * dataset). [totalRows] is the true record count of the dataset, used only to
 * report truncation; pass a negative value when it equals what was read.
 */
class CompoundTableModel(map: Map<String, Any?>, totalRows: Long = -1) : AbstractTableModel() {

    private val columns: List<String> = map.keys.toList()
    private val values: List<Any?> = columns.map { map[it] }
    private val length: Int = values.maxOfOrNull { col ->
        if (col != null && col.javaClass.isArray) JArray.getLength(col) else 0
    } ?: 0
    val rows: Int = length
    private val total: Long = if (totalRows >= 0) totalRows else length.toLong()

    override fun getRowCount(): Int = rows

    override fun getColumnCount(): Int = columns.size + 1

    override fun getColumnName(column: Int): String =
        if (column == 0) "#" else columns[column - 1]

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
        if (columnIndex == 0) return rowIndex
        val col = values[columnIndex - 1] ?: return null
        if (!col.javaClass.isArray || rowIndex >= JArray.getLength(col)) return null
        return JArray.get(col, rowIndex)
    }

    fun truncationNote(): String? =
        if (total > rows) "Showing rows 0–${rows - 1} of $total" else null

    companion object {
        /** Upper bound on rows × columns read into memory for a compound dataset. */
        const val MAX_CELLS = 2_000_000L

        /** Max records to read fully when hyperslab (partial) reads aren't available. */
        const val MAX_ROWS_FULL = 200_000L
    }
}

/** Minimal read-only table model over pre-computed string rows. */
class ReadOnlyTableModel(
    private val columnNames: Array<String>,
    private val data: List<Array<Any?>>,
) : AbstractTableModel() {
    override fun getRowCount(): Int = data.size
    override fun getColumnCount(): Int = columnNames.size
    override fun getColumnName(column: Int): String = columnNames[column]
    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? = data[rowIndex][columnIndex]
}

/** Formats every cell through [Hdf5Format] and right-aligns numbers and indices. */
class Hdf5CellRenderer(private val hasIndexColumn: Boolean = true) : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int,
    ): Component {
        val component = super.getTableCellRendererComponent(
            table, Hdf5Format.formatCell(value), isSelected, hasFocus, row, column,
        )
        val indexCell = hasIndexColumn && column == 0
        horizontalAlignment = if (indexCell || value is Number) SwingConstants.RIGHT else SwingConstants.LEFT
        if (indexCell && !isSelected) {
            foreground = UIUtil.getContextHelpForeground()
        }
        return component
    }
}

/** Comparator for table cell values: numbers sort numerically, everything else naturally. */
object Hdf5Compare {
    fun compare(a: Any?, b: Any?): Int {
        if (a === b) return 0
        if (a == null) return -1
        if (b == null) return 1
        if (a is Number && b is Number) return a.toDouble().compareTo(b.toDouble())
        if (a is Boolean && b is Boolean) return a.compareTo(b)
        if (a is Comparable<*> && a.javaClass == b.javaClass) {
            @Suppress("UNCHECKED_CAST")
            return (a as Comparable<Any>).compareTo(b)
        }
        return a.toString().compareTo(b.toString())
    }
}

/** What the background loader produces for the currently selected node. */
sealed class Payload {
    class Grid(val model: GridTableModel) : Payload()
    class Compound(val model: CompoundTableModel) : Payload()
    class Scalar(val text: String) : Payload()
    class Message(val text: String) : Payload()
    class Error(val text: String) : Payload()
}

/** Reads (off the EDT) whatever is needed to render the selected node's data tab. */
object Hdf5Data {

    fun buildPayload(item: Hdf5NodeItem): Payload {
        val ds = item.dataset ?: return Payload.Message(
            if (item.kind == Hdf5NodeItem.Kind.GROUP) {
                "Group — no tabular data. See the Attributes tab."
            } else {
                "No data view is available for this node."
            },
        )
        if (ds.isEmpty) return Payload.Message("Empty dataset (null dataspace — no data).")

        if (ds.isCompound) return buildCompoundPayload(ds)

        if (ds.dimensions.isEmpty() || ds.isScalar) {
            return Payload.Scalar(Hdf5Format.formatValue(ds.data))
        }
        return Payload.Grid(GridTableModel(ds))
    }

    /**
     * Builds a compound payload without ever reading the whole dataset when it is
     * large. For a 1-D compound we probe the column count with a single-record
     * hyperslab read, then read only enough leading records to stay under
     * [CompoundTableModel.MAX_CELLS]. If hyperslab reads aren't supported for the
     * dataset's layout we fall back to a full read only when it is clearly small,
     * otherwise we show a message instead of risking an out-of-memory error.
     */
    private fun buildCompoundPayload(ds: Dataset): Payload {
        val dims = ds.dimensions
        val total: Long = if (dims.isEmpty()) 1L else dims[0].toLong()

        if (dims.size == 1) {
            val probe = readCompound(ds, 1)
            if (probe != null) {
                val columns = probe.size.coerceAtLeast(1)
                val cap = maxOf(1L, CompoundTableModel.MAX_CELLS / columns)
                val rowsToRead = minOf(total, cap).toInt()
                val data = if (rowsToRead <= 1) probe else readCompound(ds, rowsToRead) ?: probe
                return Payload.Compound(CompoundTableModel(data, total))
            }
        }

        // Hyperslab reads unavailable (or a multidimensional compound): only read
        // fully when the dataset is clearly small.
        if (ds.size <= CompoundTableModel.MAX_ROWS_FULL) {
            val data = ds.data
            if (data is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                return Payload.Compound(CompoundTableModel(data as Map<String, Any?>))
            }
        }

        return Payload.Message(
            "Compound dataset is too large to display safely (${ds.size} records) and " +
                "partial reading isn't supported for its storage layout.",
        )
    }

    /** Reads the first [rows] records of a 1-D compound as a column map, or null if unsupported. */
    private fun readCompound(ds: Dataset, rows: Int): Map<String, Any?>? {
        val n = minOf(ds.dimensions[0], rows)
        val raw = runCatching { ds.getData(longArrayOf(0), intArrayOf(n)) }.getOrNull()
        @Suppress("UNCHECKED_CAST")
        return raw as? Map<String, Any?>
    }
}

/** Builds the HTML shown in the info header for the selected node. */
object Hdf5Info {

    fun html(item: Hdf5NodeItem): String {
        val node = item.node
        val sb = StringBuilder("<html><table cellpadding='1'>")
        row(sb, "Path", node.path)
        when (item.kind) {
            Hdf5NodeItem.Kind.GROUP -> {
                row(sb, "Kind", if (node is HdfFile) "File (root group)" else "Group")
                row(sb, "Children", childCount(node).toString())
            }
            Hdf5NodeItem.Kind.DATASET -> describeDataset(sb, item.dataset!!)
            Hdf5NodeItem.Kind.OTHER -> row(sb, "Kind", node.type?.toString() ?: "Node")
        }
        row(sb, "Attributes", attributeCount(node).toString())
        sb.append("</table></html>")
        return sb.toString()
    }

    private fun describeDataset(sb: StringBuilder, ds: Dataset) {
        row(sb, "Kind", "Dataset")
        val dims = ds.dimensions
        row(sb, "Shape", if (dims.isEmpty()) "scalar" else dims.joinToString(" × "))
        row(sb, "Data type", ds.javaType?.simpleName ?: "?")
        row(sb, "Elements", ds.size.toString())
        row(sb, "Size", Hdf5Format.humanBytes(ds.sizeInBytes))
        runCatching { ds.dataLayout?.toString() }.getOrNull()?.let { row(sb, "Layout", it) }
        val flags = buildList {
            if (ds.isCompound) add("compound")
            if (ds.isVariableLength) add("variable-length")
        }
        if (flags.isNotEmpty()) row(sb, "Flags", flags.joinToString(", "))
    }

    private fun childCount(node: Node): Int =
        runCatching { (node as Group).children.size }.getOrDefault(0)

    private fun attributeCount(node: Node): Int =
        runCatching { node.attributes.size }.getOrDefault(0)

    private fun row(sb: StringBuilder, key: String, value: String) {
        sb.append("<tr><td><b>")
            .append(Hdf5Format.escapeHtml(key))
            .append("</b></td><td>")
            .append(Hdf5Format.escapeHtml(value))
            .append("</td></tr>")
    }
}

/** Builds the attributes table model for any node. */
object Hdf5Attributes {

    private val COLUMNS = arrayOf("Name", "Value", "Type", "Shape")

    fun empty(): ReadOnlyTableModel = ReadOnlyTableModel(COLUMNS, emptyList())

    fun model(node: Node): ReadOnlyTableModel {
        val attributes = runCatching { node.attributes }.getOrDefault(emptyMap())
        val rows = attributes.values.map { attribute ->
            val value = runCatching { Hdf5Format.formatValue(attribute.data) }.getOrElse { "<error>" }
            val type = runCatching { attribute.javaType?.simpleName }.getOrNull() ?: "?"
            val shape = runCatching {
                if (attribute.dimensions.isEmpty()) "scalar" else attribute.dimensions.joinToString(" × ")
            }.getOrDefault("")
            arrayOf<Any?>(attribute.name, value, type, shape)
        }
        return ReadOnlyTableModel(COLUMNS, rows)
    }
}
