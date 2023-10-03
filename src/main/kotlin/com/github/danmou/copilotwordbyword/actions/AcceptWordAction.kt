package com.github.danmou.copilotwordbyword.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.github.copilot.completions.CopilotCompletionType
import com.github.copilot.editor.CopilotEditorManagerImpl
import com.github.copilot.editor.CopilotEditorUtil
import com.github.copilot.util.ApplicationUtil
import com.intellij.openapi.actionSystem.ActionManager

internal class AcceptWordAction : AnAction() {
    private val logger = logger<AcceptWordAction>()

    override fun actionPerformed(e: AnActionEvent) {
        val project: Project? = findCurrentProject()
        val fileEditorManager = FileEditorManager.getInstance(project!!)
        val editor = fileEditorManager.selectedTextEditor ?: return
        val editorManager = CopilotEditorManagerImpl()

        if (!CopilotEditorUtil.isSelectedEditor(editor)) return

        val hasCompletions = editorManager.hasCompletionInlays(editor)

        if (!hasCompletions) return
        logger.info("hasCompletions")
        val inlays = editorManager.collectInlays(editor, 0, editor.document.textLength)
        logger.info("inlays: $inlays")
        if (inlays.isEmpty()) return
        logger.info("inlays.isNotEmpty()")
        val firstInlay = inlays[0]
        val inlayType = firstInlay.accessField("type") as CopilotCompletionType
        logger.info("inlayType: $inlayType")
        var inlayContent = firstInlay.accessField("content") as String
        if (inlayType == CopilotCompletionType.Block) {
            inlayContent = "\n" + inlayContent
        }
        logger.info("inlayContent: $inlayContent")
        val firstWords = inlayContent.split(
                Regex("((?<=[ \t])(?=\\S))|((?<=[^\\s\\w])(?=\\w))|$"),
                2,
        )
        logger.info("firstWords: $firstWords")
        if (firstWords.isEmpty()) return
        // If the first word is a single character, we include the next word
        val firstWord = if (firstWords.size > 1 && firstWords[0].length == 1) {
            firstWords[0] + firstWords[1]
        } else {
            firstWords[0]
        }
        logger.info("firstWord: $firstWord")

        WriteCommandAction.runWriteCommandAction(project) {
            editor.document.insertString(editor.caretModel.offset, firstWord)
        }
        editor.caretModel.moveToOffset(editor.caretModel.offset + firstWord.length)
        ActionManager.getInstance().getAction("copilot.requestCompletions").actionPerformed(e)
    }

    private fun findCurrentProject(): Project? {
        return ApplicationUtil.findCurrentProject()
    }

    private fun Any.accessField(fieldName: String): Any? {
        return javaClass.getDeclaredField(fieldName).let { field ->
            field.isAccessible = true
            field.get(this)
        }
    }
}
