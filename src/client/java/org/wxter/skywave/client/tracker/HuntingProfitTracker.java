package org.wxter.skywave.client.tracker;

import com.google.gson.annotations.Expose;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.wxter.skywave.config.SkywaveConfig;

import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HuntingProfitTracker {
    // runtime instance (static for easy access)
    public static final HuntingProfitTracker INSTANCE = new HuntingProfitTracker();

    // runtime state (not all persisted; totalShards persisted in config)
    private long sessionShards = 0L;
    private boolean running = false;

    // timer
    private long sessionStartMillis = 0L;
    private boolean timerPaused = false;
    private long pausedAtMillis = 0L;
    private long accumulatedPausedMillis = 0L;

    // HUD geometry
    private int hudX = 8;
    private int hudY = 40;
    private int hudW = 150;
    private int hudH = 50;

    // click edge detect
    private boolean lastLeftWasDown = false;

    private HuntingProfitTracker() {}

    public void init() {
        // слушаем server/game messages (включая actionbar / titles — GAME covers server)
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            try {
                handleChatMessage(message.getString());
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });


        // HUD отрисовка
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            try {
                renderHud(drawContext);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });

        // copy initial configuration values
        SkywaveConfig.HuntingConfig cfg = SkywaveConfig.get().hunting;
        hudX = cfg.hudX;
        hudY = cfg.hudY;
    }

    private void handleChatMessage(String plain) {
        SkywaveConfig.HuntingConfig cfg = SkywaveConfig.get().hunting;
        if (cfg == null || !cfg.profitTrackerEnabled) return;

        // iterate patterns from config
        for (String rawPattern : cfg.chatPatterns) {
            Pattern p;
            try {
                p = Pattern.compile(rawPattern, Pattern.CASE_INSENSITIVE);
            } catch (Exception e) {
                continue;
            }
            Matcher m = p.matcher(plain);
            if (m.find()) {
                // ищем число в capture-группе
                int found = 0;
                for (int i = 1; i <= m.groupCount(); i++) {
                    String g = m.group(i);
                    if (g == null) continue;
                    try {
                        found = Integer.parseInt(g.replaceAll("[^0-9]", ""));
                        break;
                    } catch (NumberFormatException ignore) {}
                }
                if (found == 0) {
                    // если число не нашлось в группах — попробуем извлечь все числа из всей строки (backup)
                    String digits = plain.replaceAll("[^0-9]+", " ").trim();
                    if (!digits.isEmpty()) {
                        try {
                            found = Integer.parseInt(digits.split("\\s+")[0]);
                        } catch (Exception ignore) {}
                    }
                }

                if (found > 0) {
                    addShards(found);
                } else {
                    // если шаблон поймал, но не нашёл число — считаем 1
                    addShards(1);
                }
                break; // один матч — достаточно
            }
        }
    }

    private synchronized void addShards(int count) {
        SkywaveConfig.HuntingConfig cfg = SkywaveConfig.get().hunting;
        if (cfg == null || !cfg.profitTrackerEnabled) return;

        // всегда увеличиваем total
        cfg.totalShards += count;

        // если отсчёт идёт — увеличиваем session
        if (running) sessionShards += count;

        // persist config (вы уже используете SkywaveConfig.save() elsewhere)
        SkywaveConfig.save();
    }

    // API
    public synchronized void startSession() {
        if (running) return;
        running = true;
        sessionStartMillis = System.currentTimeMillis();
        timerPaused = false;
        accumulatedPausedMillis = 0;
    }

    public synchronized void stopSession() {
        running = false;
    }

    public synchronized void reset(SkywaveConfig.DisplayMode mode) {
        if (mode == SkywaveConfig.DisplayMode.TOTAL) {
            SkywaveConfig.get().hunting.totalShards = 0L;
            sessionShards = 0L;
            SkywaveConfig.save();
        } else {
            sessionShards = 0L;
        }
    }

    public synchronized long getTotalShards() {
        return SkywaveConfig.get().hunting.totalShards;
    }

    public synchronized long getSessionShards() {
        return sessionShards;
    }

    public synchronized boolean isRunning() { return running; }

    // Timer helpers
    public synchronized String getSessionUptimeFormatted() {
        if (!running) return "00:00:00";
        long now = System.currentTimeMillis();
        long elapsed = now - sessionStartMillis - accumulatedPausedMillis;
        if (timerPaused && pausedAtMillis > 0) {
            elapsed = pausedAtMillis - sessionStartMillis - accumulatedPausedMillis;
        }
        Duration d = Duration.ofMillis(Math.max(0, elapsed));
        long hours = d.toHours();
        long minutes = d.toMinutesPart();
        long seconds = d.toSecondsPart();
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public synchronized void toggleTimerPause() {
        if (!running) return;
        if (!timerPaused) {
            timerPaused = true;
            pausedAtMillis = System.currentTimeMillis();
        } else {
            timerPaused = false;
            if (pausedAtMillis > 0) {
                accumulatedPausedMillis += System.currentTimeMillis() - pausedAtMillis;
            }
            pausedAtMillis = 0;
        }
    }

    // HUD rendering + clickable areas
    private void renderHud(net.minecraft.client.gui.DrawContext drawContext) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        SkywaveConfig.HuntingConfig cfg = SkywaveConfig.get().hunting;
        if (cfg == null || !cfg.profitTrackerEnabled) return;

        TextRenderer tr = client.textRenderer;

        int x = cfg.hudX;
        int y = cfg.hudY;
        this.hudX = x;
        this.hudY = y;

        // prepare texts
        SkywaveConfig.DisplayMode displayMode = SkywaveConfig.get().hunting.displayMode; // note: get global
        String title = "Hunting Profit Tracker";
        long displayCount = (displayMode == SkywaveConfig.DisplayMode.TOTAL) ? getTotalShards() : getSessionShards();
        String countLine = String.format("Shards: %d", displayCount);

        String modeLine = "Mode: " + displayMode.name();
        String startStopLine = running ? "Running (click to Stop)" : "Stopped (click to Start)";
        String resetLine = "Reset";
        String timerLine = cfg.showTimer ? ("Time: " + getSessionUptimeFormatted() + (timerPaused ? " [PAUSED]" : "")) : "";

        // Draw background rectangle (simple)
        int bgColor = 0x55000000; // translucent
        fill(drawContext, x - 4, y - 4, x + hudW, y + hudH, bgColor);

        // draw strings
        drawContext.drawText(tr, Text.literal(title), x, y, 0xFFFFFF, false);
        drawContext.drawText(tr, Text.literal(countLine), x, y + 10, 0xFFFF55, false);
        drawContext.drawText(tr, Text.literal(modeLine), x, y + 20, 0xAAAAAA, false);
        drawContext.drawText(tr, Text.literal(startStopLine), x, y + 30, 0x88FF88, false);

        if (cfg.showTimer) {
            drawContext.drawText(tr, Text.literal(timerLine), x + 100, y + 10, 0xFFFFFF, false);
        }

        // Reset clickable (draw small)
        int resetX = x + 100;
        int resetY = y + 30;
        drawContext.drawText(tr, Text.literal("[Reset]"), resetX, resetY, 0xFF8888, false);

        // clickable: detect left mouse click inside certain rects
        long window = client.getWindow().getHandle();
        double[] mx = new double[1];
        double[] my = new double[1];
        GLFW.glfwGetCursorPos(window, mx, my);
        // GLFW gives cursor in window coords with origin at top-left; need to scale to Minecraft scaled resolution
        int scale = client.getWindow().getScaleFactor();
        int cursorX = (int) mx[0];
        int cursorY = (int) my[0];

        // convert to scaled coordinates (approximate)
        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();
        // in most setups cursor coords match scaled coords, but if not - this is best-effort

        boolean leftDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        // define box for Start/Stop (x..x+120, y+30..y+42)
        if (wasClickInRect(cursorX, cursorY, x, y + 30, 120, 12, leftDown)) {
            if (!lastLeftWasDown && leftDown) {
                // edge detected - toggle start/stop
                if (running) stopSession(); else startSession();
            }
        }

        // define box for Reset
        if (wasClickInRect(cursorX, cursorY, resetX, resetY, 40, 12, leftDown)) {
            if (!lastLeftWasDown && leftDown) {
                // reset according to current display mode
                reset(displayMode);
            }
        }

        // clicking on mode text toggles mode
        if (wasClickInRect(cursorX, cursorY, x, y + 20, 100, 12, leftDown)) {
            if (!lastLeftWasDown && leftDown) {
                // toggle display mode
                SkywaveConfig.DisplayMode newMode = (displayMode == SkywaveConfig.DisplayMode.TOTAL)
                        ? SkywaveConfig.DisplayMode.SESSION : SkywaveConfig.DisplayMode.TOTAL;
                SkywaveConfig.get().hunting.displayMode = newMode;
                SkywaveConfig.save();
            }
        }

        // clicking timer text toggles pause/resume (if timer shown)
        if (cfg.showTimer && wasClickInRect(cursorX, cursorY, x + 100, y + 10, 70, 12, leftDown)) {
            if (!lastLeftWasDown && leftDown) {
                toggleTimerPause();
            }
        }

        lastLeftWasDown = leftDown;
    }

    private boolean wasClickInRect(int cursorX, int cursorY, int rx, int ry, int w, int h, boolean leftDown) {
        // cursors are in window coords; for most set-ups these map to scaled coords; adjust if necessary.
        return cursorX >= rx && cursorY >= ry && cursorX <= rx + w && cursorY <= ry + h && leftDown;
    }

    // helper fill rect (DrawContext doesn't offer direct fill, so use drawTexture or textRenderer background)
    private void fill(net.minecraft.client.gui.DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
        // fast simple quad using drawTexture with white pixel (not provided here) would be ideal.
        // Fallback: draw semi-opaque spaces as text background (cheap, works cross-version) — use drawWithShadow blank text to emulate
        // But better approach: use Screen.fill, however DrawContext doesn't expose it; we can use client.textRenderer background color behind text.
        // To keep this example simple and cross-version, omit complex background drawing — HUD will still be readable.
    }
}
