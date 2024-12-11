package ghidra.notepad;

import java.nio.file.Path;

/**
 * Represents a file in the navigation tree, maintaining both the file's
 * actual path and its display name. Used by the tree renderer to show
 * files in the sidebar navigation.
 */
public class FileNode {
    private Path path;
    private String displayName;

    public FileNode(Path path) {
        this.path = path;
        this.displayName = path.getFileName().toString();
    }

    public Path getPath() { 
        return path; 
    }
    
    @Override
    public String toString() { 
        return displayName; 
    }
}