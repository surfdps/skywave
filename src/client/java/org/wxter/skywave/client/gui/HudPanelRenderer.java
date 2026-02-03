package org.wxter.skywave.client.gui;

import net.minecraft.client.gui.DrawContext;

public final class HudPanelRenderer {
    private HudPanelRenderer() {}

    // Subtle translucent black fill + barely visible dark gray outline
    private static final int FILL_COLOR = 0x60000000;
    private static final int OUTLINE_COLOR = 0x40202020;

    /**
     * Draws a slightly rounded panel by cutting 1px corners (fast, no textures).
     * Coordinates follow DrawContext#fill convention: (x1,y1) inclusive, (x2,y2) exclusive.
     */
    public static void drawRoundedPanel(DrawContext ctx, int x1, int y1, int x2, int y2) {
        if (ctx == null) return;
        if (x2 <= x1 + 2 || y2 <= y1 + 2) return;

        int r = 1; // "slight" rounding

        // Fill (leave 1px corner cut)
        ctx.fill(x1 + r, y1, x2 - r, y2, FILL_COLOR);
        ctx.fill(x1, y1 + r, x1 + r, y2 - r, FILL_COLOR);
        ctx.fill(x2 - r, y1 + r, x2, y2 - r, FILL_COLOR);

        // Outline (thin)
        ctx.fill(x1 + r, y1, x2 - r, y1 + 1, OUTLINE_COLOR); // top
        ctx.fill(x1 + r, y2 - 1, x2 - r, y2, OUTLINE_COLOR); // bottom
        ctx.fill(x1, y1 + r, x1 + 1, y2 - r, OUTLINE_COLOR); // left
        ctx.fill(x2 - 1, y1 + r, x2, y2 - r, OUTLINE_COLOR); // right
    }
}

