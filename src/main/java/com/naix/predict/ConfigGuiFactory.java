package com.naix.predict;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.client.config.GuiConfig;

import java.util.List;
import java.util.Set;

public class ConfigGuiFactory implements IModGuiFactory {
    @Override
    public void initialize(Minecraft minecraftInstance) {
        // no-op
    }

    /**
     * For this Forge version the factory provides a mainConfigGuiClass() which is instantiated
     * when the Mods->Config button is pressed. Return a simple wrapper Gui class that builds
     * a GuiConfig using the existing Configuration instance.
     */
    @Override
    public Class<? extends GuiScreen> mainConfigGuiClass() {
        return ModsConfigGui.class;
    }

    @Override
    public Set<net.minecraftforge.fml.client.IModGuiFactory.RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return java.util.Collections.emptySet();
    }

    @Override
    public net.minecraftforge.fml.client.IModGuiFactory.RuntimeOptionGuiHandler getHandlerFor(net.minecraftforge.fml.client.IModGuiFactory.RuntimeOptionCategoryElement element) {
        return null;
    }

    /**
     * A small GuiConfig wrapper with a no-argument constructor so Forge can instantiate it
     * via mainConfigGuiClass(). It builds the config element list from FireballPredict.config
     * (which is set during preInit) or falls back to an empty list.
     */
    public static class ModsConfigGui extends GuiConfig {
        public ModsConfigGui() {
            super(null,
                buildList(),
                FireballPredict.MODID,
                false,
                false,
                "Fireball Predict Configuration");
        }

        private static java.util.List<net.minecraftforge.fml.client.config.IConfigElement> buildList() {
            Configuration cfg = FireballPredict.config;
            if (cfg == null) return java.util.Collections.emptyList();
            return new ConfigElement(cfg.getCategory(Configuration.CATEGORY_GENERAL)).getChildElements();
        }
    }
}
