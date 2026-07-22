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
        private static final int BTN_RESTORE_DEFAULTS = 900;
        private static final int BTN_CHOOSE_SOUND = 901;

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
            // Combine categories: general + alerts + rendering + performance (if present)
            java.util.List<net.minecraftforge.fml.client.config.IConfigElement> list = new java.util.ArrayList<>();
            try {
                if (cfg.hasCategory("general")) list.addAll(new ConfigElement(cfg.getCategory("general")).getChildElements());
                if (cfg.hasCategory("alerts")) list.addAll(new ConfigElement(cfg.getCategory("alerts")).getChildElements());
                if (cfg.hasCategory("rendering")) list.addAll(new ConfigElement(cfg.getCategory("rendering")).getChildElements());
                if (cfg.hasCategory("performance")) list.addAll(new ConfigElement(cfg.getCategory("performance")).getChildElements());
            } catch (Exception e) {
                // Fallback: return full general category
                return new ConfigElement(cfg.getCategory(Configuration.CATEGORY_GENERAL)).getChildElements();
            }
            return list;
        }

        @Override
        public void initGui() {
            super.initGui();
            // Add Restore Defaults and Choose Sound buttons at bottom left
            int btnWidth = 150;
            int btnHeight = 20;
            int x = 10;
            int y = this.height - 30;
            this.buttonList.add(new net.minecraft.client.gui.GuiButton(BTN_RESTORE_DEFAULTS, x, y, btnWidth, btnHeight, "Restore Defaults"));
            this.buttonList.add(new net.minecraft.client.gui.GuiButton(BTN_CHOOSE_SOUND, x + btnWidth + 4, y, btnWidth, btnHeight, "Choose Alert Sound"));
        }

        @Override
        protected void actionPerformed(net.minecraft.client.gui.GuiButton button) {
            super.actionPerformed(button);
            if (button.id == BTN_RESTORE_DEFAULTS) {
                // Reset all properties to defaults
                Configuration cfg = FireballPredict.config;
                if (cfg != null) {
                    for (String catName : cfg.getCategoryNames()) {
                        net.minecraftforge.common.config.ConfigCategory cat = cfg.getCategory(catName);
                        for (java.util.Map.Entry<String, net.minecraftforge.common.config.Property> e : cat.entrySet()) {
                            try {
                                e.getValue().setToDefault();
                            } catch (Exception ex) {
                                // ignore
                            }
                        }
                    }
                    cfg.save();
                    FireballPredict.applyConfig(cfg);
                    net.minecraft.client.Minecraft.getMinecraft().thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText("[FireballPredict] Defaults restored."));
                    // Rebuild GUI to reflect defaults
                    mc.displayGuiScreen(new ModsConfigGui());
                }
            } else if (button.id == BTN_CHOOSE_SOUND) {
                mc.displayGuiScreen(new SoundSelectGui(this));
            }
        }

        @Override
        public void onGuiClosed() {
            super.onGuiClosed();
            // Ensure config changes are applied immediately
            Configuration cfg = FireballPredict.config;
            if (cfg != null) {
                try {
                    cfg.save();
                } catch (Exception ignored) {}
                FireballPredict.applyConfig(cfg);
            }
        }
    }

    /**
     * Simple screen to choose from a few safe built-in alert sounds. Selecting a sound writes
     * it to the alerts.alertSound config property and returns to the parent config screen.
     */
    public static class SoundSelectGui extends GuiScreen {
        private final GuiScreen parent;
        private final String[] builtIn = new String[]{"random.pop", "random.orb", "entity.arrow.hit", "entity.experience.orb.pickup", "entity.generic.explode"};

        public SoundSelectGui(GuiScreen parent) {
            this.parent = parent;
        }

        @Override
        public void initGui() {
            this.buttonList.clear();
            int id = 2000;
            int y = 40;
            for (String s : builtIn) {
                this.buttonList.add(new net.minecraft.client.gui.GuiButton(id++, this.width / 2 - 100, y, 200, 20, s));
                y += 24;
            }
            this.buttonList.add(new net.minecraft.client.gui.GuiButton(2100, this.width / 2 - 100, y + 8, 200, 20, "Cancel"));
        }

        @Override
        protected void actionPerformed(net.minecraft.client.gui.GuiButton button) {
            if (button.id == 2100) {
                mc.displayGuiScreen(parent);
                return;
            }
            if (button.id >= 2000 && button.id < 2100) {
                int idx = button.id - 2000;
                if (idx >= 0 && idx < builtIn.length) {
                    String picked = builtIn[idx];
                    Configuration cfg = FireballPredict.config;
                    if (cfg != null) {
                        net.minecraftforge.common.config.Property p = cfg.get("alerts", "alertSound", FireballPredict.ALERT_SOUND);
                        p.set(picked);
                        cfg.save();
                        FireballPredict.applyConfig(cfg);
                        net.minecraft.client.Minecraft.getMinecraft().thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText("[FireballPredict] Alert sound set to " + picked));
                    }
                }
                mc.displayGuiScreen(parent);
            }
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            this.drawDefaultBackground();
            drawCenteredString(this.fontRendererObj, "Select Alert Sound", this.width / 2, 12, 0xFFFFFF);
            super.drawScreen(mouseX, mouseY, partialTicks);
        }

        @Override
        public boolean doesGuiPauseGame() { return false; }
    }
}

