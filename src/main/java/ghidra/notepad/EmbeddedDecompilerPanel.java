package ghidra.notepad;

import ghidra.app.decompiler.*;
import ghidra.program.model.listing.Function;
import ghidra.program.model.pcode.*;
import ghidra.app.decompiler.ClangSyntaxToken;
import generic.theme.Gui;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.Set;

/**
 * Renders a slice of Ghidra-decompiled C code inline in the markdown preview.
 *
 * Uses PrettyPrinter (the same formatter the decompiler window uses) to get
 * ClangLine objects with exact line numbers, then colours each token using the
 * same Ghidra theme keys the decompiler window uses — giving a pixel-perfect
 * match in both layout and colour.
 */
public class EmbeddedDecompilerPanel extends JPanel {
    private static final int LINE_HEIGHT = 19;
    private static final int MIN_HEIGHT  = 60;
    private static final int MAX_HEIGHT  = 400;

    // C keywords that appear as ClangOpToken — coloured the same as types in the decompiler
    private static final Set<String> KEYWORDS = Set.of(
        "if", "else", "while", "do", "for", "return", "goto", "break", "continue",
        "switch", "case", "default", "sizeof", "typedef", "struct", "union", "enum",
        "const", "static", "extern", "volatile", "register", "inline", "NULL"
    );

    private static final int BASE_CODE_PT = 13;

    private final JTextPane   codePane;
    private final JScrollPane codeScrollPane;
    private Runnable          navigationCallback;
    private float             zoomFactor = 1.0f;

    // Cached state for re-rendering when zoom changes
    private Function           lastFunction;
    private ClangTokenGroup    lastMarkup;
    private String             lastCText;
    private Integer            lastStartLine;
    private Integer            lastEndLine;

    public void setNavigationCallback(Runnable callback) {
        this.navigationCallback = callback;
    }

    public void setZoomFactor(float zoom) {
        this.zoomFactor = zoom;
        applyFonts();
        if (lastFunction != null) {
            render(lastFunction, lastMarkup, lastCText, lastStartLine, lastEndLine);
        }
    }

    public EmbeddedDecompilerPanel(String address, Integer startLine, Integer endLine) {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 20));
        setOpaque(false);

        codePane = new JTextPane();
        codePane.setEditable(false);
        codePane.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        codePane.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        codePane.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (navigationCallback != null) navigationCallback.run();
            }
        });
        applyThemeColors();
        applyFonts();

        codeScrollPane = new JScrollPane(codePane);
        codeScrollPane.setBorder(null);
        codeScrollPane.setPreferredSize(new Dimension(0, MIN_HEIGHT));
        // Forward wheel events to the outer scroll pane when this one is at its limits
        codeScrollPane.addMouseWheelListener(e -> {
            JScrollBar vsb = codeScrollPane.getVerticalScrollBar();
            boolean atTop    = vsb.getValue() <= vsb.getMinimum();
            boolean atBottom = vsb.getValue() >= vsb.getMaximum() - vsb.getVisibleAmount();
            if ((e.getWheelRotation() < 0 && atTop) || (e.getWheelRotation() > 0 && atBottom)) {
                JScrollPane outer = (JScrollPane) SwingUtilities.getAncestorOfClass(
                    JScrollPane.class, EmbeddedDecompilerPanel.this);
                if (outer != null) outer.dispatchEvent(
                    SwingUtilities.convertMouseEvent(codeScrollPane, e, outer));
            }
        });

        add(codeScrollPane, BorderLayout.CENTER);

        setStatusText("Decompiling…");
    }

    public void setStatusText(String message) {
        codePane.setBackground(codeBg());
        codePane.setText(message);
    }

    /**
     * Render a slice of the decompiled function using PrettyPrinter — the same
     * line-formatting engine the decompiler window uses — so line numbers and
     * token colours match the decompiler window exactly.
     *
     * @param function  the Ghidra function object (needed by PrettyPrinter)
     * @param markup    the ClangTokenGroup from DecompileResults.getCCodeMarkup()
     * @param startLine first line to show, 1-indexed, matching the decompiler window (null = start)
     * @param endLine   last line to show, inclusive (null = end)
     */
    /**
     * @param cText the plain text from DecompileResults.getDecompiledFunction().getC(),
     *              used to recover leading whitespace — line N in cText corresponds to
     *              ClangLine with getLineNumber()==N.
     */
    public void render(Function function, ClangTokenGroup markup, String cText,
                       Integer startLine, Integer endLine) {
        lastFunction  = function;
        lastMarkup    = markup;
        lastCText     = cText;
        lastStartLine = startLine;
        lastEndLine   = endLine;

        List<ClangLine> lines;
        try {
            lines = new PrettyPrinter(function, markup, s -> s).getLines();
        } catch (Exception e) {
            setStatusText("// Error formatting decompiler output: " + e.getMessage());
            return;
        }

        // Build an index of plain-text lines for indentation lookup (1-indexed)
        String[] cLines = (cText != null) ? cText.split("\n", -1) : new String[0];

        // getLineNumber() is 0-indexed; the decompiler window displays 1-indexed line numbers,
        // so subtract 1 to convert user-specified line numbers to internal line numbers.
        int from = startLine != null ? startLine - 1 : 0;
        int to   = endLine   != null ? endLine   - 1 : Integer.MAX_VALUE;

        DefaultStyledDocument doc = new DefaultStyledDocument();
        boolean firstLine = true;
        int matchedLines  = 0;

        try {
            for (ClangLine line : lines) {
                int lineNum = line.getLineNumber();
                if (lineNum < from || lineNum > to) continue;

                if (!firstLine) {
                    doc.insertString(doc.getLength(), "\n", monoAttrs(Gui.getColor("color.fg")));
                }
                firstLine = false;
                matchedLines++;

                // Indentation comes from the plain C text — PrettyPrinter line numbers
                // are 1-indexed and map directly to cLines[lineNum-1].
                if (lineNum > 0 && lineNum <= cLines.length) {
                    String cl = cLines[lineNum - 1];
                    int wsLen = 0;
                    while (wsLen < cl.length() && cl.charAt(wsLen) == ' ') wsLen++;
                    if (wsLen > 0) {
                        doc.insertString(doc.getLength(), cl.substring(0, wsLen),
                            monoAttrs(Gui.getColor("color.fg")));
                    }
                }

                for (ClangToken token : line.getAllTokens()) {
                    String text = token.toString();
                    if (text == null || text.isEmpty()) continue;
                    doc.insertString(doc.getLength(), text, monoAttrs(tokenColor(token)));
                }
            }
        } catch (BadLocationException e) {
            e.printStackTrace();
        }

        String rangeStr = startLine == null ? ""
            : startLine.equals(endLine) ? " [" + startLine + "]"
            : " [" + startLine + "–" + endLine + "]";
        codePane.setToolTipText(function.getName() + rangeStr);

        codePane.setDocument(doc);
        codePane.setBackground(codeBg());
        codePane.setCaretPosition(0);

        // 14 = codePane border top(6) + bottom(6) + scrollpane insets(~2)
        int h = Math.min(matchedLines * LINE_HEIGHT + 14, MAX_HEIGHT);
        codeScrollPane.setPreferredSize(new Dimension(0, h));
        revalidate();
    }

    public void refreshTheme() {
        applyThemeColors();
        applyFonts();
    }

    // ----- private helpers -----

    private void applyThemeColors() {
        Color bg = codeBg();
        codePane.setBackground(bg);
        codePane.setForeground(Gui.getColor("color.fg"));
        codePane.setSelectionColor(Gui.getColor("color.bg.selection"));
        codePane.setSelectedTextColor(Gui.getColor("color.fg"));
        codePane.setCaretColor(Gui.getColor("color.cursor.focused"));
    }

    /** Returns a slightly darker shade of the document background for the code region. */
    private static Color codeBg() {
        Color base = Gui.getColor("color.bg");
        return new Color(
            Math.max(0, base.getRed()   - 12),
            Math.max(0, base.getGreen() - 12),
            Math.max(0, base.getBlue()  - 12));
    }

    private void applyFonts() {
        codePane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12)
            .deriveFont(BASE_CODE_PT * zoomFactor));
    }

    private SimpleAttributeSet monoAttrs(Color color) {
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setForeground(attrs, color);
        StyleConstants.setFontFamily(attrs, Font.MONOSPACED);
        StyleConstants.setFontSize(attrs, Math.max(1, Math.round(BASE_CODE_PT * zoomFactor)));
        return attrs;
    }

    /** Map ClangToken subclass → the same Ghidra theme color the decompiler window uses. */
    private static Color tokenColor(ClangToken token) {
        if (token instanceof ClangTypeToken)     return Gui.getColor("color.fg.decompiler.type");
        if (token instanceof ClangFuncNameToken) return Gui.getColor("color.fg.decompiler.function.name");
        if (token instanceof ClangLabelToken)    return Gui.getColor("color.fg.decompiler.function.name");

        if (token instanceof ClangCommentToken) return Gui.getColor("color.fg.decompiler.comment");
        if (token instanceof ClangFieldToken)   return Gui.getColor("color.fg.decompiler.variable");

        if (token instanceof ClangVariableToken vt) {
            HighVariable high = vt.getHighVariable();
            if (high instanceof HighParam)  return Gui.getColor("color.fg.decompiler.parameter");
            if (high instanceof HighGlobal) return Gui.getColor("color.fg.decompiler.global");
            // HighConstant (numeric literals), or null high → constant color
            if (high == null || high instanceof HighConstant)
                return Gui.getColor("color.fg.decompiler.constant");
            return Gui.getColor("color.fg.decompiler.variable");
        }

        // Keywords may appear as ClangOpToken or ClangSyntaxToken depending on Ghidra version
        if (token instanceof ClangOpToken || token instanceof ClangSyntaxToken) {
            String text = token.toString();
            if (text != null && KEYWORDS.contains(text)) {
                return Gui.getColor("color.fg.decompiler.keyword");
            }
        }

        // ClangSyntaxToken, ClangBreak, and plain operators — default foreground
        return Gui.getColor("color.fg");
    }
}
