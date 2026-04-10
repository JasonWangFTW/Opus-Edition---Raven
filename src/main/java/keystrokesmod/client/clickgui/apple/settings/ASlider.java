package keystrokesmod.client.clickgui.apple.settings;

import keystrokesmod.client.clickgui.apple.AppleClickGui;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.utils.RenderUtils;
import keystrokesmod.client.utils.font.FontUtil;

public class ASlider extends ASettingComponent {

    private static final int TRACK_H  = 3;
    private static final int THUMB_D  = 8; // 8px diameter

    private final SliderSetting setting;
    private boolean dragging;

    public ASlider(SliderSetting setting) {
        this.setting = setting;
        this.height = 18;
    }

    @Override
    public void draw(int mx, int my) {
        if (dragging) {
            float p = Math.max(0f, Math.min(1f, (mx - x) / (float) width));
            setting.setValue(setting.getMin() + p * (setting.getMax() - setting.getMin()));
        }

        String name = setting.getName();
        String value = String.valueOf(setting.getInput());
        FontUtil.poppinsMedium.drawSmoothString(name, x, y + 1, AppleClickGui.C_TEXT);
        float valueX = x + width - (float) FontUtil.poppinsRegular.getStringWidth(value);
        FontUtil.poppinsRegular.drawSmoothString(value, valueX, y + 1, AppleClickGui.C_TEXT_SEC);

        int trackY = y + height - TRACK_H - 2;

        // Background track — gray
        RenderUtils.drawRoundedRect(x, trackY, x + width, trackY + TRACK_H, TRACK_H / 2f, AppleClickGui.C_SLIDER_BG);

        // Filled track — green from left to current position
        float percent = (float) ((setting.getInput() - setting.getMin()) / (setting.getMax() - setting.getMin()));
        int fillW = (int) (width * percent);
        if (fillW > 0)
            RenderUtils.drawRoundedRect(x, trackY, x + fillW, trackY + TRACK_H, TRACK_H / 2f, AppleClickGui.C_SLIDER_FILL);

        // Thumb — white circle
        int thumbX = x + fillW - THUMB_D / 2;
        int thumbY = trackY + (TRACK_H / 2) - THUMB_D / 2;
        RenderUtils.drawRoundedRect(thumbX, thumbY, thumbX + THUMB_D, thumbY + THUMB_D, THUMB_D / 2f, AppleClickGui.C_THUMB);
    }

    @Override
    public void clicked(int mx, int my, int button) {
        dragging = true;
    }

    @Override
    public void mouseReleased(int mx, int my, int button) {
        dragging = false;
    }
}