package org.wxter.skywave.client.tracker;

import com.google.gson.*;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.wxter.skywave.client.gui.SkywaveHudMoveScreen;
import org.wxter.skywave.config.SkywaveConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HuntingProfitTracker {

    public static final HuntingProfitTracker INSTANCE = new HuntingProfitTracker();

    private final ConcurrentHashMap<String, Long> sessionCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> totalCounts = new ConcurrentHashMap<>();

    private volatile boolean running = false;

    private long sessionStartMillis = 0L;
    private boolean timerPaused = false;
    private long pausedAtMillis = 0L;
    private long accumulatedPausedMillis = 0L;

    // HUD position & size
    private int hudW = 260;
    private int hudH = 120;

    // dragging
    private boolean moveMode = false;
    private boolean dragging = false;
    private int dragOffsetX, dragOffsetY;

    private boolean lastLeftWasDown = false;

    private final BazaarPriceFetcher priceFetcher = new BazaarPriceFetcher();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "skywave-hunting-sched");
        t.setDaemon(true);
        return t;
    });

    private HuntingProfitTracker() {
        long initial = 30L;
        long periodMinutes = Math.max(1, SkywaveConfig.get().bazaarRefreshMinutes);
        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (SkywaveConfig.get().hypixelApiKey != null && !SkywaveConfig.get().hypixelApiKey.isEmpty()) {
                    priceFetcher.refreshAll();
                }
            } catch (Throwable t) { t.printStackTrace(); }
        }, initial, periodMinutes, TimeUnit.MINUTES);
    }

    public void init() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            try {
                if (message != null) handleChatMessage(message.getString());
            } catch (Throwable t) { t.printStackTrace(); }
        });

        HudRenderCallback.EVENT.register((drawContext, tick) -> {
            try {
                onHudRender(drawContext);
            } catch (Throwable t) { t.printStackTrace(); }
        });
    }

    private void handleChatMessage(String raw) {
        if (raw == null) return;
        SkywaveConfig.HuntingConfig cfg = SkywaveConfig.get().hunting;
        if (cfg == null || !cfg.profitTrackerEnabled) return;

        String plain = stripColorCodes(raw).trim();
        if (plain.isEmpty()) return;

        boolean matched = false;
        for (String pat : cfg.chatPatterns) {
            try {
                Pattern p = Pattern.compile(pat, Pattern.CASE_INSENSITIVE);
                Matcher m = p.matcher(plain);
                if (m.find()) {
                    ParsedResult r = parseFromMatcher(m, plain);
                    if (r != null) {
                        recordShard(r.name, r.count);
                        matched = true;
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }

        if (!matched && plain.toLowerCase().contains("shard")) {
            ParsedResult r = parseByProximity(plain);
            if (r != null) recordShard(r.name, r.count);
        }
    }

    private String stripColorCodes(String s) {
        return s == null ? "" : s.replaceAll("§.", "");
    }

    private static class ParsedResult { final String name; final int count; ParsedResult(String n, int c){name=n;count=c;} }

    private ParsedResult parseFromMatcher(Matcher m, String plain) {
        int foundCount = 0;
        String foundName = null;
        for (int i = 1; i <= m.groupCount(); i++) {
            String g = m.group(i);
            if (g == null) continue;
            String digits = g.replaceAll("[^0-9]", "");
            if (!digits.isEmpty()) {
                try { foundCount = Integer.parseInt(digits); } catch (Exception ignored) {}
                continue;
            }
            if (foundName == null && g.trim().length() > 0) {
                String nm = g.replaceAll("\\s*Shards?\\s*$", "").trim();
                if (!nm.isEmpty()) foundName = nm;
            }
        }
        if (foundName == null) {
            int idx = plain.toLowerCase().indexOf("shard");
            if (idx > 0) {
                String before = plain.substring(0, idx).trim();
                String[] parts = before.split("\\s+");
                if (parts.length > 0) foundName = parts[parts.length - 1].replaceAll("[^\\w\\-']", "");
            }
        }
        if (foundName == null) return null;
        if (foundCount <= 0) foundCount = 1;
        return new ParsedResult(foundName, foundCount);
    }

    private ParsedResult parseByProximity(String plain) {
        String lower = plain.toLowerCase();
        int idx = lower.indexOf("shard");
        if (idx < 0) return null;
        String before = plain.substring(0, idx).trim();

        Pattern numPattern = Pattern.compile("(?:x\\s*(\\d+)|(\\d+))\\s*$", Pattern.CASE_INSENSITIVE);
        Matcher mn = numPattern.matcher(before);
        int count = 1;
        if (mn.find()) {
            String g = mn.group(1) != null ? mn.group(1) : mn.group(2);
            try { count = Integer.parseInt(g); } catch (Exception ignored) {}
            before = before.substring(0, mn.start()).trim();
        } else {
            Matcher anyNum = Pattern.compile("(\\d+)").matcher(before);
            String last = null;
            while (anyNum.find()) last = anyNum.group(1);
            if (last != null) {
                try { count = Integer.parseInt(last); } catch (Exception ignored) { count = 1; }
                before = before.replaceFirst("\\b" + last + "\\b\\s*$", "").trim();
            }
        }

        String[] parts = before.split("\\s+");
        String name = parts.length > 0 ? parts[parts.length - 1] : "Shard";
        name = name.replaceAll("[^\\w\\-']", "").trim();
        if (name.isEmpty()) name = "Shard";
        return new ParsedResult(name, count);
    }

    private void recordShard(String rawName, int count) {
        if (rawName == null || rawName.isEmpty()) rawName = "Shard";
        String name = normalizeName(rawName);

        sessionCounts.merge(name, (long) count, Long::sum);
        totalCounts.merge(name, (long) count, Long::sum);

        SkywaveConfig.HuntingConfig cfg = SkywaveConfig.get().hunting;
        if (cfg != null) {
            cfg.totalShards += count;
            long prev = cfg.huntingTotals.getOrDefault(name, 0L);
            cfg.huntingTotals.put(name, prev + count);
            SkywaveConfig.save();
        }
    }

    private String normalizeName(String s) { return s.trim(); }

    public synchronized void startSession() {
        if (running) return;
        running = true;
        sessionStartMillis = System.currentTimeMillis();
        timerPaused = false;
        accumulatedPausedMillis = 0;
        pausedAtMillis = 0;
        sessionCounts.clear();
    }

    public synchronized void stopSession() { running = false; }

    public synchronized void reset(SkywaveConfig.DisplayMode mode) {
        SkywaveConfig.HuntingConfig cfg = SkywaveConfig.get().hunting;
        if (mode == SkywaveConfig.DisplayMode.TOTAL) {
            if (cfg != null) cfg.totalShards = 0L;
            cfg.huntingTotals.clear();
            totalCounts.clear();
            sessionCounts.clear();
            SkywaveConfig.save();
        } else {
            sessionCounts.clear();
        }
    }

    public synchronized boolean isRunning() { return running; }

    public synchronized String getSessionUptimeFormatted() {
        if (!running) return "00:00:00";
        long now = System.currentTimeMillis();
        long elapsed = now - sessionStartMillis - accumulatedPausedMillis;
        if (timerPaused && pausedAtMillis > 0) elapsed = pausedAtMillis - sessionStartMillis - accumulatedPausedMillis;
        Duration d = Duration.ofMillis(Math.max(0, elapsed));
        long hours = d.toHours();
        long minutes = d.toMinutesPart();
        long seconds = d.toSecondsPart();
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public synchronized double getSessionHoursElapsed() {
        if (!running) return 0.0;
        long now = System.currentTimeMillis();
        long elapsed = now - sessionStartMillis - accumulatedPausedMillis;
        if (timerPaused && pausedAtMillis > 0) elapsed = pausedAtMillis - sessionStartMillis - accumulatedPausedMillis;
        return Math.max(0.0, elapsed / 3600000.0);
    }

    public synchronized void toggleTimerPause() {
        if (!running) return;
        if (!timerPaused) {
            timerPaused = true;
            pausedAtMillis = System.currentTimeMillis();
        } else {
            timerPaused = false;
            if (pausedAtMillis > 0) accumulatedPausedMillis += System.currentTimeMillis() - pausedAtMillis;
            pausedAtMillis = 0;
        }
    }

    // ================= HUD rendering =================
    public void onHudRender(DrawContext ctx) {
        SkywaveConfig.HuntingConfig cfg = SkywaveConfig.get().hunting;
        if (cfg == null || !cfg.profitTrackerEnabled) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;

        if (client.currentScreen != null && !(client.currentScreen instanceof SkywaveHudMoveScreen)) {
            drawHudOnly(ctx, cfg);
            return; // клики и dragging не трогаем
        }

        drawHudWithDragging(ctx, cfg, client);
    }

    private void drawHudOnly(DrawContext ctx, SkywaveConfig.HuntingConfig cfg) {
        int x = cfg.hudX;
        int y = cfg.hudY;

        Map<String, Long> counts = (cfg.displayMode == SkywaveConfig.DisplayMode.TOTAL) ? cfg.huntingTotals : sessionCounts;

        double totalCoins = 0.0;
        List<String> lines = new ArrayList<>();
        if (counts.isEmpty()) lines.add("No shards yet");
        else {
            for (Map.Entry<String, Long> e : counts.entrySet()) {
                String item = e.getKey();
                long cnt = e.getValue();
                double unit = priceFetcher.getPriceFor(item);
                double sum = unit * cnt;
                totalCoins += sum;
                String unitStr = unit > 0 ? String.format("%.2f", unit) : "??";
                String sumStr = unit > 0 ? String.format("%.2f", sum) : "??";
                lines.add(item + ": " + cnt + " × " + unitStr + " = " + sumStr);
            }
        }

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        ctx.fill(x - 4, y - 4, x + hudW, y + hudH, 0x66000000);

        int ly = y + 36;
        for (String L : lines) {
            ctx.drawText(tr, Text.literal(L), x + 6, ly, 0xFFFFAA, false);
            ly += 10;
        }
        ctx.drawText(tr, Text.literal("Total: " + String.format("%.2f", totalCoins)), x + 6, ly, 0xFFEE99, false);
    }

    private void drawHudWithDragging(DrawContext ctx, SkywaveConfig.HuntingConfig cfg, MinecraftClient client) {
        // рисуем HUD и обрабатываем dragging
        drawHudOnly(ctx, cfg);

        long window = client.getWindow().getHandle();
        double[] mx = new double[1], my = new double[1];
        GLFW.glfwGetCursorPos(window, mx, my);
        int mouseX = (int) mx[0];
        int mouseY = (int) my[0];

        boolean leftDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        int width = 140;
        int height = cfg.showTimer ? 38 : 28;

        if (leftDown && !dragging) {
            if (mouseX >= cfg.hudX && mouseX <= cfg.hudX + width &&
                    mouseY >= cfg.hudY && mouseY <= cfg.hudY + height) {
                dragging = true;
                dragOffsetX = mouseX - cfg.hudX;
                dragOffsetY = mouseY - cfg.hudY;
            }
        }

        if (!leftDown) dragging = false;

        if (dragging) {
            cfg.hudX = mouseX - dragOffsetX;
            cfg.hudY = mouseY - dragOffsetY;
        }
    }

    public void enableMoveMode() { moveMode = true; }

    public void disableMoveMode() { moveMode = false; dragging = false; SkywaveConfig.save(); }

    public void clientTick() {
        // можно оставить пустым — все dragging теперь через onHudRender
    }

    // ==================== BazaarPriceFetcher ====================
    private class BazaarPriceFetcher {
        private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        private final ConcurrentHashMap<String, Double> priceCache = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Long> cacheTime = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, String> nameToProductId = new ConcurrentHashMap<>();
        private long itemsIndexFetchedAt = 0L;

        private final long PRICE_TTL_MS = TimeUnit.MINUTES.toMillis(Math.max(1, SkywaveConfig.get().bazaarRefreshMinutes));
        private final long ITEMS_TTL_MS = TimeUnit.HOURS.toMillis(4);

        public double getPriceFor(String displayName) {
            if (displayName == null) return 0.0;
            String key = displayName.trim();
            Long t = cacheTime.get(key);
            if (t != null && (System.currentTimeMillis() - t) < PRICE_TTL_MS) {
                return priceCache.getOrDefault(key, 0.0);
            }
            scheduler.execute(() -> fetchPriceFor(key));
            return priceCache.getOrDefault(key, 0.0);
        }

        public void refreshAll() {
            try {
                buildItemsIndexIfNeeded(true);
                fetchBazaarAll();
            } catch (Throwable t) { t.printStackTrace(); }
        }

        private void fetchPriceFor(String displayName) {
            try {
                buildItemsIndexIfNeeded(false);
                String pid = findProductIdFor(displayName);
                if (pid == null) { cacheTime.put(displayName, System.currentTimeMillis()); priceCache.put(displayName, 0.0); return; }
                String apiKey = SkywaveConfig.get().hypixelApiKey;
                if (apiKey == null || apiKey.isEmpty()) { cacheTime.put(displayName, System.currentTimeMillis()); priceCache.put(displayName, 0.0); return; }
                String url = "https://api.hypixel.net/skyblock/bazaar?key=" + apiKey;
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().timeout(Duration.ofSeconds(10)).build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200) { cacheTime.put(displayName, System.currentTimeMillis()); priceCache.put(displayName, 0.0); return; }
                JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                if (!root.has("products")) { cacheTime.put(displayName, System.currentTimeMillis()); priceCache.put(displayName, 0.0); return; }
                JsonObject products = root.getAsJsonObject("products");
                if (!products.has(pid)) { cacheTime.put(displayName, System.currentTimeMillis()); priceCache.put(displayName, 0.0); return; }
                JsonObject prod = products.getAsJsonObject(pid);
                double price = extractPriceFromProduct(prod);
                priceCache.put(displayName, price);
                cacheTime.put(displayName, System.currentTimeMillis());
            } catch (IOException | InterruptedException e) { e.printStackTrace(); }
            catch (Throwable t) { t.printStackTrace(); }
        }

        private void fetchBazaarAll() {
            // оставляем как есть
        }

        private double extractPriceFromProduct(JsonObject prod) {
            // оставляем как есть
            return 0.0;
        }

        private void buildItemsIndexIfNeeded(boolean force) {
            // оставляем как есть
        }

        private String findProductIdFor(String displayName) {
            // оставляем как есть
            return null;
        }
    }
}