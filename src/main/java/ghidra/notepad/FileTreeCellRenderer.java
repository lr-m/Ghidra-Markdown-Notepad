package ghidra.notepad;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.HashMap;
import javax.imageio.ImageIO;

import generic.theme.Gui;
import ghidra.notepad.DocumentStateHandler;

public class FileTreeCellRenderer extends DefaultTreeCellRenderer {
    private static final int THUMBNAIL_SIZE = 16; // Size to match other icons
    private final Icon folderIcon = UIManager.getIcon("Tree.closedIcon");
    private final Icon expandedFolderIcon = UIManager.getIcon("Tree.openIcon");
    private final Icon fileIcon = UIManager.getIcon("Tree.leafIcon");
    private final Map<Path, Icon> imageIconCache = new HashMap<>();
    private final DocumentStateHandler documentStateHandler;
    private final DefaultMutableTreeNode rootNode;
    private final Color background;
    private final Color foreground;
    private final Color selectionBackground;
    private final Color selectionForeground;
    
    public FileTreeCellRenderer(DocumentStateHandler documentStateHandler, DefaultMutableTreeNode rootNode) {
        this.documentStateHandler = documentStateHandler;
        this.rootNode = rootNode;
        
        // Get system colors from Gui to match TableOfContents
        this.background = Gui.getColor("color.bg");
        this.foreground = Gui.getColor("color.fg");
        this.selectionBackground = Gui.getColor("color.bg.selection");
        this.selectionForeground = Gui.getColor("color.fg");
        
        // Set renderer colors
        setBackgroundNonSelectionColor(background);
        setBackgroundSelectionColor(selectionBackground);
        setTextNonSelectionColor(foreground);
        setTextSelectionColor(selectionForeground);
        
        // Important: Set these to null to allow proper background painting
        setBackground(null);
        setOpaque(false);
    }
    
    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value,
            boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
        
        // Set transparent background when not selected
        if (!sel) {
            setBackground(null);
            setOpaque(false);
        } else {
            setBackground(selectionBackground);
            setOpaque(true);
        }
        
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
        if (node == rootNode) {
            setIcon(expanded ? expandedFolderIcon : folderIcon);
            setText(value.toString());
        } else if (node.getUserObject() instanceof FileNode) {
            FileNode fileNode = (FileNode) node.getUserObject();
            Path filePath = fileNode.getPath();
            String fileName = filePath.getFileName().toString().toLowerCase();
            
            if (fileName.endsWith(".png") || fileName.endsWith(".jpg") || 
                fileName.endsWith(".jpeg") || fileName.endsWith(".gif")) {
                // Get or create thumbnail icon
                Icon thumbnail = imageIconCache.computeIfAbsent(filePath, this::createThumbnail);
                setIcon(thumbnail != null ? thumbnail : fileIcon);
            } else {
                setIcon(fileIcon);
            }
            
            // Handle unsaved changes indicator for markdown files
            if (fileName.endsWith(".md")) {
                DocumentState state = documentStateHandler.getDocumentState(fileNode.getPath());
                if (state != null && state.hasUnsavedChanges()) {
                    setText("*" + fileNode.toString());
                } else {
                    setText(fileNode.toString());
                }
            } else {
                setText(fileNode.toString());
            }
            setToolTipText(fileNode.toString());
        } else {
            setIcon(expanded ? expandedFolderIcon : folderIcon);
            setText(value.toString());
        }
        
        return this;
    }
    
    private Icon createThumbnail(Path imagePath) {
        try {
            BufferedImage img = ImageIO.read(imagePath.toFile());
            if (img != null) {
                // Scale the image maintaining aspect ratio
                double scale = Math.min(
                    (double) THUMBNAIL_SIZE / img.getWidth(),
                    (double) THUMBNAIL_SIZE / img.getHeight()
                );
                int width = (int) (img.getWidth() * scale);
                int height = (int) (img.getHeight() * scale);
                
                BufferedImage thumbnail = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2d = thumbnail.createGraphics();
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2d.drawImage(img, 0, 0, width, height, null);
                g2d.dispose();
                
                return new ImageIcon(thumbnail);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    public void clearCache() {
        imageIconCache.clear();
    }
}