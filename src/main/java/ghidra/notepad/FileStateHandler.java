package ghidra.notepad;

import java.nio.file.Path;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

/**
 * Interface defining operations for tracking the currently active file
 * and its editor state. Provides methods for managing the current file
 * selection and editor content.
 */
public interface FileStateHandler {
    Path getCurrentFile();
    void setCurrentFile(Path file);
    void clearCurrentFile();
    void setEditorText(String text);
    void swapEditor(RSyntaxTextArea newEditor);
}