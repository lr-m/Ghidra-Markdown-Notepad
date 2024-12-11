package ghidra.notepad;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.tree.*;
import java.awt.Rectangle;
import java.nio.file.Path;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

/**
 * Maintains the state of an individual document within the Markdown Notepad,
 * including its text content, editing history, and unsaved changes status.
 * Each document has its own RSyntaxTextArea editor instance and manages
 * change tracking for the file tree display.
 */
public class DocumentState {
    private RSyntaxTextArea editor;
    private boolean hasUnsavedChanges;
    private final DefaultTreeModel treeModel;
    private final Path filePath;
    private final DefaultMutableTreeNode treeNode;
    private final JTree fileTree;
    private final Runnable undoRedoStateCallback;  // Add callback for undo/redo state updates
    
    public DocumentState(String content, Path file, DefaultTreeModel treeModel, 
            DefaultMutableTreeNode node, JTree fileTree, Runnable undoRedoStateCallback) {
        this.treeModel = treeModel;
        this.filePath = file;
        this.treeNode = node;
        this.fileTree = fileTree;
        this.undoRedoStateCallback = undoRedoStateCallback;
        this.editor = new RSyntaxTextArea(20, 60);
        
        initializeEditor();
        setupDocumentListener();
        
        // Set initial content without it being an undoable action
        this.editor.setText(content);
        this.editor.discardAllEdits();
        
        this.hasUnsavedChanges = false;
    }
    
    private void initializeEditor() {
        this.editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_MARKDOWN);
        this.editor.setCodeFoldingEnabled(true);
        this.editor.setAntiAliasingEnabled(true);
        this.editor.setLineWrap(true);
        this.editor.setWrapStyleWord(true);
    }
    
    private void setupDocumentListener() {
        this.editor.getDocument().addDocumentListener(new DocumentListener() {
            private void updateState() {
                if (!hasUnsavedChanges) {
                    hasUnsavedChanges = true;
                    SwingUtilities.invokeLater(() -> {
                        updateTreeNode();
                        fileTree.repaint();  // Force immediate repaint
                    });
                }
                // Call the undo/redo state callback whenever document changes
                if (undoRedoStateCallback != null) {
                    SwingUtilities.invokeLater(undoRedoStateCallback);
                }
            }
            
            @Override
            public void insertUpdate(DocumentEvent e) { updateState(); }
            @Override
            public void removeUpdate(DocumentEvent e) { updateState(); }
            @Override
            public void changedUpdate(DocumentEvent e) { updateState(); }
        });
    }

    private void updateTreeNode() {
        if (treeNode != null) {
            treeModel.nodeChanged(treeNode);
            // Get the path to this node and make sure it's visible
            TreePath path = new TreePath(treeModel.getPathToRoot(treeNode));
            Rectangle bounds = fileTree.getPathBounds(path);
            if (bounds != null) {
                fileTree.repaint(bounds);
            }
        }
    }
    
    public String getContent() { 
        return editor.getText(); 
    }

    public void setContent(String content) { 
        if (!this.editor.getText().equals(content)) {
            this.editor.setText(content);
            hasUnsavedChanges = true;
            SwingUtilities.invokeLater(this::updateTreeNode);
        }
    }

    public RSyntaxTextArea getEditor() { 
        return editor; 
    }

    public boolean hasUnsavedChanges() { 
        return hasUnsavedChanges; 
    }

    public void setUnsavedChanges(boolean value) { 
        if (this.hasUnsavedChanges != value) {
            this.hasUnsavedChanges = value;
            SwingUtilities.invokeLater(() -> {
                updateTreeNode();
                fileTree.repaint();  // Force immediate repaint
            });
        }
    }
}