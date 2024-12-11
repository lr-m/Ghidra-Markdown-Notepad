package ghidra.notepad;

import java.nio.file.Path;

/**
 * Interface defining operations for managing document states across the notepad.
 * Provides methods for updating, removing, and retrieving document states as
 * files are renamed, moved, or deleted within the collection.
 */
public interface DocumentStateHandler {
    void updateDocumentStates(Path oldPath, Path newPath);
    void removeDocumentState(Path path);
    DocumentState getDocumentState(Path path);
}