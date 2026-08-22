package com.h5viewer

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

/**
 * Binary file type for HDF5 containers. Registering it keeps the IDE from trying
 * to open these files as text and lets [Hdf5FileEditorProvider] claim them.
 *
 * Referenced from plugin.xml via `fieldName="INSTANCE"` — a Kotlin `object`
 * exposes itself through the synthetic static `INSTANCE` field.
 */
object Hdf5FileType : FileType {
    private val ICON: Icon = IconLoader.getIcon("/icons/hdf5.svg", Hdf5FileType::class.java)

    override fun getName(): String = "HDF5"
    override fun getDescription(): String = "HDF5 data file"
    override fun getDefaultExtension(): String = "h5"
    override fun getIcon(): Icon = ICON
    override fun isBinary(): Boolean = true
    override fun isReadOnly(): Boolean = true
    override fun getCharset(file: VirtualFile, content: ByteArray): String? = null
}
