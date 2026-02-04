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
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.text.MutableText;
import net.minecraft.util.Formatting;
import org.wxter.skywave.ModConstants;
import org.lwjgl.glfw.GLFW;
import org.wxter.skywave.client.HypixelSkyblockContext;
import org.wxter.skywave.client.gui.HudPanelRenderer;
import org.wxter.skywave.config.SkywaveConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
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
    private static final int VALUE_COLOR = 0xFFFFFFAA;
    private static final int MUTED_COLOR = 0xFFB0B0B0;
    private static final int DANGER_COLOR = 0xFFFF5555;
    private static final int SUCCESS_COLOR = 0xFF55FF55;
    private static final int LOOTSHARE_TAG_COLOR = 0xFF555555;
    private static final int YELLOW_ORANGE_COLOR = 0xFFFFAA00;

    private final ItemTrackerState trackerState = new ItemTrackerState(new HuntingStorage());
    private final List<ClickableRegion> clickRegions = new ArrayList<>();
    private final ConcurrentHashMap<String, Long> highlightUntilByKey = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> lastSeenRarityRgbByBaseName = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> lastSeenRarityWeightByBaseName = new ConcurrentHashMap<>();

    private static final int MAX_VISIBLE_ITEM_LINES = 6;
    private int itemScrollOffset = 0;
    private int lastRenderedItemLines = 0;
    private int lastMouseX = -1;
    private int lastMouseY = -1;

    private volatile double cachedCoinsPerHour = 0.0;
    private volatile long cachedCoinsPerHourAt = 0L;

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
                ModConstants.LOGGER.error("HuntingProfitTracker scheduled refresh failed", t);
            }
        }, initial, periodMinutes, TimeUnit.MINUTES);
    }

    public void init() {
        seedRarityCacheFromConfig();

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            try {
                if (message != null) handleChatMessage(message.getString());
            } catch (Throwable t) {
                ModConstants.LOGGER.error("HuntingProfitTracker chat handler failed", t);
            }
        });

        HudRenderCallback.EVENT.register((drawContext, tick) -> {
            try {
                onHudRender(drawContext);
            } catch (Throwable t) {
                ModConstants.LOGGER.error("HuntingProfitTracker HUD render failed", t);
            }
        });

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            ScreenEvents.afterRender(screen).register((renderedScreen, drawContext, mouseX, mouseY, tickDelta) -> {
                try {
                    onScreenRender(drawContext);
                } catch (Throwable t) {
                    ModConstants.LOGGER.error("HuntingProfitTracker screen render failed", t);
                }
            });
        });

        // Try to warm the price cache shortly after client init (without waiting for the periodic scheduler).
        scheduler.execute(() -> {
            try {
                SkywaveConfig cfg = SkywaveConfig.get();
                if (cfg.hypixelApiKey != null && !cfg.hypixelApiKey.isEmpty()
                        && cfg.hunting != null && cfg.hunting.showUnitPrices) {
                    priceFetcher.refreshAll();
                }
            } catch (Throwable t) {
                ModConstants.LOGGER.error("HuntingProfitTracker initial price warmup failed", t);
            }
        });
    }

    private void handleChatMessage(String raw) {
        if (raw == null) return;
        SkywaveConfig.HuntingConfig cfg = SkywaveConfig.get().hunting;
        if (cfg == null || !cfg.profitTrackerEnabled || !trackerState.isRunning()) return;
        if (!HypixelSkyblockContext.isOnHypixelSkyblock()) return;

        String plain = stripColorCodes(raw).trim();
        if (plain.isEmpty()) return;

        boolean hasFormatting = raw.indexOf('\u00A7') >= 0;
        boolean isBeaconRewardShardLine = isBeaconRewardShardLine(plain);

        // Only parse system-like shard messages that come with legacy formatting codes,
        // OR indented beacon reward lines (which are plain text).
        if (!hasFormatting && !isBeaconRewardShardLine) return;

        String lower = plain.toLowerCase();
        boolean lootshare = containsLootshare(lower);

        boolean isSentToBoxLine = lower.startsWith("you sent") && lower.contains("hunting box");
        if (isSentToBoxLine && !cfg.countSentToHuntingBox) return;

        // Extra guard: only parse the standard system shard messages, lootshare lines, or beacon reward shard lines.
        if (!plain.startsWith("You ") && !lootshare && !isBeaconRewardShardLine) return;

        if (isSentToBoxLine && cfg.countSentToHuntingBox) {
            ParsedResult sent = parseSentToHuntingBox(plain);
            if (sent != null) {
                updateLastSeenRarityFromRaw(raw, sent.name);
                recordShard(sent.name, sent.count, false);
                return;
            }
        }

        boolean matched = false;
        for (String pat : cfg.chatPatterns) {
            try {
                Pattern p = Pattern.compile(pat, Pattern.CASE_INSENSITIVE);
                Matcher m = p.matcher(plain);
                if (m.find()) {
                    ParsedResult r = parseFromMatcher(m, plain);
                    if (r != null) {
                        updateLastSeenRarityFromRaw(raw, r.name);
                        recordShard(r.name, r.count, lootshare);
                        matched = true;
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        if (!matched && lower.contains("shard")) {
            ParsedResult r = parseByProximity(plain);
            if (r != null) {
                updateLastSeenRarityFromRaw(raw, r.name);
                recordShard(r.name, r.count, lootshare);
            }
        }
    }

    private String stripColorCodes(String s) {
        return s == null ? "" : s.replaceAll("\u00A7.", "");
    }

    private boolean containsLootshare(String lowerPlain) {
        if (lowerPlain == null || lowerPlain.isEmpty()) return false;
        if (lowerPlain.contains("loot share")) return true;
        if (lowerPlain.contains("lootshare")) return true;
        return lowerPlain.replace(" ", "").contains("lootshare");
    }

    private void updateLastSeenRarityFromRaw(String rawWithCodes, String parsedName) {
        if (rawWithCodes == null || rawWithCodes.isEmpty()) return;
        if (parsedName == null || parsedName.isBlank()) return;

        Character code = findLastColorCodeBeforeName(rawWithCodes, parsedName);
        if (code == null) return;

        String baseName = normalizeName(parsedName);
        if (baseName == null || baseName.isBlank()) return;

        Integer rgb = minecraftColorCodeToRgb(code);
        if (rgb != null) {
            lastSeenRarityRgbByBaseName.put(baseName, rgb);
        }

        Integer weight = minecraftColorCodeToRarityWeight(code);
        if (weight != null) {
            lastSeenRarityWeightByBaseName.put(baseName, weight);
        }

        boolean changed = false;
        SkywaveConfig.HuntingConfig cfg = SkywaveConfig.get().hunting;
        if (cfg != null) {
            if (rgb != null) {
                Integer prev = cfg.shardRarityRgb.get(baseName);
                if (prev == null || !prev.equals(rgb)) {
                    cfg.shardRarityRgb.put(baseName, rgb);
                    changed = true;
                }
            }
            if (weight != null) {
                Integer prevW = cfg.shardRarityWeight.get(baseName);
                if (prevW == null || !prevW.equals(weight)) {
                    cfg.shardRarityWeight.put(baseName, weight);
                    changed = true;
                }
            }
        }
        if (changed) {
            SkywaveConfig.save();
        }
    }

    private void seedRarityCacheFromConfig() {
        try {
            SkywaveConfig.HuntingConfig cfg = SkywaveConfig.get().hunting;
            if (cfg == null) return;
            if (cfg.shardRarityRgb != null && !cfg.shardRarityRgb.isEmpty()) {
                lastSeenRarityRgbByBaseName.putAll(cfg.shardRarityRgb);
            }
            if (cfg.shardRarityWeight != null && !cfg.shardRarityWeight.isEmpty()) {
                lastSeenRarityWeightByBaseName.putAll(cfg.shardRarityWeight);
            }
        } catch (Throwable t) {
            ModConstants.LOGGER.warn("Failed to seed shard rarity cache from config", t);
        }
    }

    private Character findLastColorCodeBeforeName(String rawWithCodes, String parsedName) {
        String rawLower = rawWithCodes.toLowerCase(Locale.ROOT);
        String nameLower = parsedName.toLowerCase(Locale.ROOT);
        int idx = rawLower.indexOf(nameLower);
        if (idx < 0) return null;

        for (int i = idx - 2; i >= 0; i--) {
            if (rawWithCodes.charAt(i) != '\u00A7') continue;
            if (i + 1 >= rawWithCodes.length()) continue;
            char code = Character.toLowerCase(rawWithCodes.charAt(i + 1));
            if (minecraftColorCodeToRgb(code) != null) return code;
        }

        return null;
    }

    private Integer minecraftColorCodeToRgb(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0' -> 0x000000; // black
            case '1' -> 0x0000AA; // dark blue
            case '2' -> 0x00AA00; // dark green
            case '3' -> 0x00AAAA; // dark aqua
            case '4' -> 0xAA0000; // dark red
            case '5' -> 0xAA00AA; // dark purple
            case '6' -> 0xFFAA00; // gold
            case '7' -> 0xAAAAAA; // gray
            case '8' -> 0x555555; // dark gray
            case '9' -> 0x5555FF; // blue
            case 'a' -> 0x55FF55; // green
            case 'b' -> 0x55FFFF; // aqua
            case 'c' -> 0xFF5555; // red
            case 'd' -> 0xFF55FF; // light purple
            case 'e' -> 0xFFFF55; // yellow
            case 'f' -> 0xFFFFFF; // white
            default -> null;
        };
    }

    private Integer minecraftColorCodeToRarityWeight(char code) {
        return switch (Character.toLowerCase(code)) {
            case 'f' -> 1; // Common
            case 'a' -> 2; // Uncommon
            case '9' -> 3; // Rare
            case '5' -> 4; // Epic
            case '6' -> 5; // Legendary
            case 'd' -> 6; // Mythic
            case 'b' -> 7; // Divine
            case 'c' -> 8; // Special
            default -> null;
        };
    }

    private boolean isBeaconRewardShardLine(String plain) {
        if (plain == null) return false;
        // Beacon rewards are displayed as indented lines, e.g. "     Beaconmite Shard x2"
        return plain.matches("(?i)^\\s{2,}.+\\s+Shards?\\s+x\\d+\\s*!?\\s*$");
    }

    private ParsedResult parseSentToHuntingBox(String plain) {
        if (plain == null) return null;
        // Examples (after stripping codes):
        // "You sent a Draconic Shard to your Hunting Box."
        // "You sent 25 XYZ Shards to your Hunting Box."
        Pattern p = Pattern.compile("(?i)^You\\s+sent\\s+(?:a\\s+)?(?:x\\s*)?(?:(\\d+)\\s+)?(.+?)\\s+Shards?\\s+to\\s+your\\s+Hunting\\s+Box[.!]?\\s*$");
        Matcher m = p.matcher(plain.trim());
        if (!m.find()) return null;

        int count = 1;
        String cnt = m.group(1);
        if (cnt != null && !cnt.isBlank()) {
            try {
                count = Integer.parseInt(cnt.trim());
            } catch (NumberFormatException ignored) {
                count = 1;
            }
        }

        String name = m.group(2);
        if (name == null) return null;
        name = name.trim();
        if (name.isEmpty()) return null;

        return new ParsedResult(name, Math.max(1, count));
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

        // Safety: remove accidental leading counts
        name = name.replaceAll("(?i)^[xх×✕]?\\s*\\d+\\s+", "");

        if (name.isEmpty()) name = "Shard";
        return new ParsedResult(name, count);
    }

    private static final String KEY_LOOTSHARE_SUFFIX = "||lootshare";

    private List<String> getAllTrackedBaseNames() {
        try {
            Set<String> names = ConcurrentHashMap.newKeySet();
            Map<String, Long> total = trackerState.getCounts(SkywaveConfig.DisplayMode.TOTAL);
            Map<String, Long> session = trackerState.getCounts(SkywaveConfig.DisplayMode.SESSION);
            if (total != null) {
                for (String k : total.keySet()) {
                    if (k == null) continue;
                    String base = k.endsWith(KEY_LOOTSHARE_SUFFIX) ? k.substring(0, k.length() - KEY_LOOTSHARE_SUFFIX.length()) : k;
                    if (!base.isBlank()) names.add(base);
                }
            }
            if (session != null) {
                for (String k : session.keySet()) {
                    if (k == null) continue;
                    String base = k.endsWith(KEY_LOOTSHARE_SUFFIX) ? k.substring(0, k.length() - KEY_LOOTSHARE_SUFFIX.length()) : k;
                    if (!base.isBlank()) names.add(base);
                }
            }
            return new ArrayList<>(names);
        } catch (Throwable t) {
            ModConstants.LOGGER.error("Failed to compute tracked base names for price refresh", t);
            return Collections.emptyList();
        }
    }

    private void recordShard(String rawName, int count, boolean lootshare) {
        if (rawName == null || rawName.isEmpty()) rawName = "Shard";
        String name = normalizeName(rawName);
        if (name == null || name.isBlank()) {
            name = "Unknown Shard";
        }
        String key = lootshare ? (name + KEY_LOOTSHARE_SUFFIX) : name;
        trackerState.recordItem(key, count);
        highlightUntilByKey.put(key, System.currentTimeMillis() + 3_000L);
        ModConstants.LOGGER.debug("Recording shard: name='{}' lootshare={} count={}", name, lootshare, count);
    }

    private String normalizeName(String s) {
        if (s == null) return "Unknown Shard";

        // Remove MC formatting
        s = s.replaceAll("\u00A7.", "");

        // Remove trailing "Shard(s)" (tracker stores base name; price lookup adds suffix back when needed)
        s = s.replaceAll("(?i)\\s*Shards?\\s*$", "");

        // Remove leading quantity like "x2 ", "2 ", "x 2 "
        s = s.replaceAll("(?i)^[xх×✕]?\\s*\\d+\\s+", "");
        s = s.replaceAll("(?i)^[xх×✕]\\s*\\d+\\s*", "");

        // Remove control chars
        s = s.replaceAll("\\p{C}", "");

        // Trim weird edge symbols
        s = s.replaceAll("^[^A-Za-z0-9]+", "").replaceAll("[^A-Za-z0-9]+$", "");

        // Collapse spaces
        s = s.replaceAll("\\s+", " ").trim();

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
        if (!HypixelSkyblockContext.isOnHypixelSkyblock(client)) return;

        // Only render on the main HUD (no screens). Screen overlays are handled by onScreenRender().
        if (client.currentScreen != null) return;
        boolean allowClicks = false;
        boolean showControls = false;
        drawHud(ctx, cfg, client, allowClicks, showControls);
    }

    public void renderMovePreview(DrawContext ctx) {
        SkywaveConfig.HuntingConfig cfg = SkywaveConfig.get().hunting;
        if (cfg == null || !cfg.profitTrackerEnabled) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;

        // In the Move GUI we want a stable preview regardless of server/world state.
        boolean allowClicks = false;
        boolean showControls = true;
        drawHud(ctx, cfg, client, allowClicks, showControls);
    }

    private void onScreenRender(DrawContext ctx) {
        SkywaveConfig.HuntingConfig cfg = SkywaveConfig.get().hunting;
        if (cfg == null || !cfg.profitTrackerEnabled) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;
        if (client.currentScreen == null) return;
        if (!HypixelSkyblockContext.isOnHypixelSkyblock(client)) return;

        // Render only on "in-game" screens where a HUD overlay makes sense:
        // inventory/container (controls), or chat (no controls). Avoid config/mod menus.
        boolean isInventory = client.currentScreen instanceof HandledScreen<?>;
        boolean isChat = client.currentScreen instanceof ChatScreen;
        if (!isInventory && !isChat) return;

        boolean allowClicks = isInventory;
        boolean showControls = isInventory;
        drawHud(ctx, cfg, client, allowClicks, showControls);
    }

    private void drawHud(DrawContext ctx, SkywaveConfig.HuntingConfig cfg, MinecraftClient client, boolean allowClicks, boolean showControls) {
        int x = cfg.hudX;
        int y = cfg.hudY;

        TextRenderer tr = client.textRenderer;
        clickRegions.clear();

        List<DisplayLine> lines = buildLines(cfg, showControls);
        int lineHeight = tr.fontHeight + 2;

        int gap = tr.getWidth("  ");
        int maxCountCol = 0;
        int maxPriceCol = 0;
        for (DisplayLine line : lines) {
            if (line.kind != DisplayLine.Kind.ITEM) continue;
            maxCountCol = Math.max(maxCountCol, tr.getWidth(line.itemCountText));
            maxPriceCol = Math.max(maxPriceCol, tr.getWidth(line.itemPriceText));
        }

        int maxWidth = 0;
        int totalHeight = 0;
        for (DisplayLine line : lines) {
            int width = 0;
            if (line.text != null && !line.text.isEmpty()) {
                if (line.kind == DisplayLine.Kind.ITEM) {
                    int nameW = line.itemName == null ? 0 : tr.getWidth(line.itemName);
                    width = maxCountCol + gap + nameW + gap + maxPriceCol;
                } else {
                    width = tr.getWidth(line.text);
                    if (line.suffixText != null && !line.suffixText.isEmpty()) {
                        width += tr.getWidth(line.suffixText);
                    }
                    if (line.suffixText2 != null && !line.suffixText2.isEmpty()) {
                        width += tr.getWidth(line.suffixText2);
                    }
                }
            }
            maxWidth = Math.max(maxWidth, width);
            totalHeight += lineHeight + Math.max(0, line.extraSpacingAfter);
        }
        totalHeight = Math.max(0, totalHeight);

        // Cache mouse position in scaled coordinates (used for scrolling)
        long window = client.getWindow().getHandle();
        double[] mx = new double[1];
        double[] my = new double[1];
        GLFW.glfwGetCursorPos(window, mx, my);
        double scale = client.getWindow().getScaleFactor();
        lastMouseX = (int) (mx[0] / scale);
        lastMouseY = (int) (my[0] / scale);

        // background panel (global QoL setting)
        if (SkywaveConfig.get().hudBackgroundPanelsEnabled && maxWidth > 0 && totalHeight > 0) {
            HudPanelRenderer.drawRoundedPanel(ctx, x - 4, y - 3, x + maxWidth + 4, y + totalHeight + 3);
        }

        int ly = y;
        for (DisplayLine line : lines) {
            int width = 0;
            if (line.text != null && !line.text.isEmpty()) {
                if (line.kind == DisplayLine.Kind.ITEM) {
                    String countText = line.itemCountText;
                    if (line.itemCountFlash) {
                        countText = "§a§l" + countText + "§r";
                    } else {
                        countText = "§7" + countText + "§r";
                    }
                    Text nameText = line.itemName == null ? Text.empty() : line.itemName;
                    String priceText = "§6" + line.itemPriceText + "§r";

                    int countW = tr.getWidth(line.itemCountText);
                    int nameW = tr.getWidth(nameText);
                    int priceW = tr.getWidth(line.itemPriceText);

                    ctx.drawTextWithShadow(tr, countText, x, ly, 0xFFFFFFFF);
                    ctx.drawTextWithShadow(tr, nameText, x + maxCountCol + gap, ly, 0xFFFFFFFF);
                    ctx.drawTextWithShadow(tr, priceText, x + (maxWidth - priceW), ly, 0xFFFFFFFF);

                    width = maxCountCol + gap + nameW + gap + maxPriceCol;
                } else {
                    width = tr.getWidth(line.text);
                    ctx.drawTextWithShadow(tr, line.text, x, ly, line.color);

                    if (line.suffixText != null && !line.suffixText.isEmpty()) {
                        ctx.drawTextWithShadow(tr, line.suffixText, x + width, ly, line.suffixColor);
                        width += tr.getWidth(line.suffixText);
                    }

                    if (line.suffixText2 != null && !line.suffixText2.isEmpty()) {
                        ctx.drawTextWithShadow(tr, line.suffixText2, x + width, ly, line.suffixColor2);
                        width += tr.getWidth(line.suffixText2);
                    }
                }

                if (allowClicks && line.onClick != null) {
                    clickRegions.add(new ClickableRegion(x, ly, width, tr.fontHeight, line.onClick));
                }
            }
            ly += lineHeight + Math.max(0, line.extraSpacingAfter);
        }

        lastHudBounds = new HudBounds(x, y, maxWidth, totalHeight);

        if (allowClicks) {
            handleClicks(client);
        }
    }

    private List<DisplayLine> buildLines(SkywaveConfig.HuntingConfig cfg, boolean showControls) {
        List<DisplayLine> lines = new ArrayList<>();
        lines.add(DisplayLine.simple("§lHunting Profit Tracker:§r", TITLE_COLOR));
        lines.add(DisplayLine.spacer(4));

        Map<String, Long> rawCountsRef = trackerState.getCounts(cfg.displayMode);
        Map<String, Long> rawCounts = rawCountsRef == null ? Collections.emptyMap() : new HashMap<>(rawCountsRef);
        if (rawCounts.isEmpty()) {
            lines.add(DisplayLine.simple("No shards yet", MUTED_COLOR));
        } else {
            Map<String, Boolean> mergedFlashByBaseName = null;
            Map<String, Long> counts = rawCounts;
            if (!cfg.showLootshareShards) {
                mergedFlashByBaseName = new ConcurrentHashMap<>();
                Map<String, Long> merged = new ConcurrentHashMap<>();
                long nowForFlash = System.currentTimeMillis();
                for (Map.Entry<String, Long> e : rawCounts.entrySet()) {
                    String rawKey = e.getKey();
                    long cnt = e.getValue();
                    if (rawKey == null || rawKey.isBlank() || cnt <= 0) continue;
                    String baseName = rawKey.endsWith(KEY_LOOTSHARE_SUFFIX)
                            ? rawKey.substring(0, rawKey.length() - KEY_LOOTSHARE_SUFFIX.length())
                            : rawKey;
                    if (baseName.isBlank()) continue;
                    merged.merge(baseName, cnt, Long::sum);
                    boolean flash = (highlightUntilByKey.getOrDefault(rawKey, 0L) > nowForFlash);
                    if (flash) mergedFlashByBaseName.put(baseName, true);
                }
                counts = merged;
            }

            double totalCoins = 0.0;
            boolean hasAnyPrice = false;
            List<DisplayLine> itemLines = new ArrayList<>();
            long now = System.currentTimeMillis();

            record ItemEntry(
                    String rawKey,
                    String baseName,
                    boolean lootshare,
                    long count,
                    boolean flash,
                    double unitPrice,
                    boolean hasPrice,
                    double profit,
                    int rarityWeight,
                    int rarityRgb
            ) {}

            List<ItemEntry> entries = new ArrayList<>();
            for (Map.Entry<String, Long> e : counts.entrySet()) {
                String rawKey = e.getKey();
                long cnt = e.getValue();
                if (rawKey == null || rawKey.isBlank() || cnt <= 0) continue;

                boolean lootshare = cfg.showLootshareShards && rawKey.endsWith(KEY_LOOTSHARE_SUFFIX);
                String baseName = lootshare
                        ? rawKey.substring(0, rawKey.length() - KEY_LOOTSHARE_SUFFIX.length())
                        : rawKey;
                if (baseName.isBlank()) continue;

                double unit = cfg.showUnitPrices ? priceFetcher.getPriceFor(baseName, cfg.bazaarPriceMode) : 0.0;
                boolean hasPrice = unit > 0;
                double profit = hasPrice ? (unit * cnt) : 0.0;

                boolean flash = (mergedFlashByBaseName != null)
                        ? mergedFlashByBaseName.getOrDefault(baseName, false)
                        : (highlightUntilByKey.getOrDefault(rawKey, 0L) > now);

                int rarityRgb = lastSeenRarityRgbByBaseName.getOrDefault(baseName, getSavedRarityRgb(baseName, priceFetcher.getRarityRgb(baseName)));
                int rarityWeight = lastSeenRarityWeightByBaseName.getOrDefault(baseName, getSavedRarityWeight(baseName, priceFetcher.getRarityWeight(baseName)));

                entries.add(new ItemEntry(rawKey, baseName, lootshare, cnt, flash, unit, hasPrice, profit, rarityWeight, rarityRgb));
            }

            SkywaveConfig.HuntingSortMode sortMode = cfg.sortMode == null ? SkywaveConfig.HuntingSortMode.PROFIT : cfg.sortMode;
            if (!cfg.showUnitPrices && sortMode == SkywaveConfig.HuntingSortMode.PROFIT) {
                sortMode = SkywaveConfig.HuntingSortMode.RARITY;
            }

            SkywaveConfig.HuntingSortMode effectiveSortMode = sortMode;
            entries.sort((a, b) -> {
                int c;
                if (effectiveSortMode == SkywaveConfig.HuntingSortMode.RARITY) {
                    c = Integer.compare(b.rarityWeight(), a.rarityWeight());
                    if (c != 0) return c;
                    c = Double.compare(b.profit(), a.profit());
                    if (c != 0) return c;
                } else {
                    c = Double.compare(b.profit(), a.profit());
                    if (c != 0) return c;
                    c = Integer.compare(b.rarityWeight(), a.rarityWeight());
                    if (c != 0) return c;
                }

                c = a.baseName().compareToIgnoreCase(b.baseName());
                if (c != 0) return c;
                return Boolean.compare(b.lootshare(), a.lootshare());
            });

            for (ItemEntry it : entries) {
                if (it.hasPrice()) {
                    hasAnyPrice = true;
                    totalCoins += it.profit();
                }

                String countText = "x" + it.count();
                Text name = buildShardNameText(it.baseName(), it.lootshare(), it.rarityRgb());

                String right = cfg.showUnitPrices
                        ? (it.hasPrice() ? formatCoinsShort(it.profit()) : "??")
                        : String.valueOf(it.count());

                itemLines.add(DisplayLine.itemRow(countText, it.flash(), name, right));
            }

            // collapse/scroll if too many items
            lastRenderedItemLines = itemLines.size();
            if (itemLines.size() > MAX_VISIBLE_ITEM_LINES) {
                int maxOffset = Math.max(0, itemLines.size() - MAX_VISIBLE_ITEM_LINES);
                itemScrollOffset = Math.max(0, Math.min(itemScrollOffset, maxOffset));

                int start = itemScrollOffset;
                int end = Math.min(itemLines.size(), itemScrollOffset + MAX_VISIBLE_ITEM_LINES);
                lines.addAll(itemLines.subList(start, end));
                lines.add(DisplayLine.simple("§oMore items (scroll)§r", LOOTSHARE_TAG_COLOR));
            } else {
                lines.addAll(itemLines);
            }

            lines.add(DisplayLine.spacer(4));

            if (cfg.showUnitPrices) {
                DisplayLine profitLine = new DisplayLine("Total Profit: ", SUCCESS_COLOR, null);
                profitLine.suffixText = "§l" + (hasAnyPrice ? formatCoinsLong(totalCoins) : "??");
                profitLine.suffixColor = YELLOW_ORANGE_COLOR;
                lines.add(profitLine);

                if (cfg.displayMode == SkywaveConfig.DisplayMode.SESSION) {
                    double coinsPerHour = getCoinsPerHourCached(totalCoins);
                    DisplayLine cphLine = new DisplayLine("§oCoins/h: §r", MUTED_COLOR, null);
                    cphLine.suffixText = "§o" + formatCoinsLong(coinsPerHour) + "§r";
                    cphLine.suffixColor = coinsPerHourColor(coinsPerHour);
                    lines.add(cphLine);
                }
            } else {
                long totalShards = counts.values().stream().mapToLong(Long::longValue).sum();
                lines.add(DisplayLine.simple("Total shards: " + totalShards, SUCCESS_COLOR));

                double hours = getSessionHoursElapsed();
                double shardsPerHour = hours > 0 ? (totalShards / hours) : 0.0;
                lines.add(DisplayLine.simple("Shards/h: " + String.format("%,.2f", shardsPerHour), MUTED_COLOR));
            }
        }

        if (cfg.showTimer) {
            lines.add(DisplayLine.spacer(4));

            String timerValue = getSessionUptimeFormatted();
            boolean paused = trackerState.isRunning() && trackerState.isTimerPaused();

            StringBuilder timerText = new StringBuilder("Timer: ").append(timerValue);
            if (paused) timerText.append(" §cPaused§r");
            if (showControls) {
                timerText.append(" §a[").append(paused ? "Resume" : "Pause").append("]§r");
            }

            DisplayLine timer = new DisplayLine(timerText.toString(), MUTED_COLOR, showControls ? this::toggleTimerPause : null);
            lines.add(timer);
        }

            if (showControls) {
                lines.add(DisplayLine.spacer(4));

                String startLabel = trackerState.isRunning() ? "Stop Count" : "Start Count";
                lines.add(new DisplayLine("§l§a[" + startLabel + "]§r", SUCCESS_COLOR, () -> {
                    if (trackerState.isRunning()) stopSession();
                    else startSession();
                }));

                lines.add(new DisplayLine("§lMode: §a[" + formatMode(cfg.displayMode) + "]§r", SUCCESS_COLOR, () -> {
                    cfg.displayMode = cfg.displayMode == SkywaveConfig.DisplayMode.TOTAL
                            ? SkywaveConfig.DisplayMode.SESSION
                            : SkywaveConfig.DisplayMode.TOTAL;
                    SkywaveConfig.save();
                }));

                if (cfg.showUnitPrices) {
                    String buy = cfg.bazaarPriceMode == SkywaveConfig.BazaarPriceMode.BUY_OFFER
                            ? "§a[Buy Offer]§r"
                            : "§2[§7Buy Offer§2]§r";
                    String sell = cfg.bazaarPriceMode == SkywaveConfig.BazaarPriceMode.SELL_OFFER
                            ? "§a[Sell Offer]§r"
                            : "§2[§7Sell Offer§2]§r";
                    lines.add(new DisplayLine("§lPrice format: " + buy + " §8/§r " + sell, SUCCESS_COLOR, () -> {
                        cfg.bazaarPriceMode = cfg.bazaarPriceMode == SkywaveConfig.BazaarPriceMode.BUY_OFFER
                                ? SkywaveConfig.BazaarPriceMode.SELL_OFFER
                                : SkywaveConfig.BazaarPriceMode.BUY_OFFER;
                        SkywaveConfig.save();
                    }));
                }

                lines.add(new DisplayLine("§lReset Tracker§r", DANGER_COLOR, () -> reset(cfg.displayMode)));
            }

            return lines;
        }

    private String formatMode(SkywaveConfig.DisplayMode mode) {
        return mode == SkywaveConfig.DisplayMode.TOTAL ? "Total" : "Session";
    }

    private String formatCoins(double value) {
        return String.format("%,.2f", value);
    }

    private String formatCoinsLong(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "0";
        return String.format("%,.0f", Math.max(0.0, value));
    }

    private String formatCoinsShort(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "0";
        double v = Math.max(0.0, value);
        if (v >= 1_000_000_000) return String.format("%,.2fB", v / 1_000_000_000.0);
        if (v >= 1_000_000) return String.format("%,.2fM", v / 1_000_000.0);
        if (v >= 1_000) return String.format("%,.2fK", v / 1_000.0);
        return String.format("%,.0f", v);
    }

    private Text buildShardNameText(String baseName, boolean lootshare, int rarityRgb) {
        String safeName = baseName == null ? "" : baseName;
        TextColor color = TextColor.fromRgb(rarityRgb & 0xFFFFFF);
        MutableText out = Text.literal(safeName).setStyle(Style.EMPTY.withColor(color))
                .append(Text.literal(" Shard").formatted(Formatting.GRAY));

        if (lootshare) {
            out = out.append(Text.literal(" LS").formatted(Formatting.DARK_GRAY));
        }

        return out;
    }

    private int getSavedRarityRgb(String baseName, int fallback) {
        SkywaveConfig.HuntingConfig cfg = SkywaveConfig.get().hunting;
        if (cfg == null || cfg.shardRarityRgb == null || baseName == null) return fallback;
        Integer v = cfg.shardRarityRgb.get(baseName);
        return v == null ? fallback : v;
    }

    private int getSavedRarityWeight(String baseName, int fallback) {
        SkywaveConfig.HuntingConfig cfg = SkywaveConfig.get().hunting;
        if (cfg == null || cfg.shardRarityWeight == null || baseName == null) return fallback;
        Integer v = cfg.shardRarityWeight.get(baseName);
        return v == null ? fallback : v;
    }

    private double getCoinsPerHourCached(double totalCoins) {
        long now = System.currentTimeMillis();
        if (cachedCoinsPerHourAt == 0L || (now - cachedCoinsPerHourAt) >= 2_000L) {
            double hours = getSessionHoursElapsed();
            cachedCoinsPerHour = hours > 0 ? (totalCoins / hours) : 0.0;
            cachedCoinsPerHourAt = now;
        }
        return cachedCoinsPerHour;
    }

    private int coinsPerHourColor(double cph) {
        if (cph > 500_000_000) return 0xFF55FFFF; // turquoise
        if (cph > 100_000_000) return 0xFFFF55FF; // purple
        if (cph > 50_000_000) return 0xFFFFAA00;  // orange
        if (cph > 15_000_000) return 0xFFFFFF55;  // yellow
        if (cph > 0) return 0xFF55FF55;           // green
        return MUTED_COLOR;
    }

    public void onMouseScroll(double verticalAmount) {
        if (verticalAmount == 0) return;
        SkywaveConfig.HuntingConfig cfg = SkywaveConfig.get().hunting;
        if (cfg == null || !cfg.profitTrackerEnabled) return;
        if (lastHudBounds == null || !lastHudBounds.contains(lastMouseX, lastMouseY)) return;
        if (lastRenderedItemLines <= MAX_VISIBLE_ITEM_LINES) return;

        int maxOffset = Math.max(0, lastRenderedItemLines - MAX_VISIBLE_ITEM_LINES);
        int delta = verticalAmount > 0 ? -1 : 1;
        itemScrollOffset = Math.max(0, Math.min(itemScrollOffset + delta, maxOffset));
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

    public HudBounds getHudBounds() {
        return lastHudBounds;
    }

    public void setHudPosition(int x, int y) {
        SkywaveConfig.get().hunting.hudX = x;
        SkywaveConfig.get().hunting.hudY = y;
        SkywaveConfig.save();
    }

    private static final class DisplayLine {
        enum Kind { TEXT, ITEM }

        final Kind kind;
        final String text;
        final int color;
        final Runnable onClick;
        String suffixText;
        int suffixColor;
        String suffixText2;
        int suffixColor2;
        int extraSpacingAfter;

        // ITEM fields (rendered via drawHud custom columns)
        String itemCountText;
        Text itemName;
        String itemPriceText;
        boolean itemCountFlash;

        private DisplayLine(Kind kind, String text, int color, Runnable onClick) {
            this.kind = kind == null ? Kind.TEXT : kind;
            this.text = text == null ? "" : text;
            this.color = color;
            this.onClick = onClick;
            this.suffixText = null;
            this.suffixColor = color;
            this.suffixText2 = null;
            this.suffixColor2 = color;
            this.extraSpacingAfter = 0;
            this.itemCountText = null;
            this.itemName = null;
            this.itemPriceText = null;
            this.itemCountFlash = false;
        }

        DisplayLine(String text, int color, Runnable onClick) {
            this(Kind.TEXT, text, color, onClick);
        }

        static DisplayLine simple(String text, int color) {
            return new DisplayLine(text, color, null);
        }

        static DisplayLine spacer(int extraPixels) {
            DisplayLine line = new DisplayLine("", MUTED_COLOR, null);
            line.extraSpacingAfter = Math.max(0, extraPixels);
            return line;
        }

        static DisplayLine twoCol(String left, String right, int leftColor, int rightColor) {
            DisplayLine line = new DisplayLine(left, leftColor, null);
            line.suffixText = " - " + right;
            line.suffixColor = rightColor;
            return line;
        }

        static DisplayLine itemRow(String countText, boolean flash, Text nameText, String priceText) {
            DisplayLine line = new DisplayLine(Kind.ITEM, "__ITEM__", 0xFFFFFFFF, null);
            line.itemCountText = countText == null ? "" : countText;
            line.itemCountFlash = flash;
            line.itemName = nameText == null ? Text.empty() : nameText;
            line.itemPriceText = priceText == null ? "" : priceText;
            return line;
        }
    }

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
        private final ConcurrentHashMap<String, String> baseNameToProductId = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Long> missingIdLogTime = new ConcurrentHashMap<>();

        private final ConcurrentHashMap<String, Double> buyPriceByProductId = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Double> sellPriceByProductId = new ConcurrentHashMap<>();
        private volatile long bazaarSnapshotFetchedAt = 0L;
        private volatile boolean bazaarSnapshotRefreshQueued = false;

        private final ConcurrentHashMap<String, Integer> rarityRgbByBaseName = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Integer> rarityWeightByBaseName = new ConcurrentHashMap<>();
        private volatile long itemsIndexFetchedAt = 0L;
        private volatile boolean itemsIndexRefreshQueued = false;

        private final long SNAPSHOT_TTL_MS = TimeUnit.SECONDS.toMillis(30);
        private final long ITEMS_TTL_MS = TimeUnit.HOURS.toMillis(6);
        private static final long MISSING_ID_LOG_COOLDOWN_MS = TimeUnit.MINUTES.toMillis(2);

        public double getPriceFor(String baseName, SkywaveConfig.BazaarPriceMode mode) {
            if (baseName == null || baseName.isBlank()) return 0.0;
            ensureBazaarSnapshotFresh();

            String productId = resolveBazaarProductId(baseName);
            if (productId == null) return 0.0;
            if (mode == SkywaveConfig.BazaarPriceMode.SELL_OFFER) {
                return sellPriceByProductId.getOrDefault(productId, 0.0);
            }
            return buyPriceByProductId.getOrDefault(productId, 0.0);
        }

        public int getRarityRgb(String baseName) {
            if (baseName == null || baseName.isBlank()) return 0xFFFFFF;
            ensureItemsIndexFresh();
            return rarityRgbByBaseName.getOrDefault(baseName, 0xFFFFFF);
        }

        public int getRarityWeight(String baseName) {
            if (baseName == null || baseName.isBlank()) return 0;
            ensureItemsIndexFresh();
            return rarityWeightByBaseName.getOrDefault(baseName, 0);
        }

        public void refreshAll() {
            try {
                refreshBazaarSnapshot(true);
                refreshItemsIndex(true);
            } catch (Throwable t) {
                ModConstants.LOGGER.error("Bazaar refresh failed", t);
            }
        }

        private void ensureBazaarSnapshotFresh() {
            long now = System.currentTimeMillis();
            if ((now - bazaarSnapshotFetchedAt) < SNAPSHOT_TTL_MS) return;
            if (bazaarSnapshotRefreshQueued) return;
            bazaarSnapshotRefreshQueued = true;
            scheduler.execute(() -> {
                try {
                    refreshBazaarSnapshot(false);
                } finally {
                    bazaarSnapshotRefreshQueued = false;
                }
            });
        }

        private void ensureItemsIndexFresh() {
            long now = System.currentTimeMillis();
            if ((now - itemsIndexFetchedAt) < ITEMS_TTL_MS && !rarityRgbByBaseName.isEmpty()) return;
            if (itemsIndexRefreshQueued) return;
            itemsIndexRefreshQueued = true;
            scheduler.execute(() -> {
                try {
                    refreshItemsIndex(false);
                } finally {
                    itemsIndexRefreshQueued = false;
                }
            });
        }

        private void refreshBazaarSnapshot(boolean force) {
            long now = System.currentTimeMillis();
            if (!force && (now - bazaarSnapshotFetchedAt) < SNAPSHOT_TTL_MS) return;

            try {
                // This endpoint is accessible without an API key; keep it keyless to avoid requiring user keys.
                String url = "https://api.hypixel.net/v2/skyblock/bazaar";
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().timeout(Duration.ofSeconds(12)).build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200) return;

                JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                if (root.has("success") && !root.get("success").getAsBoolean()) return;
                if (!root.has("products")) return;
                JsonObject products = root.getAsJsonObject("products");

                buyPriceByProductId.clear();
                sellPriceByProductId.clear();
                for (String productId : products.keySet()) {
                    JsonObject prod = products.getAsJsonObject(productId);
                    if (prod == null || !prod.has("quick_status")) continue;
                    JsonObject qs = prod.getAsJsonObject("quick_status");
                    double buy = qs.has("buyPrice") ? qs.get("buyPrice").getAsDouble() : 0.0;
                    double sell = qs.has("sellPrice") ? qs.get("sellPrice").getAsDouble() : 0.0;
                    buyPriceByProductId.put(productId, Math.max(0.0, buy));
                    sellPriceByProductId.put(productId, Math.max(0.0, sell));
                }

                bazaarSnapshotFetchedAt = now;
            } catch (IOException | InterruptedException e) {
                ModConstants.LOGGER.error("Bazaar snapshot fetch failed", e);
            } catch (Throwable t) {
                ModConstants.LOGGER.error("Bazaar snapshot fetch failed", t);
            }
        }

        private void refreshItemsIndex(boolean force) {
            long now = System.currentTimeMillis();
            if (!force && (now - itemsIndexFetchedAt) < ITEMS_TTL_MS && !rarityRgbByBaseName.isEmpty()) return;

            try {
                // This endpoint is accessible without an API key.
                String url = "https://api.hypixel.net/v2/resources/skyblock/items";
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().timeout(Duration.ofSeconds(12)).build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200) return;

                JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                if (root.has("success") && !root.get("success").getAsBoolean()) return;
                if (!root.has("items")) return;

                JsonArray items = root.getAsJsonArray("items");
                rarityRgbByBaseName.clear();
                rarityWeightByBaseName.clear();
                for (int i = 0; i < items.size(); i++) {
                    JsonObject obj = items.get(i).getAsJsonObject();
                    if (obj == null || !obj.has("name")) continue;
                    String name = obj.get("name").getAsString();
                    if (name == null || name.isBlank()) continue;
                    if (!(name.endsWith(" Shard") || name.endsWith(" Shards"))) continue;
                    String base = name.replaceFirst("(?i)\\s*Shards?$", "").trim();
                    if (base.isEmpty()) continue;

                    String tier = obj.has("tier") ? obj.get("tier").getAsString() : null;

                    Integer rgb = null;
                    if (obj.has("color")) {
                        try {
                            rgb = hypixelColorToRgb(obj.get("color").getAsString());
                        } catch (Throwable ignored) {}
                    }
                    if (rgb == null) rgb = tierToRgb(tier);
                    if (rgb == null) rgb = 0xFFFFFF;

                    rarityRgbByBaseName.put(base, rgb);
                    rarityWeightByBaseName.put(base, tierToWeight(tier));
                }

                itemsIndexFetchedAt = now;
            } catch (IOException | InterruptedException e) {
                ModConstants.LOGGER.error("SkyBlock items index fetch failed", e);
            } catch (Throwable t) {
                ModConstants.LOGGER.error("SkyBlock items index fetch failed", t);
            }
        }

        private String resolveBazaarProductId(String baseName) {
            if (baseName == null || baseName.isBlank()) return null;
            String cached = baseNameToProductId.get(baseName);
            if (cached != null) return cached;

            // Require a snapshot so we know the actual product ids that exist.
            ensureBazaarSnapshotFresh();
            if (buyPriceByProductId.isEmpty() && sellPriceByProductId.isEmpty()) {
                // snapshot not ready yet
                return null;
            }

            String token = baseName.toUpperCase().replaceAll("[^A-Z0-9]+", "_");
            String[] candidates = new String[]{
                    token + "_SHARD",
                    "SHARD_" + token,
                    token,
                    token + "_SHARDS"
            };

            for (String cand : candidates) {
                if (buyPriceByProductId.containsKey(cand) || sellPriceByProductId.containsKey(cand)) {
                    baseNameToProductId.put(baseName, cand);
                    return cand;
                }
            }

            // Fallback: scan for a shard product containing the token (rare, but helps with naming edge-cases).
            String best = null;
            for (String id : buyPriceByProductId.keySet()) {
                if (id == null) continue;
                if (!id.contains(token)) continue;
                if (id.contains("SHARD")) {
                    best = id;
                    break;
                }
                if (best == null) best = id;
            }
            if (best != null) {
                baseNameToProductId.put(baseName, best);
                return best;
            }

            long now = System.currentTimeMillis();
            Long last = missingIdLogTime.get(baseName);
            if (last == null || (now - last) > MISSING_ID_LOG_COOLDOWN_MS) {
                ModConstants.LOGGER.warn("Bazaar: no product id match for '{}'", baseName);
                missingIdLogTime.put(baseName, now);
            }

            return null;
        }

        private Integer tierToRgb(String tier) {
            if (tier == null) return null;
            return switch (tier.toUpperCase()) {
                case "COMMON" -> 0xFFFFFF;
                case "UNCOMMON" -> 0x55FF55;
                case "RARE" -> 0x5555FF;
                case "EPIC" -> 0xAA00AA;
                case "LEGENDARY" -> 0xFFAA00;
                case "MYTHIC" -> 0xFF55FF;
                case "DIVINE" -> 0x55FFFF;
                case "SPECIAL", "VERY_SPECIAL", "SUPREME" -> 0xFF5555;
                default -> 0xFFFFFF;
            };
        }

        private int tierToWeight(String tier) {
            if (tier == null) return 0;
            return switch (tier.toUpperCase()) {
                case "COMMON" -> 1;
                case "UNCOMMON" -> 2;
                case "RARE" -> 3;
                case "EPIC" -> 4;
                case "LEGENDARY" -> 5;
                case "MYTHIC" -> 6;
                case "DIVINE" -> 7;
                case "SPECIAL", "VERY_SPECIAL", "SUPREME" -> 8;
                default -> 0;
            };
        }

        private Integer hypixelColorToRgb(String raw) {
            if (raw == null) return null;
            String s = raw.trim();
            if (s.isEmpty()) return null;

            if (s.matches("(?i)^#?[0-9a-f]{6}$")) {
                String hex = s.startsWith("#") ? s.substring(1) : s;
                try {
                    return Integer.parseInt(hex, 16) & 0xFFFFFF;
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }

            return switch (s.toUpperCase().replace(' ', '_')) {
                case "WHITE" -> 0xFFFFFF;
                case "GREEN", "LIME" -> 0x55FF55;
                case "BLUE" -> 0x5555FF;
                case "PURPLE", "DARK_PURPLE" -> 0xAA00AA;
                case "GOLD", "ORANGE" -> 0xFFAA00;
                case "PINK", "LIGHT_PURPLE" -> 0xFF55FF;
                case "AQUA", "CYAN" -> 0x55FFFF;
                case "RED", "LIGHT_RED" -> 0xFF5555;
                case "DARK_RED" -> 0xAA0000;
                case "YELLOW" -> 0xFFFF55;
                case "GRAY", "GREY" -> 0xAAAAAA;
                case "DARK_GRAY", "DARK_GREY" -> 0x555555;
                default -> null;
            };
        }
    }
}
