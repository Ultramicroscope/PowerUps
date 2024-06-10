package wtf.ultra;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import wtf.ultra.mixin.IActiveRenderInfoMixin;
import wtf.ultra.mixin.IRenderManagerMixin;

import java.nio.FloatBuffer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.lwjgl.opengl.GL11.*;

@Mod(modid = "powerups", useMetadata=true)
public class PowerUpsMod {
    private static final Pattern ARENA_LOCRAW = Pattern.compile("^\\{\"server\":\"([^\"]*)\",\"gametype\":\"ARENA\",\"mode\":\"[^\"]*\",\"map\":\"([^\"]*)\"}$");
    private static final Pattern ACTIVATED = Pattern.compile("^([a-zA-Z0-9_]{2,16}) activated the (HEALING|DAMAGE|MAGICAL KEY) powerup!$");
    private static final Pattern SPAWNED = Pattern.compile("^The (HEALING|DAMAGE|MAGICAL KEY) PowerUp has spawned!$");
    private static final Pattern LOCRAW = Pattern.compile("^\\{(\".*\":\".*\",)?+\".*\":\".*\"}$");
    private static final Pattern ARENA = Pattern.compile("^\\s*ARENA: Arena\\d+$");
    private static final Pattern VS = Pattern.compile("^\\s*VS$");
    private static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(1);
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static boolean firstHp, brawlin, locdin;
    private static Vec3 dPos, hPos, kPos;
    private static String dmgUser, mini;
    private static long start, hp, dmg;

    public static void sendLocraw() {
        SCHEDULER.schedule(
                () -> mc.thePlayer.sendChatMessage("/locraw"),
                500, TimeUnit.MILLISECONDS);
    }

    public static void locin() { locdin = true; }

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
            locdin = false;
            // group1: mininserver, group2: mapname
            Matcher matchResult = ARENA_LOCRAW.matcher(text);
            //noinspection AssignmentUsedAsCondition
            if (brawlin = matchResult.matches()) {
                String server = matchResult.group(1);
                if (mini == null || !mini.equals(server)) {
                    mini = server;
                    start = 0;
                }
            }
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
                    hPos = entity.getPositionVector();
                    if (firstHp) firstHp = false;
                    break;
                case "DAMAGE":
                    dPos = entity.getPositionVector();
                    break;
                default: //case "MAGICAL KEY":
                    kPos = entity.getPositionVector();
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
                hPos = null;
                hp = System.currentTimeMillis();
                break;
            case "DAMAGE":
                dPos = null;
                dmgUser = matchResult.group(1);
                dmg = System.currentTimeMillis();
                break;
            default: //case "MAGICAL KEY":
                kPos = null;
        }

        return !activated;
    }

    private static void setStart(long ms) {
        start = ms;
        hp = ms;
        dmg = ms;
        firstHp = true;
        dPos = null;
        hPos = null;
        kPos = null;
        dmgUser = null;
    }

    private void rect(double left, double top, double right, double bottom,
                      double r, double g, double b, double a) {
        glColor4d(r, g, b, a);
        glBegin(GL_QUADS);
        glVertex2d(left, top);
        glVertex2d(right, top);
        glVertex2d(right, bottom);
        glVertex2d(left, bottom);
        glEnd();
    }

    private void draw(double x1, double y1, double z1, double x2, double y2, double z2,
                      double hw, double hh, double r, double g, double b,
                      float m0, float m4, float m8,  float m12,
                      float m1, float m5, float m9,  float m13,
                      float m2, float m6, float m10, float m14,
                      float m3, float m7, float m11, float m15,
                      float p0, float p4, float p8,  float p12,
                      float p1, float p5, float p9,  float p13,
//                    float p2, float p6, float p10, float p14,
                      float p3, float p7, float p11, float p15) {
        double x = x2 - x1;
        double y = y2 - y1;
        double z = z2 - z1;

        double v1 = x * m0 + y * m4 + z * m8 + m12;
        double v2 = x * m1 + y * m5 + z * m9 + m13;
        double v3 = x * m2 + y * m6 + z * m10 + m14;
        double v4 = x * m3 + y * m7 + z * m11 + m15;
        x = v1 * p0 + v2 * p4 + v3 * p8  + v4 * p12;
        y = v1 * p1 + v2 * p5 + v3 * p9  + v4 * p13;
//      z = v1 * p2 + v2 * p6 + v3 * p10 + v4 * p14; // z-value (depth)
        double cw = v1 * p3 + v2 * p7 + v3 * p11 + v4 * p15;

        if (cw != 0) {
            cw = 1 / cw;

            double px = Math.min(hw * 2, Math.max(0, (x * cw + 1) * hw));
            double py = Math.min(hh * 2, Math.max(0, (1 - y * cw) * hh));
            if (cw < 0) {
                px = hw - px < 0 ? 0 : hw * 2;
                py = hh - py < 0 ? 0 : hh * 2;
            }

            double d = Math.max(0.2, Math.abs(hw - px) / hw);
            double s = d * hw / 4;

            rect(px - s, py + s, px + s, py - s, r, g, b, d);
        }
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SuppressWarnings("DataFlowIssue") @SubscribeEvent
    public void renderGameOverlay(RenderGameOverlayEvent.Post event) {
        if (brawlin && start > 0) {
            float hw = (float) event.resolution.getScaledWidth_double() / 2;
            float hh = (float) event.resolution.getScaledHeight_double() / 2;

            switch (event.type) {
                case TEXT:
                    long now = System.currentTimeMillis();
                    int fontHeight = mc.fontRendererObj.FONT_HEIGHT + 1;

                    long hpTimer = now - hp;
                    if (firstHp) hpTimer += 30000;
                    long dmgTimer = now - dmg;

                    int line = 1;

                    if (hPos == null && 57000 <= hpTimer && hpTimer < 90000) {
                        long attempt = (hpTimer - 54000) / 3000;
                        String text = String.format("%s%s%% %sHEALING %sin: %s%d", EnumChatFormatting.LIGHT_PURPLE,
                                attempt == 11L ? "100" : String.format("%.1f", 100 * attempt / 11.0),
                                EnumChatFormatting.GREEN, EnumChatFormatting.YELLOW, EnumChatFormatting.WHITE,
                                attempt * 3000 + 57000 - hpTimer);
                        mc.fontRendererObj.drawStringWithShadow(text,
                                hw - mc.fontRendererObj.getStringWidth(text) / 2f,
                                fontHeight * line++ + hh, 16777215);
                    }

                    if (dPos == null && 57000 <= dmgTimer && dmgTimer < 90000) {
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

                    break;
                case CROSSHAIRS:
                    IRenderManagerMixin rm = (IRenderManagerMixin) mc.getRenderManager();
                    double x = rm.getRenderPosX();
                    double y = rm.getRenderPosY();
                    double z = rm.getRenderPosZ();

                    FloatBuffer m = IActiveRenderInfoMixin.getMODELVIEW();
                    FloatBuffer p = IActiveRenderInfoMixin.getPROJECTION();

                    float m0 = m.get(0); float m4 = m.get(4); float m8  = m.get(8);  float m12 = m.get(12);
                    float m1 = m.get(1); float m5 = m.get(5); float m9  = m.get(9);  float m13 = m.get(13);
                    float m2 = m.get(2); float m6 = m.get(6); float m10 = m.get(10); float m14 = m.get(14);
                    float m3 = m.get(3); float m7 = m.get(7); float m11 = m.get(11); float m15 = m.get(15);

                    float p0 = p.get(0); float p4 = p.get(4); float p8  = p.get(8);  float p12 = p.get(12);
                    float p1 = p.get(1); float p5 = p.get(5); float p9  = p.get(9);  float p13 = p.get(13);
//                  float p2 = p.get(2); float p6 = p.get(6); float p10 = p.get(10); float p14 = p.get(14);
                    float p3 = p.get(3); float p7 = p.get(7); float p11 = p.get(11); float p15 = p.get(15);

                    glPushMatrix();

                    GlStateManager.enableBlend();
                    GlStateManager.disableTexture2D();

                    glEnable(GL_LINE_SMOOTH);
                    GlStateManager.blendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

                    if (dPos != null) draw(x, y, z, dPos.xCoord, dPos.yCoord, dPos.zCoord, hw, hh, 1, 0, 0,
                            m0, m4, m8,  m12,
                            m1, m5, m9,  m13,
                            m2, m6, m10, m14,
                            m3, m7, m11, m15,
                            p0, p4, p8,  p12,
                            p1, p5, p9,  p13,
//                          p2, p6, p10, p14,
                            p3, p7, p11, p15);
                    if (hPos != null) draw(x, y, z, hPos.xCoord, hPos.yCoord, hPos.zCoord, hw, hh, 0, 1, 0,
                            m0, m4, m8,  m12,
                            m1, m5, m9,  m13,
                            m2, m6, m10, m14,
                            m3, m7, m11, m15,
                            p0, p4, p8,  p12,
                            p1, p5, p9,  p13,
//                          p2, p6, p10, p14,
                            p3, p7, p11, p15);
                    if (kPos != null) draw(x, y, z, kPos.xCoord, kPos.yCoord, kPos.zCoord, hw, hh, 0, 0, 1,
                            m0, m4, m8,  m12,
                            m1, m5, m9,  m13,
                            m2, m6, m10, m14,
                            m3, m7, m11, m15,
                            p0, p4, p8,  p12,
                            p1, p5, p9,  p13,
//                          p2, p6, p10, p14,
                            p3, p7, p11, p15);

                    glDisable(GL_LINE_SMOOTH);

                    GlStateManager.enableTexture2D();
                    GlStateManager.disableBlend();

                    glColor4d(1, 1, 1, 1);

                    glPopMatrix();
            }
        }
    }
}
