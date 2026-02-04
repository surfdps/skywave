package org.wxter.skywave.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import com.mojang.blaze3d.opengl.GlStateManager;
import org.wxter.skywave.client.waypoints.CustomWaypoints;
import org.wxter.skywave.config.SkywaveConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CustomWaypointsScreen extends Screen {
    private static final int DEFAULT_COLOR = 0xFF00BFFF;

    private static final int[] COLOR_PALETTE = {
            0xFFFFFFFF, 0xFFBFBFBF, 0xFF7F7F7F, 0xFF000000,
            0xFFFF5555, 0xFFFFAA00, 0xFFFFFF55, 0xFF55FF55,
            0xFF00AA00, 0xFF00AAAA, 0xFF55FFFF, 0xFF5555FF,
            0xFFAA00AA, 0xFFFF55FF, 0xFFFFAAAA, 0xFFAA5500
    };

    private final Screen parent;

    private WaypointListWidget waypointList;
    private ButtonWidget presetDropdownButton;
    private boolean presetDropdownOpen = false;
    private int presetDropdownScroll = 0;
    private List<String> presetDropdownIds = List.of();
    private TextFieldWidget presetNameField;

    private TextFieldWidget nameField;
    private TextFieldWidget xField;
    private TextFieldWidget yField;
    private TextFieldWidget zField;
    private CyclingButtonWidget<Boolean> enabledToggle;
    private ButtonWidget sendAllButton;
    private ButtonWidget sendPartyButton;
    private boolean suppressEnabledToggleCallback = false;
    private final List<ColorSwatchWidget> colorSwatches = new ArrayList<>();
    private int selectedColorArgb = DEFAULT_COLOR;

    private String selectedWaypointId = null;
    private Text statusText = null;
    private long statusUntilMs = 0L;

    private int listX;
    private int listY;
    private int listWidth;
    private int listHeight;
    private int waypointListHeight;
    private int editorX;
    private int editorY;
    private int editorWidth;
    private int editorHeight;
    private boolean twoColumns;

    public CustomWaypointsScreen(Screen parent) {
        super(Text.literal("Custom Waypoints"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int padding = 12;
        int spacing = 10;
        int presetRowY = 34;
        int contentTop = 68;
        int bottomPadding = 14;

        this.twoColumns = this.width >= 540;
        int desiredEditorWidth = Math.max(240, this.width / 3);
        int rightPanelWidth = Math.min(300, desiredEditorWidth);

        if (twoColumns) {
            listX = padding;
            listY = contentTop;
            listWidth = Math.max(220, this.width - (padding * 2) - rightPanelWidth - spacing);
            listHeight = Math.max(90, this.height - contentTop - bottomPadding);

            editorX = listX + listWidth + spacing;
            editorY = contentTop;
            editorWidth = rightPanelWidth;
            editorHeight = listHeight;
        } else {
            listX = padding;
            listY = contentTop;
            listWidth = this.width - padding * 2;
            listHeight = Math.max(90, (this.height - contentTop - bottomPadding - spacing) / 2);

            editorX = padding;
            editorY = listY + listHeight + spacing;
            editorWidth = listWidth;
            editorHeight = Math.max(120, this.height - editorY - bottomPadding);
        }

        buildPresetRow(padding, presetRowY, spacing);

        int listActionsH = 20;
        int listActionsGap = 8;
        int reserved = listActionsGap + listActionsH;
        listHeight = Math.max(listHeight, 90 + reserved);
        waypointListHeight = Math.max(60, listHeight - reserved);

        waypointList = new WaypointListWidget(MinecraftClient.getInstance(), listWidth, waypointListHeight, listY, 28, this::onWaypointSelected);
        waypointList.position(listWidth, waypointListHeight, listX, listY);
        waypointList.setWaypoints(CustomWaypoints.activeWaypoints());
        addDrawableChild(waypointList);

        buildListActions(listX, listY + waypointListHeight + listActionsGap, listWidth, listActionsH, spacing);

        buildEditor(editorX, editorY, editorWidth, editorHeight, spacing);

        if (selectedWaypointId != null) {
            onWaypointSelected(CustomWaypoints.getWaypointById(selectedWaypointId));
        }
    }

    private void buildListActions(int x, int y, int width, int height, int spacing) {
        int halfW = Math.max(90, (width - spacing) / 2);
        int halfW2 = Math.max(90, width - halfW - spacing);

        sendAllButton = ButtonWidget.builder(Text.literal("Send All"), b -> sendSelectedWaypoint(false))
                .dimensions(x, y, halfW, height).build();
        sendPartyButton = ButtonWidget.builder(Text.literal("Send Party"), b -> sendSelectedWaypoint(true))
                .dimensions(x + halfW + spacing, y, halfW2, height).build();

        addDrawableChild(sendAllButton);
        addDrawableChild(sendPartyButton);
        updateShareButtonsState();
    }

    private void buildPresetRow(int padding, int y, int spacing) {
        SkywaveConfig.WaypointPreset activePreset = CustomWaypoints.activePreset();

        int buttonWidth = 70;
        int actionsWidth = (buttonWidth * 3) + (spacing * 2);

        int presetButtonWidth = Math.min(240, Math.max(160, (this.width - padding * 2 - actionsWidth - spacing) / 2));
        int presetNameWidth = Math.min(200, Math.max(120, this.width - padding * 2 - presetButtonWidth - actionsWidth - spacing * 3));

        presetDropdownIds = buildPresetIdList();
        presetDropdownButton = ButtonWidget.builder(presetDropdownButtonText(), b -> togglePresetDropdown())
                .dimensions(padding, y, presetButtonWidth, 20)
                .build();
        addDrawableChild(presetDropdownButton);

        int x = padding + presetButtonWidth + spacing;

        presetNameField = new TextFieldWidget(this.textRenderer, x, y, presetNameWidth, 20, Text.literal("Preset Name"));
        presetNameField.setMaxLength(32);
        presetNameField.setPlaceholder(Text.literal("Preset name"));
        presetNameField.setText(activePreset == null ? "" : activePreset.name);
        addDrawableChild(presetNameField);

        x += presetNameWidth + spacing;

        addDrawableChild(ButtonWidget.builder(Text.literal("Create"), b -> {
            CustomWaypoints.createPreset(presetNameField.getText());
            MinecraftClient.getInstance().setScreen(new CustomWaypointsScreen(parent));
        }).dimensions(x, y, buttonWidth, 20).build());
        x += buttonWidth + spacing;

        addDrawableChild(ButtonWidget.builder(Text.literal("Rename"), b -> {
            CustomWaypoints.renameActivePreset(presetNameField.getText());
            MinecraftClient.getInstance().setScreen(new CustomWaypointsScreen(parent));
        }).dimensions(x, y, buttonWidth, 20).build());
        x += buttonWidth + spacing;

        addDrawableChild(ButtonWidget.builder(Text.literal("Delete"), b -> {
            CustomWaypoints.deletePreset(CustomWaypoints.config().activePresetId);
            MinecraftClient.getInstance().setScreen(new CustomWaypointsScreen(parent));
        }).dimensions(x, y, buttonWidth, 20).build());
    }

    private List<String> buildPresetIdList() {
        List<SkywaveConfig.WaypointPreset> presets = CustomWaypoints.presets();
        List<String> ids = new ArrayList<>();
        if (presets != null) {
            for (SkywaveConfig.WaypointPreset preset : presets) {
                if (preset == null || preset.id == null || preset.id.isBlank()) continue;
                ids.add(preset.id);
            }
        }
        if (ids.isEmpty()) ids.add("default");
        return ids;
    }

    private Text presetDropdownButtonText() {
        String activeId = CustomWaypoints.config().activePresetId;
        SkywaveConfig.WaypointPreset preset = CustomWaypoints.getPresetById(activeId);
        String name = preset == null || preset.name == null || preset.name.isBlank() ? "Preset" : preset.name;
        String suffix = presetDropdownOpen ? " ▴" : " ▾";
        return Text.literal(name + suffix);
    }

    private void togglePresetDropdown() {
        presetDropdownOpen = !presetDropdownOpen;
        presetDropdownIds = buildPresetIdList();

        if (presetDropdownButton != null) {
            presetDropdownButton.setMessage(presetDropdownButtonText());
        }

        if (!presetDropdownOpen) return;

        int activeIndex = -1;
        String activeId = CustomWaypoints.config().activePresetId;
        for (int i = 0; i < presetDropdownIds.size(); i++) {
            if (activeId.equals(presetDropdownIds.get(i))) {
                activeIndex = i;
                break;
            }
        }

        int visibleRows = getPresetDropdownVisibleRows();
        int maxScroll = Math.max(0, presetDropdownIds.size() - visibleRows);
        if (activeIndex < 0) {
            presetDropdownScroll = 0;
        } else {
            presetDropdownScroll = MathHelper.clamp(activeIndex - 2, 0, maxScroll);
        }
    }

    private int getPresetDropdownItemHeight() {
        return 20;
    }

    private int getPresetDropdownVisibleRows() {
        if (presetDropdownButton == null) return 0;
        int itemHeight = getPresetDropdownItemHeight();
        int availableBelow = this.height - (presetDropdownButton.getY() + presetDropdownButton.getHeight()) - 12;
        int maxRowsBySpace = Math.max(3, availableBelow / itemHeight);
        int maxRows = Math.min(10, maxRowsBySpace);
        return Math.min(maxRows, Math.max(1, presetDropdownIds.size()));
    }

    private boolean isMouseOverPresetDropdown(double mouseX, double mouseY) {
        if (!presetDropdownOpen || presetDropdownButton == null) return false;
        int itemHeight = getPresetDropdownItemHeight();
        int visibleRows = getPresetDropdownVisibleRows();
        int x = presetDropdownButton.getX();
        int y = presetDropdownButton.getY() + presetDropdownButton.getHeight() + 2;
        int w = presetDropdownButton.getWidth();
        int h = visibleRows * itemHeight;
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private void selectPreset(String presetId) {
        CustomWaypoints.setActivePreset(presetId);
        presetDropdownOpen = false;
        presetDropdownScroll = 0;
        if (presetDropdownButton != null) {
            presetDropdownButton.setMessage(presetDropdownButtonText());
        }

        SkywaveConfig.WaypointPreset preset = CustomWaypoints.activePreset();
        if (presetNameField != null) {
            presetNameField.setText(preset == null ? "" : preset.name);
        }

        selectedWaypointId = null;
        clearWaypointEditor();
        refreshWaypointList();
    }

    private void buildEditor(int x, int y, int width, int height, int spacing) {
        int fieldHeight = 20;
        int cursorY = y + 18;

        nameField = new TextFieldWidget(this.textRenderer, x, cursorY, width, fieldHeight, Text.literal("Waypoint Name"));
        nameField.setMaxLength(48);
        nameField.setPlaceholder(Text.literal("Waypoint name (e.g. Start Dig)"));
        addDrawableChild(nameField);

        cursorY += 30;

        int coordWidth = Math.max(60, (width - spacing * 2) / 3);
        xField = new TextFieldWidget(this.textRenderer, x, cursorY, coordWidth, fieldHeight, Text.literal("X"));
        yField = new TextFieldWidget(this.textRenderer, x + coordWidth + spacing, cursorY, coordWidth, fieldHeight, Text.literal("Y"));
        zField = new TextFieldWidget(this.textRenderer, x + (coordWidth + spacing) * 2, cursorY, coordWidth, fieldHeight, Text.literal("Z"));
        xField.setPlaceholder(Text.literal("X"));
        yField.setPlaceholder(Text.literal("Y"));
        zField.setPlaceholder(Text.literal("Z"));
        xField.setTextPredicate(CustomWaypointsScreen::isIntegerInput);
        yField.setTextPredicate(CustomWaypointsScreen::isIntegerInput);
        zField.setTextPredicate(CustomWaypointsScreen::isIntegerInput);
        addDrawableChild(xField);
        addDrawableChild(yField);
        addDrawableChild(zField);

        cursorY += 30;

        int toggleWidth = Math.min(160, width);
        int toggleX = x + Math.max(0, (width - toggleWidth) / 2);
        enabledToggle = CyclingButtonWidget.onOffBuilder(Text.literal("True"), Text.literal("False"))
                .initially(true)
                .build(toggleX, cursorY, toggleWidth, 20, Text.literal("Enabled"), (btn, value) -> onEnabledToggled(value));
        addDrawableChild(enabledToggle);

        cursorY += 34;

        buildColorPalette(x, cursorY, width, spacing);

        int buttonRows = 2;
        int buttonsTop = y + height - (fieldHeight * buttonRows) - (spacing * (buttonRows - 1));
        if (!twoColumns) {
            buttonsTop = Math.min(buttonsTop, cursorY + 52);
        }

        int buttonWidth = Math.max(72, (width - spacing * 2) / 3);
        int row1Y = buttonsTop;
        int row2Y = buttonsTop + fieldHeight + spacing;

        addDrawableChild(ButtonWidget.builder(Text.literal("Add"), b -> onAdd())
                .dimensions(x, row1Y, buttonWidth, fieldHeight).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Update"), b -> onUpdate())
                .dimensions(x + buttonWidth + spacing, row1Y, buttonWidth, fieldHeight).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Delete"), b -> onDelete())
                .dimensions(x + (buttonWidth + spacing) * 2, row1Y, buttonWidth, fieldHeight).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Clear"), b -> clearSelectionAndFields())
                .dimensions(x, row2Y, buttonWidth, fieldHeight).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Use Pos"), b -> fillFromPlayerPos())
                .dimensions(x + buttonWidth + spacing, row2Y, buttonWidth, fieldHeight).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
                .dimensions(x + (buttonWidth + spacing) * 2, row2Y, buttonWidth, fieldHeight).build());
    }

    private void buildColorPalette(int x, int y, int width, int spacing) {
        colorSwatches.clear();

        int swatchSize = 16;
        int gap = 4;

        int columns = MathHelper.clamp((width + gap) / (swatchSize + gap), 4, 8);
        int rows = (int) Math.ceil(COLOR_PALETTE.length / (double) columns);

        int paletteWidth = (columns * swatchSize) + ((columns - 1) * gap);
        int startX = x + Math.max(0, (width - paletteWidth) / 2);

        for (int i = 0; i < COLOR_PALETTE.length; i++) {
            int col = i % columns;
            int row = i / columns;
            int swatchX = startX + col * (swatchSize + gap);
            int swatchY = y + row * (swatchSize + gap);
            ColorSwatchWidget swatch = new ColorSwatchWidget(swatchX, swatchY, swatchSize, COLOR_PALETTE[i]);
            colorSwatches.add(swatch);
            addDrawableChild(swatch);
        }
    }

    private Text presetText(String id) {
        SkywaveConfig.WaypointPreset preset = CustomWaypoints.getPresetById(id);
        String name = preset == null ? "Preset" : preset.name;
        return Text.literal(name == null || name.isBlank() ? "Preset" : name);
    }

    private void onWaypointSelected(SkywaveConfig.WaypointEntry wp) {
        if (wp == null) return;
        selectedWaypointId = wp.id;

        nameField.setText(wp.name == null ? "" : wp.name);
        xField.setText(String.valueOf(wp.x));
        yField.setText(String.valueOf(wp.y));
        zField.setText(String.valueOf(wp.z));
        selectedColorArgb = wp.color;
        suppressEnabledToggleCallback = true;
        enabledToggle.setValue(wp.enabled);
        suppressEnabledToggleCallback = false;
        updateShareButtonsState();
    }

    private void clearWaypointEditor() {
        nameField.setText("");
        xField.setText("");
        yField.setText("");
        zField.setText("");
        selectedColorArgb = DEFAULT_COLOR;
        suppressEnabledToggleCallback = true;
        enabledToggle.setValue(true);
        suppressEnabledToggleCallback = false;
    }

    private void onEnabledToggled(boolean enabled) {
        if (suppressEnabledToggleCallback) return;
        if (selectedWaypointId == null) {
            setStatus(Text.literal("Select a waypoint first.").formatted(Formatting.GRAY));
            return;
        }

        boolean ok = CustomWaypoints.setWaypointEnabled(selectedWaypointId, enabled);
        if (!ok) {
            setStatus(Text.literal("Waypoint not found.").formatted(Formatting.RED));
            return;
        }

        refreshWaypointList();
    }

    private void clearSelectionAndFields() {
        selectedWaypointId = null;
        clearWaypointEditor();
        if (waypointList != null) {
            waypointList.setSelected(null);
        }
        updateShareButtonsState();
    }

    private void fillFromPlayerPos() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;
        BlockPos pos = mc.player.getBlockPos();
        xField.setText(String.valueOf(pos.getX()));
        yField.setText(String.valueOf(pos.getY()));
        zField.setText(String.valueOf(pos.getZ()));
    }

    private void onAdd() {
        ParsedInput input = parseInput();
        if (input == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        String dim = mc == null || mc.world == null ? "minecraft:overworld" : mc.world.getRegistryKey().getValue().toString();

        SkywaveConfig.WaypointEntry wp = CustomWaypoints.addWaypoint(input.name, input.x, input.y, input.z, selectedColorArgb, dim);
        setStatus(Text.literal("Waypoint added.").formatted(Formatting.GREEN));
        refreshWaypointList();
        selectWaypoint(wp.id);
    }

    private void onUpdate() {
        if (selectedWaypointId == null) {
            setStatus(Text.literal("Select a waypoint to update.").formatted(Formatting.RED));
            return;
        }
        ParsedInput input = parseInput();
        if (input == null) return;

        SkywaveConfig.WaypointEntry existing = CustomWaypoints.getWaypointById(selectedWaypointId);
        String dim = existing == null ? null : existing.dimension;
        boolean ok = CustomWaypoints.updateWaypoint(selectedWaypointId, input.name, input.x, input.y, input.z, selectedColorArgb, dim, enabledToggle.getValue());
        if (!ok) {
            setStatus(Text.literal("Waypoint not found.").formatted(Formatting.RED));
            return;
        }
        setStatus(Text.literal("Waypoint updated.").formatted(Formatting.GREEN));
        refreshWaypointList();
        selectWaypoint(selectedWaypointId);
    }

    private void onDelete() {
        if (selectedWaypointId == null) {
            setStatus(Text.literal("Select a waypoint to delete.").formatted(Formatting.RED));
            return;
        }
        CustomWaypoints.deleteWaypoint(selectedWaypointId);
        setStatus(Text.literal("Waypoint deleted.").formatted(Formatting.YELLOW));
        selectedWaypointId = null;
        clearWaypointEditor();
        refreshWaypointList();
        updateShareButtonsState();
    }

    private void updateShareButtonsState() {
        boolean hasSelection = selectedWaypointId != null && !selectedWaypointId.isBlank();
        if (sendAllButton != null) sendAllButton.active = hasSelection;
        if (sendPartyButton != null) sendPartyButton.active = hasSelection;
    }

    private void sendSelectedWaypoint(boolean party) {
        if (selectedWaypointId == null) {
            setStatus(Text.literal("Select a waypoint first.").formatted(Formatting.RED));
            return;
        }

        SkywaveConfig.WaypointEntry wp = CustomWaypoints.getWaypointById(selectedWaypointId);
        if (wp == null) {
            setStatus(Text.literal("Waypoint not found.").formatted(Formatting.RED));
            return;
        }

        String name = wp.name == null ? "Waypoint" : wp.name.trim();
        if (name.isEmpty()) name = "Waypoint";

        String msg = "x: " + wp.x + " y: " + wp.y + " z: " + wp.z + " | " + name;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        var nh = client.getNetworkHandler();
        if (nh == null) {
            setStatus(Text.literal("Not connected.").formatted(Formatting.RED));
            return;
        }

        if (party) {
            nh.sendChatCommand("pc " + msg);
            setStatus(Text.literal("Sent to party chat.").formatted(Formatting.GREEN));
        } else {
            nh.sendChatMessage(msg);
            setStatus(Text.literal("Sent to chat.").formatted(Formatting.GREEN));
        }
    }

    private void selectWaypoint(String waypointId) {
        if (waypointList == null || waypointId == null) return;
        waypointList.selectById(waypointId);
    }

    private void refreshWaypointList() {
        if (waypointList != null) {
            waypointList.setWaypoints(CustomWaypoints.activeWaypoints());
        }
    }

    private ParsedInput parseInput() {
        String name = nameField.getText();
        Integer x = parseInt(xField.getText());
        Integer y = parseInt(yField.getText());
        Integer z = parseInt(zField.getText());
        if (x == null || y == null || z == null) {
            setStatus(Text.literal("Invalid coordinates.").formatted(Formatting.RED));
            return null;
        }
        return new ParsedInput(name, x, y, z);
    }

    private static Integer parseInt(String text) {
        try {
            String trimmed = text == null ? "" : text.trim();
            if (trimmed.isEmpty() || trimmed.equals("-")) return null;
            return Integer.parseInt(trimmed);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isIntegerInput(String text) {
        if (text == null || text.isEmpty()) return true;
        return text.matches("-?\\d*");
    }

    private void setStatus(Text text) {
        this.statusText = text;
        this.statusUntilMs = System.currentTimeMillis() + 3_000L;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 14, 0xFFFFFF);

        ctx.drawTextWithShadow(this.textRenderer, Text.literal("Waypoints").formatted(Formatting.AQUA, Formatting.BOLD), listX, listY - 12, 0xFFFFFF);
        ctx.drawTextWithShadow(this.textRenderer, Text.literal("Editor").formatted(Formatting.AQUA, Formatting.BOLD), editorX, editorY - 12, 0xFFFFFF);

        ctx.drawStrokedRectangle(listX - 1, listY - 1, listWidth + 2, listHeight + 2, 0x60FFFFFF);
        ctx.drawStrokedRectangle(editorX - 1, editorY - 1, editorWidth + 2, editorHeight + 2, 0x60FFFFFF);

        int editorTextY = editorY + 2;
        String selected = selectedWaypointId == null ? "No waypoint selected" : "Selected: " + safeShortName(CustomWaypoints.getWaypointById(selectedWaypointId));
        ctx.drawTextWithShadow(this.textRenderer, Text.literal(selected).formatted(Formatting.GRAY), editorX, editorTextY, 0xA0A0A0);

        String colorHex = String.format(Locale.ROOT, "#%06X", (selectedColorArgb & 0xFFFFFF));
        ctx.drawTextWithShadow(this.textRenderer, Text.literal("Color: " + colorHex).formatted(Formatting.GRAY), editorX, editorTextY + 12, 0xA0A0A0);

        if (statusText != null && System.currentTimeMillis() <= statusUntilMs) {
            ctx.drawCenteredTextWithShadow(this.textRenderer, statusText, this.width / 2, this.height - 18, 0xFFFFFF);
        }

        super.render(ctx, mouseX, mouseY, delta);

        if (presetDropdownOpen && presetDropdownButton != null) {
            int itemHeight = getPresetDropdownItemHeight();
            int visibleRows = getPresetDropdownVisibleRows();
            int listX = presetDropdownButton.getX();
            int listY = presetDropdownButton.getY() + presetDropdownButton.getHeight() + 2;
            int listW = presetDropdownButton.getWidth();
            int listH = visibleRows * itemHeight;

            GlStateManager._disableDepthTest();
            try {
                GlStateManager._enableBlend();

                ctx.fill(listX, listY, listX + listW, listY + listH, 0xFF0F0F0F);
                ctx.drawStrokedRectangle(listX, listY, listW, listH, 0xE0FFFFFF);

                String activeId = CustomWaypoints.config().activePresetId;
                int maxScroll = Math.max(0, presetDropdownIds.size() - visibleRows);
                presetDropdownScroll = MathHelper.clamp(presetDropdownScroll, 0, maxScroll);

                for (int row = 0; row < visibleRows; row++) {
                    int idx = presetDropdownScroll + row;
                    if (idx >= presetDropdownIds.size()) break;
                    String id = presetDropdownIds.get(idx);

                    int rowY = listY + row * itemHeight;
                    boolean hovered = mouseX >= listX && mouseX < listX + listW && mouseY >= rowY && mouseY < rowY + itemHeight;
                    boolean active = id != null && id.equals(activeId);

                    int baseRow = (row % 2 == 0) ? 0xFF151515 : 0xFF121212;
                    ctx.fill(listX + 1, rowY, listX + listW - 1, rowY + itemHeight, baseRow);

                    if (active) {
                        ctx.fill(listX + 1, rowY, listX + listW - 1, rowY + itemHeight, 0xA000BFFF);
                    }
                    if (hovered) {
                        ctx.fill(listX + 1, rowY, listX + listW - 1, rowY + itemHeight, 0x40FFFFFF);
                    }
                }

                if (maxScroll > 0) {
                    int barX = listX + listW - 5;
                    ctx.fill(barX, listY + 1, barX + 4, listY + listH - 1, 0xFF1C1C1C);

                    int thumbMinH = 10;
                    int thumbH = Math.max(thumbMinH, (int) Math.floor((visibleRows / (double) presetDropdownIds.size()) * (listH - 2)));
                    int thumbTravel = (listH - 2) - thumbH;
                    int thumbY = listY + 1 + (int) Math.round((presetDropdownScroll / (double) maxScroll) * thumbTravel);
                    ctx.fill(barX, thumbY, barX + 4, thumbY + thumbH, 0xE0FFFFFF);
                }

                for (int row = 0; row < visibleRows; row++) {
                    int idx = presetDropdownScroll + row;
                    if (idx >= presetDropdownIds.size()) break;
                    String id = presetDropdownIds.get(idx);

                    int rowY = listY + row * itemHeight;
                    boolean active = id != null && id.equals(activeId);

                    Text label = presetText(id);
                    String str = label.getString();
                    if (active) str = "> " + str;
                    String trimmed = this.textRenderer.trimToWidth(str, listW - 10);
                    ctx.drawTextWithShadow(this.textRenderer, trimmed, listX + 5, rowY + 6, active ? 0xFFFFFFFF : 0xFFE6E6E6);
                }
            } finally {
                GlStateManager._enableDepthTest();
            }
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubleClick) {
        double mouseX = click.x();
        double mouseY = click.y();

        if (presetDropdownOpen) {
            if (isMouseOverPresetDropdown(mouseX, mouseY)) {
                int itemHeight = getPresetDropdownItemHeight();
                int visibleRows = getPresetDropdownVisibleRows();
                int listY = presetDropdownButton.getY() + presetDropdownButton.getHeight() + 2;
                int row = (int) ((mouseY - listY) / itemHeight);
                int idx = presetDropdownScroll + row;
                if (idx >= 0 && idx < presetDropdownIds.size()) {
                    selectPreset(presetDropdownIds.get(idx));
                } else {
                    presetDropdownOpen = false;
                }
                return true;
            }

            if (presetDropdownButton != null && presetDropdownButton.isMouseOver(mouseX, mouseY)) {
                return super.mouseClicked(click, doubleClick);
            }

            presetDropdownOpen = false;
            if (presetDropdownButton != null) {
                presetDropdownButton.setMessage(presetDropdownButtonText());
            }
            return true;
        }

        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (presetDropdownOpen && (isMouseOverPresetDropdown(mouseX, mouseY) || (presetDropdownButton != null && presetDropdownButton.isMouseOver(mouseX, mouseY)))) {
            int visibleRows = getPresetDropdownVisibleRows();
            int maxScroll = Math.max(0, presetDropdownIds.size() - visibleRows);
            if (verticalAmount < 0) {
                presetDropdownScroll = MathHelper.clamp(presetDropdownScroll + 1, 0, maxScroll);
            } else if (verticalAmount > 0) {
                presetDropdownScroll = MathHelper.clamp(presetDropdownScroll - 1, 0, maxScroll);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        if (presetDropdownOpen && input.key() == 256) { // GLFW_KEY_ESCAPE
            presetDropdownOpen = false;
            if (presetDropdownButton != null) {
                presetDropdownButton.setMessage(presetDropdownButtonText());
            }
            return true;
        }
        return super.keyPressed(input);
    }

    private static String safeShortName(SkywaveConfig.WaypointEntry wp) {
        if (wp == null) return "Waypoint";
        String name = wp.name == null ? "Waypoint" : wp.name.trim();
        if (name.isEmpty()) name = "Waypoint";
        if (name.length() > 22) name = name.substring(0, 22) + "...";
        return name;
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    private record ParsedInput(String name, int x, int y, int z) {}

    private final class ColorSwatchWidget extends ClickableWidget {
        private final int argb;

        private ColorSwatchWidget(int x, int y, int size, int argb) {
            super(x, y, size, size, Text.empty());
            this.argb = argb;
        }

        @Override
        protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
            ctx.fill(getX(), getY(), getX() + width, getY() + height, argb);

            boolean selected = (argb == selectedColorArgb);
            int borderColor;
            if (selected) {
                borderColor = 0xFFFFFFFF;
            } else if (this.hovered) {
                borderColor = 0xFFBFBFBF;
            } else {
                borderColor = 0xFF000000;
            }

            ctx.drawStrokedRectangle(getX(), getY(), width, height, borderColor);
            if (selected) {
                ctx.drawStrokedRectangle(getX() + 1, getY() + 1, width - 2, height - 2, borderColor);
            }
        }

        @Override
        public void onClick(net.minecraft.client.gui.Click click, boolean doubleClick) {
            selectedColorArgb = argb;
        }

        @Override
        protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {
        }
    }

    private static final class WaypointListWidget extends AlwaysSelectedEntryListWidget<WaypointListWidget.Entry> {
        private final java.util.function.Consumer<SkywaveConfig.WaypointEntry> onSelect;

        private WaypointListWidget(MinecraftClient client, int width, int height, int y, int itemHeight,
                                   java.util.function.Consumer<SkywaveConfig.WaypointEntry> onSelect) {
            super(client, width, height, y, itemHeight);
            this.onSelect = onSelect;
        }

        public void setWaypoints(List<SkywaveConfig.WaypointEntry> waypoints) {
            clearEntries();
            if (waypoints == null) return;
            for (SkywaveConfig.WaypointEntry wp : waypoints) {
                if (wp == null) continue;
                addEntry(new Entry(wp));
            }
        }

        public void selectById(String waypointId) {
            if (waypointId == null || waypointId.isBlank()) return;
            for (Entry entry : children()) {
                if (entry == null || entry.waypoint == null) continue;
                if (waypointId.equals(entry.waypoint.id)) {
                    setSelected(entry);
                    return;
                }
            }
        }

        @Override
        protected void drawMenuListBackground(DrawContext ctx) {
            ctx.fill(getX(), getY(), getRight(), getBottom(), 0x55000000);
        }

        @Override
        protected void drawHeaderAndFooterSeparators(DrawContext ctx) {
        }

        @Override
        public void setSelected(Entry entry) {
            super.setSelected(entry);
            if (entry != null && onSelect != null) {
                onSelect.accept(entry.waypoint);
            }
        }

        static final class Entry extends AlwaysSelectedEntryListWidget.Entry<Entry> {
            private final SkywaveConfig.WaypointEntry waypoint;

            private Entry(SkywaveConfig.WaypointEntry waypoint) {
                this.waypoint = waypoint;
            }

            @Override
            public Text getNarration() {
                return Text.literal(waypoint.name == null ? "Waypoint" : waypoint.name);
            }

            @Override
            public void render(DrawContext ctx, int mouseX, int mouseY, boolean hovered, float tickDelta) {
                int x = getContentX();
                int y = getContentY();
                String name = waypoint.name == null ? "Waypoint" : waypoint.name.trim();
                if (name.isEmpty()) name = "Waypoint";
                String coords = waypoint.x + ", " + waypoint.y + ", " + waypoint.z;

                if (hovered) {
                    ctx.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x22FFFFFF);
                }

                int nameColor = waypoint.enabled ? (waypoint.color | 0xFF000000) : 0xFF6A6A6A;
                ctx.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, name, x + 2, y + 4, nameColor);
                ctx.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, coords, x + 2, y + 16, 0xA0A0A0);
            }

            @Override
            public void forEachChild(java.util.function.Consumer<ClickableWidget> consumer) {
            }
        }
    }
}
