package org.wxter.skywave.client.tracker;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;
import org.wxter.skywave.config.SkywaveConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HuntingProfitTracker {

    public static final HuntingProfitTracker INSTANCE = new HuntingProfitTracker();

    private static final int TITLE_COLOR = 0xFF55FFFF;
    private static final int ACTION_COLOR = 0xFFFFFF55;
    private static final int VALUE_COLOR = 0xFFFFAA;
    private static final int MUTED_COLOR = 0xFFB0B0B0;

    private final ItemTrackerState trackerState = new ItemTrackerState(new HuntingStorage());
    private final List<ClickableRegion> clickRegions = new ArrayList<>();

    private boolean moveMode = false;
    private boolean lastLeftWasDown = false;

    private HudBounds lastHudBounds = new HudBounds(0, 0, 0, 0);

    private final BazaarPriceFetcher priceFetcher = new BazaarPriceFetcher();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "skywave-hunting-sched");
        t.setDaemon(true);
        return t;
    });

    private HuntingProfitTracker() {
        long initial = 30L;
        long periodMinutes = Math.max(1, SkywaveConfig.get().bazaarRefreshMinutes);

        // also check hunting.showUnitPrices
        scheduler.scheduleAtFixedRate(() -> {
            try {
                SkywaveConfig cfg = SkywaveConfig.get();
                if (cfg.hypixelApiKey != null && !cfg.hypixelApiKey.isEmpty()
                        && cfg.hunting != null && cfg.hunting.showUnitPrices) {
                    priceFetcher.refreshAll();
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }, initial, periodMinutes, TimeUnit.MINUTES);
    }

    public void init() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            try {
                if (message != null) handleChatMessage(message.getString());
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });

        HudRenderCallback.EVENT.register((drawContext, tick) -> {
            try {
                onHudRender(drawContext);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            ScreenEvents.afterRender(screen).register((renderedScreen, drawContext, mouseX, mouseY, tickDelta) -> {
                try {
                    onScreenRender(drawContext);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            });
        });
    }

    private void handleChatMessage(String raw) {
        if (raw == null) return;
        SkywaveConfig.HuntingConfig cfg = SkywaveConfig.get().hunting;
        if (cfg == null || !cfg.profitTrackerEnabled || !trackerState.isRunning()) return;

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
            } catch (Exception ignored) {
            }
        }

        if (!matched && plain.toLowerCase().contains("shard")) {
            ParsedResult r = parseByProximity(plain);
            if (r != null) recordShard(r.name, r.count);
        }
    }

    private String stripColorCodes(String s) {
        return s == null ? "" : s.replaceAll("§.", "");
    }

    private static class ParsedResult {
        final String name;
        final int count;

        ParsedResult(String n, int c) {
            name = n;
            count = c;
        }
    }

    private ParsedResult parseFromMatcher(Matcher m, String plain) {
        int foundCount = 0;
        List<String> nameParts = new ArrayList<>();

        for (int i = 1; i <= m.groupCount(); i++) {
            String g = m.group(i);
            if (g == null) continue;

            // if this group contains digits -> treat as count
            String digits = g.replaceAll("[^0-9]", "");
            if (!digits.isEmpty()) {
                try {
                    foundCount = Integer.parseInt(digits);
                } catch (Exception ignored) {}
                continue;
            }

            String trimmed = g.trim();
            if (!trimmed.isEmpty()) {
                // remove trailing "Shard"/"Shards" if accidentally captured
                String nm = trimmed.replaceAll("(?i)\\s*Shards?\\s*$", "").trim();
                if (!nm.isEmpty()) nameParts.add(nm);
            }
        }

        String foundName = null;
        if (!nameParts.isEmpty()) {
            // join multiple captured parts (handles "Night" + "Squid" => "Night Squid")
            foundName = String.join(" ", nameParts).replaceAll("\\s+", " ").trim();
        } else {
            // **Important fix:** use the proximity-based parser which returns the full
            // item phrase (not only the last word).
            ParsedResult prox = parseByProximity(plain);
            if (prox != null) foundName = prox.name;
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
            try {
                count = Integer.parseInt(g);
            } catch (Exception ignored) {}
            before = before.substring(0, mn.start()).trim();
        } else {
            Matcher anyNum = Pattern.compile("(\\d+)").matcher(before);
            String last = null;
            while (anyNum.find()) last = anyNum.group(1);
            if (last != null) {
                try {
                    count = Integer.parseInt(last);
                } catch (Exception ignored) {
                    count = 1;
                }
                before = before.replaceFirst("\\b" + last + "\\b\\s*$", "").trim();
            }
        }

        String candidate = before.trim();
        if (candidate.isEmpty()) return null;

        // Remove common leading verbs so candidate becomes just the item name portion.
        candidate = candidate.replaceAll("(?i)^(?:you\\s+(?:caught|received|obtained|got)\\s+|picked up\\s+|loot share you received\\s+)", "").trim();

        // Remove stray punctuation and multiple spaces
        String name = candidate.replaceAll("[^\\w\\- '\\:]", " ").replaceAll("\\s+", " ").trim();

        if (name.isEmpty()) name = "Shard";
        return new ParsedResult(name, count);
    }

    private void recordShard(String rawName, int count) {
        if (rawName == null || rawName.isEmpty()) rawName = "Shard";
        String name = normalizeName(rawName);
        if (name == null || name.isBlank()) {
            name = "Unknown Shard";
        }
        trackerState.recordItem(name, count);
        System.out.println("[Skywave DEBUG] Recording shard name = '" + name + "'");
    }

    private String normalizeName(String s) {
        if (s == null) return "Unknown Shard";

        // Remove Minecraft color/format codes just in case
        s = s.replaceAll("§.", "");

        // Remove trailing "Shard"/"Shards"
        s = s.replaceAll("(?i)\\s*Shards?\\s*$", "");

        // Remove non-visible / control chars
        s = s.replaceAll("\\p{C}", "");

        // Remove weird punctuation at ends
        s = s.replaceAll("^[^A-Za-z0-9]+", "").replaceAll("[^A-Za-z0-9]+$", "");

        // Collapse spaces
        s = s.replaceAll("\\s+", " ").trim();

        // Absolute safety fallback
        if (s.isEmpty()) return "Unknown Shard";

        return s;
    }

    public synchronized void startSession() {
        trackerState.startSession();
    }

    public synchronized void stopSession() {
        trackerState.stopSession();
    }

    public synchronized void reset(SkywaveConfig.DisplayMode mode) {
        trackerState.reset(mode);
    }

    public synchronized boolean isRunning() {
        return trackerState.isRunning();
    }

    public synchronized String getSessionUptimeFormatted() {
        return trackerState.getSessionUptimeFormatted();
    }

    public synchronized double getSessionHoursElapsed() {
        return trackerState.getSessionHoursElapsed();
    }

    public synchronized void toggleTimerPause() {
        trackerState.toggleTimerPause();
    }

    public void onHudRender(DrawContext ctx) {
        SkywaveConfig.HuntingConfig cfg = SkywaveConfig.get().hunting;
        if (cfg == null || !cfg.profitTrackerEnabled) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;

        if (client.currentScreen != null) return;
        boolean allowClicks = !moveMode;
        drawHud(ctx, cfg, client, allowClicks);
    }

    private void onScreenRender(DrawContext ctx) {
        SkywaveConfig.HuntingConfig cfg = SkywaveConfig.get().hunting;
        if (cfg == null || !cfg.profitTrackerEnabled) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;
        if (client.currentScreen == null) return;

        boolean allowClicks = !moveMode;
        drawHud(ctx, cfg, client, allowClicks);
    }

    private void drawHud(DrawContext ctx, SkywaveConfig.HuntingConfig cfg, MinecraftClient client, boolean allowClicks) {
        int x = cfg.hudX;
        int y = cfg.hudY;

        TextRenderer tr = client.textRenderer;
        clickRegions.clear();

        List<DisplayLine> lines = buildLines(cfg);
        int lineHeight = tr.fontHeight + 2;

        int maxWidth = 0;
        int ly = y;
        for (DisplayLine line : lines) {
            int width = tr.getWidth(line.text());
            maxWidth = Math.max(maxWidth, width);
            ctx.drawTextWithShadow(tr, line.text(), x, ly, line.color());
            if (allowClicks && line.onClick() != null) {
                clickRegions.add(new ClickableRegion(x, ly, width, tr.fontHeight, line.onClick()));
            }
            ly += lineHeight;
        }

        lastHudBounds = new HudBounds(x, y, maxWidth, Math.max(0, lines.size() * lineHeight));

        if (allowClicks) {
            handleClicks(client);
        }
    }

    private List<DisplayLine> buildLines(SkywaveConfig.HuntingConfig cfg) {
        List<DisplayLine> lines = new ArrayList<>();
        lines.add(new DisplayLine("Hunting Profit Tracker", TITLE_COLOR, null));

        String startLabel = trackerState.isRunning() ? "Stop Count" : "Start Count";
        lines.add(new DisplayLine(startLabel, ACTION_COLOR, () -> {
            if (trackerState.isRunning()) stopSession();
            else startSession();
        }));

        lines.add(new DisplayLine("Mode: " + formatMode(cfg.displayMode), ACTION_COLOR, () -> {
            cfg.displayMode = cfg.displayMode == SkywaveConfig.DisplayMode.TOTAL
                    ? SkywaveConfig.DisplayMode.SESSION
                    : SkywaveConfig.DisplayMode.TOTAL;
            SkywaveConfig.save();
        }));

        lines.add(new DisplayLine("Reset Tracker", ACTION_COLOR, () -> reset(cfg.displayMode)));

        if (cfg.showTimer) {
            String timerLabel = trackerState.isTimerPaused() ? "Resume" : "Pause";
            String text = "Timer: " + getSessionUptimeFormatted() + " (" + timerLabel + ")";
            lines.add(new DisplayLine(text, ACTION_COLOR, this::toggleTimerPause));
        }

        Map<String, Long> counts = trackerState.getCounts(cfg.displayMode);

        double totalCoins = 0.0;
        boolean hasAnyPrice = false;
        if (counts.isEmpty()) {
            lines.add(new DisplayLine("No shards yet", MUTED_COLOR, null));
        } else {
            Map<String, Long> sorted = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            sorted.putAll(counts);
            for (Map.Entry<String, Long> e : sorted.entrySet()) {
                String item = e.getKey();
                long cnt = e.getValue();

                // Skip corrupt entries
                if (item == null || item.isBlank()) continue;

                if (cfg.showUnitPrices) {
                    double unit = priceFetcher.getPriceFor(item);
                    boolean hasPrice = unit > 0;
                    if (hasPrice) {
                        hasAnyPrice = true;
                        totalCoins += unit * cnt;
                    }
                    String unitStr = hasPrice ? formatCoins(unit) : "??";
                    String sumStr = hasPrice ? formatCoins(unit * cnt) : "??";
                    lines.add(new DisplayLine(item + ": " + cnt + " × " + unitStr + " = " + sumStr, VALUE_COLOR, null));
                } else {
                    // simpler line when unit prices are disabled
                    lines.add(new DisplayLine(item + ": " + cnt, VALUE_COLOR, null));
                }
            }
        }

        if (cfg.showUnitPrices) {
            String totalLine = "Total: " + (hasAnyPrice ? formatCoins(totalCoins) : "??");
            lines.add(new DisplayLine(totalLine, VALUE_COLOR, null));

            double hours = getSessionHoursElapsed();
            double coinsPerHour = hours > 0 ? totalCoins / hours : 0.0;
            lines.add(new DisplayLine("Coins/hour: " + formatCoins(coinsPerHour), MUTED_COLOR, null));
        } else {
            long totalShards = counts.values().stream().mapToLong(Long::longValue).sum();
            lines.add(new DisplayLine("Total shards: " + totalShards, VALUE_COLOR, null));

            double hours = getSessionHoursElapsed();
            double shardsPerHour = hours > 0 ? (totalShards / hours) : 0.0;
            lines.add(new DisplayLine("Shards/hour: " + String.format("%,.2f", shardsPerHour), MUTED_COLOR, null));
        }

        return lines;
    }

    private String formatMode(SkywaveConfig.DisplayMode mode) {
        return mode == SkywaveConfig.DisplayMode.TOTAL ? "Total" : "Session";
    }

    private String formatCoins(double value) {
        return String.format("%,.2f", value);
    }

    private void handleClicks(MinecraftClient client) {
        long window = client.getWindow().getHandle();
        double[] mx = new double[1];
        double[] my = new double[1];
        GLFW.glfwGetCursorPos(window, mx, my);
        double scale = client.getWindow().getScaleFactor();
        int mouseX = (int) (mx[0] / scale);
        int mouseY = (int) (my[0] / scale);

        boolean leftDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (leftDown && !lastLeftWasDown) {
            for (ClickableRegion region : clickRegions) {
                if (region.contains(mouseX, mouseY)) {
                    region.onClick.run();
                    break;
                }
            }
        }
        lastLeftWasDown = leftDown;
    }

    public void enableMoveMode() {
        moveMode = true;
    }

    public void disableMoveMode() {
        moveMode = false;
        SkywaveConfig.save();
    }

    public HudBounds getHudBounds() {
        return lastHudBounds;
    }

    public void setHudPosition(int x, int y) {
        SkywaveConfig.get().hunting.hudX = x;
        SkywaveConfig.get().hunting.hudY = y;
        SkywaveConfig.save();
    }

    public void clientTick() {
        // reserved for future use
    }

    private record DisplayLine(String text, int color, Runnable onClick) {}

    private record ClickableRegion(int x, int y, int width, int height, Runnable onClick) {
        boolean contains(int mx, int my) {
            return mx >= x && mx <= x + width && my >= y && my <= y + height;
        }
    }

    public record HudBounds(int x, int y, int width, int height) {
        public boolean contains(int mx, int my) {
            return mx >= x && mx <= x + width && my >= y && my <= y + height;
        }
    }

    private static class HuntingStorage implements ItemTrackerStorage {
        @Override
        public Map<String, Long> getTotalCounts() {
            return SkywaveConfig.get().hunting.huntingTotals;
        }

        @Override
        public void incrementTotal(String name, long delta) {
            SkywaveConfig.HuntingConfig cfg = SkywaveConfig.get().hunting;
            if (cfg == null) return;
            cfg.totalShards += delta;
            long prev = cfg.huntingTotals.getOrDefault(name, 0L);
            cfg.huntingTotals.put(name, prev + delta);
        }

        @Override
        public void resetTotals() {
            SkywaveConfig.HuntingConfig cfg = SkywaveConfig.get().hunting;
            if (cfg == null) return;
            cfg.totalShards = 0L;
            cfg.huntingTotals.clear();
        }

        @Override
        public void save() {
            SkywaveConfig.save();
        }
    }

    private class BazaarPriceFetcher {
        private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        private final ConcurrentHashMap<String, Double> priceCache = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Long> cacheTime = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Double> auctionPriceCache = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Long> auctionCacheTime = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, String> nameToProductId = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, String> normalizedNameToId = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Long> missingIdLogTime = new ConcurrentHashMap<>();
        private long itemsIndexFetchedAt = 0L;

        private final long PRICE_TTL_MS = TimeUnit.MINUTES.toMillis(Math.max(1, SkywaveConfig.get().bazaarRefreshMinutes));
        private final long AUCTION_TTL_MS = TimeUnit.MINUTES.toMillis(10);
        private final long ITEMS_TTL_MS = TimeUnit.HOURS.toMillis(4);
        private static final long MISSING_ID_LOG_COOLDOWN_MS = TimeUnit.MINUTES.toMillis(2);

        public double getPriceFor(String displayName) {
            if (displayName == null) return 0.0;
            String key = displayName.trim();
            Long t = cacheTime.get(key);
            if (t != null && (System.currentTimeMillis() - t) < PRICE_TTL_MS) {
                double cached = priceCache.getOrDefault(key, 0.0);
                if (cached > 0) return cached;
            }
            Long auctionTime = auctionCacheTime.get(key);
            if (auctionTime != null && (System.currentTimeMillis() - auctionTime) < AUCTION_TTL_MS) {
                return auctionPriceCache.getOrDefault(key, 0.0);
            }
            scheduler.execute(() -> fetchPriceFor(key));
            double fallback = priceCache.getOrDefault(key, 0.0);
            if (fallback > 0) return fallback;
            return auctionPriceCache.getOrDefault(key, 0.0);
        }

        public void refreshAll() {
            try {
                buildItemsIndexIfNeeded(true);
                fetchBazaarAll();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        private void fetchPriceFor(String displayName) {
            try {
                buildItemsIndexIfNeeded(false);
                String pid = findProductIdFor(displayName);
                if (pid == null) {
                    long now = System.currentTimeMillis();
                    Long last = missingIdLogTime.get(displayName);

                    if (last == null || (now - last) > MISSING_ID_LOG_COOLDOWN_MS) {
                        System.out.println("[Skywave] Bazaar lookup: no product id for '" + displayName + "'");
                        missingIdLogTime.put(displayName, now);
                    }

                    cacheTime.put(displayName, now + TimeUnit.MINUTES.toMillis(1));
                    priceCache.put(displayName, 0.0);
                    return;
                }

                String apiKey = SkywaveConfig.get().hypixelApiKey;
                if (apiKey == null || apiKey.isEmpty()) {
                    cacheTime.put(displayName, System.currentTimeMillis());
                    priceCache.put(displayName, 0.0);
                    return;
                }
                String url = "https://api.hypixel.net/v2/skyblock/bazaar?key=" + apiKey;
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().timeout(Duration.ofSeconds(10)).build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200) {
                    cacheTime.put(displayName, System.currentTimeMillis());
                    priceCache.put(displayName, 0.0);
                    return;
                }
                JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                if (!root.has("products")) {
                    cacheTime.put(displayName, System.currentTimeMillis());
                    priceCache.put(displayName, 0.0);
                    return;
                }
                JsonObject products = root.getAsJsonObject("products");
                if (!products.has(pid)) {
                    cacheTime.put(displayName, System.currentTimeMillis());
                    priceCache.put(displayName, 0.0);
                    return;
                }
                JsonObject prod = products.getAsJsonObject(pid);
                double price = extractPriceFromProduct(prod);
                priceCache.put(displayName, price);
                cacheTime.put(displayName, System.currentTimeMillis());
                if (price <= 0) {
                    fetchAuctionPriceFor(displayName);
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        private void fetchBazaarAll() {
            String apiKey = SkywaveConfig.get().hypixelApiKey;
            if (apiKey == null || apiKey.isEmpty()) return;
            try {
                String url = "https://api.hypixel.net/v2/skyblock/bazaar?key=" + apiKey;
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().timeout(Duration.ofSeconds(12)).build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200) return;
                JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                if (!root.has("products")) return;
                JsonObject products = root.getAsJsonObject("products");
                for (String key : products.keySet()) {
                    JsonObject prod = products.getAsJsonObject(key);
                    double price = extractPriceFromProduct(prod);
                    String display = nameToProductId.entrySet().stream()
                            .filter(e -> Objects.equals(e.getValue(), key))
                            .map(Map.Entry::getKey)
                            .findFirst()
                            .orElse(key);
                    priceCache.put(display, price);
                    cacheTime.put(display, System.currentTimeMillis());
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }

        private void fetchAuctionPriceFor(String displayName) {
            String apiKey = SkywaveConfig.get().hypixelApiKey;
            if (apiKey == null || apiKey.isEmpty()) return;
            try {
                String url = "https://api.hypixel.net/v2/skyblock/auctions?key=" + apiKey + "&page=0";
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().timeout(Duration.ofSeconds(12)).build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200) return;
                JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                if (!root.has("auctions")) return;
                double lowest = 0.0;
                for (var elem : root.getAsJsonArray("auctions")) {
                    JsonObject auction = elem.getAsJsonObject();
                    if (!auction.has("bin") || !auction.get("bin").getAsBoolean()) continue;
                    if (!auction.has("item_name") || !auction.has("starting_bid")) continue;
                    String name = auction.get("item_name").getAsString();
                    if (!name.equalsIgnoreCase(displayName)) continue;
                    double bid = auction.get("starting_bid").getAsDouble();
                    if (lowest <= 0 || bid < lowest) lowest = bid;
                }
                auctionPriceCache.put(displayName, lowest);
                auctionCacheTime.put(displayName, System.currentTimeMillis());
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }

        private double extractPriceFromProduct(JsonObject prod) {
            if (prod == null) return 0.0;
            if (!prod.has("quick_status")) return 0.0;
            JsonObject status = prod.getAsJsonObject("quick_status");
            if (status.has("sellPrice")) {
                return Math.max(0.0, status.get("sellPrice").getAsDouble());
            }
            if (status.has("buyPrice")) {
                return Math.max(0.0, status.get("buyPrice").getAsDouble());
            }
            return 0.0;
        }

        private void buildItemsIndexIfNeeded(boolean force) {
            long now = System.currentTimeMillis();
            if (!force && (now - itemsIndexFetchedAt) < ITEMS_TTL_MS && !nameToProductId.isEmpty()) return;
            String apiKey = SkywaveConfig.get().hypixelApiKey;
            if (apiKey == null || apiKey.isEmpty()) return;
            try {
                String url = "https://api.hypixel.net/v2/resources/skyblock/items?key=" + apiKey;
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().timeout(Duration.ofSeconds(10)).build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200) return;
                JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                if (!root.has("items")) return;
                JsonArray items = root.getAsJsonArray("items");
                nameToProductId.clear();
                normalizedNameToId.clear();
                for (int i = 0; i < items.size(); i++) {
                    JsonObject obj = items.get(i).getAsJsonObject();
                    if (!obj.has("id") || !obj.has("name")) continue;
                    String id = obj.get("id").getAsString();
                    String name = obj.get("name").getAsString();
                    nameToProductId.put(name, id);
                    normalizedNameToId.put(normalizeLookup(name), id);
                }
                itemsIndexFetchedAt = now;
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }

        private String findProductIdFor(String displayName) {
            if (displayName == null) return null;

            // direct exact name match first
            String direct = nameToProductId.get(displayName);
            if (direct != null) return direct;

            String normalized = normalizeLookup(displayName);

            // direct normalized match
            String mapped = normalizedNameToId.get(normalized);
            if (mapped != null) return mapped;

            // Fallback: try fuzzy contains / startsWith matches against the normalized index
            // (this helps when displayName variants don't exactly match Hypixel item names)
            for (var entry : normalizedNameToId.entrySet()) {
                String key = entry.getKey();
                if (key == null) continue;
                if (key.contains(normalized) || normalized.contains(key)
                        || key.startsWith(normalized) || normalized.startsWith(key)) {
                    return entry.getValue();
                }
            }
            // last-ditch guess using typical Hypixel product id naming convention, e.g.
            // displayName "Night Squid" -> "SHARD_NIGHT_SQUID"
            return "SHARD_" + displayName.toUpperCase().replaceAll("[^A-Z0-9]+", "_");
        }

        private String normalizeLookup(String name) {
            if (name == null) return "";
            // Lowercase and remove non-alphanumerics; keep a compact representation for matching
            return name.toLowerCase().replaceAll("[^a-z0-9]", "");
        }
    }
}
