# Ghidra Markdown Notepad Plugin

<p align="center">

  <img src="readme_images/big_logo.png" width="400">

</p>

<div align="center">

**A feature-rich markdown editor plugin for Ghidra that supports collections of notes without leaving Ghidra.**

</div>

# Markdown Notepad

I wrote this plugin to solve the problem of juggling tabs to navigate between Ghidra and Obsidian when reverse engineering a binary and writing notes.

The result is a nice `Markdown Notepad` window that can be placed in a convenient space in Ghidra so notes can be taken without needing Obsidian open.

![crayon.png](/readme_images/vaporwave_both.png)

And for the light-mode sadists:

![crayon.png](/readme_images/light_both.png)

## Features

**My favourite features:**

- Using `[0x1234]` syntax to make addresses clickable in the preview (makes Ghidra go to that address)
- Using `{0x5678}` syntax to create an address link that is previewed as the function name at that address (no more fear of inconsistent function names for the same function in notes!)
- Ghidra theme integration (syntax highlighting and preview colours match the selected Ghidra theme)
- Auto-opening of same collection on restart
- Search all documents, easy to flick through results with arrow keys
- Image imports (not as simple as Obsidian but works well)
- Built into Ghidra so no juggling Obsidian window around
- Easy file organisation in the file tree on the left of the window
- Easy to navigate between collections of notes if needed
- Contents listing for easy navigation within long markdown notes

**More specific:**

- *Document Management*
    - Create and manage collections of markdown documents
    - Hierarchical directory structure for organizing notes
    - Drag-and-drop file organization
    - File and directory renaming/deletion
    - Unsaved changes indication
- *Editor Features*
    - Markdown syntax highlighting
    - Real-time preview with split view
    - Undo/redo functionality
    - Document and collection-wide search
    - Support for tables
    - Contents listing for easy navigation and overview
    - Zoom support
    - Easy navigation to previous notes with back/forward buttons
- *Image Support*
    - Drag-and-drop image importing
    - Clipboard paste support for images
- *Navigation and Integration*
    - Program address linking (`[0x1234]` syntax for direct navigation)
    - Function address linking (`{0x5678}` syntax for function name preview + address navigation)
    - Clickable web links in preview
    - Keyboard shortcuts for all major operations
    - Integration with the Ghidra theming system
- *Search Capabilities*
    - Document search
    - Collection-wide search with context preview
    - Case-sensitive search option
    - Results navigation

## Keyboard Shortcuts

| Action | Shortcut |
|--------|----------|
| Open Collection | Ctrl+O |
| Create New Collection | Ctrl+Shift+N |
| Save Document | Ctrl+S |
| Undo | Ctrl+Z |
| Redo | Ctrl+Y |
| New Document | Ctrl+N |
| New Directory | Ctrl+Shift+D |
| Import Image | Ctrl+I |
| Document Search | Ctrl+F |
| Collection Search | Ctrl+Shift+F |
| Zoom In | Ctrl+Equals |
| Zoom Out | Ctrl+Minus |
| Go Back | Alt+Left |
| Go Forwards | Alt+Right |

## Requirements

- Ghidra 11.2.1 installed

## Libraries Used

The plugin is built on the following key components:
- RSyntaxTextArea for syntax highlighting
- CommonMark for markdown parsing
- Ghidra's docking framework for UI integration
- Java NIO for file operations

## Installation

1. Download the `.zip` of the plugin
2. Open Ghidra, and on the main window go to `File` -> `Install Extensions`
3. Click the green '+' on the rop right, and select the downloaded `.zip` file
4. Restart Ghidra to install the plugin
5. Open Ghidra, you should get a prompt to configure the plugin, if so, select the tick box on the left to complete the installation
6. Should now be available in `Window` -> `Markdown Notepad` (if not, next step)
7. If not available, will need manual configuring, go to `File` -> `Configure`
8. Now in the `Configure Tool` menu, click the `Configure` in the `Miscellaneous` section
9. Should see the `MarkdownNotepadPlugin` in the list, if so, make sure the first tick box in the row is ticked
10. Should now be all good to write some notes!

