package ghidra.notepad;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Maintains a history of visited files for back/forward navigation.
 */
public class NavigationHistory {
    private final List<Path> history = new ArrayList<>();
    private int currentIndex = -1;
    
    public void addLocation(Path path) {
        // Don't add if it's the same as current location
        if (currentIndex >= 0 && currentIndex < history.size() && 
            history.get(currentIndex).equals(path)) {
            return;
        }
        
        // Remove any forward history
        if (currentIndex < history.size() - 1) {
            history.subList(currentIndex + 1, history.size()).clear();
        }
        
        history.add(path);
        currentIndex = history.size() - 1;
    }
    
    public boolean canGoBack() {
        return currentIndex > 0;
    }
    
    public boolean canGoForward() {
        return currentIndex < history.size() - 1;
    }
    
    public Path goBack() {
        if (canGoBack()) {
            currentIndex--;
            return history.get(currentIndex);
        }
        return null;
    }
    
    public Path goForward() {
        if (canGoForward()) {
            currentIndex++;
            return history.get(currentIndex);
        }
        return null;
    }

    public void reset() {
        history.clear();
        currentIndex = -1;
    }
}