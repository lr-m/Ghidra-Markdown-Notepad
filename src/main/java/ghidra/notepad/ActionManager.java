package ghidra.notepad;

import docking.*;
import docking.action.*;
import ghidra.framework.plugintool.PluginTool;
import javax.swing.*;
import java.awt.event.KeyEvent;
import java.awt.event.InputEvent;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manages all user actions and keyboard shortcuts for the Markdown Notepad plugin.
 * This includes file operations (open, save), editing operations (undo, redo),
 * and collection management. Each action is configured with appropriate keyboard
 * shortcuts, menu placement, and toolbar integration.
 */
public class ActionManager {
    private final MarkdownNotepadProvider provider;
    private final PluginTool tool;
    private DockingAction undoAction;
    private DockingAction redoAction;
    private DockingAction backAction;
    private DockingAction forwardAction;

    public ActionManager(MarkdownNotepadProvider provider, PluginTool tool) {
        this.provider = provider;
        this.tool = tool;
        createActions();
    }

    public void createActions() {
        createCollectionActions();
        createFileActions();
        createSearchActions();
        createNavigationActions();
    }

    private void createCollectionActions() {
        // Open Collection (Ctrl+O)
        DockingAction openDirAction = new DockingAction("Open Collection", provider.getName()) {
            @Override
            public void actionPerformed(ActionContext context) {
                JFileChooser chooser = new JFileChooser();
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                
                if (provider.getCurrentDirectory() != null) {
                    chooser.setCurrentDirectory(provider.getCurrentDirectory().toFile());
                }
                
                if (chooser.showOpenDialog(provider.getComponent()) == JFileChooser.APPROVE_OPTION) {
                    Path selectedPath = chooser.getSelectedFile().toPath();
                    if (Files.exists(selectedPath) && Files.isDirectory(selectedPath)) {
                        provider.loadDirectory(selectedPath);
                    }
                }
            }
        };
        configureAction("Collection", openDirAction, "Open Collection", KeyEvent.VK_O, 
            "/images/open_collection.png", "Open collection");

        // Create New Collection (Ctrl+Shift+N)
        DockingAction createCollectionAction = new DockingAction("Create New Collection", provider.getName()) {
            @Override
            public void actionPerformed(ActionContext context) {
                provider.createNewCollection();
            }
        };
        configureAction("Collection", createCollectionAction, "Create Collection", 
            KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK),
            "/images/new_collection.png", "Create new collection");
    }

    private void createFileActions() {
        // Save (Ctrl+S)
        DockingAction saveAction = new DockingAction("Save", provider.getName()) {
            @Override
            public void actionPerformed(ActionContext context) {
                provider.saveCurrentFile();
            }
        };
        configureAction("File", saveAction, "Save", KeyEvent.VK_S, 
            "/images/save.png", "Save current file");

        // Undo (Ctrl+Z)
        undoAction = new DockingAction("Undo", provider.getName()) {
            @Override
            public void actionPerformed(ActionContext context) {
                provider.performUndo();
            }
        };
        configureAction("Edit", undoAction, "Undo", KeyEvent.VK_Z, 
            "/images/undo.png", "Undo");
        
        // Redo (Ctrl+Y)
        redoAction = new DockingAction("Redo", provider.getName()) {
            @Override
            public void actionPerformed(ActionContext context) {
                provider.performRedo();
            }
        };
        configureAction("Edit", redoAction, "Redo", KeyEvent.VK_Y, 
            "/images/redo.png", "Redo");

        // New Document (Ctrl+N)
        DockingAction newDocAction = new DockingAction("Create New Note", provider.getName()) {
            @Override
            public void actionPerformed(ActionContext context) {
                provider.createNewDocument();
            }
        };
        configureAction("File", newDocAction, "New Document", KeyEvent.VK_N, 
            "/images/new_note.png", "Create new document");
    
        // New Directory (Ctrl+Shift+D)
        DockingAction newDirAction = new DockingAction("Create New Directory", provider.getName()) {
            @Override
            public void actionPerformed(ActionContext context) {
                provider.createNewDirectory();
            }
        };
        configureAction("File", newDirAction, "New Directory", 
            KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK),
            "/images/new_folder.png", "Create new directory");

        // Import Image (Ctrl+I)
        DockingAction importImageAction = new DockingAction("Import Image", provider.getName()) {
            @Override
            public void actionPerformed(ActionContext context) {
                ImageImportDialog dialog = new ImageImportDialog(tool.getActiveWindow(), provider);
                dialog.setVisible(true);
            }
        };
        configureAction("File", importImageAction, "Import Image", KeyEvent.VK_I, 
            "/images/picture.png", "Import image");
    }

    private void createSearchActions() {
        // Document Search (Ctrl+F)
        DockingAction searchDocAction = new DockingAction("Search Current Document", provider.getName()) {
            @Override
            public void actionPerformed(ActionContext context) {
                provider.showDocumentSearchPanel();
            }
        };
        configureAction("Search", searchDocAction, "Search Document", KeyEvent.VK_F, 
            "/images/find_doc.png", "Search in current document");

        // Collection Search (Ctrl+Shift+F)
        DockingAction searchCollectionAction = new DockingAction("Search Collection", provider.getName()) {
            @Override
            public void actionPerformed(ActionContext context) {
                provider.showCollectionSearchDialog();
            }
        };
        configureAction("Search", searchCollectionAction, "Search Collection", 
            KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK),
            "/images/find_all.png", "Search in collection");
    }

    private void createNavigationActions() {
        // Zoom In (Ctrl+=)
        DockingAction zoomInAction = new DockingAction("Zoom In", provider.getName()) {
            @Override
            public void actionPerformed(ActionContext context) {
                provider.changeZoom(0.1f);
            }
        };
        configureAction("View", zoomInAction, "Zoom In", 
            KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK),
            "/images/zoom_in.png", "Zoom in");

        // Zoom Out (Ctrl+-)
        DockingAction zoomOutAction = new DockingAction("Zoom Out", provider.getName()) {
            @Override
            public void actionPerformed(ActionContext context) {
                provider.changeZoom(-0.1f);
            }
        };
        configureAction("View", zoomOutAction, "Zoom Out", 
            KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK),
            "/images/zoom_out.png", "Zoom out");

        // Back
        DockingAction backAction = new DockingAction("Back", provider.getName()) {
            @Override
            public void actionPerformed(ActionContext context) {
                provider.navigateBack();
            }
        };
        configureAction("Navigate", backAction, "Back", 
            KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK),
            "/images/back.png", "Go back");

        // Forward
        DockingAction forwardAction = new DockingAction("Forward", provider.getName()) {
            @Override
            public void actionPerformed(ActionContext context) {
                provider.navigateForward();
            }
        };
        configureAction("Navigate", forwardAction, "Forward", 
            KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, InputEvent.ALT_DOWN_MASK),
            "/images/forward.png", "Go forward");
    }

    private void configureAction(String section, DockingAction action, String menuName, 
            int keyEvent, String iconPath, String tooltip) {
        configureAction(section, action, menuName, 
            KeyStroke.getKeyStroke(keyEvent, InputEvent.CTRL_DOWN_MASK), 
            iconPath, tooltip);
    }

    private void configureAction(String section, DockingAction action, String menuName, 
            KeyStroke keyStroke, String iconPath, String tooltip) {
        action.setEnabled(true);
        Icon icon = new ImageIcon(getClass().getResource(iconPath));
        action.setMenuBarData(new MenuData(new String[] { section, menuName }, icon));
        action.setKeyBindingData(new KeyBindingData(keyStroke));
        action.setToolBarData(new ToolBarData(icon));
        action.setDescription(tooltip);
        provider.addLocalAction(action);
    }

    public void updateUndoRedoActions(boolean canUndo, boolean canRedo) {
        if (undoAction != null) {
            undoAction.setEnabled(canUndo);
        }
        if (redoAction != null) {
            redoAction.setEnabled(canRedo);
        }
    }

    public void updateNavigationButtons(boolean canGoBack, boolean canGoForward) {
        if (backAction != null) {
            backAction.setEnabled(canGoBack);
        }
        if (forwardAction != null) {
            forwardAction.setEnabled(canGoForward);
        }
    }
}