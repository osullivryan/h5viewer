package com.h5viewer

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.Locale

/** Opens HDF5 files in [Hdf5FileEditor] instead of the default binary editor. */
class Hdf5FileEditorProvider : FileEditorProvider, DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean {
        if (file.isDirectory || !file.isValid) return false
        if (file.fileType === Hdf5FileType) return true
        // Fall back to the extension in case the type association was overridden.
        return when (file.extension?.lowercase(Locale.ROOT)) {
            "h5", "hdf5", "hdf", "he5" -> true
            else -> false
        }
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor =
        Hdf5FileEditor(file)

    override fun getEditorTypeId(): String = "hdf5-viewer"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}
