package ghidra.notepad;

import javax.swing.*;
import javax.swing.tree.*;
import java.nio.file.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Enumeration;

/**
 * Manages the file tree structure and operations, including adding,
 * finding, and updating nodes. Handles path resolution and maintains
 * the expanded state of directories during tree updates.
 */
public class TreeOperations {
    private final DefaultMutableTreeNode rootNode;
    private final DefaultTreeModel treeModel;
    private final JTree fileTree;
    private Path currentDirectory;

    public TreeOperations(DefaultMutableTreeNode rootNode, DefaultTreeModel treeModel, 
                         JTree fileTree) {
        this.rootNode = rootNode;
        this.treeModel = treeModel;
        this.fileTree = fileTree;
    }

    public void setCurrentDirectory(Path currentDirectory) {
        this.currentDirectory = currentDirectory;
    }

    public void addDirectoryToTree(Path dir) {
        if (dir.equals(currentDirectory)) {
            return; // Skip the root directory
        }

        Path relativePath = currentDirectory.relativize(dir);
        DefaultMutableTreeNode current = rootNode;
        
        for (int i = 0; i < relativePath.getNameCount(); i++) {
            Path subPath = relativePath.getName(i);
            DefaultMutableTreeNode node = findChildByName(current, subPath.toString());
            if (node == null) {
                node = new DefaultMutableTreeNode(subPath.toString());
                current.add(node);
            }
            current = node;
        }
    }

    public void addFileToTree(Path file) {
        Path relativePath = currentDirectory.relativize(file);
        DefaultMutableTreeNode current = rootNode;
        
        for (int i = 0; i < relativePath.getNameCount() - 1; i++) {
            Path subPath = relativePath.getName(i);
            DefaultMutableTreeNode node = findChildByName(current, subPath.toString());
            if (node == null) {
                node = new DefaultMutableTreeNode(subPath.toString());
                current.add(node);
            }
            current = node;
        }

        current.add(new DefaultMutableTreeNode(new FileNode(file)));
    }

    public DefaultMutableTreeNode findChildByName(DefaultMutableTreeNode parent, String name) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) parent.getChildAt(i);
            if (node.getUserObject().toString().equals(name)) {
                return node;
            }
        }
        return null;
    }

    public DefaultMutableTreeNode findNodeForPath(Path path) {
        Enumeration<TreeNode> e = rootNode.depthFirstEnumeration();
        while (e.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) e.nextElement();
            if (node.getUserObject() instanceof FileNode) {
                FileNode fileNode = (FileNode) node.getUserObject();
                if (fileNode.getPath().equals(path)) {
                    return node;
                }
            }
        }
        return null;
    }

    public Path getNodePath(DefaultMutableTreeNode node) {
        List<String> pathParts = new ArrayList<>();
        DefaultMutableTreeNode current = node;
        
        while (current != rootNode) {
            Object userObject = current.getUserObject();
            if (userObject instanceof String) {
                pathParts.add(0, (String) userObject);
            } else if (userObject instanceof FileNode) {
                pathParts.add(0, ((FileNode) userObject).getPath().getFileName().toString());
            }
            current = (DefaultMutableTreeNode) current.getParent();
        }
        
        Path result = currentDirectory;
        for (String part : pathParts) {
            result = result.resolve(part);
        }
        return result;
    }

    public Enumeration<TreePath> getExpandedPaths() {
        TreePath root = new TreePath(rootNode);
        return fileTree.getExpandedDescendants(root);
    }

    public void restoreExpandedPaths(Enumeration<TreePath> paths) {
        if (paths != null) {
            while (paths.hasMoreElements()) {
                TreePath path = paths.nextElement();
                TreePath newPath = findMatchingPath(path);
                if (newPath != null) {
                    fileTree.expandPath(newPath);
                }
            }
        }
    }

    private TreePath findMatchingPath(TreePath oldPath) {
        Object[] oldObjects = oldPath.getPath();
        DefaultMutableTreeNode current = rootNode;
        List<Object> newObjects = new ArrayList<>();
        newObjects.add(current);
        
        for (int i = 1; i < oldObjects.length; i++) {
            String nodeName = oldObjects[i].toString();
            DefaultMutableTreeNode child = findChildByName(current, nodeName);
            if (child == null) {
                return null;
            }
            current = child;
            newObjects.add(child);
        }
        
        return new TreePath(newObjects.toArray());
    }

    public void refreshTree() {
        if (currentDirectory != null) {
            Enumeration<TreePath> expandedPaths = getExpandedPaths();
            
            // Clear and reload the tree
            rootNode.removeAllChildren();
            rootNode.setUserObject(currentDirectory.getFileName().toString());

            try {
                // First, create the directory structure
                Files.walk(currentDirectory)
                    .filter(Files::isDirectory)
                    .forEach(this::addDirectoryToTree);

                // Then add both markdown and image files
                Files.walk(currentDirectory)
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.toString().toLowerCase();
                        return name.endsWith(".md") || 
                            name.endsWith(".png") || 
                            name.endsWith(".jpg") || 
                            name.endsWith(".jpeg") || 
                            name.endsWith(".gif");
                    })
                    .forEach(this::addFileToTree);
            } catch (IOException e) {
                e.printStackTrace();
            }
            
            treeModel.reload();
            fileTree.expandRow(0);
            restoreExpandedPaths(expandedPaths);
        }
    }

    public Path getCurrentSelectedDirectory() {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) 
            fileTree.getLastSelectedPathComponent();
        
        if (node == null) {
            return currentDirectory;
        }
        
        while (node != null) {
            Object userObject = node.getUserObject();
            if (userObject instanceof FileNode) {
                Path path = ((FileNode) userObject).getPath();
                return path.getParent();
            } else if (userObject instanceof String) {
                if (node == rootNode) {
                    return currentDirectory;
                } else {
                    StringBuilder pathBuilder = new StringBuilder();
                    Object[] path = node.getUserObjectPath();
                    for (int i = 1; i < path.length; i++) {
                        pathBuilder.append(path[i].toString()).append(File.separator);
                    }
                    return currentDirectory.resolve(pathBuilder.toString());
                }
            }
            node = (DefaultMutableTreeNode) node.getParent();
        }
        
        return currentDirectory;
    }
}