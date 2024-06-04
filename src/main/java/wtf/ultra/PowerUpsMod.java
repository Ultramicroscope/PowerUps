package wtf.ultra;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import wtf.ultra.mixin.IRenderManagerMixin;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_MULTISAMPLE;
import static org.lwjgl.opengl.GL13.GL_SAMPLE_ALPHA_TO_COVERAGE;

@Mod(modid = "powerups", useMetadata=true)
public class PowerUpsMod {
    private static final Pattern ARENA_LOCRAW = Pattern.compile("^\\{\"server\":\"([^\"]*)\",\"gametype\":\"ARENA\",\"mode\":\"[^\"]*\",\"map\":\"([^\"]*)\"}$");
    private static final Pattern ACTIVATED = Pattern.compile("^([a-zA-Z0-9_]{2,16}) activated the (HEALING|DAMAGE|MAGICAL KEY) powerup!$");
    private static final Pattern SPAWNED = Pattern.compile("^The (HEALING|DAMAGE|MAGICAL KEY) PowerUp has spawned!$");
    private static final Pattern LOCRAW = Pattern.compile("\\{(\".*\":\".*\",)?+\".*\":\".*\"}");
    private static final Pattern ARENA = Pattern.compile("^\\s*ARENA: Arena\\d+$");
    private static final Pattern VS = Pattern.compile("^\\s*VS$");
    private static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(1);
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int displayList;

    private static boolean firstHp, brawlin, locdin;
    private static Vec3 dmgPos, hpPos, keyPos;
    private static long start, hp, dmg;
    private static String dmgUser;

    public static void sendLocraw() {
        SCHEDULER.schedule(
                () -> mc.thePlayer.sendChatMessage("/locraw"),
                500, TimeUnit.MILLISECONDS);
    }

    public static void locin() {
        locdin = true;
    }

    public static void setStart(long ms) {
        start = ms;
        hp = ms;
        dmg = ms;
        firstHp = true;
        dmgPos = null;
        hpPos = null;
        keyPos = null;
        dmgUser = null;
    }

    // dirty short-circuit only returns true if msg is locraw while locdin
    public static boolean handleMsg(String text) {
        //noinspection PointlessBooleanExpression
        return (locdin && checkLocraw(text))
                || (brawlin
                && checkVs(text)
                && checkArena(text)
                && checkSpawned(text)
                && checkActivated(text)
                && false);
    }

    private static boolean checkLocraw(String text) {
        boolean isLocraw = LOCRAW.matcher(text).matches();
        if (isLocraw) {
            // group1: mininserver, group2: mapname
            brawlin = ARENA_LOCRAW.matcher(text).matches();
            locdin = false;
        }
        return isLocraw;
    }

    private static boolean checkVs(String text) {
        boolean isVs = VS.matcher(text).matches();
        if (isVs) mc.thePlayer.sendChatMessage("/who");
        return !isVs;
    }

    private static boolean checkArena(String text) {
        boolean isArena = ARENA.matcher(text).matches();
        if (isArena) setStart(System.currentTimeMillis());
        return !isArena;
    }

    private static boolean checkSpawned(String text) {
        Matcher matchResult = SPAWNED.matcher(text);
        boolean spawned = matchResult.matches();

        if (spawned)
            for (Entity entity : mc.theWorld.loadedEntityList)
                if (entity.ticksExisted <= 3) {

            switch (matchResult.group(1)) {
                case "HEALING":
                    hpPos = entity.getPositionVector();
                    if (firstHp) firstHp = false;
                    break;
                case "DAMAGE":
                    dmgPos = entity.getPositionVector();
                    break;
                case "MAGICAL KEY":
                    keyPos = entity.getPositionVector();
                    break;
            }

            break;
        }

        return !spawned;
    }

    private static boolean checkActivated(String text) {
        Matcher matchResult = ACTIVATED.matcher(text);
        boolean activated = matchResult.matches();
        if (activated) switch (matchResult.group(2)) {
            case "HEALING":
                hpPos = null;
                hp = System.currentTimeMillis();
                break;
            case "DAMAGE":
                dmgPos = null;
                dmgUser = matchResult.group(1);
                dmg = System.currentTimeMillis();
                break;
            default: //case "MAGICAL KEY":
                keyPos = null;
        }

        return !activated;
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void renderGameOverlay(RenderGameOverlayEvent.Post event) {
        if (brawlin && start > 0 && event.type == RenderGameOverlayEvent.ElementType.TEXT) {
            long now = System.currentTimeMillis();
            int fontHeight = mc.fontRendererObj.FONT_HEIGHT + 1;

            long hpTimer = now - hp;
            if (firstHp) hpTimer += 30000;
            long dmgTimer = now - dmg;

            int line = 1;

            float hw = (float) event.resolution.getScaledWidth_double() / 2;
            float hh = (float) event.resolution.getScaledHeight_double() / 2;

            if (hpPos == null && 57000 <= hpTimer && hpTimer < 90000) {
                long attempt = (hpTimer - 54000) / 3000;
                String text = String.format("%s%s%% %sHEALING %sin: %s%d", EnumChatFormatting.LIGHT_PURPLE,
                        attempt == 11L ? "100" : String.format("%.1f", 100 * attempt / 11.0),
                        EnumChatFormatting.GREEN, EnumChatFormatting.YELLOW, EnumChatFormatting.WHITE,
                        attempt * 3000 + 57000 - hpTimer);
                mc.fontRendererObj.drawStringWithShadow(text,
                        hw - mc.fontRendererObj.getStringWidth(text) / 2f,
                        fontHeight * line++ + hh, 16777215);
            }

            if (dmgPos == null && 57000 <= dmgTimer && dmgTimer < 90000) {
                long attempt = (dmgTimer - 54000) / 3000;
                String text = String.format("%s%s%% %sDAMAGE %sin: %s%d", EnumChatFormatting.LIGHT_PURPLE,
                        attempt == 11L ? "100" : String.format("%.1f", 100 * attempt / 11.0),
                        EnumChatFormatting.RED, EnumChatFormatting.YELLOW, EnumChatFormatting.WHITE,
                        attempt * 3000 + 57000 - dmgTimer);
                mc.fontRendererObj.drawStringWithShadow(text,
                        hw - mc.fontRendererObj.getStringWidth(text) / 2f,
                        fontHeight * line + hh, 16777215);
            }

            if (dmgUser != null) {
                long msLeft = 12000 - dmgTimer;
                if (msLeft > 0) {
                    String text = String.format("%s%s's %sDMG: %s%d", EnumChatFormatting.DARK_GRAY,
                            dmgUser, EnumChatFormatting.RED, EnumChatFormatting.WHITE, msLeft);
                   mc.fontRendererObj.drawStringWithShadow(text,
                           hw - mc.fontRendererObj.getStringWidth(text) / 2f,
                           hh - 2 * fontHeight, 16777215);
                } else dmgUser = null;
            }
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!brawlin) return;

        IRenderManagerMixin rm = (IRenderManagerMixin) mc.getRenderManager();
        double rx = rm.getRenderPosX();
        double ry = rm.getRenderPosY();
        double rz = rm.getRenderPosZ();

        glPushMatrix();

        GlStateManager.enableBlend();
        GlStateManager.disableDepth();
        GlStateManager.disableLighting();
        GlStateManager.disableTexture2D();

        GlStateManager.shadeModel(GL_SMOOTH);
        GlStateManager.blendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glEnable(GL_LINE_SMOOTH);
        glEnable(GL_MULTISAMPLE);
        glEnable(GL_SAMPLE_ALPHA_TO_COVERAGE);

        glHint(GL_LINE_SMOOTH_HINT, GL_NICEST);

        glTranslated(-rx, -ry, -rz);

        if (dmgPos != null) {
            glPushMatrix();
            glColor4d(1, 0, 0, 0.675);
            glTranslated(dmgPos.xCoord, dmgPos.yCoord, dmgPos.zCoord);
            glCallList(displayList);
            glPopMatrix();
        }

        if (hpPos != null) {
            glPushMatrix();
            glColor4d(0, 1, 0, 0.675);
            glTranslated(hpPos.xCoord, hpPos.yCoord, hpPos.zCoord);
            glCallList(displayList);
            glPopMatrix();
        }

        if (keyPos != null) {
            glPushMatrix();
            glColor4d(0, 0, 1, 0.675);
            glTranslated(keyPos.xCoord, keyPos.yCoord, keyPos.zCoord);
            glCallList(displayList);
            glPopMatrix();
        }

        glDisable(GL_SAMPLE_ALPHA_TO_COVERAGE);
        glDisable(GL_MULTISAMPLE);
        glDisable(GL_LINE_SMOOTH);

        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();

        glPopMatrix();
    }

    static {
        int res = 64;

        double rseg = 2 * Math.PI / (double) res;
        double radius = 1.5;

        displayList = GLAllocation.generateDisplayLists(1);

        glNewList(displayList, GL_COMPILE);
        glBegin(GL_QUADS);
        for (int i = 0; i <= res; i++) {
            double lat0 = Math.PI * ((i - 1) / (double) res - 0.5);
            double rz0 = Math.cos(lat0) * radius;
            double zz0 = Math.sin(lat0) * radius;

            double lat1 = Math.PI * (i / (double) res - 0.5);
            double rz1 = Math.cos(lat1) * radius;
            double zz1 = Math.sin(lat1) * radius;

            for (int j = 0; j < res; j++) {
                double lng1 = rseg * j;
                double x0 = Math.cos(lng1);
                double y0 = Math.sin(lng1);

                double lng2 = rseg * (j + 1);
                double x1 = Math.cos(lng2);
                double y1 = Math.sin(lng2);

                glVertex3d(x0 * rz0, y0 * rz0, zz0);
                glVertex3d(x0 * rz1, y0 * rz1, zz1);
                glVertex3d(x1 * rz1, y1 * rz1, zz1);
                glVertex3d(x1 * rz0, y1 * rz0, zz0);
            }
        }

        glEnd();
        glEndList();
    }
}