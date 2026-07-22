package com.naix.predict;

// Placeholder factory kept for backward compatibility with @Mod.guiFactory string.
// The actual in-game config is opened via a keybind (default: O) implemented in FireballPredict.
// Keeping a simple no-op class avoids compile/runtime issues caused by mismatched IModGuiFactory
// signatures across different Forge versions.
public class ConfigGuiFactory {
    // Intentionally empty; GuiFactory logic is handled via keybind in the mod class.
}
