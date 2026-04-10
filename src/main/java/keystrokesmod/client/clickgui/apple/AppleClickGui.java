package keystrokesmod.client.clickgui.apple;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import keystrokesmod.client.main.Raven;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.Module.ModuleCategory;
import keystrokesmod.client.module.modules.client.GuiModule;
import keystrokesmod.client.utils.CoolDown;
import keystrokesmod.client.utils.RenderUtils;
import keystrokesmod.client.utils.Utils;
import keystrokesmod.client.utils.font.FontUtil;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;

public class AppleClickGui extends GuiScreen {

    // ── layout ─────────────────────────────────────────────────
    static final int WIN_W     = 340;
    static final int WIN_H     = 220;
    static final int SIDEBAR_W = 72;   // narrower, icon-focused sidebar

    // ── new palette — #1a1a1a bg, #4CAF50 green, gold favorites ──
    public static final int C_BG          = 0xFF1A1A1A;
    public static final int C_SIDEBAR     = 0xFF141414; // darker sidebar panel
    public static final int C_SEP         = 0xFF2A2A2A; // subtle dividers
    public static final int C_HOVER       = 0x18FFFFFF;
    public static final int C_SELECTED    = 0xFF4CAF50; // green pill highlight for active cat
    public static final int C_CARD        = 0xFF222222; // module row bg
    public static final int C_CARD_ON     = 0xFF1B3A1B; // dark green tint for enabled modules
    public static final int C_TEXT        = 0xFFE0E0E0;
    public static final int C_TEXT_SEC    = 0xFF808080; // gray description text
    public static final int C_ACCENT      = 0xFF4CAF50; // green accent
    public static final int C_ON          = 0xFF4CAF50; // toggle on = green
    public static final int C_OFF         = 0xFF3A3A3A; // toggle off = gray
    public static final int C_THUMB       = 0xFFFFFFFF;
    public static final int C_SLIDER_FILL = 0xFF4CAF50; // green slider fill
    public static final int C_SLIDER_BG   = 0xFF3A3A3A; // slider track bg
    public static final int C_FAVORITE    = 0xFFFFD700; // gold star
    public static final int C_FAVORITE_OFF = 0xFF555555; // unfavorited star
    public static final int C_DOTS        = 0xFF888888; // three-dot expand button
    public static final int C_DIVIDER     = 0xFF2A2A2A; // thin divider between modules
    public static final int C_COUNT_BG    = 0xFF4CAF50; // badge bg for enabled count
    public static final int C_COUNT_TEXT  = 0xFFFFFFFF; // badge text

    public static int mouseX, mouseY;

    private final List<ModuleCategory> categories = new ArrayList<>();
    private ModuleCategory activeCategory;
    private final List<AppleModuleRow> rows = new ArrayList<>();

    // ── favorites ──
    public static final Set<String> favorites = new HashSet<>();

    private int scrollOffset = 0;
    private final CoolDown openAnim = new CoolDown(200);

    // ── category icons (simple Unicode glyphs) ──
    private static final String[] CAT_ICONS = {
        "\u2694", // ⚔ Combat
        "\u27A4", // ➤ Movement
        "\u263A", // ☺ Player
        "\u25C9", // ◉ Render
        "\u2699", // ⚙ Other
        "\u2606"  // ☆ Client
    };

    public AppleClickGui() {
        for (ModuleCategory cat : ModuleCategory.values()) {
            if (cat == ModuleCategory.category) continue;
            if (cat == ModuleCategory.config) continue;
            if (!Raven.moduleManager.getModulesInCategory(cat).isEmpty())
                categories.add(cat);
        }
        if (!categories.isEmpty())
            setActiveCategory(categories.get(0));
    }

    public void open() {
        openAnim.setCooldown(200);
        openAnim.start();
    }

    private void setActiveCategory(ModuleCategory cat) {
        activeCategory = cat;
        scrollOffset = 0;
        rows.clear();
        for (Module mod : Raven.moduleManager.getModulesInCategory(cat)) {
            if (mod instanceof keystrokesmod.client.module.GuiModule
                    && ((keystrokesmod.client.module.GuiModule) mod).getGuiCategory() == ModuleCategory.config)
                continue;
            rows.add(new AppleModuleRow(mod));
        }
    }

    private int winX() { return (width  - WIN_W) / 2; }
    private int winY() { return (height - WIN_H) / 2; }

    /** Count enabled modules in a category */
    private int countEnabled(ModuleCategory cat) {
        int count = 0;
        for (Module m : Raven.moduleManager.getModulesInCategory(cat)) {
            if (m.isEnabled()) count++;
        }
        return count;
    }

    // ── Favorites helpers ──
    public static boolean isFavorite(String moduleName) {
        return favorites.contains(moduleName);
    }

    public static void toggleFavorite(String moduleName) {
        if (favorites.contains(moduleName)) favorites.remove(moduleName);
        else favorites.add(moduleName);
    }

    // ── GuiScreen ──────────────────────────────────────────────

    @Override
    public void initGui() {
        super.initGui();
        open();
    }

    @Override
    public void drawScreen(int mx, int my, float partial) {
        super.drawScreen(mx, my, partial);
        mouseX = mx;
        mouseY = my;

        // dim overlay
        drawRect(0, 0, width, height, 0xA0000000);

        float t = Utils.Client.smoothPercent(openAnim.getElapsedTime() / (float) openAnim.getCooldownTime());
        int wx = winX(), wy = winY();

        // scale-in animation from center
        GL11.glPushMatrix();
        GL11.glTranslatef(wx + WIN_W / 2f, wy + WIN_H / 2f, 0f);
        GL11.glScalef(t, t, 1f);
        GL11.glTranslatef(-(wx + WIN_W / 2f), -(wy + WIN_H / 2f), 0f);

        drawWindowFrame(wx, wy);
        drawSidebar(mx, my, wx, wy);
        drawModulePanel(mx, my, wx, wy);

        GL11.glPopMatrix();
    }

    private void drawWindowFrame(int wx, int wy) {
        // Main window background — #1A1A1A, 10px radius
        RenderUtils.drawRoundedRect(wx, wy, wx + WIN_W, wy + WIN_H, 10, C_BG);
        // 1px border subtle
        RenderUtils.drawRoundedOutline(wx, wy, wx + WIN_W, wy + WIN_H, 10, 1, C_SEP);
        // Sidebar panel background — darker
        RenderUtils.drawRoundedRect(wx, wy, wx + SIDEBAR_W, wy + WIN_H, 10, C_SIDEBAR,
                new boolean[]{true, true, false, false});
        // Vertical divider
        Gui.drawRect(wx + SIDEBAR_W, wy + 8, wx + SIDEBAR_W + 1, wy + WIN_H - 8, C_SEP);
    }

    private int sidebarItemSpacing() {
        return categories.isEmpty() ? 28 : Math.min(28, (WIN_H - 24) / categories.size());
    }

    private void drawSidebar(int mx, int my, int wx, int wy) {
        int spacing = sidebarItemSpacing();
        int sy = wy + 14;
        int catIdx = 0;
        for (ModuleCategory cat : categories) {
            boolean active = cat == activeCategory;
            int itemLeft = wx + 6;
            int itemRight = wx + SIDEBAR_W - 6;
            int itemTop = sy - 3;
            int itemBottom = sy + 19;
            boolean hover = !active && mx >= itemLeft && mx <= itemRight && my >= itemTop && my <= itemBottom;

            // Selected: green pill bg, Hover: subtle highlight
            if (active)
                RenderUtils.drawRoundedRect(itemLeft, itemTop, itemRight, itemBottom, 8, C_SELECTED);
            else if (hover)
                RenderUtils.drawRoundedRect(itemLeft, itemTop, itemRight, itemBottom, 8, C_HOVER);

            // Icon — use simple text glyph
            String icon = catIdx < CAT_ICONS.length ? CAT_ICONS[catIdx] : "\u2022";
            int textColor = active ? 0xFFFFFFFF : C_TEXT_SEC;
            FontUtil.poppinsRegular.drawSmoothString(icon, wx + 10,
                    sy + (16 - FontUtil.poppinsRegular.getHeight()) / 2f, textColor);

            // Category name — smaller, right of icon
            String name = cat.getName();
            FontUtil.poppinsRegular.drawSmoothString(name, wx + 22,
                    sy + (16 - FontUtil.poppinsRegular.getHeight()) / 2f, textColor);

            // Enabled count badge
            int enabledCount = countEnabled(cat);
            if (enabledCount > 0) {
                String countStr = String.valueOf(enabledCount);
                int badgeW = Math.max(12, (int) FontUtil.poppinsRegular.getStringWidth(countStr) + 6);
                int badgeX = itemRight - badgeW - 2;
                int badgeY = sy + 1;
                RenderUtils.drawRoundedRect(badgeX, badgeY, badgeX + badgeW, badgeY + 12, 6,
                        active ? 0x44FFFFFF : C_COUNT_BG);
                FontUtil.poppinsRegular.drawSmoothString(countStr,
                        badgeX + (badgeW - (float) FontUtil.poppinsRegular.getStringWidth(countStr)) / 2f,
                        badgeY + (12 - FontUtil.poppinsRegular.getHeight()) / 2f, C_COUNT_TEXT);
            }

            sy += spacing;
            catIdx++;
        }
    }

    private void drawModulePanel(int mx, int my, int wx, int wy) {
        int panelX = wx + SIDEBAR_W + 1;
        int panelY = wy;
        int panelW = WIN_W - SIDEBAR_W - 1;
        int panelH = WIN_H;

        ScaledResolution sr = new ScaledResolution(Raven.mc);
        int scale = sr.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(
            panelX * scale,
            Raven.mc.displayHeight - (panelY + panelH) * scale,
            panelW * scale,
            panelH * scale
        );

        int rowY = panelY + 8 + scrollOffset;
        boolean first = true;
        for (AppleModuleRow row : rows) {
            // Draw thin divider between module rows (not before first)
            if (!first) {
                Gui.drawRect(panelX + 12, rowY, panelX + panelW - 12, rowY + 1, C_DIVIDER);
                rowY += 1;
            }
            first = false;

            row.setPosition(panelX + 8, rowY, panelW - 16);
            row.draw(mx, my);
            rowY += row.getTotalHeight();
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    @Override
    public void mouseClicked(int mx, int my, int button) throws IOException {
        int wx = winX(), wy = winY();

        // Sidebar category selection
        int spacing = sidebarItemSpacing();
        int sy = wy + 14;
        for (ModuleCategory cat : categories) {
            int itemLeft = wx + 6;
            int itemRight = wx + SIDEBAR_W - 6;
            if (mx >= itemLeft && mx <= itemRight && my >= sy - 3 && my <= sy + 19) {
                setActiveCategory(cat);
                return;
            }
            sy += spacing;
        }

        // Module rows
        for (AppleModuleRow row : rows)
            if (row.mouseDown(mx, my, button)) return;
    }

    @Override
    public void mouseReleased(int mx, int my, int button) {
        rows.forEach(r -> r.mouseReleased(mx, my, button));
        if (Raven.clientConfig != null) Raven.clientConfig.saveConfig();
    }

    @Override
    public void keyTyped(char c, int key) throws IOException {
        rows.forEach(r -> r.keyTyped(c, key));
        if (key == 1) { // ESC
            Raven.mc.displayGuiScreen(null);
            Raven.configManager.save();
            if (Raven.clientConfig != null) Raven.clientConfig.saveConfig();
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int scroll = Mouse.getEventDWheel();
        if (scroll == 0) return;

        int wx = winX(), wy = winY();
        int panelX = wx + SIDEBAR_W + 1;
        if (mouseX < panelX || mouseX > wx + WIN_W || mouseY < wy || mouseY > wy + WIN_H)
            return;

        scrollOffset += scroll > 0 ? 15 : -15;
        int totalH = rows.stream().mapToInt(AppleModuleRow::getTotalHeight).sum() + 20;
        int minScroll = Math.min(0, WIN_H - 10 - totalH);
        scrollOffset = Math.max(minScroll, Math.min(0, scrollOffset));
    }

    @Override
    public void onGuiClosed() {
        Raven.mc.gameSettings.guiScale = GuiModule.guiScale;
        Raven.configManager.save();
        if (Raven.clientConfig != null) Raven.clientConfig.saveConfig();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}