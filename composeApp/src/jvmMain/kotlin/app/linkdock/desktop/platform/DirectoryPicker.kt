package app.linkdock.desktop.platform

import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

object DirectoryPicker {

    fun pickDirectory(initialPath: String?): String? {
        var selectedPath: String? = null

        val openDialog = {
            val chooser = JFileChooser().apply {
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                isAcceptAllFileFilterUsed = false
                dialogTitle = "저장할 폴더 선택"

                if (!initialPath.isNullOrBlank()) {
                    val initialDir = File(initialPath)
                    if (initialDir.exists()) {
                        currentDirectory = if (initialDir.isDirectory) {
                            initialDir
                        } else {
                            initialDir.parentFile
                        }
                    }
                }
            }

            val result = chooser.showOpenDialog(null)
            if (result == JFileChooser.APPROVE_OPTION) {
                selectedPath = chooser.selectedFile.absolutePath
            }
        }

        if (SwingUtilities.isEventDispatchThread()) {
            openDialog()
        } else {
            SwingUtilities.invokeAndWait(openDialog)
        }

        return selectedPath
    }
}