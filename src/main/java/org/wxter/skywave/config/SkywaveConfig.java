package org.wxter.skywave.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class SkywaveConfig {

    public enum RainReminderType {
        CHAT,      // отправлять в чат
        ONSCREEN   // отображать крупным текстом сверху
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("skywave.json");

    private static SkywaveConfig INSTANCE;

    // ===== НАСТРОЙКИ =====
    public boolean rainReminderEnabled = true;
    public boolean rainReminderSound = true;

    public RainReminderType rainReminderType = RainReminderType.CHAT;

    public boolean nightSquidHighlight = true;

    // ARGB (int) — по умолчанию голубой с полной прозрачностью (0xFF00BFFF)
    // Сохраняется в JSON как число.
    public int nightSquidColor = 0xFF00BFFF;
    // ======================

    public static SkywaveConfig get() {
        if (INSTANCE == null) load();
        return INSTANCE;
    }

    public static void load() {
        if (Files.exists(FILE)) {
            try (Reader reader = Files.newBufferedReader(FILE)) {
                INSTANCE = GSON.fromJson(reader, SkywaveConfig.class);
            } catch (IOException e) {
                e.printStackTrace();
                INSTANCE = new SkywaveConfig();
            }
        } else {
            INSTANCE = new SkywaveConfig();
            save();
        }
    }

    public static void save() {
        try {
            Path dir = FILE.getParent();
            if (dir != null && !Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            try (Writer writer = Files.newBufferedWriter(FILE)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}