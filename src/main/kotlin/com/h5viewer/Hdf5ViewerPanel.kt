package com.h5viewer

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.table.JBTable
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import io.jhdf.HdfFile
import io.jhdf.api.Group
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JComponent
import javax.swing.JSpinner
import javax.swing.JTable
import javax.swing.SpinnerNumberModel
import javax.swing.SwingConstants
import javax.swing.table.TableModel
import javax.swing.table.TableRowSorter
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

/**
 * The HDF5 viewer: a lazily-populated hierarchy tree on the left and, on the
 * right, an info header plus tabs for the selected node's data and attributes.
 *
 * All file I/O happens off the EDT; the UI shows a "Loading…" placeholder while
 * a background read is in flight, and stale reads are dropped via a generation
 * counter.
 */
class Hdf5ViewerPanel(private val file: VirtualFile) : JBPanel<Hdf5ViewerPanel>(BorderLayout()) {

    private val log = thisLogger()

    @Volatile private var hdfFile: HdfFile? = null
    @Volatile private var disposed = false

    private val tree = Tree()
    private val infoLabel = JBLabel()
    private val dataHolder = JBPanel<JBPanel<*>>(BorderLayout())
    private val attributesHolder = JBPanel<JBPanel<*>>(BorderLayout())
    private val loadGeneration = AtomicInteger(0)

    val preferredFocusedComponent: JComponent get() = tree

    init {
        buildUi()
        openAsync()
    }

    private fun buildUi() {
        tree.isRootVisible = true
        tree.showsRootHandles = true
        tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        tree.cellRenderer = Hdf5TreeCellRenderer()
        tree.model = DefaultTreeModel(DefaultMutableTreeNode("Loading…"))
        tree.addTreeSelectionListener { onTreeSelection() }

        infoLabel.verticalAlignment = SwingConstants.TOP
        infoLabel.border = JBUI.Borders.empty(6, 8)

        val tabs = JBTabbedPane()
        tabs.addTab("Data", dataHolder)
        tabs.addTab("Attributes", attributesHolder)

        val right = JBPanel<JBPanel<*>>(BorderLayout())
        right.add(infoLabel, BorderLayout.NORTH)
        right.add(tabs, BorderLayout.CENTER)

        val splitter = JBSplitter(false, 0.32f)
        splitter.splitterProportionKey = "h5viewer.splitter.proportion"
        splitter.firstComponent = ScrollPaneFactory.createScrollPane(tree)
        splitter.secondComponent = right

        add(splitter, BorderLayout.CENTER)
        setDataComponent(centerMessage("Select a dataset to view its contents."))
        setAttributes(Hdf5Attributes.empty())
    }

    private fun openAsync() {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val opened = HdfFile(File(file.path))
                val root = buildTree(opened)
                onEdt {
                    if (disposed) {
                        closeQuietly(opened)
                        return@onEdt
                    }
                    hdfFile = opened
                    tree.model = DefaultTreeModel(root)
                    val rootPath = TreePath(root)
                    tree.expandPath(rootPath)
                    tree.selectionPath = rootPath
                }
            } catch (t: Throwable) {
                log.warn("Failed to open HDF5 file ${file.path}", t)
                onEdt {
                    infoLabel.text = ""
                    setDataComponent(errorComponent("Failed to open HDF5 file:\n${t.message ?: t.javaClass.name}"))
                }
            }
        }
    }

    private fun buildTree(root: HdfFile): Hdf5TreeNode {
        val rootItem = Hdf5NodeItem(root, file.name, "", Hdf5NodeItem.Kind.GROUP)
        val rootNode = Hdf5TreeNode(rootItem)
        val visited = HashSet<Long>().apply { add(root.address) }
        addChildren(root, rootNode, visited, intArrayOf(0))
        return rootNode
    }

    private fun addChildren(group: Group, parent: Hdf5TreeNode, visited: MutableSet<Long>, count: IntArray) {
        val children = runCatching { group.children }.getOrNull() ?: return
        for ((name, child) in children) {
            if (count[0] >= MAX_NODES) return
            count[0]++
            val node = Hdf5TreeNode(makeItem(child, name))
            parent.add(node)
            if (child.isGroup && visited.add(child.address)) {
                addChildren(child as Group, node, visited, count)
            }
        }
    }

    private fun makeItem(node: io.jhdf.api.Node, name: String): Hdf5NodeItem = when {
        node.isGroup -> Hdf5NodeItem(node, name, "", Hdf5NodeItem.Kind.GROUP)
        node is io.jhdf.api.Dataset -> Hdf5NodeItem(
            node, name, runCatching { Hdf5Format.shapeAndType(node) }.getOrDefault(""),
            Hdf5NodeItem.Kind.DATASET,
        )
        else -> Hdf5NodeItem(node, name, "", Hdf5NodeItem.Kind.OTHER)
    }

    private fun onTreeSelection() {
        val item = (tree.lastSelectedPathComponent as? Hdf5TreeNode)?.item ?: return
        val generation = loadGeneration.incrementAndGet()
        setDataComponent(centerMessage("Loading…"))

        ApplicationManager.getApplication().executeOnPooledThread {
            if (disposed) return@executeOnPooledThread
            val info = runCatching { Hdf5Info.html(item) }
                .getOrElse { "<html>${Hdf5Format.escapeHtml(it.message ?: it.javaClass.name)}</html>" }
            val attributes = runCatching { Hdf5Attributes.model(item.node) }
                .getOrDefault(Hdf5Attributes.empty())
            val payload = runCatching { Hdf5Data.buildPayload(item) }
                .getOrElse { Payload.Error(it.message ?: it.javaClass.name) }

            onEdt {
                if (disposed || generation != loadGeneration.get()) return@onEdt
                infoLabel.text = info
                setAttributes(attributes)
                applyPayload(payload)
            }
        }
    }

    private fun applyPayload(payload: Payload) = when (payload) {
        is Payload.Grid -> setDataComponent(buildGridComponent(payload.model))
        is Payload.Compound -> setDataComponent(buildCompoundComponent(payload.model))
        is Payload.Scalar -> setDataComponent(scalarComponent(payload.text))
        is Payload.Message -> setDataComponent(centerMessage(payload.text))
        is Payload.Error -> setDataComponent(errorComponent(payload.text))
    }

    private fun buildGridComponent(model: GridTableModel): JComponent {
        val panel = JBPanel<JBPanel<*>>(BorderLayout())
        panel.add(JBScrollPane(buildTable(model, hasIndexColumn = true)), BorderLayout.CENTER)

        val top = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), JBUI.scale(4)))
        var hasTop = false
        if (model.fixedCount > 0) {
            top.add(JBLabel("Slice:"))
            for (axis in 0 until model.fixedCount) {
                top.add(JBLabel("dim$axis"))
                val spinner = JSpinner(SpinnerNumberModel(0, 0, model.dims[axis] - 1, 1))
                spinner.addChangeListener { model.setLead(axis, spinner.value as Int) }
                top.add(spinner)
            }
            hasTop = true
        }
        model.truncationNote()?.let {
            top.add(noteLabel(it))
            hasTop = true
        }
        if (hasTop) panel.add(top, BorderLayout.NORTH)
        return panel
    }

    private fun buildCompoundComponent(model: CompoundTableModel): JComponent {
        val panel = JBPanel<JBPanel<*>>(BorderLayout())
        panel.add(JBScrollPane(buildTable(model, hasIndexColumn = true)), BorderLayout.CENTER)
        model.truncationNote()?.let {
            val top = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), JBUI.scale(4)))
            top.add(noteLabel(it))
            panel.add(top, BorderLayout.NORTH)
        }
        return panel
    }

    private fun buildTable(model: TableModel, hasIndexColumn: Boolean): JBTable {
        val table = JBTable(model)
        table.autoResizeMode = JTable.AUTO_RESIZE_OFF
        table.setShowGrid(true)
        table.cellSelectionEnabled = true
        table.tableHeader.reorderingAllowed = false
        table.setDefaultRenderer(Any::class.java, Hdf5CellRenderer(hasIndexColumn))

        // Column sorting (numeric-aware) — click a header to sort, shift-click to add a level.
        val sorter = TableRowSorter(model)
        val comparator = Comparator<Any?> { a, b -> Hdf5Compare.compare(a, b) }
        for (column in 0 until model.columnCount) sorter.setComparator(column, comparator)
        table.rowSorter = sorter

        // Find-as-you-type: jump to matching cells.
        TableSpeedSearch.installOn(table)

        if (hasIndexColumn && table.columnModel.columnCount > 0) {
            table.columnModel.getColumn(0).apply {
                preferredWidth = JBUI.scale(64)
                maxWidth = JBUI.scale(120)
            }
        }
        return table
    }

    private fun setAttributes(model: ReadOnlyTableModel) {
        attributesHolder.removeAll()
        val content: JComponent =
            if (model.rowCount == 0) centerMessage("No attributes.")
            else JBScrollPane(buildTable(model, hasIndexColumn = false))
        attributesHolder.add(content, BorderLayout.CENTER)
        attributesHolder.revalidate()
        attributesHolder.repaint()
    }

    private fun setDataComponent(component: JComponent) {
        dataHolder.removeAll()
        dataHolder.add(component, BorderLayout.CENTER)
        dataHolder.revalidate()
        dataHolder.repaint()
    }

    private fun scalarComponent(text: String): JComponent {
        val area = JBTextArea(text).apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            border = JBUI.Borders.empty(8)
        }
        return JBScrollPane(area)
    }

    private fun centerMessage(text: String): JComponent = JBLabel(toHtml(text), SwingConstants.CENTER).apply {
        horizontalAlignment = SwingConstants.CENTER
        foreground = UIUtil.getInactiveTextColor()
    }

    private fun errorComponent(text: String): JComponent = JBLabel(toHtml(text), SwingConstants.CENTER).apply {
        horizontalAlignment = SwingConstants.CENTER
        icon = AllIcons.General.Error
        horizontalTextPosition = SwingConstants.RIGHT
    }

    private fun noteLabel(text: String): JBLabel = JBLabel(text).apply {
        foreground = UIUtil.getContextHelpForeground()
    }

    private fun toHtml(text: String): String =
        "<html>${Hdf5Format.escapeHtml(text).replace("\n", "<br>")}</html>"

    private fun onEdt(block: () -> Unit) = ApplicationManager.getApplication().invokeLater(block)

    private fun closeQuietly(hf: HdfFile) {
        runCatching { hf.close() }
    }

    fun dispose() {
        disposed = true
        val hf = hdfFile
        hdfFile = null
        if (hf != null) closeQuietly(hf)
    }

    companion object {
        private const val MAX_NODES = 200_000
    }
}
