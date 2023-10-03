package com.github.danmou.copilotwordbyword.actions

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project

import com.github.copilot.completions.CopilotCompletionType
import com.github.copilot.editor.CopilotEditorManagerImpl
import com.github.copilot.editor.CopilotEditorUtil
import com.github.copilot.util.ApplicationUtil

internal class AcceptWordAction : AnAction() {
    private val logger = logger<AcceptWordAction>()
    private val project: Project? get() = ApplicationUtil.findCurrentProject()
    private val editor: Editor? get() {
        val project = project ?: return null
        return FileEditorManager.getInstance(project).selectedTextEditor
    }

    override fun actionPerformed(e: AnActionEvent) {
        val firstWord = getFirstWordOfSuggestion()

        if (firstWord != null) {
            insertText(firstWord)
        }

        // Request completions again since they are not automatically shown after inserting text
        ActionManager.getInstance().getAction("copilot.requestCompletions").actionPerformed(e)
    }


    private fun getFirstWordOfSuggestion(): String? {
        val inlayContent = getInlayContent() ?: return null
        val firstWord = extractFirstWord(inlayContent) ?: return null
        logger.debug("firstWord: $firstWord")
        return firstWord
    }

    private fun getInlayContent(): String? {
        val editor = editor ?: return null
        val copilotEditorManager = CopilotEditorManagerImpl()
        if (!CopilotEditorUtil.isSelectedEditor(editor)) return null
        if (!copilotEditorManager.hasCompletionInlays(editor)) return null

        val inlays = copilotEditorManager.collectInlays(editor, 0, editor.document.textLength)
        logger.debug("inlays: $inlays")
        if (inlays.isEmpty()) return null

        // There should be either one (either Block or Inline) inlay or two inlays (Inline followed
        // by Block). We only care about the first inlay since that should contain the next word.
        val inlay = inlays[0]
        val inlayType = inlay.type
        logger.debug("inlayType: $inlayType")

        // The first word must be included in the first two lines of the inlay content
        var inlayContent = inlay.contentLines.take(2).joinToString("\n")
        if (inlayType == CopilotCompletionType.Block) {
            // Block inlays start on the next line
            inlayContent = "\n" + inlayContent
        }
        logger.debug("inlayContent: $inlayContent")

        return inlayContent
    }

    private fun extractFirstWord(text: String): String? {
        // Extract the first two words from the inlay content
        val firstWords = text.split(
                // Regex that gives a zero-length match at the end of whitespace, at the beginning
                // of words that follow punctuation and at the end of lines.
                Regex("((?<=[ \t])(?=\\S))|((?<=[^\\s\\w])(?=\\w))|$"),
                2,
        )
        logger.debug("firstWords: $firstWords")
        if (firstWords.isEmpty()) return null

        // If the first word is just a single character, we include the next word
        return if (firstWords.size > 1 && firstWords[0].length == 1) {
            firstWords[0] + firstWords[1]
        } else {
            firstWords[0]
        }
    }

    private fun insertText(text: String) {
        val project = project ?: return
        val editor = editor ?: return
        WriteCommandAction.runWriteCommandAction(project) {
            editor.document.insertString(editor.caretModel.offset, text)
        }
        editor.caretModel.moveToOffset(editor.caretModel.offset + text.length)
    }
}
