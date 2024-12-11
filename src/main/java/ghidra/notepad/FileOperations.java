// FileOperations.java
package ghidra.notepad;

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import javax.imageio.ImageIO;
import java.awt.Color;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.JTree;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import ghidra.notepad.FileNode;
import ghidra.notepad.DocumentStateHandler;
import ghidra.notepad.FileStateHandler;

/**
 * Handles all file system operations including creating, deleting, and
 * modifying files and directories. Also manages image imports and file
 * content loading/saving operations within the note collection.
 */
public class FileOperations {
    private final JPanel mainPanel;
    private final JEditorPane previewPane;
    private final Path currentDirectory;
    private final JTabbedPane tabbedPane;
    private final Runnable refreshTreeCallback;
    private final LoadFileCallback loadFileCallback;
    private final DocumentStateHandler documentStateHandler;
    private final FileStateHandler fileStateHandler;
    private final DefaultTreeModel treeModel;
    private final JTree fileTree;

    public interface LoadFileCallback {
        void loadFile(Path file);
    }

    public FileOperations(JPanel mainPanel, JEditorPane previewPane, Path currentDirectory, 
                        JTabbedPane tabbedPane, Runnable refreshTreeCallback,
                        LoadFileCallback loadFileCallback, DocumentStateHandler documentStateHandler,
                        FileStateHandler fileStateHandler, DefaultTreeModel treeModel,
                        JTree fileTree) {
        this.mainPanel = mainPanel;
        this.previewPane = previewPane;
        this.currentDirectory = currentDirectory;
        this.tabbedPane = tabbedPane;
        this.refreshTreeCallback = refreshTreeCallback;
        this.loadFileCallback = loadFileCallback;
        this.documentStateHandler = documentStateHandler;
        this.fileStateHandler = fileStateHandler;
        this.treeModel = treeModel;
        this.fileTree = fileTree;
    }

    public void createNewDocument(Path targetDir, String fileName) {
        if (!fileName.endsWith(".md")) {
            fileName += ".md";
        }
        
        Path newFile = targetDir.resolve(fileName);
        if (Files.exists(newFile)) {
            JOptionPane.showMessageDialog(mainPanel,
                "A file with this name already exists",
                "File Exists",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            String template = """
                # %s
                """.formatted(
                    fileName.replace(".md", "")
                );
            
            Files.writeString(newFile, template);
            refreshTreeCallback.run();
            loadFileCallback.loadFile(newFile);
            
        } catch (IOException e) {
            JOptionPane.showMessageDialog(mainPanel,
                "Error creating document: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    public void createNewDirectory(Path targetDir, String dirName) {
        Path newDir = targetDir.resolve(dirName);
        
        if (Files.exists(newDir)) {
            JOptionPane.showMessageDialog(mainPanel,
                "A directory with this name already exists",
                "Directory Exists",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            Files.createDirectory(newDir);
            refreshTreeCallback.run();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(mainPanel,
                "Error creating directory: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    public void saveFile(Path file, String content) {
        try {
            Files.writeString(file, content);
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(mainPanel,
                "Error saving file: " + e.getMessage(),
                "Save Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    public void loadImagePreview(Path file, Color backgroundColor, Color foregroundColor) {
        try {
            // Disable edit tab for images
            tabbedPane.setEnabledAt(0, false);
            
            // Switch to preview tab
            tabbedPane.setSelectedIndex(1);
            
            // Load and scale the image
            BufferedImage img = ImageIO.read(file.toFile());
            if (img != null) {
                // Calculate scaling to fit preview pane while maintaining aspect ratio
                double scale = Math.min(
                    (double) 800 / img.getWidth(),
                    (double) 600 / img.getHeight()
                );
                
                // Only scale down, not up
                if (scale < 1.0) {
                    int newWidth = (int) (img.getWidth() * scale);
                    int newHeight = (int) (img.getHeight() * scale);
                    
                    // Create centered HTML for the image
                    String html = String.format("""
                        <html>
                        <body style='text-align: center; margin: 0; padding: 20px; background-color: %s;'>
                            <img src='%s' width='%d' height='%d' 
                                style='display: block; margin: auto; box-shadow: 0 2px 8px rgba(0,0,0,0.2);'>
                            <p style='color: %s; font-family: Arial; margin-top: 10px;'>
                                Original size: %dx%d
                            </p>
                        </body>
                        </html>
                        """,
                        getHexColor(backgroundColor),
                        file.toUri(),
                        newWidth,
                        newHeight,
                        getHexColor(foregroundColor),
                        img.getWidth(),
                        img.getHeight()
                    );
                    previewPane.setText(html);
                } else {
                    // Show original size
                    String html = String.format("""
                        <html>
                        <body style='text-align: center; margin: 0; padding: 20px; background-color: %s;'>
                            <img src='%s' width='%d' height='%d' 
                                style='display: block; margin: auto; box-shadow: 0 2px 8px rgba(0,0,0,0.2);'>
                        </body>
                        </html>
                        """,
                        getHexColor(backgroundColor),
                        file.toUri(),
                        img.getWidth(),
                        img.getHeight()
                    );
                    previewPane.setText(html);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            previewPane.setText("<html><body><center>Error loading image: " + e.getMessage() + "</center></body></html>");
        }
    }

    private String getHexColor(Color color) {
        return String.format("#%02x%02x%02x", 
            color.getRed(), color.getGreen(), color.getBlue());
    }

    public void createCollectionStructure(Path collectionPath) throws IOException {
        Path welcomePath = collectionPath.resolve("welcome.md");
        String welcomeContent = """
            # Sample Markdown Document

            ## Introduction

            This is a sample markdown document showing various formatting options. Markdown is a lightweight markup language that you can use to add formatting elements to plaintext text documents.

            ## Memory Addresses

            Click to jump to this address in the binary - saves having to copy addresses all the time...

            [0x1c9954]

            [34038b]

            ## Text Formatting

            Here's how you can format text in different ways:
            - **Bold text** is created using double asterisks
            - *Italic text* uses single asterisks
            - ***Bold and italic*** combines both

            ## Lists

            ### Unordered Lists

            * First item
            * Second item
            * Nested item
            * Another nested item
            * Third item

            ### Ordered Lists

            1. First step
            2. Second step
            1. Sub-step one
            2. Sub-step two
            3. Third step

            ## Links and Images

            To import or paste an image, click the `Import Image` button, and paste/drag the image to the window, it will place the markdown in the current document cursor position.

            [Visit my website](https://luke-m.xyz)

            ![Alt text for an image](images/image.jpg)

            ## Code

            Inline code: `const greeting = "Hello, World!";`

            Code block:
            ```python
            def greet(name):
                print(f"Hello, {name}!")
                return True

            greet("User")
            ```

            ## Quotes

            > This is a blockquote. You can use it to emphasize or quote text.
            > 
            > It can span multiple paragraphs if you add a > on the blank lines between them.

            ## Tables

            | Header 1 | Header 2 | Header 3 |
            |----------|----------|----------|
            | Row 1    | Data     | Data     |
            | Row 2    | Data     | Data     |

            """;
        Files.writeString(welcomePath, welcomeContent);
    }

    public void deleteFile(FileNode fileNode) {
        int result = JOptionPane.showConfirmDialog(
            mainPanel,
            "Are you sure you want to delete this file?",
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION);
            
        if (result == JOptionPane.YES_OPTION) {
            try {
                Path path = fileNode.getPath();
                Files.delete(path);
                
                // Clean up document states
                documentStateHandler.removeDocumentState(path);
                
                // Only clear editor if we're deleting the currently open file
                if (fileStateHandler.getCurrentFile() != null && 
                    fileStateHandler.getCurrentFile().equals(path)) {
                    fileStateHandler.clearCurrentFile();
                    RSyntaxTextArea newEditor = EditorUtils.createNewEditor();
                    fileStateHandler.swapEditor(newEditor);
                }
                
                refreshTreeCallback.run();
                
            } catch (IOException e) {
                JOptionPane.showMessageDialog(mainPanel,
                    "Error deleting file: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public void renameFile(FileNode fileNode) {
        String newName = JOptionPane.showInputDialog(
            mainPanel,
            "Enter new name:",
            "Rename File",
            JOptionPane.PLAIN_MESSAGE);
            
        if (newName != null && !newName.trim().isEmpty()) {
            try {
                if (!newName.endsWith(".md")) {
                    newName += ".md";
                }
                
                Path oldPath = fileNode.getPath();
                Path newPath = oldPath.resolveSibling(newName);
                
                if (Files.exists(newPath)) {
                    JOptionPane.showMessageDialog(mainPanel,
                        "A file with this name already exists",
                        "File Exists",
                        JOptionPane.WARNING_MESSAGE);
                    return;
                }
                
                // Move the file
                Files.move(oldPath, newPath);
                
                // Update document states
                documentStateHandler.updateDocumentStates(oldPath, newPath);
                
                // Refresh the tree to show the new name
                refreshTreeCallback.run();
                
            } catch (IOException e) {
                JOptionPane.showMessageDialog(mainPanel,
                    "Error renaming file: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public void deleteDirectory(DefaultMutableTreeNode dirNode, Path dirPath) {
        String dirName = dirNode.getUserObject().toString();
        int result = JOptionPane.showConfirmDialog(
            mainPanel,
            "Are you sure you want to delete the directory '" + dirName + "' and all its contents?",
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION);
            
        if (result == JOptionPane.YES_OPTION) {
            try {
                // Delete all files in the directory
                Files.walk(dirPath)
                    .sorted((p1, p2) -> -p1.compareTo(p2)) // Reverse order to delete files before directories
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                            // Remove from document states if it's a file
                            if (Files.isRegularFile(path)) {
                                documentStateHandler.removeDocumentState(path);
                                if (fileStateHandler.getCurrentFile() != null && 
                                    fileStateHandler.getCurrentFile().equals(path)) {
                                    fileStateHandler.clearCurrentFile();
                                    fileStateHandler.setEditorText("");
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                
                refreshTreeCallback.run();
                
            } catch (IOException e) {
                JOptionPane.showMessageDialog(mainPanel,
                    "Error deleting directory: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public void renameDirectory(DefaultMutableTreeNode dirNode, Path dirPath) {
        String oldName = dirNode.getUserObject().toString();
        String newName = (String) JOptionPane.showInputDialog(
            mainPanel,
            "Enter new name:",
            "Rename Directory",
            JOptionPane.PLAIN_MESSAGE,
            null,
            null,
            oldName);
            
        if (newName != null && !newName.trim().isEmpty() && !newName.equals(oldName)) {
            try {
                Path newPath = dirPath.resolveSibling(newName);
                
                if (Files.exists(newPath)) {
                    JOptionPane.showMessageDialog(mainPanel,
                        "A directory with this name already exists",
                        "Directory Exists",
                        JOptionPane.WARNING_MESSAGE);
                    return;
                }
                
                // Move the directory
                Files.move(dirPath, newPath);
                
                // Update document states for all files in the directory
                documentStateHandler.updateDocumentStates(dirPath, newPath);
                
                refreshTreeCallback.run();
                
            } catch (IOException e) {
                JOptionPane.showMessageDialog(mainPanel,
                    "Error renaming directory: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}