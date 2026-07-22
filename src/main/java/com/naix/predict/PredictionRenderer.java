package com.naix.predict;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class PredictionRenderer
{
    private static final float ALPHA = 0.3f;
    private static final int CACHE_SIZE = 7;
    private static final int RENDER_TICK_INTERVAL = 3; // 每 N 帧更新屏幕坐标以减少 glGet 开销
    // 视角剔除阈值（平方），由配置提供（在运行时使用 FireballPredict.RENDER_CULL_DIST）

    // 缓存避免每帧分配
    private boolean[] fullCache = new boolean[CACHE_SIZE * CACHE_SIZE * CACHE_SIZE];
    private boolean[] opaqueCache = new boolean[CACHE_SIZE * CACHE_SIZE * CACHE_SIZE];
    private int cacheCenterX = Integer.MIN_VALUE, cacheCenterY = Integer.MIN_VALUE, cacheCenterZ = Integer.MIN_VALUE;

    private int renderTickCounter = 0;
    private FloatBuffer modelview = BufferUtils.createFloatBuffer(16);
    private FloatBuffer projection = BufferUtils.createFloatBuffer(16);
    private IntBuffer viewport = BufferUtils.createIntBuffer(16);
    private FloatBuffer screenCoords = BufferUtils.createFloatBuffer(3);

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event)
    {
        if (!FireballPredict.enabled || FireballPredict.currentHitPos == null) return;

        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.theWorld;
        if (world == null) return;

        BlockPos hit = FireballPredict.currentHitPos;
        int color = FireballPredict.currentColor;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;

        double playerX = mc.thePlayer.lastTickPosX
            + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * event.partialTicks;
        double playerY = mc.thePlayer.lastTickPosY
            + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * event.partialTicks;
        double playerZ = mc.thePlayer.lastTickPosZ
            + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * event.partialTicks;

        // 视角简单剔除：若预测点较远且位于玩家视线后方，则跳过开销较大的渲染（减少 GPU/CPU 负担）
        double hitCenterX = hit.getX() + 0.5;
        double hitCenterY = hit.getY() + 0.5;
        double hitCenterZ = hit.getZ() + 0.5;
        double dxp = hitCenterX - mc.thePlayer.posX;
        double dyp = hitCenterY - mc.thePlayer.posY;
        double dzp = hitCenterZ - mc.thePlayer.posZ;
        double distSq = dxp * dxp + dyp * dyp + dzp * dzp;
        boolean skipHeavy = false;
        double cullDistSq = FireballPredict.RENDER_CULL_DIST * FireballPredict.RENDER_CULL_DIST;
        if (distSq > cullDistSq) {
            net.minecraft.util.Vec3 look = mc.thePlayer.getLook(1.0f);
            double len = Math.sqrt(dxp * dxp + dyp * dyp + dzp * dzp);
            if (len > 1e-6) {
                double dot = (look.xCoord * dxp + look.yCoord * dyp + look.zCoord * dzp) / len;
                if (dot < -0.15) skipHeavy = true; // 明显在背面且远距离则跳过
            }
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(-playerX, -playerY, -playerZ);
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(770, 771);
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();

        float etaScreenX = -1, etaScreenY = -1;
        boolean etaOnScreen = false;

        try
        {
            if (!skipHeavy) {
                final int SIZE = CACHE_SIZE;
                // 仅在中心位置变化时或首次填充时更新缓存，避免每帧新分配/查询
                if (hit.getX() != cacheCenterX || hit.getY() != cacheCenterY || hit.getZ() != cacheCenterZ) {
                    cacheCenterX = hit.getX(); cacheCenterY = hit.getY(); cacheCenterZ = hit.getZ();
                    for (int ix = 0; ix < SIZE; ix++) {
                        for (int iy = 0; iy < SIZE; iy++) {
                            for (int iz = 0; iz < SIZE; iz++) {
                                BlockPos bp = hit.add(ix - 3, iy - 3, iz - 3);
                                int idx = (ix * SIZE + iy) * SIZE + iz;
                                if (!world.isAirBlock(bp)) {
                                    net.minecraft.block.Block block = world.getBlockState(bp).getBlock();
                                    fullCache[idx] = block.isFullCube();
                                    opaqueCache[idx] = block.isOpaqueCube();
                                } else {
                                    fullCache[idx] = false;
                                    opaqueCache[idx] = false;
                                }
                            }
                        }
                    }
                }

                Tessellator tess = Tessellator.getInstance();
                WorldRenderer wr = tess.getWorldRenderer();
                wr.begin(7, DefaultVertexFormats.POSITION_COLOR);

                for (int dx = -2; dx <= 2; dx++) {
                    for (int dy = -2; dy <= 2; dy++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            int cx = dx + 3, cy = dy + 3, cz = dz + 3;
                            int ci = (cx * SIZE + cy) * SIZE + cz;
                            if (!fullCache[ci]) continue;

                            double bx = hit.getX() + dx;
                            double by = hit.getY() + dy;
                            double bz = hit.getZ() + dz;
                            double ex = bx + 1.0;
                            double ey = by + 1.0;
                            double ez = bz + 1.0;

                            int ni;

                            // 底 Y-
                            ni = (cx * SIZE + (cy - 1)) * SIZE + cz;
                            if (!opaqueCache[ni]) {
                                wr.pos(bx, by, bz).color(r, g, b, ALPHA).endVertex();
                                wr.pos(bx, by, ez).color(r, g, b, ALPHA).endVertex();
                                wr.pos(ex, by, ez).color(r, g, b, ALPHA).endVertex();
                                wr.pos(ex, by, bz).color(r, g, b, ALPHA).endVertex();
                            }
                            // 顶 Y+
                            ni = (cx * SIZE + (cy + 1)) * SIZE + cz;
                            if (!opaqueCache[ni]) {
                                wr.pos(bx, ey, bz).color(r, g, b, ALPHA).endVertex();
                                wr.pos(ex, ey, bz).color(r, g, b, ALPHA).endVertex();
                                wr.pos(ex, ey, ez).color(r, g, b, ALPHA).endVertex();
                                wr.pos(bx, ey, ez).color(r, g, b, ALPHA).endVertex();
                            }
                            // 西 X-
                            ni = ((cx - 1) * SIZE + cy) * SIZE + cz;
                            if (!opaqueCache[ni]) {
                                wr.pos(bx, by, bz).color(r, g, b, ALPHA).endVertex();
                                wr.pos(bx, ey, bz).color(r, g, b, ALPHA).endVertex();
                                wr.pos(bx, ey, ez).color(r, g, b, ALPHA).endVertex();
                                wr.pos(bx, by, ez).color(r, g, b, ALPHA).endVertex();
                            }
                            // 东 X+
                            ni = ((cx + 1) * SIZE + cy) * SIZE + cz;
                            if (!opaqueCache[ni]) {
                                wr.pos(ex, by, bz).color(r, g, b, ALPHA).endVertex();
                                wr.pos(ex, by, ez).color(r, g, b, ALPHA).endVertex();
                                wr.pos(ex, ey, ez).color(r, g, b, ALPHA).endVertex();
                                wr.pos(ex, ey, bz).color(r, g, b, ALPHA).endVertex();
                            }
                            // 北 Z-
                            ni = (cx * SIZE + cy) * SIZE + (cz - 1);
                            if (!opaqueCache[ni]) {
                                wr.pos(bx, by, bz).color(r, g, b, ALPHA).endVertex();
                                wr.pos(ex, by, bz).color(r, g, b, ALPHA).endVertex();
                                wr.pos(ex, ey, bz).color(r, g, b, ALPHA).endVertex();
                                wr.pos(bx, ey, bz).color(r, g, b, ALPHA).endVertex();
                            }
                            // 南 Z+
                            ni = (cx * SIZE + cy) * SIZE + (cz + 1);
                            if (!opaqueCache[ni]) {
                                wr.pos(bx, by, ez).color(r, g, b, ALPHA).endVertex();
                                wr.pos(bx, ey, ez).color(r, g, b, ALPHA).endVertex();
                                wr.pos(ex, ey, ez).color(r, g, b, ALPHA).endVertex();
                                wr.pos(ex, by, ez).color(r, g, b, ALPHA).endVertex();
                            }
                        }
                    }
                }

                tess.draw();

                // 绘制白色直线：火球发射位置 → 预测落点
                net.minecraft.util.Vec3 origin = FireballPredict.currentFireballOrigin;
                if (origin != null) {
                    double hitX = hit.getX() + 0.5;
                    double hitY = hit.getY() + 0.5;
                    double hitZ = hit.getZ() + 0.5;

                    GL11.glLineWidth(2.5f);
                    WorldRenderer wrLine = tess.getWorldRenderer();
                    wrLine.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
                    wrLine.pos(origin.xCoord, origin.yCoord, origin.zCoord).color(1.0f, 1.0f, 1.0f, 0.75f).endVertex();
                    wrLine.pos(hitX, hitY, hitZ).color(1.0f, 1.0f, 1.0f, 0.75f).endVertex();
                    tess.draw();
                }

                // 计算 ETA 文字在屏幕上的位置（每隔若干帧更新，减少 glGet）
                if (FireballPredict.currentFireball != null && FireballPredict.currentETA >= 0) {
                    renderTickCounter++;
                    if (renderTickCounter % RENDER_TICK_INTERVAL == 0) {
                        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelview);
                        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
                        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);

                        double textX = hit.getX() + 0.5;
                        double textY = hit.getY() + 3.5;
                        double textZ = hit.getZ() + 0.5;

                        if (GLU.gluProject((float) textX, (float) textY, (float) textZ,
                                modelview, projection, viewport, screenCoords)) {
                            if (screenCoords.get(2) >= 0 && screenCoords.get(2) <= 1) {
                                etaScreenX = screenCoords.get(0);
                                etaScreenY = viewport.get(3) - screenCoords.get(1); // 翻转 Y 轴
                                etaOnScreen = true;
                            }
                        }
                    }
                }
            }
        }
        finally
        {
            GlStateManager.enableDepth();
            GlStateManager.depthMask(true);
            GlStateManager.enableCull();
            GlStateManager.enableLighting();
            GlStateManager.disableBlend();
            GlStateManager.enableTexture2D();
            GlStateManager.popMatrix();
        }

        // 渲染 ETA 文字
        if (etaOnScreen && FireballPredict.currentETA >= 0) {
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glOrtho(0, mc.displayWidth, mc.displayHeight, 0, -1, 1);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();

            GlStateManager.disableLighting();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(770, 771);

            String text = String.format("%.1fs", FireballPredict.currentETA);
            int halfW = mc.fontRendererObj.getStringWidth(text) / 2;
            int textY = (int) (etaScreenY - 5);
            float scale = 2.5f;

            GlStateManager.pushMatrix();
            GlStateManager.translate(etaScreenX, textY, 0);
            GlStateManager.scale(scale, scale, 1.0f);
            // 黑色描边：上下左右各偏移 1px
            mc.fontRendererObj.drawString(text, -halfW - 1, -1, 0x000000);
            mc.fontRendererObj.drawString(text, -halfW + 1, -1, 0x000000);
            mc.fontRendererObj.drawString(text, -halfW - 1,  1, 0x000000);
            mc.fontRendererObj.drawString(text, -halfW + 1,  1, 0x000000);
            // 红色正文居中
            mc.fontRendererObj.drawString(text, -halfW, 0, 0xFF5555);
            GlStateManager.popMatrix();

            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
        }

        // HUD 警告显示（可选）        if (FireballPredict.hudWarningActive) {            GL11.glMatrixMode(GL11.GL_PROJECTION);            GL11.glPushMatrix();            GL11.glLoadIdentity();            GL11.glOrtho(0, mc.displayWidth, mc.displayHeight, 0, -1, 1);            GL11.glMatrixMode(GL11.GL_MODELVIEW);            GL11.glPushMatrix();            GL11.glLoadIdentity();            GlStateManager.disableLighting();            GlStateManager.enableBlend();            GlStateManager.blendFunc(770, 771);            String warn = "当前位于烈焰弹爆炸范围内";            int w = mc.fontRendererObj.getStringWidth(warn);            int centerX = mc.displayWidth / 2;            int x = centerX - w / 2;            int y = 20; // 屏幕顶部            int padding = 8;            int bgLeft = x - padding;            int bgTop = y - 6;            int bgRight = x + w + padding;            int bgBottom = y + 12;            // 背景 / 风格            if (FireballPredict.HUD_STYLE == 2) {                // 渐变（简单两段）                int mid = (bgTop + bgBottom) / 2;                Gui.drawRect(bgLeft, bgTop, bgRight, mid, 0xCCFF3333); // 半透明红                Gui.drawRect(bgLeft, mid, bgRight, bgBottom, 0xCCFF9933); // 半透明橙            } else {                Gui.drawRect(bgLeft, bgTop, bgRight, bgBottom, 0xAA000000); // 半透明黑            }            // 图标样式            if (FireballPredict.HUD_STYLE == 1 || FireballPredict.HUD_STYLE == 2) {                int iconLeft = bgLeft + 4;                int iconTop = y - 2;                Gui.drawRect(iconLeft, iconTop, iconLeft + 12, iconTop + 12, 0xFFFF4444);                x = iconLeft + 16; // 文本偏移，避免覆盖图标            }            // 文本描边 + 正文            mc.fontRendererObj.drawString(warn, x - 1, y - 1, 0x000000);            mc.fontRendererObj.drawString(warn, x + 1, y - 1, 0x000000);            mc.fontRendererObj.drawString(warn, x - 1, y + 1, 0x000000);            mc.fontRendererObj.drawString(warn, x + 1, y + 1, 0x000000);            mc.fontRendererObj.drawString(warn, x, y, 0xFF4444);            GL11.glMatrixMode(GL11.GL_PROJECTION);            GL11.glPopMatrix();            GL11.glMatrixMode(GL11.GL_MODELVIEW);            GL11.glPopMatrix();        }
    }
}