package ghidra.notepad;

import ghidra.app.decompiler.ClangTokenGroup;
import ghidra.app.decompiler.DecompileResults;
import ghidra.program.model.listing.Function;

import generic.theme.Gui;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import javax.swing.*;
import javax.swing.text.html.*;
import java.awt.*;
import java.awt.Desktop;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.regex.*;
import javax.imageio.ImageIO;

/**
 * Composite preview panel that renders markdown content with optional inline
 * decompiler panels. Replaces the plain JEditorPane + PreviewUtils pair.
 *
 * <p>Embed syntax (all use single braces):
 * <ul>
 *   <li>{addr}      — clickable function-name link (no embed)</li>
 *   <li>{addr}[]    — embed entire decompiled function</li>
 *   <li>{addr}[N]   — embed line N only</li>
 *   <li>{addr}[N-M] — embed lines N–M inclusive</li>
 * </ul>
 */
public class CompositePreviewPanel extends JPanel {

    // Matches {addr}[], {addr}[N], or {addr}[N-M]  — single braces, brackets required
    private static final Pattern EMBED_PATTERN = Pattern.compile(
        "\\{(0x[0-9a-fA-F]+|[0-9a-fA-F]+)\\}\\[(\\d*)(?:-(\\d+))?\\]");

    private final JPanel innerPanel;
    private final JScrollPane scrollPane;
    private final Parser markdownParser;
    private final HtmlRenderer htmlRenderer;
    private final JPanel parentPanel;

    private Path currentFile;
    private Path currentDirectory;
    private AddressNavigationHandler navigationHandler;
    private FunctionNameResolver functionNameResolver;
    private DecompilationCallback decompilationCallback;
    private float zoomFactor = 1.0f;

    // Cache decompiler output by address so we don't re-decompile on every keystroke
    private final Map<String, DecompOutput> decompCache = new HashMap<>();
    private String lastRenderedContent = null;

    public interface AddressNavigationHandler {
        void navigateToAddress(String address);
    }

    public interface FunctionNameResolver {
        String getFunctionName(String address);
    }

    /** Carries the Function, token markup, and plain C text (for indentation). */
    public record DecompOutput(Function function, ClangTokenGroup markup, String cText) {}

    public interface DecompilationCallback {
        DecompOutput decompile(String address);
    }

    private static final class EmbedSpec {
        final String address;
        final Integer startLine;
        final Integer endLine;

        EmbedSpec(String address, Integer startLine, Integer endLine) {
            this.address = address;
            this.startLine = startLine;
            this.endLine = endLine;
        }
    }

    public CompositePreviewPanel(Parser markdownParser, HtmlRenderer htmlRenderer, JPanel parentPanel) {
        super(new BorderLayout());
        this.markdownParser = markdownParser;
        this.htmlRenderer = htmlRenderer;
        this.parentPanel = parentPanel;

        innerPanel = new ScrollablePanel();
        innerPanel.setLayout(new BoxLayout(innerPanel, BoxLayout.Y_AXIS));
        innerPanel.setBackground(Gui.getColor("color.bg"));

        scrollPane = new JScrollPane(innerPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        add(scrollPane, BorderLayout.CENTER);
    }

    // ----- Setters -----

    public void setAddressNavigationHandler(AddressNavigationHandler handler) {
        this.navigationHandler = handler;
    }

    public void setFunctionNameResolver(FunctionNameResolver resolver) {
        this.functionNameResolver = resolver;
    }

    public void setDecompilationCallback(DecompilationCallback callback) {
        this.decompilationCallback = callback;
    }

    public void setCurrentFile(Path file) {
        this.currentFile = file;
    }

    public void setCurrentDirectory(Path dir) {
        this.currentDirectory = dir;
    }

    public void setZoomFactor(float factor) {
        this.zoomFactor = factor;
        applyZoomToEmbeds();
    }

    private void applyZoomToEmbeds() {
        for (Component c : innerPanel.getComponents()) {
            if (c instanceof EmbeddedDecompilerPanel ep) ep.setZoomFactor(zoomFactor);
        }
    }

    public void clearDecompCache() {
        decompCache.clear();
    }

    // ----- Public API -----

    /**
     * Show arbitrary HTML directly (used for image previews).
     */
    public void setDirectHtml(String html) {
        lastRenderedContent = null;
        SwingUtilities.invokeLater(() -> {
            innerPanel.removeAll();
            if (!html.isEmpty()) {
                JEditorPane pane = createHtmlPane();
                initHtmlKit(pane);
                pane.setText(html);
                pane.setCaretPosition(0);
                innerPanel.add(pane);
            }
            innerPanel.setBackground(Gui.getColor("color.bg"));
            innerPanel.revalidate();
            innerPanel.repaint();
        });
    }

    /**
     * Render markdown content. Splits on {{ADDRESS}}[start-end] patterns
     * and builds a composite panel with HTML and decompiler sections.
     */
    public void updatePreview(String markdownContent) {
        if (markdownContent.equals(lastRenderedContent)) return;
        lastRenderedContent = markdownContent;

        if (markdownContent.isEmpty()) {
            SwingUtilities.invokeLater(() -> {
                innerPanel.removeAll();
                innerPanel.revalidate();
                innerPanel.repaint();
            });
            return;
        }

        List<Object> segments = parseSegments(markdownContent);

        // Build HTML panes and embed panels (may involve decompilation)
        List<Component> components = new ArrayList<>();
        for (Object seg : segments) {
            if (seg instanceof String text) {
                if (!text.isBlank()) {
                    components.add(buildHtmlSection(text));
                }
            } else if (seg instanceof EmbedSpec spec) {
                components.add(buildEmbedSection(spec));
            }
        }

        SwingUtilities.invokeLater(() -> {
            innerPanel.removeAll();
            for (Component c : components) {
                // Let each component fill the full width
                if (c instanceof JComponent jc) {
                    jc.setMaximumSize(new Dimension(Integer.MAX_VALUE, jc.getMaximumSize().height));
                    jc.setAlignmentX(Component.LEFT_ALIGNMENT);
                }
                innerPanel.add(c);
            }
            innerPanel.setBackground(Gui.getColor("color.bg"));
            innerPanel.revalidate();
            innerPanel.repaint();
        });
    }

    /**
     * Rebuild HTML kit on all existing HTML panes to pick up the new theme,
     * then re-render the last content.
     */
    public void refreshTheme() {
        String content = lastRenderedContent;
        lastRenderedContent = null; // force re-render
        // Refresh embed panels immediately
        SwingUtilities.invokeLater(() -> {
            for (Component c : innerPanel.getComponents()) {
                if (c instanceof EmbeddedDecompilerPanel ep) {
                    ep.setZoomFactor(zoomFactor);
                    ep.refreshTheme();
                }
            }
            innerPanel.setBackground(Gui.getColor("color.bg"));
        });
        // Re-render full content so HTML panes pick up new theme colors
        if (content != null) {
            updatePreview(content);
        }
    }

    // ----- Private helpers -----

    private List<Object> parseSegments(String content) {
        List<Object> segments = new ArrayList<>();
        Matcher m = EMBED_PATTERN.matcher(content);
        int lastEnd = 0;
        while (m.find()) {
            String before = content.substring(lastEnd, m.start());
            if (!before.isEmpty()) segments.add(before);
            // group(2) is the start number (empty string = entire function)
            // group(3) is the end number (absent = same as start, i.e. single line)
            String g2 = m.group(2);
            String g3 = m.group(3);
            Integer startLine = (g2 != null && !g2.isEmpty()) ? Integer.valueOf(g2) : null;
            Integer endLine   = (g3 != null) ? Integer.valueOf(g3) : startLine;
            segments.add(new EmbedSpec(m.group(1), startLine, endLine));
            lastEnd = m.end();
        }
        String tail = content.substring(lastEnd);
        if (!tail.isEmpty()) segments.add(tail);
        return segments;
    }

    private JEditorPane buildHtmlSection(String markdownText) {
        Node doc = markdownParser.parse(markdownText);
        String html = htmlRenderer.render(doc);
        String processed = processHtml(html);
        String styledHtml = "<html><body>" + processed + "</body></html>";

        JEditorPane pane = createHtmlPane();
        initHtmlKit(pane);
        pane.setText(styledHtml);
        pane.setCaretPosition(0);
        return pane;
    }

    private EmbeddedDecompilerPanel buildEmbedSection(EmbedSpec spec) {
        EmbeddedDecompilerPanel panel = new EmbeddedDecompilerPanel(
            spec.address, spec.startLine, spec.endLine);
        panel.setZoomFactor(zoomFactor);
        if (navigationHandler != null) {
            panel.setNavigationCallback(() -> navigationHandler.navigateToAddress(spec.address));
        }

        // Kick off async decompilation so we don't block the EDT
        SwingWorker<DecompOutput, Void> worker = new SwingWorker<>() {
            @Override
            protected DecompOutput doInBackground() {
                synchronized (decompCache) {
                    return decompCache.computeIfAbsent(spec.address, addr -> {
                        if (decompilationCallback == null) return null;
                        return decompilationCallback.decompile(addr);
                    });
                }
            }

            @Override
            protected void done() {
                try {
                    DecompOutput output = get();
                    if (output != null) {
                        panel.render(output.function(), output.markup(), output.cText(),
                                     spec.startLine, spec.endLine);
                    } else {
                        panel.setStatusText("// Could not decompile " + spec.address);
                    }
                    innerPanel.revalidate();
                    innerPanel.repaint();
                } catch (Exception e) {
                    panel.setStatusText("// Decompilation error: " + e.getMessage());
                }
            }
        };
        worker.execute();

        return panel;
    }

    private JEditorPane createHtmlPane() {
        return new JEditorPane() {
            @Override
            public boolean getScrollableTracksViewportWidth() {
                return true;
            }
        };
    }

    /**
     * Initialises the HTMLEditorKit and per-document stylesheet on a fresh
     * JEditorPane, applying Ghidra theme colors and the current zoom factor.
     */
    private void initHtmlKit(JEditorPane pane) {
        pane.setEditable(false);
        pane.setContentType("text/html");
        pane.setBorder(null);
        pane.setOpaque(true);

        HTMLEditorKit kit = new HTMLEditorKit();
        pane.setEditorKit(kit);
        // Use document-level stylesheet — isolated, never shared with other components
        StyleSheet ss = ((HTMLDocument) pane.getDocument()).getStyleSheet();
        applyHtmlStyles(ss);

        // Hyperlink handler
        pane.addHyperlinkListener(e -> {
            if (e.getEventType() == javax.swing.event.HyperlinkEvent.EventType.ACTIVATED) {
                String href = e.getDescription();
                if (href == null) return;
                if (href.startsWith("address://")) {
                    if (navigationHandler != null) {
                        navigationHandler.navigateToAddress(href.substring(10));
                    }
                } else {
                    try {
                        Desktop.getDesktop().browse(new URI(href));
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(parentPanel,
                            "Error opening link: " + ex.getMessage(),
                            "Link Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        });
    }

    private void applyHtmlStyles(StyleSheet ss) {
        Color headerColor     = Gui.getColor("color.fg.decompiler.function.name");
        Color boldColor       = Gui.getColor("color.fg.decompiler.keyword");
        Color italicColor     = Gui.getColor("color.fg.decompiler.type");
        Color linkColor       = Gui.getColor("color.fg.decompiler.variable");
        Color codeColor       = Gui.getColor("color.fg.decompiler.comment");
        Color blockquoteColor = Gui.getColor("color.fg.decompiler.global");
        Color background      = Gui.getColor("color.bg");
        Color foreground      = Gui.getColor("color.fg");

        String headerHex     = hex(headerColor);
        String boldHex       = hex(boldColor);
        String italicHex     = hex(italicColor);
        String linkHex       = hex(linkColor);
        String codeHex       = hex(codeColor);
        String blockquoteHex = hex(blockquoteColor);
        String bgHex         = hex(background);
        String fgHex         = hex(foreground);

        float base = 14 * zoomFactor;
        ss.addRule("body { font-family: Arial, sans-serif; margin: 20px; background-color: " + bgHex + "; color: " + fgHex + "; font-size: " + base + "px; line-height: 1.6; }");
        ss.addRule("h1 { color: " + headerHex + "; font-size: " + (base * 2.0f) + "px; }");
        ss.addRule("h2 { color: " + linkHex    + "; font-size: " + (base * 1.8f) + "px; }");
        ss.addRule("h3, h4, h5, h6 { color: " + boldHex + "; font-size: " + (base * 1.5f) + "px; }");
        ss.addRule("p { font-size: " + base + "px; }");
        ss.addRule("strong { color: " + boldHex + "; }");
        ss.addRule("em { color: " + italicHex + "; }");
        ss.addRule("a { color: " + headerHex + "; text-decoration: underline; }");
        ss.addRule("code { color: " + codeHex + "; background-color: " + hex(background.brighter()) + "; padding: 2px 4px; border-radius: 4px; font-family: monospace; font-size: " + base + "px; }");
        ss.addRule("pre { background-color: " + hex(background.brighter()) + "; padding: 10px; border-radius: 4px; font-size: " + base + "px; }");
        ss.addRule("pre code { background-color: transparent; padding: 0; }");
        ss.addRule("blockquote { border-left: 4px solid " + blockquoteHex + "; margin: 0; padding-left: 20px; color: " + blockquoteHex + "; font-size: " + base + "px; }");
        ss.addRule("table { border-collapse: collapse; margin: 15px 0; }");
        ss.addRule("th, td { border: 1px solid " + hex(foreground.darker()) + "; padding: 8px; font-size: " + base + "px; }");
        ss.addRule("th { background-color: " + hex(background.brighter()) + "; color: " + italicHex + "; }");
        ss.addRule("li { font-size: " + base + "px; }");
    }

    private static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    /**
     * Post-processes rendered HTML to:
     * 1. Convert relative image paths to absolute file:// URIs and add dimensions.
     * 2. Convert {address} references to clickable links with optional function names.
     * 3. Convert [address] references to clickable navigation links.
     */
    private String processHtml(String html) {
        html = addHeadingAnchors(html);
        html = processImagePaths(html);
        html = processFunctionRefs(html);
        html = processAddressRefs(html);
        return html;
    }

    /**
     * Scroll the preview to the heading that starts at the given character offset
     * in the raw markdown. Called from the TOC click handler.
     */
    public void scrollToMarkdownPosition(int markdownPosition) {
        if (lastRenderedContent == null) return;
        String heading = extractHeadingAt(lastRenderedContent, markdownPosition);
        if (heading == null) return;
        String anchorId = toAnchorId(heading);
        // scrollToReference calls scrollRectToVisible which propagates up to our outer JScrollPane
        for (Component c : innerPanel.getComponents()) {
            if (c instanceof JEditorPane pane) {
                pane.scrollToReference(anchorId);
            }
        }
    }

    /**
     * Injects named anchors inside heading elements so JEditorPane.scrollToReference() can find them.
     * scrollToReference looks for <a name="..."> tags specifically, not id attributes.
     */
    private static String addHeadingAnchors(String html) {
        Pattern p = Pattern.compile("<(h[1-6])>(.+?)</h[1-6]>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = p.matcher(html);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String tag      = m.group(1).toLowerCase();
            String inner    = m.group(2);
            String plainText = inner.replaceAll("<[^>]+>", "");
            String id       = toAnchorId(plainText);
            m.appendReplacement(sb, Matcher.quoteReplacement(
                "<" + tag + "><a name=\"" + id + "\"></a>" + inner + "</" + tag + ">"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String extractHeadingAt(String content, int position) {
        if (position >= content.length()) return null;
        Pattern p = Pattern.compile("^#{1,6}\\s+(.+?)\\s*#*$", Pattern.MULTILINE);
        Matcher m = p.matcher(content.substring(position));
        return (m.find() && m.start() == 0) ? m.group(1).trim() : null;
    }

    private static String toAnchorId(String text) {
        return text.toLowerCase()
            .replaceAll("[^a-z0-9\\s-]", "")
            .trim()
            .replaceAll("\\s+", "-");
    }

    private String processImagePaths(String html) {
        if (currentFile == null || currentDirectory == null) return html;

        Pattern pattern = Pattern.compile("(<img[^>]+src=\")([^\"]+)\"([^>]*)>");
        Matcher matcher = pattern.matcher(html);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String prefix  = matcher.group(1);
            String imgPath = matcher.group(2);
            String rest    = matcher.group(3);

            if (imgPath.startsWith("http://") || imgPath.startsWith("https://") || imgPath.startsWith("file://")) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }

            try {
                Path absPath;
                if (imgPath.startsWith("/")) {
                    absPath = currentDirectory.resolve(imgPath.substring(1));
                } else {
                    absPath = currentFile.getParent().resolve(imgPath);
                }

                if (Files.exists(absPath)) {
                    BufferedImage img = ImageIO.read(absPath.toFile());
                    if (img != null) {
                        double maxW  = 600 * zoomFactor;
                        double maxH  = 450 * zoomFactor;
                        double scale = Math.min(maxW / img.getWidth(), maxH / img.getHeight());
                        int w = scale < 1.0 ? (int)(img.getWidth()  * scale) : img.getWidth();
                        int h = scale < 1.0 ? (int)(img.getHeight() * scale) : img.getHeight();
                        String imgTag = prefix + absPath.toFile().toURI() + "\" width=\"" + w + "\" height=\"" + h + "\">";
                        matcher.appendReplacement(result, Matcher.quoteReplacement(
                            "<div style=\"text-align:center\">" + imgTag + "</div>"));
                        continue;
                    }
                }
                matcher.appendReplacement(result, Matcher.quoteReplacement(
                    "<div style=\"text-align:center\">" + prefix + absPath.toFile().toURI() + "\"></div>"));
            } catch (IOException e) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String processFunctionRefs(String html) {
        Pattern pattern = Pattern.compile("\\{(0x[0-9a-fA-F]+|[0-9a-fA-F]+)\\}");
        Matcher matcher = pattern.matcher(html);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String address = matcher.group(1);
            String display = functionNameResolver != null
                ? functionNameResolver.getFunctionName(address) : address;
            if (display == null) display = address;
            matcher.appendReplacement(result, Matcher.quoteReplacement(
                "<a href=\"address://" + address + "\">" + display + "</a>"));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String processAddressRefs(String html) {
        Pattern pattern = Pattern.compile("\\[(0x[0-9a-fA-F]+|[0-9a-fA-F]+)\\]");
        Matcher matcher = pattern.matcher(html);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String address = matcher.group(1);
            matcher.appendReplacement(result, Matcher.quoteReplacement(
                "<a href=\"address://" + address + "\">" + address + "</a>"));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /** A JPanel that implements Scrollable so JScrollPane tracks viewport width correctly. */
    private static class ScrollablePanel extends JPanel implements Scrollable {
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 100; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
