package com.h5viewer

import com.intellij.icons.AllIcons
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import io.jhdf.api.Dataset
import io.jhdf.api.Node
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

/** A single jHDF node prepared for display, with pre-computed labels. */
class Hdf5NodeItem(
    val node: Node,
    val displayName: String,
    val secondary: String,
    val kind: Kind,
) {
    enum class Kind { GROUP, DATASET, OTHER }

    val dataset: Dataset? get() = node as? Dataset
}

/** Tree node backed by an [Hdf5NodeItem]; groups may have children, others are leaves. */
class Hdf5TreeNode(val item: Hdf5NodeItem) : DefaultMutableTreeNode(item) {
    override fun isLeaf(): Boolean = item.kind != Hdf5NodeItem.Kind.GROUP
    override fun getAllowsChildren(): Boolean = item.kind == Hdf5NodeItem.Kind.GROUP
}

class Hdf5TreeCellRenderer : ColoredTreeCellRenderer() {
    override fun customizeCellRenderer(
        tree: JTree,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean,
    ) {
        val item = (value as? Hdf5TreeNode)?.item ?: return
        icon = when (item.kind) {
            Hdf5NodeItem.Kind.GROUP -> AllIcons.Nodes.Folder
            Hdf5NodeItem.Kind.DATASET -> AllIcons.FileTypes.Any_type
            Hdf5NodeItem.Kind.OTHER -> AllIcons.Nodes.Unknown
        }
        append(item.displayName)
        if (item.secondary.isNotEmpty()) {
            append("   ${item.secondary}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        }
    }
}
