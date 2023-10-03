package com.github.danmou.copilotwordbyword.actions

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager

import com.github.copilot.completions.CopilotCompletionType
import com.github.copilot.editor.CopilotEditorManagerImpl
import com.github.copilot.editor.CopilotEditorUtil
import com.github.copilot.util.ApplicationUtil

internal class AcceptWordAction : AnAction() {
    private val logger = logger<AcceptWordAction>()

    override fun actionPerformed(e: AnActionEvent) {
        val project = ApplicationUtil.findCurrentProject() ?: return
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        val copilotEditorManager = CopilotEditorManagerImpl()
        if (!CopilotEditorUtil.isSelectedEditor(editor)) return
        if (!copilotEditorManager.hasCompletionInlays(editor)) return

        val inlays = copilotEditorManager.collectInlays(editor, 0, editor.document.textLength)
        logger.debug("inlays: $inlays")
        if (inlays.isEmpty()) return

        // There should be either one (either Block or Inline) inlay or two inlays (Inline followed
        // by Block). We only care about the first inlay since that should contain the next word.
        val inlay = inlays[0]

        val inlayType = inlay.type
        logger.debug("inlayType: $inlayType")
        var inlayContent = inlay.contentLines.take(2).joinToString("\n")
        if (inlayType == CopilotCompletionType.Block) {
            inlayContent = "\n" + inlayContent
        }
        logger.debug("inlayContent: $inlayContent")

        // Extract the first two words from the inlay content
        val firstWords = inlayContent.split(
                // Regex that gives a zero-length match at the end of whitespace, at the beginning
                // of words that follow punctuation and at the end of lines.
                Regex("((?<=[ \t])(?=\\S))|((?<=[^\\s\\w])(?=\\w))|$"),
                2,
        )
        logger.debug("firstWords: $firstWords")
        if (firstWords.isEmpty()) return

        // If the first word is just a single character, we include the next word
        val firstWord = if (firstWords.size > 1 && firstWords[0].length == 1) {
            firstWords[0] + firstWords[1]
        } else {
            firstWords[0]
        }
        logger.debug("firstWord: $firstWord")

        WriteCommandAction.runWriteCommandAction(project) {
            editor.document.insertString(editor.caretModel.offset, firstWord)
        }
        editor.caretModel.moveToOffset(editor.caretModel.offset + firstWord.length)

        // Request completions again since they are otherwise only shown after typing
        ActionManager.getInstance().getAction("copilot.requestCompletions").actionPerformed(e)
    }
}
