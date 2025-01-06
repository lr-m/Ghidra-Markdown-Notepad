package ghidra.notepad;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Date;
import java.util.List;
import javax.imageio.ImageIO;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

/**
 * Dialog window for importing images into markdown documents. Supports
 * drag-and-drop, clipboard paste, and file selection operations, with
 * automatic image scaling and markdown link generation.
 */
public class ImageImportDialog extends JDialog {
    private JLabel imageLabel;
    private BufferedImage currentImage;
    private String currentImageFormat = "png"; // Default format
    private final JButton pasteButton = new JButton("Paste from Clipboard");
    private final JButton saveButton = new JButton("Save Image");
    private final MarkdownNotepadProvider provider;

    public ImageImportDialog(Window owner, MarkdownNotepadProvider provider) {
        super(owner, "Import Image", ModalityType.APPLICATION_MODAL);
        this.provider = provider;
        setSize(800, 600);
        setLocationRelativeTo(owner);

        saveButton.setEnabled(false);
        
        initializeComponents();
        setupDropTarget();
        setupKeyboardShortcuts();
    }

    private void initializeComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Create image display area
        imageLabel = new JLabel("<html><center>Drag an image here or paste from clipboard<br>" + 
            "Supported formats: PNG, JPG, JPEG</center></html>");
        imageLabel.setHorizontalAlignment(JLabel.CENTER);
        imageLabel.setPreferredSize(new Dimension(760, 500));
        imageLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY));

        // Button panel
        JPanel buttonPanel = new JPanel();
        pasteButton.addActionListener(e -> pasteFromClipboard());
        saveButton.addActionListener(e -> saveImage());

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());

        buttonPanel.add(pasteButton);
        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);

        mainPanel.add(imageLabel, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }

    private void setupDropTarget() {
        new DropTarget(imageLabel, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    @SuppressWarnings("unchecked")
                    List<File> droppedFiles = (List<File>) dtde.getTransferable()
                        .getTransferData(DataFlavor.javaFileListFlavor);

                    if (!droppedFiles.isEmpty()) {
                        File imageFile = droppedFiles.get(0);
                        if (isImageFile(imageFile)) {
                            // Get the file extension
                            String fileName = imageFile.getName().toLowerCase();
                            currentImageFormat = fileName.substring(fileName.lastIndexOf('.') + 1);
                            if (currentImageFormat.equals("jpeg")) {
                                currentImageFormat = "jpg";
                            }
                            loadImage(imageFile);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void setupKeyboardShortcuts() {
        getRootPane().registerKeyboardAction(
            e -> pasteFromClipboard(),
            KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        );
    }

    private boolean isImageFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
    }

    private void loadImage(File file) {
        try {
            currentImage = ImageIO.read(file);
            displayImage();
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error loading image: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void pasteFromClipboard() {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            if (clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) {
                currentImage = (BufferedImage) clipboard.getData(DataFlavor.imageFlavor);
                currentImageFormat = "png"; // Default to PNG for clipboard images
                displayImage();
            }
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error pasting image: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void displayImage() {
        if (currentImage != null) {
            // Scale image to fit if needed
            ImageIcon icon = new ImageIcon(currentImage);
            if (icon.getIconWidth() > 760 || icon.getIconHeight() > 500) {
                icon = new ImageIcon(currentImage.getScaledInstance(
                    760, 500, Image.SCALE_SMOOTH));
            }
            imageLabel.setIcon(icon);
            imageLabel.setText("");
            saveButton.setEnabled(true);
            
            // Update window title to show image format
            setTitle("Import Image - " + currentImageFormat.toUpperCase());
        }
    }

    private void saveImage() {
        Path currentDir = provider.getCurrentDirectory();
        if (currentImage == null || currentDir == null) return;

        try {
            // Create images directory if it doesn't exist
            Path imagesDir = currentDir.resolve("images");
            if (!Files.exists(imagesDir)) {
                Files.createDirectory(imagesDir);
            }

            // Generate unique filename
            String timestamp = String.format("%1$tY%1$tm%1$td_%1$tH%1$tM%1$tS", new Date());
            String filename = timestamp + "." + currentImageFormat;
            Path imagePath = imagesDir.resolve(filename);

            // Save image in original format
            ImageIO.write(currentImage, currentImageFormat, imagePath.toFile());

            // Insert markdown link at cursor position
            RSyntaxTextArea currentEditor = provider.getCurrentEditor();
            if (currentEditor != null) {
                String markdownLink = String.format("![%s](/images/%s)",
                    filename.substring(0, filename.lastIndexOf('.')), filename);
                currentEditor.replaceSelection(markdownLink);
            }

            // Refresh the tree to show the new image
            provider.refreshTree();

            dispose();

        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error saving image: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }
}