package ghidra.notepad;

import ghidra.MiscellaneousPluginPackage;
import ghidra.app.plugin.PluginCategoryNames;
import ghidra.framework.plugintool.*;
import ghidra.framework.plugintool.util.PluginStatus;

@PluginInfo(
    status = PluginStatus.RELEASED,
    packageName = MiscellaneousPluginPackage.NAME,
    category = "Notes",
    shortDescription = "Markdown Notepad",
    description = "Markdown notepad integrated into Ghidra"
)
public class MarkdownNotepadPlugin extends Plugin {
    private MarkdownNotepadProvider provider;

    public MarkdownNotepadPlugin(PluginTool tool) {
        super(tool);
        provider = new MarkdownNotepadProvider(tool, getName());
    }

    @Override
    public void dispose() {
        provider.setVisible(false);
    }
}
