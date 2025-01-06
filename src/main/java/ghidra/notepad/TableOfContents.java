package ghidra.notepad;

import generic.theme.Gui;
import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.util.*;
import java.util.regex.*;
import javax.swing.Timer;

public class TableOfContents extends JPanel {
    private final JTree tocTree;
    private final DefaultMutableTreeNode rootNode;
    private final DefaultTreeModel treeModel;
    private final javax.swing.Timer updateTimer;
    private TocCallback callback;
    
    public interface TocCallback {
        void jumpToPosition(int position);
    }
    
    private static class TocEntry {
        String text;
        int level;
        int position;
        
        TocEntry(String text, int level, int position) {
            this.text = text;
            this.level = level;
            this.position = position;
        }
        
        @Override
        public String toString() {
            return text;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TocEntry that = (TocEntry) o;
            return level == that.level && text.equals(that.text);
        }

        @Override
        public int hashCode() {
            return Objects.hash(text, level);
        }
    }
    
    public TableOfContents() {
        setLayout(new BorderLayout());
        rootNode = new DefaultMutableTreeNode("Table of Contents");
        treeModel = new DefaultTreeModel(rootNode);
        tocTree = new JTree(treeModel);
        tocTree.setRootVisible(false);
        
        // Create a debounced timer for updates
        updateTimer = new javax.swing.Timer(300, e -> performUpdate());
        updateTimer.setRepeats(false);
        
        // Get system colors from Gui
        Color background = Gui.getColor("color.bg");
        Color foreground = Gui.getColor("color.fg");
        Color selectionBackground = Gui.getColor("color.bg.selection");
        Color selectionForeground = Gui.getColor("color.fg");
        
        // Set background colors
        setBackground(background);
        tocTree.setBackground(background);
        tocTree.setForeground(foreground);
        
        JScrollPane scrollPane = new JScrollPane(tocTree);
        scrollPane.setBackground(background);
        scrollPane.getViewport().setBackground(background);
        
        // Set tree renderer to handle colors properly
        DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value,
                    boolean sel, boolean expanded, boolean leaf, int row,
                    boolean hasFocus) {
                Component c = super.getTreeCellRendererComponent(tree, value, sel,
                    expanded, leaf, row, hasFocus);
                if (!sel) {
                    c.setBackground(background);
                    c.setForeground(foreground);
                }
                return c;
            }
        };
        
        // Set renderer colors
        renderer.setBackgroundNonSelectionColor(background);
        renderer.setBackgroundSelectionColor(selectionBackground);
        renderer.setTextNonSelectionColor(foreground);
        renderer.setTextSelectionColor(selectionForeground);
        
        tocTree.setCellRenderer(renderer);
        
        // Add selection listener
        tocTree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode)
                tocTree.getLastSelectedPathComponent();
            if (node != null && node.getUserObject() instanceof TocEntry && callback != null) {
                TocEntry entry = (TocEntry) node.getUserObject();
                callback.jumpToPosition(entry.position);
            }
        });
        
        add(scrollPane, BorderLayout.CENTER);
    }

    private String currentContent;
    
    public void scheduleUpdate(String content) {
        currentContent = content;
        updateTimer.restart();
    }
    
    private void performUpdate() {
        updateToc(currentContent, callback);
    }
    
    public void updateToc(String content, TocCallback callback) {
        this.callback = callback;
        
        if (content == null || content.isEmpty()) {
            if (rootNode.getChildCount() > 0) {
                rootNode.removeAllChildren();
                treeModel.reload();
            }
            return;
        }
        
        // Get current expanded paths
        Set<String> expandedPaths = new HashSet<>();
        Enumeration<TreePath> expanded = tocTree.getExpandedDescendants(new TreePath(rootNode));
        if (expanded != null) {
            while (expanded.hasMoreElements()) {
                TreePath path = expanded.nextElement();
                expandedPaths.add(getPathString(path));
            }
        }
        
        // Match any level of headers
        Pattern headerPattern = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*#*$", Pattern.MULTILINE);
        Matcher matcher = headerPattern.matcher(content);
        
        // Build new tree structure
        DefaultMutableTreeNode[] lastNodes = new DefaultMutableTreeNode[7];
        lastNodes[0] = rootNode;
        rootNode.removeAllChildren();
        
        while (matcher.find()) {
            String hashes = matcher.group(1);
            String headerText = matcher.group(2).trim();
            int level = hashes.length();
            int position = matcher.start();
            
            TocEntry entry = new TocEntry(headerText, level, position);
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(entry);
            
            // Find appropriate parent
            DefaultMutableTreeNode parent = null;
            for (int i = level - 1; i >= 0; i--) {
                if (lastNodes[i] != null) {
                    parent = lastNodes[i];
                    break;
                }
            }
            if (parent == null) parent = rootNode;
            
            parent.add(node);
            lastNodes[level] = node;
            for (int i = level + 1; i < lastNodes.length; i++) {
                lastNodes[i] = null;
            }
        }
        
        treeModel.reload();

        // Expand all nodes by default
        for (int i = 0; i < tocTree.getRowCount(); i++) {
            tocTree.expandRow(i);
        }
        
        // Restore expanded paths
        restoreExpandedPaths(rootNode, "", expandedPaths);
    }
    
    private String getPathString(TreePath path) {
        StringBuilder sb = new StringBuilder();
        Object[] pathElements = path.getPath();
        for (int i = 1; i < pathElements.length; i++) { // Skip root
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) pathElements[i];
            if (node.getUserObject() instanceof TocEntry) {
                TocEntry entry = (TocEntry) node.getUserObject();
                sb.append("/").append(entry.level).append("-").append(entry.text);
            }
        }
        return sb.toString();
    }
    
    private void restoreExpandedPaths(DefaultMutableTreeNode node, String currentPath, Set<String> expandedPaths) {
        if (!currentPath.isEmpty() && expandedPaths.contains(currentPath)) {
            tocTree.expandPath(new TreePath(node.getPath()));
        }
        
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            if (child.getUserObject() instanceof TocEntry) {
                TocEntry entry = (TocEntry) child.getUserObject();
                String childPath = currentPath + "/" + entry.level + "-" + entry.text;
                restoreExpandedPaths(child, childPath, expandedPaths);
            }
        }
    }
}