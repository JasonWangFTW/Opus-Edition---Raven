package keystrokesmod.client.clickgui.apple.settings;

import keystrokesmod.client.clickgui.apple.AppleClickGui;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.setting.impl.TickSetting;
import keystrokesmod.client.utils.CoolDown;
import keystrokesmod.client.utils.RenderUtils;
import keystrokesmod.client.utils.Utils;
import keystrokesmod.client.utils.font.FontUtil;

public class AToggle extends ASettingComponent {

    private static final int TRACK_W    = 24;
    private static final int TRACK_H    = 12;
    private static final int KNOB_D     = 10;
    private static final int KNOB_INSET = 1;

    private final TickSetting setting;
    private final Module mod;
    private final CoolDown anim = new CoolDown(250);

    public AToggle(TickSetting setting, Module mod) {
        this.setting = setting;
        this.mod = mod;
        this.height = 16;
    }

    @Override
    public void draw(int mx, int my) {
        float ty = y + (height - FontUtil.poppinsMedium.getHeight()) / 2f;
        FontUtil.poppinsMedium.drawSmoothString(setting.getName(), x, ty, AppleClickGui.C_TEXT);

        int trackX = x + width - TRACK_W;
        int trackY = y + (height - TRACK_H) / 2;

        float percent = Utils.Client.smoothPercent(
            (setting.isToggled() ? anim.getElapsedTime() : anim.getTimeLeft()) / (float) anim.getCooldownTime()
        );

        // Green when on, gray when off
        int trackColor = lerpColor(AppleClickGui.C_OFF, AppleClickGui.C_ON, percent);

        int knobX = (int) (trackX + KNOB_INSET + percent * (TRACK_W - KNOB_D - KNOB_INSET * 2));
        int knobY = trackY + (TRACK_H - KNOB_D) / 2;

        // Pill-shaped track
        RenderUtils.drawRoundedRect(trackX, trackY, trackX + TRACK_W, trackY + TRACK_H, TRACK_H / 2f, trackColor);
        // White knob
        RenderUtils.drawRoundedRect(knobX, knobY, knobX + KNOB_D, knobY + KNOB_D, KNOB_D / 2f, AppleClickGui.C_THUMB);
    }

    @Override
    public void clicked(int mx, int my, int button) {
        anim.setCooldown(250);
        anim.start();
        setting.toggle();
        mod.guiButtonToggled(setting);
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