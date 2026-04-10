package keystrokesmod.client.clickgui.apple;

import java.util.ArrayList;
import java.util.List;

import keystrokesmod.client.clickgui.apple.settings.ABind;
import keystrokesmod.client.clickgui.apple.settings.ACombo;
import keystrokesmod.client.clickgui.apple.settings.ADoubleSlider;
import keystrokesmod.client.clickgui.apple.settings.ARgb;
import keystrokesmod.client.clickgui.apple.settings.ASettingComponent;
import keystrokesmod.client.clickgui.apple.settings.ASlider;
import keystrokesmod.client.clickgui.apple.settings.AToggle;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.setting.Setting;
import keystrokesmod.client.module.setting.impl.ComboSetting;
import keystrokesmod.client.module.setting.impl.DoubleSliderSetting;
import keystrokesmod.client.module.setting.impl.RGBSetting;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import keystrokesmod.client.utils.CoolDown;
import keystrokesmod.client.utils.RenderUtils;
import keystrokesmod.client.utils.Utils;
import keystrokesmod.client.utils.font.FontUtil;
import net.minecraft.client.gui.Gui;

public class AppleModuleRow {

    // ── layout ──
    private static final int ROW_H         = 32;  // taller to fit name + description
    private static final int ROW_GAP       = 2;   // small gap between rows
    private static final int TOGGLE_W      = 24;  // pill toggle width
    private static final int TOGGLE_H      = 12;  // pill toggle height
    private static final int TOGGLE_KNOB   = 10;  // knob diameter
    private static final int TOGGLE_INSET  = 1;
    private static final int STAR_SIZE     = 10;  // favorite star hit area
    private static final int DOTS_SIZE     = 10;  // three-dot button hit area

    final Module mod;
    private final List<ASettingComponent> settings = new ArrayList<>();
    private ABind bindRow;
    private boolean expanded = false;

    private int x, y, width;

    private final CoolDown toggleAnim = new CoolDown(300);

    public AppleModuleRow(Module mod) {
        this.mod = mod;
        for (Setting s : mod.getSettings()) {
            ASettingComponent c = buildComponent(s);
            if (c != null) settings.add(c);
        }
        if (mod.isBindable())
            bindRow = new ABind(mod);
    }

    @SuppressWarnings("unchecked")
    private ASettingComponent buildComponent(Setting s) {
        if (s instanceof TickSetting)         return new AToggle((TickSetting) s, mod);
        if (s instanceof SliderSetting)       return new ASlider((SliderSetting) s);
        if (s instanceof ComboSetting)        return new ACombo((ComboSetting<?>) s, mod);
        if (s instanceof RGBSetting)          return new ARgb((RGBSetting) s);
        if (s instanceof DoubleSliderSetting) return new ADoubleSlider((DoubleSliderSetting) s);
        return null;
    }

    public void setPosition(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    public void draw(int mx, int my) {
        int cardH = getTotalHeight() - ROW_GAP;

        // ── Row background: dark green tint if enabled, otherwise normal card ──
        int bgColor = mod.isEnabled() ? AppleClickGui.C_CARD_ON : AppleClickGui.C_CARD;
        RenderUtils.drawRoundedRect(x, y, x + width, y + cardH, 6, bgColor);

        // ── Hover highlight ──
        boolean hoverRow = mx >= x && mx <= x + width && my >= y && my <= y + ROW_H;
        if (hoverRow) {
            RenderUtils.drawRoundedRect(x, y, x + width, y + Math.min(ROW_H, cardH), 6, AppleClickGui.C_HOVER);
        }

        // ── Module name — left side ──
        float nameY = y + 4;
        FontUtil.poppinsBold.drawSmoothString(mod.getName(), x + 12, nameY, AppleClickGui.C_TEXT);

        // ── Description / settings summary — smaller gray text below name ──
        String desc = buildDescription();
        if (desc != null && !desc.isEmpty()) {
            // Truncate if too long
            float maxDescW = width - 90; // leave room for star + toggle + dots
            String truncated = desc;
            if (FontUtil.poppinsRegular.getStringWidth(truncated) > maxDescW) {
                while (FontUtil.poppinsRegular.getStringWidth(truncated + "...") > maxDescW && truncated.length() > 0) {
                    truncated = truncated.substring(0, truncated.length() - 1);
                }
                truncated = truncated + "...";
            }
            float descY = nameY + FontUtil.poppinsBold.getHeight() + 1;
            FontUtil.poppinsRegular.drawSmoothString(truncated, x + 12, descY, AppleClickGui.C_TEXT_SEC);
        }

        // ── Favorite star (yellow if favorited, gray if not) ──
        drawStar(mx, my);

        // ── Toggle switch — pill-shaped ──
        drawToggle();

        // ── Three-dot expand/collapse button ──
        drawDotsButton(mx, my);

        // ── Expanded settings ──
        if (expanded) {
            // Separator between header and settings
            Gui.drawRect(x + 8, y + ROW_H, x + width - 8, y + ROW_H + 1, AppleClickGui.C_SEP);
            int settingY = y + ROW_H + 6;
            for (ASettingComponent comp : settings) {
                if (!comp.visable) continue;
                comp.setPosition(x + 16, settingY, width - 32);
                comp.draw(mx, my);
                settingY += comp.getHeight() + 4;
            }
            if (bindRow != null) {
                bindRow.setPosition(x + 16, settingY, width - 32);
                bindRow.draw(mx, my);
            }
        }
    }

    /** Build a summary string from current settings values */
    private String buildDescription() {
        // If module has a description set, use that as base
        String base = mod.getDescription();
        if (base != null && !base.isEmpty()) return base;

        // Otherwise auto-generate from settings
        StringBuilder sb = new StringBuilder();
        for (Setting s : mod.getSettings()) {
            if (sb.length() > 60) break; // keep it short
            if (s instanceof SliderSetting) {
                SliderSetting sl = (SliderSetting) s;
                if (sb.length() > 0) sb.append(". ");
                sb.append(sl.getName()).append(" ").append(sl.getInput());
            } else if (s instanceof ComboSetting) {
                ComboSetting<?> cs = (ComboSetting<?>) s;
                if (sb.length() > 0) sb.append(". ");
                sb.append(cs.getMode().toString());
            } else if (s instanceof TickSetting) {
                TickSetting ts = (TickSetting) s;
                if (ts.isToggled()) {
                    if (sb.length() > 0) sb.append(". ");
                    sb.append(ts.getName());
                }
            } else if (s instanceof DoubleSliderSetting) {
                DoubleSliderSetting ds = (DoubleSliderSetting) s;
                if (sb.length() > 0) sb.append(". ");
                sb.append(ds.getInputMin()).append("-").append(ds.getInputMax());
            }
        }
        return sb.toString();
    }

    /** Draw the favorite star icon */
    private void drawStar(int mx, int my) {
        boolean fav = AppleClickGui.isFavorite(mod.getName());
        int starX = x + width - 8 - TOGGLE_W - 16 - STAR_SIZE - 10;
        int starY = y + (ROW_H - STAR_SIZE) / 2;
        int color = fav ? AppleClickGui.C_FAVORITE : AppleClickGui.C_FAVORITE_OFF;

        // Draw a star using the ★ character
        String starChar = fav ? "\u2605" : "\u2606"; // ★ filled or ☆ outline
        FontUtil.poppinsMedium.drawSmoothString(starChar,
                starX, starY + (STAR_SIZE - FontUtil.poppinsMedium.getHeight()) / 2f, color);
    }

    /** Draw the three-dot expand/collapse button */
    private void drawDotsButton(int mx, int my) {
        int dotsX = x + width - DOTS_SIZE - 4;
        int dotsY = y + (ROW_H - DOTS_SIZE) / 2;

        boolean hoverDots = mx >= dotsX - 2 && mx <= dotsX + DOTS_SIZE + 2
                && my >= dotsY - 2 && my <= dotsY + DOTS_SIZE + 2;

        int dotColor = hoverDots ? AppleClickGui.C_TEXT : AppleClickGui.C_DOTS;

        // Draw three vertical dots
        int dotR = 1;
        int centerX = dotsX + DOTS_SIZE / 2;
        for (int i = 0; i < 3; i++) {
            int dy = dotsY + 1 + i * 4;
            RenderUtils.drawRoundedRect(centerX - dotR, dy, centerX + dotR + 1, dy + dotR + 1, dotR, dotColor);
        }
    }

    private void drawToggle() {
        int tx = x + width - 8 - TOGGLE_W - 16; // leave room for dots button
        int ty = y + (ROW_H - TOGGLE_H) / 2;

        float percent = Utils.Client.smoothPercent(
            (mod.isEnabled() ? toggleAnim.getElapsedTime() : toggleAnim.getTimeLeft()) / (float) toggleAnim.getCooldownTime()
        );

        int trackColor = lerpColor(AppleClickGui.C_OFF, AppleClickGui.C_ON, percent);

        // Pill-shaped track
        RenderUtils.drawRoundedRect(tx, ty, tx + TOGGLE_W, ty + TOGGLE_H, TOGGLE_H / 2f, trackColor);

        // Knob — white circle, slides left-right
        int knobX = (int) (tx + TOGGLE_INSET + percent * (TOGGLE_W - TOGGLE_KNOB - TOGGLE_INSET * 2));
        int knobY = ty + (TOGGLE_H - TOGGLE_KNOB) / 2;
        RenderUtils.drawRoundedRect(knobX, knobY, knobX + TOGGLE_KNOB, knobY + TOGGLE_KNOB, TOGGLE_KNOB / 2f, AppleClickGui.C_THUMB);
    }

    public int getTotalHeight() {
        if (!expanded) return ROW_H + ROW_GAP;
        int h = ROW_H + 6 + ROW_GAP;
        for (ASettingComponent c : settings) {
            if (c.visable) h += c.getHeight() + 4;
        }
        if (bindRow != null) h += bindRow.getHeight() + 4;
        return h;
    }

    public boolean mouseDown(int mx, int my, int button) {
        if (mx < x || mx > x + width || my < y || my > y + getTotalHeight())
            return false;

        // ── Favorite star click ──
        int starX = x + width - 8 - TOGGLE_W - 16 - STAR_SIZE - 10;
        int starY = y + (ROW_H - STAR_SIZE) / 2;
        if (mx >= starX - 2 && mx <= starX + STAR_SIZE + 2 && my >= starY - 2 && my <= starY + STAR_SIZE + 2) {
            AppleClickGui.toggleFavorite(mod.getName());
            return true;
        }

        // ── Toggle area ──
        int tx = x + width - 8 - TOGGLE_W - 16;
        int ty = y + (ROW_H - TOGGLE_H) / 2;
        if (mx >= tx && mx <= tx + TOGGLE_W && my >= ty - 2 && my <= ty + TOGGLE_H + 2) {
            toggleAnim.setCooldown(300);
            toggleAnim.start();
            mod.toggle();
            return true;
        }

        // ── Three-dot expand/collapse button ──
        int dotsX = x + width - DOTS_SIZE - 4;
        int dotsY = y + (ROW_H - DOTS_SIZE) / 2;
        if (mx >= dotsX - 4 && mx <= dotsX + DOTS_SIZE + 4 && my >= dotsY - 4 && my <= dotsY + DOTS_SIZE + 4) {
            expanded = !expanded;
            return true;
        }

        // ── Clicking anywhere else on the header row toggles module ──
        if (my >= y && my <= y + ROW_H) {
            // Do nothing — we only expand via three-dot button, toggle via toggle
            return true;
        }

        // ── Settings area ──
        if (expanded) {
            for (ASettingComponent comp : settings) {
                if (comp.visable && comp.mouseDown(mx, my, button)) return true;
            }
            if (bindRow != null && bindRow.mouseDown(mx, my, button)) return true;
        }

        return true;
    }

    public void mouseReleased(int mx, int my, int button) {
        settings.forEach(s -> s.mouseReleased(mx, my, button));
    }

    public void keyTyped(char c, int key) {
        settings.forEach(s -> s.keyTyped(c, key));
        if (bindRow != null) bindRow.keyTyped(c, key);
    }

    private int lerpColor(int from, int to, float t) {
        int r = lerp((from >> 16) & 0xFF, (to >> 16) & 0xFF, t);
        int g = lerp((from >>  8) & 0xFF, (to >>  8) & 0xFF, t);
        int b = lerp( from        & 0xFF,  to        & 0xFF, t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private int lerp(int a, int b, float t) {
        return (int) (a + (b - a) * t);
    }
}