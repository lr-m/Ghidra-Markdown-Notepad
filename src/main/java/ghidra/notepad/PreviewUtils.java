// PreviewUtils.java
package ghidra.notepad;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import javax.swing.*;
import javax.swing.text.html.*;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import generic.theme.Gui;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.SimpleAttributeSet;

/**
 * Handles the rendering and updating of the markdown preview pane.
 * Processes markdown content into HTML, manages image paths, and
 * provides support for clickable address references in the preview.
 */
public class PreviewUtils {
    private final JEditorPane previewPane;
    private final Parser markdownParser;
    private final HtmlRenderer htmlRenderer;
    private final Timer previewUpdateTimer;
    private final JPanel mainPanel;
    private Path currentFile;
    private Path currentDirectory;
    private final RSyntaxTextArea editor;  // Add this field
    private AddressNavigationHandler navigationHandler;
    private FunctionNameResolver functionNameResolver;

    public PreviewUtils(JEditorPane previewPane, Parser markdownParser, HtmlRenderer htmlRenderer, 
                       JPanel mainPanel, Timer previewUpdateTimer, RSyntaxTextArea editor) {
        this.previewPane = previewPane;
        this.markdownParser = markdownParser;
        this.htmlRenderer = htmlRenderer;
        this.mainPanel = mainPanel;
        this.previewUpdateTimer = previewUpdateTimer;
        this.editor = editor;
        initializeHtmlKit();

        // Initialize with a default resolver that just returns the address
        this.functionNameResolver = address -> address;
    }

    public void setAddressNavigationHandler(AddressNavigationHandler handler) {
        this.navigationHandler = handler;
    }

    public void setFunctionNameResolver(FunctionNameResolver resolver) {
        this.functionNameResolver = resolver;
    }

    public void setCurrentFile(Path currentFile) {
        this.currentFile = currentFile;
    }

    public void setCurrentDirectory(Path currentDirectory) {
        this.currentDirectory = currentDirectory;
    }

    private void navigateToAddress(String addressStr) {
        if (navigationHandler != null) {
            navigationHandler.navigateToAddress(addressStr);
        }
    }

    public void initializeHtmlKit() {
        HTMLEditorKit kit = new HTMLEditorKit();
        StyleSheet styleSheet = kit.getStyleSheet();

        // Get system colors that match the syntax highlighting
        Color headerColor = Gui.getColor("color.fg.decompiler.function.name");
        Color boldColor = Gui.getColor("color.fg.decompiler.keyword");
        Color italicColor = Gui.getColor("color.fg.decompiler.type");
        Color linkColor = Gui.getColor("color.fg.decompiler.variable");
        Color codeColor = Gui.getColor("color.fg.decompiler.comment");
        Color blockquoteColor = Gui.getColor("color.fg.decompiler.global");
        
        // Get the exact same colors as used in the editor
        Color background = Gui.getColor("color.bg");
        Color foreground = Gui.getColor("color.fg");

        // Convert colors to hex strings for CSS
        String headerHex = String.format("#%02x%02x%02x", 
            headerColor.getRed(), headerColor.getGreen(), headerColor.getBlue());
        String boldHex = String.format("#%02x%02x%02x", 
            boldColor.getRed(), boldColor.getGreen(), boldColor.getBlue());
        String italicHex = String.format("#%02x%02x%02x", 
            italicColor.getRed(), italicColor.getGreen(), italicColor.getBlue());
        String linkHex = String.format("#%02x%02x%02x", 
            linkColor.getRed(), linkColor.getGreen(), linkColor.getBlue());
        String codeHex = String.format("#%02x%02x%02x", 
            codeColor.getRed(), codeColor.getGreen(), codeColor.getBlue());
        String blockquoteHex = String.format("#%02x%02x%02x", 
            blockquoteColor.getRed(), blockquoteColor.getGreen(), blockquoteColor.getBlue());
        String bgHex = String.format("#%02x%02x%02x", 
            background.getRed(), background.getGreen(), background.getBlue());
        String fgHex = String.format("#%02x%02x%02x", 
            foreground.getRed(), foreground.getGreen(), foreground.getBlue());

        // Apply consistent styling
        float baseSize = 14;
        styleSheet.addRule("body { font-family: Arial, sans-serif; margin: 20px; background-color: " + bgHex + "; color: " + fgHex + "; font-size: " + baseSize + "px; line-height: 1.6; }");
        styleSheet.addRule("h1 { color: " + headerHex + "; font-size: " + (baseSize * 2.0) + "px; }");
        styleSheet.addRule("h2 { color: " + linkHex + "; font-size: " + (baseSize * 1.8) + "px; }");
        styleSheet.addRule("h3, h4, h5, h6 { color: " + boldHex + "; font-size: " + (baseSize * 1.5) + "px; }");
        styleSheet.addRule("strong { color: " + boldHex + "; }");
        styleSheet.addRule("em { color: " + italicHex + "; }");
        styleSheet.addRule("a { color: " + headerHex + "; text-decoration: underline; }");
        styleSheet.addRule("code { color: " + codeHex + "; background-color: " + background.brighter() + "; padding: 2px 4px; border-radius: 4px; font-family: monospace; }");
        styleSheet.addRule("pre { background-color: " + background.brighter() + "; padding: 10px; border-radius: 4px; }");
        styleSheet.addRule("pre code { background-color: transparent; padding: 0; }");
        styleSheet.addRule("blockquote { border-left: 4px solid " + blockquoteHex + "; margin: 0; padding-left: 20px; color: " + blockquoteHex + "; }");
        styleSheet.addRule("table { border-collapse: collapse; margin: 15px 0; }");
        styleSheet.addRule("th, td { border: 1px solid " + foreground.darker() + "; padding: 8px; }");
        styleSheet.addRule("th { background-color: " + background.brighter() + "; color: " + italicHex + "; }");

        previewPane.setEditorKit(kit);
    
        // Add hyperlink listener with support for both address and web links
        previewPane.addHyperlinkListener(e -> {
            if (e.getEventType() == javax.swing.event.HyperlinkEvent.EventType.ACTIVATED) {
                String href = e.getDescription();
                if (href != null) {
                    if (href.startsWith("address://")) {
                        // Handle address links
                        String address = href.substring(10);
                        navigateToAddress(address);
                    } else {
                        // Handle web links
                        try {
                            Desktop desktop = Desktop.getDesktop();
                            URI uri = new URI(href);
                            desktop.browse(uri);
                        } catch (Exception ex) {
                            JOptionPane.showMessageDialog(mainPanel,
                                "Error opening link: " + ex.getMessage(),
                                "Link Error",
                                JOptionPane.ERROR_MESSAGE);
                        }
                    }
                }
            }
        });
    }

    public void updatePreview(String editorContent) {
        if (editorContent.isEmpty()) {
            previewPane.setText("");
            return;
        }
        
        // Get current and new content
        Node document = markdownParser.parse(editorContent);
        String newHtml = htmlRenderer.render(document);
        String processedHtml = processImagePaths(newHtml);
        
        // Wrap HTML in basic styling
        String styledHtml = String.format("""
            <html>
            <body>
            %s
            </body>
            </html>
            """, processedHtml);
        
        // Only update if content has changed
        if (!styledHtml.equals(previewPane.getText())) {
            previewPane.setText(styledHtml);
            previewPane.setCaretPosition(0);
        }
    }

    private String processImagePaths(String html) {
        if (currentFile == null || currentDirectory == null) {
            return html;
        }

        // Pattern to match img tags with dimensions
        Pattern pattern = Pattern.compile("(<img[^>]+src=\")([^\"]+)\"([^>]*)>");
        Matcher matcher = pattern.matcher(html);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String prefix = matcher.group(1);
            String imagePath = matcher.group(2);
            String remainingAttributes = matcher.group(3);
            
            // Skip if it's already an absolute URL
            if (imagePath.startsWith("http://") || 
                imagePath.startsWith("https://") ||
                imagePath.startsWith("file://")) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }
            
            try {
                Path absolutePath;
                // Handle absolute paths from collection root (starting with /)
                if (imagePath.startsWith("/")) {
                    imagePath = imagePath.substring(1);
                    absolutePath = currentDirectory.resolve(imagePath);
                } else {
                    // Handle relative paths from current file's location
                    Path currentDir = currentFile.getParent();
                    absolutePath = currentDir.resolve(imagePath);
                }

                // Load and scale the image
                if (Files.exists(absolutePath)) {
                    BufferedImage img = ImageIO.read(absolutePath.toFile());
                    if (img != null) {
                        // Define maximum dimensions
                        int maxWidth = 640;
                        int maxHeight = 480;
                        
                        // Calculate scaling factor
                        double scale = Math.min(
                            (double) maxWidth / img.getWidth(),
                            (double) maxHeight / img.getHeight()
                        );
                        
                        // Only scale down, not up
                        if (scale < 1.0) {
                            int newWidth = (int) (img.getWidth() * scale);
                            int newHeight = (int) (img.getHeight() * scale);
                            
                            // Add width and height attributes to the img tag
                            String replacement = prefix + absolutePath.toFile().toURI().toString() + 
                                "\" width=\"" + newWidth + "\" height=\"" + newHeight + "\">";
                            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
                        } else {
                            // Image is smaller than max dimensions, use original size
                            String replacement = prefix + absolutePath.toFile().toURI().toString() + 
                                "\" width=\"" + img.getWidth() + "\" height=\"" + img.getHeight() + "\">";
                            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
                        }
                        continue;
                    }
                }
                
                // Fallback if image loading fails
                String replacement = prefix + absolutePath.toFile().toURI().toString() + "\">";
                matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
                
            } catch (IOException e) {
                e.printStackTrace();
                // If there's an error, just use the original tag
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(result);
        
        // Then process function references with curly braces
        String processedHtml = result.toString();
        pattern = Pattern.compile("\\{(0x[0-9a-fA-F]+|[0-9a-fA-F]+)\\}");
        matcher = pattern.matcher(processedHtml);
        result = new StringBuffer();
        
        while (matcher.find()) {
            String address = matcher.group(1);
            String functionName = functionNameResolver.getFunctionName(address);
            String displayText = functionName != null ? functionName : address;
            String replacement = String.format("<a href=\"address://%s\">%s</a>", 
                address, displayText);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        
        // Finally process address references with square brackets (existing code)
        pattern = Pattern.compile("\\[(0x[0-9a-fA-F]+|[0-9a-fA-F]+)\\]");
        matcher = pattern.matcher(result.toString());
        result = new StringBuffer();
        
        while (matcher.find()) {
            String address = matcher.group(1);
            String replacement = String.format("<a href=\"address://%s\">%s</a>", 
                address, address);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        
        return result.toString();
    }

    public interface AddressNavigationHandler {
        void navigateToAddress(String address);
    }

    public interface FunctionNameResolver {
        String getFunctionName(String address);
    }
}