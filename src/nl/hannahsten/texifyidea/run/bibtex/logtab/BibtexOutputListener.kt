package nl.hannahsten.texifyidea.run.bibtex.logtab

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import nl.hannahsten.texifyidea.run.bibtex.logtab.messagehandlers.errors.*
import nl.hannahsten.texifyidea.run.bibtex.logtab.messagehandlers.warnings.BibLnNumPrintBibtexMessageHandler
import nl.hannahsten.texifyidea.run.bibtex.logtab.messagehandlers.warnings.NoFieldsBibtexMessageHandler
import nl.hannahsten.texifyidea.run.bibtex.logtab.messagehandlers.warnings.WarningBibtexMessageHandler
import nl.hannahsten.texifyidea.run.latex.ui.LatexCompileMessageTreeView
import nl.hannahsten.texifyidea.util.files.findFile
import org.apache.commons.collections.Buffer
import org.apache.commons.collections.BufferUtils
import org.apache.commons.collections.buffer.CircularFifoBuffer

/**
 *
 *
 * http://tug.org/svn/texlive/trunk/Build/source/texk/web2c/bibtex.web?view=markup
 * http://tug.ctan.org/tex-archive/biblio/bibtex/base/bibtex.web
 *
 * WEAVE: http://texdoc.net/texmf-dist/doc/generic/knuth/web/weave.pdf
 *
 * (MIT license) https://github.com/aclements/latexrun/blob/38ff6ec2815654513c91f64bdf2a5760c85da26e/latexrun#L1727
 *
 * https://github.com/lervag/vimtex/blob/master/autoload/vimtex/qf/bibtex.vim
 */
class BibtexOutputListener(
    val project: Project,
    val mainFile: VirtualFile?,
    val messageList: MutableList<BibtexLogMessage>,
    val treeView: LatexCompileMessageTreeView
) : ProcessListener {

    // Assume the window size is large enough to hold any message at once
    var window: Buffer = BufferUtils.synchronizedBuffer(CircularFifoBuffer(5))

    /** Currently open bib file. */
    var currentFile: String = ""

    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        processNewText(event.text)
    }

    fun processNewText(newText: String) {
        window.add(newText)

        updateCurrentFile(newText)

        val windowList: List<String> = window.mapNotNull { it as? String }
        val logMessage = extractMessage(windowList) ?: return

        if (!messageList.contains(logMessage)) {
            messageList.add(logMessage)
            val file = mainFile?.parent?.findFile(logMessage.fileName ?: mainFile.name)
            treeView.addMessage(
                logMessage.type.category,
                arrayOf(logMessage.message),
                file,
                (logMessage.line ?: 1) - 1, // treeview starts at 0
                0,
                null
            )
        }

        // todo compare There were x warnings message with messageList.size
    }

    /**
     * Let all message handlers try to find a message based on the window.
     */
    fun extractMessage(windowList: List<String>): BibtexLogMessage? {
        val bibtexErrorHandlers = listOf(
            BstExWarnPrintBibtexMessageHandler, // Should be before AuxErrPrintBibtexMessageHandler
            AuxErrPrintBibtexMessageHandler,
            CleanUpAndLeaveBibtexMessageHandler,
            AuxEndErrBibtexMessageHandler,
            NonexistentCrossReferenceBibtexMessageHandler
        )

        val bibtexWarningHandlers = listOf(
            NoFieldsBibtexMessageHandler,
            WarningBibtexMessageHandler,
            BibLnNumPrintBibtexMessageHandler
        )

        val handlers = bibtexErrorHandlers + bibtexWarningHandlers

        handlers.forEach { handler ->
            handler.findMessage(windowList, currentFile)?.let {
                return it
            }
        }
        return null
    }

    private fun updateCurrentFile(newText: String) {
        BibtexLogMagicRegex.bibFileOpened.find(newText)?.apply {
            currentFile = groups["file"]?.value ?: ""
        }
    }

    override fun processTerminated(event: ProcessEvent) {
        if (event.exitCode == 0) {
            treeView.setProgressText("Compilation was successful.")
        }
        else {
            treeView.setProgressText("Compilation failed.")
        }
    }

    override fun startNotified(event: ProcessEvent) {
        treeView.setProgressText("Compilation in progress...")
    }
}