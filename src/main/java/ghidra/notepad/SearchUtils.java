package ghidra.notepad;

import javax.swing.*;
import javax.swing.table.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyAdapter;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import generic.theme.Gui;


/**
 * Provides search functionality across both individual documents and
 * entire collections. Implements highlighting, result navigation,
 * and the collection search dialog interface.
 */
public class SearchUtils {
    public interface SearchResultCallback {
        void navigateToResult(Path file, int position, String searchTerm);
    }
    
    public static class SearchResult {
        public final Path filePath;
        public final String snippet;
        public final int position;
        public final String searchTerm;
        
        public SearchResult(Path filePath, String snippet, int position, String searchTerm) {
            this.filePath = filePath;
            this.snippet = snippet;
            this.position = position;
            this.searchTerm = searchTerm;
        }
    }
    
    public static List<Integer> findAllPositions(String content, String searchTerm, boolean caseSensitive) {
        // Previous implementation remains the same
        List<Integer> positions = new ArrayList<>();
        if (searchTerm.isEmpty() || content.isEmpty()) {
            return positions;
        }
        
        String searchContent = caseSensitive ? content : content.toLowerCase();
        String searchText = caseSensitive ? searchTerm : searchTerm.toLowerCase();
        
        int index = 0;
        while ((index = searchContent.indexOf(searchText, index)) != -1) {
            positions.add(index);
            index += searchText.length();
        }
        
        return positions;
    }
    
    public static List<SearchResult> searchCollection(Path rootDirectory, String searchTerm, 
            boolean caseSensitive) throws IOException {
        List<SearchResult> results = new ArrayList<>();
        
        Files.walk(rootDirectory)
            .filter(path -> path.toString().toLowerCase().endsWith(".md"))
            .forEach(file -> {
                try {
                    String content = Files.readString(file);
                    List<Integer> positions = findAllPositions(content, searchTerm, caseSensitive);
                    
                    for (int pos : positions) {
                        // Extract snippet with context (50 chars before and after)
                        int start = Math.max(0, pos - 50);
                        int end = Math.min(content.length(), pos + searchTerm.length() + 50);
                        String snippet = content.substring(start, end);
                        
                        // Add ellipsis if we're not at the start/end
                        if (start > 0) snippet = "..." + snippet;
                        if (end < content.length()) snippet = snippet + "...";
                        
                        results.add(new SearchResult(file, snippet, pos, searchTerm));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            
        return results;
    }
    
    public static void highlightText(JTextComponent textComponent, String searchTerm, 
            boolean caseSensitive) {
        // Previous implementation remains the same
        Highlighter highlighter = textComponent.getHighlighter();
        highlighter.removeAllHighlights();
        
        if (searchTerm.isEmpty()) {
            return;
        }
        
        try {
            String content = textComponent.getText();
            List<Integer> positions = findAllPositions(content, searchTerm, caseSensitive);
            
            Color highlightColor = Gui.getColor("color.palette.highlight.transparent.yellow");
            Highlighter.HighlightPainter painter = 
                new DefaultHighlighter.DefaultHighlightPainter(highlightColor);
            
            for (int pos : positions) {
                highlighter.addHighlight(pos, pos + searchTerm.length(), painter);
            }
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }
    
    private static class SearchResultsTable extends JTable {
        private final String searchTerm;
        private final SearchResultCallback callback;
        
        public SearchResultsTable(SearchResultTableModel model, String searchTerm, 
                SearchResultCallback callback) {
            super(model);
            this.searchTerm = searchTerm;
            this.callback = callback;
            setupTable();
        }
        
        private void setupTable() {
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            setShowGrid(false);
            setIntercellSpacing(new Dimension(0, 0));
            
            // Set column widths
            TableColumnModel columnModel = getColumnModel();
            columnModel.getColumn(0).setPreferredWidth(150);  // Filename column
            columnModel.getColumn(1).setPreferredWidth(450);  // Snippet column
            
            // Custom renderer for the snippet column
            columnModel.getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable table, Object value,
                        boolean isSelected, boolean hasFocus, int row, int column) {
                    String text = (String) value;
                    String html = formatSnippet(text, searchTerm);
                    
                    JLabel label = (JLabel) super.getTableCellRendererComponent(
                        table, html, isSelected, hasFocus, row, column);
                    label.putClientProperty("html.disable", Boolean.FALSE);
                    return label;
                }
            });
            
            setRowHeight(25);

            // Add selection listener for navigation
            getSelectionModel().addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting()) {
                    int selectedRow = getSelectedRow();
                    if (selectedRow >= 0) {
                        SearchResult result = ((SearchResultTableModel) getModel())
                            .getResultAt(selectedRow);
                        callback.navigateToResult(result.filePath, result.position, result.searchTerm);
                    }
                }
            });

            // Configure keyboard wrapping behavior
            setFocusTraversalKeysEnabled(false);
            InputMap im = getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
            ActionMap am = getActionMap();

            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "selectPreviousWrapping");
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "selectNextWrapping");

            am.put("selectPreviousWrapping", new AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    int currentRow = getSelectedRow();
                    int rowCount = getModel().getRowCount();
                    if (rowCount > 0) {
                        if (currentRow > 0) {
                            setSelectedRow(currentRow - 1);
                        } else {
                            setSelectedRow(rowCount - 1);  // Wrap to bottom
                        }
                    }
                }
            });

            am.put("selectNextWrapping", new AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    int currentRow = getSelectedRow();
                    int rowCount = getModel().getRowCount();
                    if (rowCount > 0) {
                        if (currentRow < rowCount - 1) {
                            setSelectedRow(currentRow + 1);
                        } else {
                            setSelectedRow(0);  // Wrap to top
                        }
                    }
                }
            });
        }

        public void setSelectedRow(int row) {
            if (row >= 0 && row < getModel().getRowCount()) {
                setRowSelectionInterval(row, row);
                scrollRectToVisible(getCellRect(row, 0, true));
            }
        }
    }
    
    private static class SearchResultTableModel extends AbstractTableModel {
        private final String[] columnNames = {"File", "Match"};
        private final List<SearchResult> results;
        
        public SearchResultTableModel(List<SearchResult> results) {
            this.results = results;
        }
        
        @Override
        public int getRowCount() {
            return results.size();
        }
        
        @Override
        public int getColumnCount() {
            return columnNames.length;
        }
        
        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }
        
        @Override
        public Object getValueAt(int row, int column) {
            SearchResult result = results.get(row);
            return switch (column) {
                case 0 -> result.filePath.getFileName().toString();
                case 1 -> result.snippet;
                default -> "";
            };
        }
        
        public SearchResult getResultAt(int row) {
            return results.get(row);
        }
    }
    
    private static String formatSnippet(String snippet, String searchTerm) {
        // Case-insensitive replacement to highlight search term
        if (searchTerm == null || searchTerm.isEmpty()) return snippet;
        
        String pattern = "(?i)" + Pattern.quote(searchTerm);
        return "<html>" + snippet.replaceAll(pattern, 
            "<span style='background-color: #FFEB3B; font-weight: bold;'>$0</span>") + "</html>";
    }
    
    public static void showSearchDialog(Component parent, Path rootDirectory, 
            SearchResultCallback callback, Runnable clearHighlights) {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), "Search Collection");
        dialog.setLayout(new BorderLayout());
        
        // Create search panel
        JPanel searchPanel = new JPanel(new BorderLayout());
        JTextField searchField = new JTextField(30);
        JCheckBox caseSensitiveBox = new JCheckBox("Case sensitive");
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(caseSensitiveBox, BorderLayout.EAST);
        
        // Create results table with empty model initially
        SearchResultTableModel model = new SearchResultTableModel(new ArrayList<>());
        SearchResultsTable resultsTable = new SearchResultsTable(model, "", callback);
        
        // Add click listeners
        resultsTable.addMouseListener(new java.awt.event.MouseAdapter() {
            private static final int CLICK_DELAY = 300; // milliseconds
            private long lastClickTime = 0;
            
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                long clickTime = System.currentTimeMillis();
                int row = resultsTable.rowAtPoint(evt.getPoint());
                
                if (row >= 0) {
                    resultsTable.setSelectedRow(row);
                    
                    // Check if this is a double click
                    if (clickTime - lastClickTime < CLICK_DELAY) {
                        clearHighlights.run();
                        dialog.dispose(); // Close on double click
                    }
                    
                    lastClickTime = clickTime;
                }
            }
        });
        
        // Add window closing listener to clean up highlights
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                clearHighlights.run();
            }
        });
        
        // Previous code for search handler...
        searchField.addActionListener(e -> {
            String searchTerm = searchField.getText();
            if (!searchTerm.isEmpty()) {
                try {
                    List<SearchResult> results = searchCollection(rootDirectory, searchTerm, 
                        caseSensitiveBox.isSelected());
                    dialog.setTitle(String.format("Search Collection - %d results", results.size()));
                    SearchResultTableModel newModel = new SearchResultTableModel(results);
                    resultsTable.setModel(newModel);
                    resultsTable.setupTable();

                    // Automatically select first result
                    if (!results.isEmpty()) {
                        SwingUtilities.invokeLater(() -> {
                            resultsTable.setSelectedRow(0);
                            resultsTable.requestFocusInWindow();
                        });
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });
        
        dialog.add(searchPanel, BorderLayout.NORTH);
        dialog.add(new JScrollPane(resultsTable), BorderLayout.CENTER);
        
        dialog.setSize(800, 500);
        dialog.setLocationRelativeTo(parent);
        
        // Focus search field when dialog opens
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent e) {
                searchField.requestFocusInWindow();
            }
        });
        
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setVisible(true);
    }
}