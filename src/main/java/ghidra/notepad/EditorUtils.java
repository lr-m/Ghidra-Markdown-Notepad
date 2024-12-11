// EditorUtils.java
package ghidra.notepad;

import generic.theme.Gui;
import org.fife.ui.rsyntaxtextarea.*;

import java.awt.Color;
import java.awt.Font;

/**
 * Utility class for creating and configuring RSyntaxTextArea editors with
 * consistent styling and syntax highlighting. Handles the application of
 * theme colors and markdown syntax rules across all editor instances.
 */
public class EditorUtils {
    public static RSyntaxTextArea createNewEditor() {
        RSyntaxTextArea newEditor = new RSyntaxTextArea(20, 60);
        
        // Basic editor settings
        newEditor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_MARKDOWN);
        newEditor.setCodeFoldingEnabled(true);
        newEditor.setAntiAliasingEnabled(true);
        newEditor.setLineWrap(true);
        newEditor.setWrapStyleWord(true);
        
        // Apply styling
        customizeSyntaxHighlighting(newEditor);
        
        return newEditor;
    }

    public static void applyEditorStyling(RSyntaxTextArea editor) {
        customizeSyntaxHighlighting(editor);
    }

    public static void customizeSyntaxHighlighting(RSyntaxTextArea editor) {
        // Get the current syntax scheme
        SyntaxScheme scheme = editor.getSyntaxScheme();
        
        // Get system colors from Gui
        Color background = Gui.getColor("color.bg");
        Color foreground = Gui.getColor("color.fg");
        Color selection = Gui.getColor("color.bg.selection");
        Color currentLine = Gui.getColor("color.bg.currentline");
        Color caretColor = Gui.getColor("color.cursor.focused");
        
        // Apply system colors to editor
        editor.setBackground(background);
        editor.setForeground(foreground);
        editor.setCurrentLineHighlightColor(currentLine);
        editor.setSelectionColor(selection);
        editor.setSelectedTextColor(foreground);
        editor.setCaretColor(caretColor);
        
        // Headers (# Header)
        scheme.getStyle(Token.RESERVED_WORD).foreground = Gui.getColor("color.fg.decompiler.function.name");
        scheme.getStyle(Token.RESERVED_WORD).font = editor.getFont().deriveFont(Font.BOLD);
        
        // Bold text (**bold**)
        scheme.getStyle(Token.RESERVED_WORD_2).foreground = Gui.getColor("color.fg.decompiler.keyword");
        scheme.getStyle(Token.RESERVED_WORD_2).font = editor.getFont().deriveFont(Font.BOLD);
        
        // Italic text (*italic*)
        scheme.getStyle(Token.DATA_TYPE).foreground = Gui.getColor("color.fg.decompiler.type");
        scheme.getStyle(Token.DATA_TYPE).font = editor.getFont().deriveFont(Font.ITALIC);
        
        // Links [text](url) - the text part
        scheme.getStyle(Token.REGEX).foreground = Gui.getColor("color.fg.decompiler.variable");
        
        // Links [text](url) - the URL part
        scheme.getStyle(Token.ANNOTATION).foreground = Gui.getColor("color.fg.decompiler.parameter");
        
        // Inline code `code`
        scheme.getStyle(Token.PREPROCESSOR).foreground = Gui.getColor("color.fg.decompiler.comment");
        
        // Code blocks with syntax highlighting
        scheme.getStyle(Token.VARIABLE).foreground = Gui.getColor("color.fg.decompiler.variable");
        
        // Bold+Italic (***text*** or ___text___)
        scheme.getStyle(Token.FUNCTION).foreground = Gui.getColor("color.fg.decompiler.variable");
        scheme.getStyle(Token.FUNCTION).font = editor.getFont().deriveFont(Font.BOLD | Font.ITALIC);
        
        // Lists and Block quotes
        scheme.getStyle(Token.LITERAL_NUMBER_DECIMAL_INT).foreground = Gui.getColor("color.fg.decompiler.global");
        
        // HTML Entity References
        scheme.getStyle(Token.MARKUP_ENTITY_REFERENCE).foreground = Gui.getColor("color.fg.decompiler.global");
        
        // HTML Tags
        scheme.getStyle(Token.MARKUP_TAG_DELIMITER).foreground = Gui.getColor("color.fg.decompiler.function.name");
        scheme.getStyle(Token.MARKUP_TAG_NAME).foreground = Gui.getColor("color.fg.decompiler.function.name");
        scheme.getStyle(Token.MARKUP_TAG_ATTRIBUTE).foreground = Gui.getColor("color.fg.decompiler.function.name");
        scheme.getStyle(Token.MARKUP_TAG_ATTRIBUTE_VALUE).foreground = Gui.getColor("color.fg.decompiler.function.name");
        
        // Strikethrough (~~text~~)
        scheme.getStyle(Token.OPERATOR).foreground = Gui.getColor("color.fg.decompiler.parameter");
        
        // Comments (used for blockquotes in some cases)
        scheme.getStyle(Token.COMMENT_EOL).foreground = Gui.getColor("color.fg.decompiler.global");
        
        // Documentation comments (used for horizontal rules ---)
        scheme.getStyle(Token.COMMENT_DOCUMENTATION).foreground = Gui.getColor("color.fg.decompiler.function.name");
        
        // General identifiers (plain text and miscellaneous)
        scheme.getStyle(Token.IDENTIFIER).foreground = foreground;
        
        // Apply the customized scheme
        editor.setSyntaxScheme(scheme);
        
        // Additional editor settings
        editor.setTabSize(4);
        editor.setTabsEmulated(true);
        editor.setPaintTabLines(false);
        editor.setShowMatchedBracketPopup(false);
        editor.setAnimateBracketMatching(false);
        editor.setHighlightSecondaryLanguages(false);
        editor.setRoundedSelectionEdges(false);
        editor.setBracketMatchingEnabled(false);
        editor.setMarkOccurrences(false);
        editor.setFadeCurrentLineHighlight(false);
    }
}