package ghidra.notepad;

import ghidra.framework.options.Options;
import ghidra.framework.plugintool.ComponentProviderAdapter;
import ghidra.framework.plugintool.PluginTool;

import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Color;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;

import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;

import javax.swing.*;
import javax.swing.tree.*;
import javax.swing.event.DocumentListener;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import org.fife.ui.rsyntaxtextarea.*;
import org.fife.ui.rtextarea.RTextScrollPane;

import docking.*;
import docking.action.*;

import ghidra.program.util.ProgramLocation;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Program;
import ghidra.app.events.ProgramLocationPluginEvent;
import ghidra.app.services.ProgramManager;

import javax.swing.TransferHandler;
import java.awt.dnd.*;

import java.awt.Rectangle;
import java.util.Collections;
import java.util.List;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import ghidra.notepad.SearchUtils.SearchResultCallback;
import ghidra.notepad.DocumentState;
import ghidra.notepad.ImageImportDialog;
import ghidra.notepad.ActionManager;
import ghidra.notepad.EditorUtils;
import ghidra.notepad.PreviewUtils;
import ghidra.notepad.FileOperations;
import ghidra.notepad.FileNode;
import ghidra.notepad.FileTreeCellRenderer;
import ghidra.notepad.DocumentStateHandler;
import ghidra.notepad.FileStateHandler;
import ghidra.notepad.SearchUtils;

/**
 * Core component provider that implements the main notepad interface.
 * Manages the editor, preview pane, file tree, and coordinates all
 * user interactions and document management operations.
 */
public class MarkdownNotepadProvider extends ComponentProviderAdapter 
        implements DocumentStateHandler, FileStateHandler {
    private static final String LAST_COLLECTION_PREFERENCE = "LastCollectionPath";
    private static final String WINDOW_TITLE = "Markdown Notepad";
    
    private JPanel mainPanel;
    private RSyntaxTextArea editor;
    private JTree fileTree;
    private DefaultMutableTreeNode rootNode;
    private DefaultTreeModel treeModel;
    private Path currentDirectory;
    private Path currentFile;
    private Map<Path, DocumentState> documentStates;
    private DocumentState currentDocument;
    private List<DocumentListener> documentListeners;

    private JTabbedPane tabbedPane;
    private JEditorPane previewPane;
    private Parser markdownParser;
    private HtmlRenderer htmlRenderer;
    private Timer previewUpdateTimer;
    private JSplitPane splitPane;
    private Program currentProgram;

    private ActionManager actionManager;
    private PreviewUtils previewUtils;
    private FileOperations fileOperations;
    private TreeOperations treeOperations;

    private JPanel searchPanel;
    private JTextField searchField;
    private JButton nextButton;
    private JButton prevButton;
    private List<Integer> currentSearchPositions = Collections.emptyList(); // Initialize to empty list
    private int currentSearchIndex = -1;

    public MarkdownNotepadProvider(PluginTool tool, String owner) {
        super(tool, WINDOW_TITLE, owner);
        setIcon(new ImageIcon(getClass().getResource("/images/logo.png")));
        documentStates = new HashMap<>();
        documentListeners = new ArrayList<>();
        initializeComponents();
        fileOperations = new FileOperations(
            mainPanel,
            previewPane,
            currentDirectory,
            tabbedPane,
            this::refreshTree,
            this::loadFile,
            this,  // DocumentStateHandler
            this,  // FileStateManager
            treeModel,
            fileTree
        );
        actionManager = new ActionManager(this, tool);
        loadLastCollection();
        setVisible(true);
    }

    public Path getCurrentDirectory() {
        return currentDirectory;
    }

    protected RSyntaxTextArea getCurrentEditor() {
        return editor;
    }

    private void saveCollectionPreference(Path collectionPath) {
        if (collectionPath != null) {
            Options options = tool.getOptions("MarkdownNotepad");
            options.setString(LAST_COLLECTION_PREFERENCE, collectionPath.toString());
        }
    }

    private void loadLastCollection() {
        Options options = tool.getOptions("MarkdownNotepad");
        String lastCollectionPath = options.getString(LAST_COLLECTION_PREFERENCE, null);
        if (lastCollectionPath != null && !lastCollectionPath.isEmpty()) {
            Path path = Paths.get(lastCollectionPath);
            if (Files.exists(path) && Files.isDirectory(path)) {
                loadDirectory(path);
            }
        }
    }

    private void navigateToAddress(String addressStr) {
        // Get the program manager service
        ProgramManager programManager = tool.getService(ProgramManager.class);
        if (programManager == null || programManager.getCurrentProgram() == null) {
            JOptionPane.showMessageDialog(mainPanel,
                "No program is currently open",
                "Navigation Error",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            // Remove any brackets and '0x' prefix
            addressStr = addressStr.replaceAll("[\\[\\]]", "").replaceAll("^0x", "");
            
            // Parse the address
            Program program = programManager.getCurrentProgram();
            long offset = Long.parseLong(addressStr, 16);
            Address address = program.getAddressFactory().getDefaultAddressSpace().getAddress(offset);
            
            // Create a new program location and fire the event
            ProgramLocation location = new ProgramLocation(program, address);
            tool.firePluginEvent(new ProgramLocationPluginEvent(getName(), location, program));
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(mainPanel,
                "Invalid address format: " + addressStr,
                "Navigation Error",
                JOptionPane.WARNING_MESSAGE);
        }
    }

    private void initializeComponents() {
        mainPanel = new JPanel(new BorderLayout());
        
        // Initialize markdown parser and renderer
        markdownParser = Parser.builder()
            .extensions(Arrays.asList(TablesExtension.create()))
            .build();
        htmlRenderer = HtmlRenderer.builder()
            .extensions(Arrays.asList(TablesExtension.create()))
            .build();
        
        // Create tabbed pane
        tabbedPane = new JTabbedPane();
        
        // Initialize a default editor that will be replaced with document-specific editors
        // Initialize a default editor
        editor = new RSyntaxTextArea(20, 60);
        editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_MARKDOWN);
        editor.setCodeFoldingEnabled(true);
        editor.setAntiAliasingEnabled(true);
        editor.setLineWrap(true);
        editor.setWrapStyleWord(true);
        EditorUtils.applyEditorStyling(editor);
        
        // Initialize file tree components
        rootNode = new DefaultMutableTreeNode("Select a directory...");
        treeModel = new DefaultTreeModel(rootNode);
        fileTree = new JTree(treeModel);
        
        // Enable drag and drop
        fileTree.setDragEnabled(true);
        fileTree.setDropMode(DropMode.ON_OR_INSERT);
        fileTree.setTransferHandler(new FileTreeTransferHandler());
        
        // Custom cell renderer to show unsaved changes and proper icons
        FileTreeCellRenderer renderer = new FileTreeCellRenderer(this, rootNode);
        fileTree.setCellRenderer(renderer);

        // After creating the fileTree, add these lines:
        fileTree.setRowHeight(0); // Let rows be as tall as they need to be
        fileTree.setLargeModel(true); // Optimize for large trees
        fileTree.setExpandsSelectedPaths(true);
        
        addTreeListener();

        treeOperations = new TreeOperations(rootNode, treeModel, fileTree);
        
        // Create preview panel
        previewPane = new JEditorPane();
        previewPane.setEditable(false);
        previewPane.setContentType("text/html");
        previewUtils = new PreviewUtils(previewPane, markdownParser, htmlRenderer, mainPanel, previewUpdateTimer, editor);
        previewUtils.setAddressNavigationHandler(this::navigateToAddress);

        // Create preview update timer
        previewUpdateTimer = new Timer(500, e -> previewUtils.updatePreview(editor.getText()));
        previewUpdateTimer.setRepeats(false);
        
        // Add components to tabs
        tabbedPane.addTab("Edit", new RTextScrollPane(editor, false));
        tabbedPane.addTab("Preview", new JScrollPane(previewPane));
        
        // Add tab change listener
        tabbedPane.addChangeListener(e -> {
            if (tabbedPane.getSelectedIndex() == 1) { // Preview tab
                previewUtils.updatePreview(editor.getText());
            }
        });
        
        // Create split pane with file tree and tabbed pane
        splitPane = new JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            new JScrollPane(fileTree),
            tabbedPane
        );
        splitPane.setDividerLocation(200);
        
        mainPanel.add(splitPane, BorderLayout.CENTER);
    }

    private void addTreeListener() {
        // Mouse listener for selection and context menu
        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                TreePath path = fileTree.getPathForLocation(e.getX(), e.getY());
                if (path == null) return;
                
                DefaultMutableTreeNode node = (DefaultMutableTreeNode)
                    path.getLastPathComponent();
                Object userObject = node.getUserObject();
                
                if (e.isPopupTrigger()) {
                    if (userObject instanceof FileNode) {
                        showFileContextMenu(e, (FileNode) userObject);
                    } else if (userObject instanceof String && node != rootNode) {
                        showDirectoryContextMenu(e, node);
                    }
                }
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                TreePath path = fileTree.getPathForLocation(e.getX(), e.getY());
                if (path == null) return;
                
                DefaultMutableTreeNode node = (DefaultMutableTreeNode)
                    path.getLastPathComponent();
                Object userObject = node.getUserObject();
                
                if (e.isPopupTrigger()) {
                    if (userObject instanceof FileNode) {
                        showFileContextMenu(e, (FileNode) userObject);
                    } else if (userObject instanceof String && node != rootNode) {
                        showDirectoryContextMenu(e, node);
                    }
                }
            }
            
            private void showFileContextMenu(MouseEvent e, FileNode fileNode) {
                JPopupMenu contextMenu = new JPopupMenu();
                
                JMenuItem renameItem = new JMenuItem("Rename");
                renameItem.addActionListener(ev -> fileOperations.renameFile(fileNode));
                contextMenu.add(renameItem);
                
                JMenuItem deleteItem = new JMenuItem("Delete");
                deleteItem.addActionListener(ev -> fileOperations.deleteFile(fileNode));
                contextMenu.add(deleteItem);
                
                contextMenu.show(e.getComponent(), e.getX(), e.getY());
            }

            private void showDirectoryContextMenu(MouseEvent e, DefaultMutableTreeNode dirNode) {
                JPopupMenu contextMenu = new JPopupMenu();
                
                JMenuItem renameItem = new JMenuItem("Rename");
                renameItem.addActionListener(ev -> fileOperations.renameDirectory(dirNode, treeOperations.getNodePath(dirNode)));
                contextMenu.add(renameItem);
                
                JMenuItem deleteItem = new JMenuItem("Delete");
                deleteItem.addActionListener(ev -> fileOperations.deleteDirectory(dirNode, treeOperations.getNodePath(dirNode)));
                contextMenu.add(deleteItem);
                
                contextMenu.show(e.getComponent(), e.getX(), e.getY());
            }
        };
        
        // Add both the selection listener and mouse listener
        fileTree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) 
                fileTree.getLastSelectedPathComponent();
            if (node != null && node.getUserObject() instanceof FileNode) {
                loadFile(((FileNode) node.getUserObject()).getPath());
            }
        });
        
        fileTree.addMouseListener(mouseAdapter);
    }

    @Override
    public void updateDocumentStates(Path oldPath, Path newPath) {
        Map<Path, DocumentState> newStates = new HashMap<>();
        documentStates.forEach((path, state) -> {
            if (path.startsWith(oldPath)) {
                Path relativePath = oldPath.relativize(path);
                Path newPathResolved = newPath.resolve(relativePath);
                newStates.put(newPathResolved, state);
                
                if (currentFile != null && currentFile.equals(path)) {
                    currentFile = newPathResolved;
                    previewUtils.setCurrentFile(currentFile);
                }
            } else {
                newStates.put(path, state);
            }
        });
        documentStates.clear();
        documentStates.putAll(newStates);
    }

    @Override
    public void removeDocumentState(Path path) {
        documentStates.remove(path);
    }

    @Override
    public DocumentState getDocumentState(Path path) {
        return documentStates.get(path);
    }

    @Override
    public Path getCurrentFile() {
        return currentFile;
    }

    @Override
    public void setCurrentFile(Path file) {
        currentFile = file;
        previewUtils.setCurrentFile(file);
    }

    @Override
    public void clearCurrentFile() {
        currentFile = null;
        previewUtils.setCurrentFile(null);
    }

    @Override
    public void setEditorText(String text) {
        editor.setText(text);
    }

    @Override
    public void swapEditor(RSyntaxTextArea newEditor) {
        editor = newEditor;
        RTextScrollPane scrollPane = new RTextScrollPane(editor, false);
        tabbedPane.setComponentAt(0, scrollPane);
    }

    private void loadFile(Path file) {
        if (currentFile != null && currentDocument != null) {
            documentStates.put(currentFile, currentDocument);
        }
        
        currentFile = file;
        previewUtils.setCurrentFile(currentFile);
        String fileName = file.getFileName().toString().toLowerCase();
        
        // Check if this is an image file
        if (fileName.endsWith(".png") || fileName.endsWith(".jpg") || 
            fileName.endsWith(".jpeg")) {
            fileOperations.loadImagePreview(file, editor.getBackground(), editor.getForeground());
            return;
        }
        
        // Re-enable edit tab for non-image files
        tabbedPane.setEnabledAt(0, true);
        
        // Find the tree node for this file
        DefaultMutableTreeNode node = treeOperations.findNodeForPath(file);
        
        // Load existing document state or create new one
        currentDocument = documentStates.computeIfAbsent(file, k -> {
            try {
                return new DocumentState(Files.readString(k), k, treeModel, node, fileTree,
                    this::updateUndoRedoActions);  // Pass the callback
            } catch (IOException e) {
                e.printStackTrace();
                return new DocumentState("", k, treeModel, node, fileTree,
                    this::updateUndoRedoActions);  // Pass the callback
            }
        });
        
        // Apply styling to the editor after creation
        EditorUtils.applyEditorStyling(currentDocument.getEditor());
        
        // Swap the editor in the UI
        RTextScrollPane scrollPane = new RTextScrollPane(currentDocument.getEditor(), false);
        tabbedPane.setComponentAt(0, scrollPane);
        editor = currentDocument.getEditor();
        
        // Update preview
        previewUtils.updatePreview(editor.getText());
        
        // Update initial undo/redo states
        updateUndoRedoActions();
    }

    public void loadDirectory(Path directory) {
        // Clear the editor and preview when loading a new collection
        clearEditorAndPreview();

        currentDirectory = directory;
        treeOperations.setCurrentDirectory(directory);
        previewUtils.setCurrentDirectory(currentDirectory);
        rootNode.removeAllChildren();
        rootNode.setUserObject(directory.getFileName().toString());

        try {
            // First, create the directory structure
            Files.walk(directory)
                .filter(Files::isDirectory)
                .forEach(dir -> treeOperations.addDirectoryToTree(dir));

            // Then add both markdown and image files
            Files.walk(directory)
                .filter(Files::isRegularFile)
                .filter(p -> {
                    String name = p.toString().toLowerCase();
                    return name.endsWith(".md") || 
                        name.endsWith(".png") || 
                        name.endsWith(".jpg") || 
                        name.endsWith(".jpeg") || 
                        name.endsWith(".gif");
                })
                .forEach(file -> treeOperations.addFileToTree(file));
                
            // Save the collection path preference
            saveCollectionPreference(directory);
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        treeModel.reload();
        fileTree.expandRow(0);
    }

    private void clearEditorAndPreview() {
        // Clear the editor
        editor.setText("");
        editor.discardAllEdits();
        
        // Clear the preview
        previewPane.setText("");
        
        // Clear current file references
        currentFile = null;
        currentDocument = null;
        
        // Clear document states
        documentStates.clear();
    }

    protected void refreshTree() {
        treeOperations.refreshTree();
    }

    public void performUndo() {
        if (currentDocument != null && currentDocument.getEditor().canUndo()) {
            currentDocument.getEditor().undoLastAction();
            updateUndoRedoActions();
        }
    }
    
    public void performRedo() {
        if (currentDocument != null && currentDocument.getEditor().canRedo()) {
            currentDocument.getEditor().redoLastAction();
            updateUndoRedoActions();
        }
    }
    
    // Update the existing updateUndoRedoActions method
    private void updateUndoRedoActions() {
        boolean canUndo = currentDocument != null && currentDocument.getEditor().canUndo();
        boolean canRedo = currentDocument != null && currentDocument.getEditor().canRedo();
        actionManager.updateUndoRedoActions(canUndo, canRedo);
    }

    public void createNewDirectory() {
        if (currentDirectory == null) {
            JOptionPane.showMessageDialog(mainPanel,
                "Please open a directory first",
                "No Directory Selected",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        String dirName = JOptionPane.showInputDialog(
            mainPanel,
            "Enter directory name:",
            "Create New Directory",
            JOptionPane.PLAIN_MESSAGE);
            
        if (dirName != null && !dirName.trim().isEmpty()) {
            fileOperations.createNewDirectory(treeOperations.getCurrentSelectedDirectory(), dirName);
        }
    }

    public void createNewCollection() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Select Location for New Collection");
        
        // Set initial directory to last used collection if available
        if (currentDirectory != null && Files.exists(currentDirectory)) {
            chooser.setCurrentDirectory(currentDirectory.toFile());
        }
        
        if (chooser.showSaveDialog(mainPanel) == JFileChooser.APPROVE_OPTION) {
            String collectionName = JOptionPane.showInputDialog(
                mainPanel,
                "Enter collection name:",
                "Create New Collection",
                JOptionPane.PLAIN_MESSAGE);
                
            if (collectionName != null && !collectionName.trim().isEmpty()) {
                try {
                    // Clear the editor and preview before creating new collection
                    clearEditorAndPreview();

                    Path collectionPath = chooser.getSelectedFile().toPath().resolve(collectionName);
                    Files.createDirectories(collectionPath);
                    fileOperations.createCollectionStructure(collectionPath);
                    loadDirectory(collectionPath);
                } catch (IOException e) {
                    JOptionPane.showMessageDialog(
                        mainPanel,
                        "Error creating collection: " + e.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }

    public void createNewDocument() {
        if (currentDirectory == null) {
            JOptionPane.showMessageDialog(mainPanel,
                "Please open a directory first",
                "No Directory Selected",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        String fileName = JOptionPane.showInputDialog(
            mainPanel,
            "Enter document name (without .md extension):",
            "Create New Document",
            JOptionPane.PLAIN_MESSAGE);
            
        if (fileName != null && !fileName.trim().isEmpty()) {
            fileOperations.createNewDocument(treeOperations.getCurrentSelectedDirectory(), fileName);
        }
    }


    // Update the save functionality:
    public void saveCurrentFile() {
        if (currentFile != null && currentDocument != null) {
            fileOperations.saveFile(currentFile, currentDocument.getContent());
            currentDocument.setUnsavedChanges(false);
        }
    }

    private class FileTreeTransferHandler extends TransferHandler {
        private final DataFlavor nodesFlavor;
        private final DataFlavor[] flavors = new DataFlavor[1];
        private DefaultMutableTreeNode[] nodesToRemove;

        public FileTreeTransferHandler() {
            try {
                String mimeType = DataFlavor.javaJVMLocalObjectMimeType +
                                ";class=\"" +
                                javax.swing.tree.DefaultMutableTreeNode[].class.getName() +
                                "\"";
                nodesFlavor = new DataFlavor(mimeType);
                flavors[0] = nodesFlavor;
            } catch(ClassNotFoundException e) {
                throw new RuntimeException("ClassNotFound: " + e.getMessage());
            }
        }

        @Override
        public boolean canImport(TransferHandler.TransferSupport support) {
            if(!support.isDrop()) {
                return false;
            }
            support.setShowDropLocation(true);
            if(!support.isDataFlavorSupported(nodesFlavor)) {
                return false;
            }
            
            // Get drop location and path
            JTree.DropLocation dl = (JTree.DropLocation)support.getDropLocation();
            TreePath dest = dl.getPath();
            DefaultMutableTreeNode target = (DefaultMutableTreeNode)dest.getLastPathComponent();
            
            // Get dragged node
            TreePath[] paths = fileTree.getSelectionPaths();
            if(paths == null || paths.length == 0) {
                return false;
            }
            
            DefaultMutableTreeNode node = (DefaultMutableTreeNode)paths[0].getLastPathComponent();
            
            // Don't allow dropping on files
            if(target.getUserObject() instanceof FileNode) {
                return false;
            }
            
            // Don't allow dropping onto itself
            if(node.equals(target)) {
                return false;
            }
            
            // Allow dropping on root
            if(target == rootNode) {
                return true;
            }
            
            // Don't allow dropping a parent into its child
            if(isAncestor(node, target)) {
                return false;
            }
            
            return true;
        }

        private boolean isAncestor(DefaultMutableTreeNode node, DefaultMutableTreeNode target) {
            if(node == null) {
                return false;
            }
            if(node.equals(target)) {
                return true;
            }
            return isAncestor((DefaultMutableTreeNode)node.getParent(), target);
        }

        @Override
        protected Transferable createTransferable(JComponent c) {
            JTree tree = (JTree)c;
            TreePath[] paths = tree.getSelectionPaths();
            if(paths != null) {
                DefaultMutableTreeNode[] nodes = new DefaultMutableTreeNode[paths.length];
                for(int i = 0; i < paths.length; i++) {
                    nodes[i] = (DefaultMutableTreeNode)paths[i].getLastPathComponent();
                }
                nodesToRemove = nodes;
                return new NodesTransferable(nodes);
            }
            return null;
        }

        @Override
        protected void exportDone(JComponent source, Transferable data, int action) {
            if(action == MOVE && nodesToRemove != null) {
                nodesToRemove = null;
            }
        }

        @Override
        public int getSourceActions(JComponent c) {
            return MOVE;
        }

        private Path getTargetPath(DefaultMutableTreeNode targetNode) {
            // For root node, return the current collection directory
            if (targetNode == rootNode) {
                return currentDirectory;
            }
            
            // Build the full path by walking up the tree
            List<String> pathParts = new ArrayList<>();
            DefaultMutableTreeNode current = targetNode;
            
            // Walk up the tree until we hit the root
            while (current != rootNode) {
                Object userObject = current.getUserObject();
                if (userObject instanceof String) {
                    pathParts.add(0, (String) userObject);
                } else if (userObject instanceof FileNode) {
                    pathParts.add(0, ((FileNode) userObject).getPath().getFileName().toString());
                }
                current = (DefaultMutableTreeNode) current.getParent();
            }
            
            // Construct the full path
            Path result = currentDirectory;
            for (String part : pathParts) {
                result = result.resolve(part);
            }
            return result;
        }

        @Override
        public boolean importData(TransferHandler.TransferSupport support) {
            if(!canImport(support)) {
                return false;
            }
            
            // Get drop location and path
            JTree.DropLocation dl = (JTree.DropLocation)support.getDropLocation();
            TreePath dest = dl.getPath();
            DefaultMutableTreeNode targetNode = (DefaultMutableTreeNode)dest.getLastPathComponent();
            
            // Extract transfer data
            DefaultMutableTreeNode[] nodes;
            try {
                Transferable t = support.getTransferable();
                nodes = (DefaultMutableTreeNode[])t.getTransferData(nodesFlavor);
            } catch(UnsupportedFlavorException | IOException e) {
                return false;
            }

            // For each node being transferred
            for(DefaultMutableTreeNode node : nodes) {
                try {
                    Path sourcePath;
                    if (node.getUserObject() instanceof FileNode) {
                        sourcePath = ((FileNode) node.getUserObject()).getPath();
                    } else if (node.getUserObject() instanceof String && node != rootNode) {
                        sourcePath = treeOperations.getNodePath(node);
                    } else {
                        continue;
                    }

                    // Get target directory
                    Path targetDirPath;
                    if (targetNode == rootNode) {
                        targetDirPath = currentDirectory;
                    } else {
                        targetDirPath = treeOperations.getNodePath(targetNode);
                    }
                    
                    Path targetPath = targetDirPath.resolve(sourcePath.getFileName());
                    
                    // Skip if source and target are the same
                    if (sourcePath.equals(targetPath)) {
                        continue;
                    }
                    
                    // Move the file or directory
                    Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    
                    // Update document states if needed
                    updateDocumentStates(sourcePath, targetPath);
                    
                } catch(IOException e) {
                    JOptionPane.showMessageDialog(mainPanel,
                        "Error moving item: " + e.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                    return false;
                }
            }
            
            // Refresh the tree after all moves are complete
            refreshTree();
            
            return true;
        }

        private class NodesTransferable implements Transferable {
            DefaultMutableTreeNode[] nodes;

            public NodesTransferable(DefaultMutableTreeNode[] nodes) {
                this.nodes = nodes;
            }

            public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
                if(!isDataFlavorSupported(flavor))
                    throw new UnsupportedFlavorException(flavor);
                return nodes;
            }

            public DataFlavor[] getTransferDataFlavors() {
                return flavors;
            }

            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return nodesFlavor.equals(flavor);
            }
        }
    }

    private void createSearchPanel() {
        searchPanel = new JPanel();
        searchPanel.setLayout(new BoxLayout(searchPanel, BoxLayout.X_AXIS));
        
        searchField = new JTextField(20);
        prevButton = new JButton("↑");
        nextButton = new JButton("↓");
        JButton closeButton = new JButton("×");
        
        prevButton.setEnabled(false);
        nextButton.setEnabled(false);
        
        // Simple search field action
        searchField.addActionListener(e -> performSearch());
        
        prevButton.addActionListener(e -> navigateSearch(false));
        nextButton.addActionListener(e -> navigateSearch(true));
        closeButton.addActionListener(e -> hideSearchPanel());
        
        searchPanel.add(searchField);
        searchPanel.add(prevButton);
        searchPanel.add(nextButton);
        searchPanel.add(closeButton);
        searchPanel.setVisible(false);
        
        mainPanel.add(searchPanel, BorderLayout.NORTH);
    }

    private void hideSearchPanel() {
        if (searchPanel != null) {
            searchPanel.setVisible(false);
            if (editor != null) {
                editor.getHighlighter().removeAllHighlights();
            }
            currentSearchPositions = Collections.emptyList();
            currentSearchIndex = -1;
        }
    }

    public void showDocumentSearchPanel() {
        if (searchPanel == null) {
            createSearchPanel();
        }
        searchPanel.setVisible(true);
        searchField.requestFocus();
    }

    private void performSearch() {
        String searchTerm = searchField.getText();
        if (searchTerm.isEmpty()) {
            currentSearchPositions = Collections.emptyList();
            currentSearchIndex = -1;
            prevButton.setEnabled(false);
            nextButton.setEnabled(false);
            return;
        }
        
        currentSearchPositions = SearchUtils.findAllPositions(editor.getText(), searchTerm, false);
        currentSearchIndex = -1;
        
        SearchUtils.highlightText(editor, searchTerm, false);
        
        boolean hasResults = !currentSearchPositions.isEmpty();
        prevButton.setEnabled(hasResults);
        nextButton.setEnabled(hasResults);
        
        if (hasResults) {
            navigateSearch(true);
        }
    }

    private void navigateSearch(boolean forward) {
        if (currentSearchPositions.isEmpty()) return;
        
        if (forward) {
            currentSearchIndex = (currentSearchIndex + 1) % currentSearchPositions.size();
        } else {
            currentSearchIndex = (currentSearchIndex - 1 + currentSearchPositions.size()) 
                % currentSearchPositions.size();
        }
        
        int position = currentSearchPositions.get(currentSearchIndex);
        editor.setCaretPosition(position);
        editor.requestFocus();
        
        try {
            Rectangle rect = editor.modelToView(position);
            editor.scrollRectToVisible(rect);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void showCollectionSearchDialog() {
        if (currentDirectory == null) {
            JOptionPane.showMessageDialog(mainPanel,
                "Please open a collection first",
                "No Collection",
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        SearchUtils.SearchResultCallback callback = (file, position, searchTerm) -> {
            loadFile(file);
            editor.setCaretPosition(position);
            editor.requestFocusInWindow();
            try {
                Rectangle rect = editor.modelToView(position);
                editor.scrollRectToVisible(rect);
                // Highlight the search term in the editor
                SearchUtils.highlightText(editor, searchTerm, false);
            } catch (Exception e) {
                e.printStackTrace();
            }
        };

        // Add method to clear highlights
        Runnable clearHighlights = () -> {
            if (editor != null) {
                editor.getHighlighter().removeAllHighlights();
            }
        };
        
        SearchUtils.showSearchDialog(mainPanel, currentDirectory, callback, clearHighlights);
    }

    @Override
    public JComponent getComponent() {
        return mainPanel;
    }
}
