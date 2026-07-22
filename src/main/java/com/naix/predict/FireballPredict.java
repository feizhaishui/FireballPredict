package com.naix.predict;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityWitherSkull;
import net.minecraft.init.Items;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.common.config.Configuration;

import org.lwjgl.input.Keyboard;

import java.util.List;

@Mod(modid = FireballPredict.MODID, version = FireballPredict.VERSION)
public class FireballPredict
{
    public static final String MODID = "fireball_predict";
    public static final String VERSION = "2.2";

    // 开关状态 (PredictionRenderer 读取)
    public static boolean enabled = true;
    public static BlockPos currentHitPos = null;
    public static EntityFireball currentFireball = null;
    public static Vec3 currentFireballOrigin = null;  // 火球发射位置
    public static int currentColor = 0x00FF00;
    public static double currentETA = -1;

    // 火球距离预测撞击点越近，警告颜色越偏红；越远则越偏绿。
    private static final double NEAR_DISTANCE = 8.0D;
    private static final double MEDIUM_DISTANCE = 24.0D;
    private static final double FAR_DISTANCE = 48.0D;

    // 可调参数
    // 默认参数（可通过配置覆盖）
    public static int UPDATE_TICK_INTERVAL = 4; // 每 N 个客户端 tick 更新一次预测（降低 CPU）
    public static double SCAN_RANGE = 64.0D;    // 仅在玩家周围该范围内扫描火球
    public static double MAX_RAY_DISTANCE = 200.0D; // 射线最远检测距离（比原来小以节省开销）
    public static double MIN_SPEED_SQ = 1e-6;  // 速度平方阈值
    public static double RENDER_CULL_DIST = 40.0D; // 视角剔除阈值（米），在渲染端平方使用

    private KeyBinding keyToggle;
    private int tickCounter = 0;
    private int warningCounter = 0;
    public static double WARN_RANGE = 2.5D;  // 5×5×5 范围

    // Alert mode: 0=HUD,1=Chat,2=Sound    public static int ALERT_MODE = 1;
    // HUD style: 0 = plain text, 1 = icon + text, 2 = gradient box + text
    public static int HUD_STYLE = 1;
    // Custom alert sound (string name as used by playSound)
    public static String ALERT_SOUND = "random.pop";
    public static float ALERT_SOUND_VOL = 1.0f;
    public static float ALERT_SOUND_PITCH = 1.0f;    // HUD warning state (client-rendered)    public static boolean hudWarningActive = false;    public static int hudWarningTicks = 0;    public static int HUD_DISPLAY_TICKS = 40; // 默认 2 秒    private static long lastWarnTimeMs = 0L;
    @EventHandler
    public void preInit(FMLPreInitializationEvent event)
    {
        // 读取配置文件（在 preInit 阶段）        Configuration cfg = new Configuration(event.getSuggestedConfigurationFile());        try {            cfg.load();            UPDATE_TICK_INTERVAL = cfg.getInt("updateTickInterval", "general", UPDATE_TICK_INTERVAL, 1, 40, "每 N 个客户端 tick 更新一次预测（降低 CPU）");            SCAN_RANGE = (double) cfg.getFloat("scanRange", "general", (float) SCAN_RANGE, 8.0F, 512.0F, "仅在玩家周围该范围内扫描火球");            MAX_RAY_DISTANCE = (double) cfg.getFloat("maxRayDistance", "general", (float) MAX_RAY_DISTANCE, 32.0F, 1024.0F, "射线最远检测距离（米）");            RENDER_CULL_DIST = (double) cfg.getFloat("renderCullDistance", "general", (float) RENDER_CULL_DIST, 8.0F, 256.0F, "若预测点远且位于玩家背面则跳过重渲染的距离阈值（米）");            MIN_SPEED_SQ = (double) cfg.getFloat("minSpeedSq", "general", (float) MIN_SPEED_SQ, 0.0F, 1.0F, "忽略速度平方低于该值的火球");            WARN_RANGE = (double) cfg.getFloat("warnRange", "general", (float) WARN_RANGE, 0.5F, 8.0F, "触发警告的半径（米）");            ALERT_MODE = cfg.getInt("alertMode", "alerts", ALERT_MODE, 0, 2, "警告方式：0=HUD,1=Chat,2=Sound");            HUD_STYLE = cfg.getInt("hudStyle", "alerts", HUD_STYLE, 0, 2, "HUD 风格：0=文本,1=图标+文本,2=渐变方块+文本");
            ALERT_SOUND = cfg.getString("alertSound", "alerts", ALERT_SOUND, "警告音效名称（如 random.pop）");
            ALERT_SOUND_VOL = cfg.getFloat("alertSoundVol", "alerts", ALERT_SOUND_VOL, 0.0f, 4.0f, "警告音量");
            ALERT_SOUND_PITCH = cfg.getFloat("alertSoundPitch", "alerts", ALERT_SOUND_PITCH, 0.1f, 4.0f, "警告音高");
            HUD_DISPLAY_TICKS = cfg.getInt("hudDisplayTicks", "alerts", HUD_DISPLAY_TICKS, 2, 200, "HUD 提示显示时长（以 tick 为单位，20 tick = 1s）");        } finally {            if (cfg.hasChanged()) cfg.save();        }        // 下面的 init 操作在 preInit 后继续注册按键和事件        // R 键：开关火焰弹预测        keyToggle = new KeyBinding(
            "key.naix_test.fireball",     // 描述
            Keyboard.KEY_R,                // R 键
            "key.categories.naix_test"     // 类别
        );        ClientRegistry.registerKeyBinding(keyToggle);        // 注册事件        FMLCommonHandler.instance().bus().register(this);        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new PredictionRenderer());    }

    /**
     * 处理客户端按键事件，默认按下 R 键时切换预测显示。
     * Handle client key input events and toggle prediction display when R is pressed.
     */
    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event)
    {
        if (keyToggle.isPressed()) {

            enabled = !enabled;
            if (Minecraft.getMinecraft().thePlayer != null) {
                Minecraft.getMinecraft().thePlayer.addChatMessage(
                    new ChatComponentText(enabled ? "§a[火焰弹预测] 已开启" : "§c[火焰弹预测] 已关闭")
                );
            }
        }
    }

    /**
     * 每 N 帧检查客户端世界，寻找最近的火球或手持火焰弹并计算预测落点。
     */
    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END) return;
        if (++tickCounter % UPDATE_TICK_INTERVAL != 0) return;
        if (!enabled) {
            currentHitPos = null;
            currentFireball = null;
            currentFireballOrigin = null;
            currentColor = 0;
            currentETA = -1;
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.theWorld;
        if (world == null || mc.thePlayer == null) {
            currentHitPos = null;
            currentFireball = null;
            currentFireballOrigin = null;
            currentColor = 0;
            currentETA = -1;
            return;
        }

        BlockPos newHit = null;
        int color = 0xFF0000;

        // 模式 1：火球检测（仅扫描玩家周围 SCAN_RANGE 范围内）
        double minDist = Double.MAX_VALUE;
        EntityFireball bestFireball = null;
        Vec3 playerPos = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);

        AxisAlignedBB bbox = mc.thePlayer.getEntityBoundingBox().expand(SCAN_RANGE, SCAN_RANGE, SCAN_RANGE);
        List<EntityFireball> candidates = world.getEntitiesWithinAABB(EntityFireball.class, bbox);
        for (EntityFireball fb : candidates) {
            if (fb == null) continue;
            if (fb instanceof EntityWitherSkull) continue;

            double speedSq = fb.motionX * fb.motionX + fb.motionY * fb.motionY + fb.motionZ * fb.motionZ;
            if (speedSq < MIN_SPEED_SQ) continue;

            // 计算射线端点（使用 MAX_RAY_DISTANCE 限制）
            Vec3 start = new Vec3(fb.posX, fb.posY, fb.posZ);
            Vec3 dir = new Vec3(fb.motionX, fb.motionY, fb.motionZ).normalize();
            Vec3 end = start.addVector(dir.xCoord * MAX_RAY_DISTANCE, dir.yCoord * MAX_RAY_DISTANCE, dir.zCoord * MAX_RAY_DISTANCE);

            MovingObjectPosition mop = world.rayTraceBlocks(start, end);
            if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                Vec3 hitVec = mop.hitVec;
                double dx = hitVec.xCoord - fb.posX;
                double dy = hitVec.yCoord - fb.posY;
                double dz = hitVec.zCoord - fb.posZ;
                double impactDistance = Math.sqrt(dx * dx + dy * dy + dz * dz);

                BlockPos hitPos = mop.getBlockPos();
                Vec3 hitCenter = new Vec3(hitPos.getX() + 0.5, hitPos.getY() + 0.5, hitPos.getZ() + 0.5);
                double playerDistance = playerPos.squareDistanceTo(hitCenter);
                if (playerDistance < minDist) {
                    minDist = playerDistance;
                    newHit = hitPos;
                    color = getDistanceColor(impactDistance);
                    bestFireball = fb;
                }
            }
        }

        // 首次发现该火球时记录其发射位置（仅在火球模式）
        if (bestFireball != null && bestFireball != currentFireball) {
            currentFireballOrigin = new Vec3(bestFireball.posX, bestFireball.posY, bestFireball.posZ);
        } else if (bestFireball == null) {
            currentFireballOrigin = null;
        }

        currentFireball = bestFireball;

        // 计算火球到达落点的预计时间（秒），使用实际速度（减少重复开销）
        if (bestFireball != null && newHit != null) {
            Vec3 fbPos = new Vec3(bestFireball.posX, bestFireball.posY, bestFireball.posZ);
            Vec3 hitCenter = new Vec3(newHit.getX() + 0.5, newHit.getY() + 0.5, newHit.getZ() + 0.5);
            double fbSpeed = Math.sqrt(
                bestFireball.motionX * bestFireball.motionX +
                bestFireball.motionY * bestFireball.motionY +
                bestFireball.motionZ * bestFireball.motionZ
            );
            if (fbSpeed > 1e-6) {
                currentETA = fbPos.distanceTo(hitCenter) / (fbSpeed * 20.0);
            } else {
                currentETA = -1;
            }
        } else {
            currentETA = -1;
        }

        // 模式 2：玩家手持火焰弹。保留原有预测，颜色固定为黄色。
        if (newHit == null) {
            for (EntityPlayer p : world.playerEntities) {
                if (p.getHeldItem() == null || p.getHeldItem().getItem() != Items.fire_charge)
                    continue;
                Vec3 eye = p.getPositionEyes(1.0f);                Vec3 look = p.getLook(1.0f);                Vec3 end = eye.addVector(look.xCoord * MAX_RAY_DISTANCE, look.yCoord * MAX_RAY_DISTANCE, look.zCoord * MAX_RAY_DISTANCE);
                MovingObjectPosition mop = world.rayTraceBlocks(eye, end);                if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {                    newHit = mop.getBlockPos();                    color = 0xFFFF00;                    break;                }            }        }
        currentHitPos = newHit;        currentColor = newHit == null ? 0 : color;
        // 玩家在落点 5×5×5 范围内时，触发警告（根据配置选择 HUD/Chat/Sound）        // 仅在火球模式（模式 1）下触发，手持火焰弹不提示        if (newHit != null && color != 0xFFFF00) {            double dx = Math.abs(mc.thePlayer.posX - (newHit.getX() + 0.5));            double dy = Math.abs(mc.thePlayer.posY - (newHit.getY() + 0.5));            double dz = Math.abs(mc.thePlayer.posZ - (newHit.getZ() + 0.5));            if (dx <= WARN_RANGE && dy <= WARN_RANGE && dz <= WARN_RANGE) {                long now = System.currentTimeMillis();                final long throttleMs = 500L; // 至少 0.5s 间隔                if (ALERT_MODE == 0) {                    // HUD: 激活 HUD 提示，持续 HUD_DISPLAY_TICKS                    hudWarningTicks = HUD_DISPLAY_TICKS;                    hudWarningActive = true;                } else if (ALERT_MODE == 1) {                    // Chat: 节流聊天消息                    if (now - lastWarnTimeMs >= throttleMs) {                        mc.thePlayer.addChatMessage(new ChatComponentText("§c当前位于烈焰弹爆炸范围内"));                        lastWarnTimeMs = now;                    }                } else if (ALERT_MODE == 2) {                    // Sound: 播放短音效并节流                    if (now - lastWarnTimeMs >= throttleMs) {                        if (mc.thePlayer != null) mc.thePlayer.playSound(ALERT_SOUND, ALERT_SOUND_VOL, ALERT_SOUND_PITCH);                        lastWarnTimeMs = now;                    }                }            }        }        // HUD 警告计时（在每次更新时递减）        if (hudWarningTicks > 0) {            hudWarningTicks--;            hudWarningActive = hudWarningTicks > 0;        }    }    private static int getDistanceColor(double distance)    {        if (distance <= NEAR_DISTANCE) return 0xFF0000;        if (distance >= FAR_DISTANCE) return 0x00FF00;        if (distance <= MEDIUM_DISTANCE) {            float progress = (float) ((distance - NEAR_DISTANCE) / (MEDIUM_DISTANCE - NEAR_DISTANCE));            return rgb(255, Math.round(255 * progress), 0);        }        float progress = (float) ((distance - MEDIUM_DISTANCE) / (FAR_DISTANCE - MEDIUM_DISTANCE));        return rgb(Math.round(255 * (1.0F - progress)), 255, 0);    }    private static int rgb(int red, int green, int blue)    {        return (red << 16) | (green << 8) | blue; //    }}