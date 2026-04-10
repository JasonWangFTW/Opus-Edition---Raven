package keystrokesmod.client.clickgui.apple.settings;

import keystrokesmod.client.clickgui.apple.AppleClickGui;
import keystrokesmod.client.module.setting.impl.RGBSetting;
import keystrokesmod.client.utils.RenderUtils;
import keystrokesmod.client.utils.font.FontUtil;

public class ARgb extends ASettingComponent {

    private static final int TRACK_H = 4;
    private static final int THUMB_R = 5;
    private static final int[] CHANNEL_COLORS = { 0xFFFF3B30, 0xFF34C759, 0xFF0A84FF };

    private final RGBSetting setting;
    private boolean dragging;
    private int draggingChannel = -1;

    public ARgb(RGBSetting setting) {
        this.setting = setting;
        this.height = 22;
    }

    @Override
    public void draw(int mx, int my) {
        if (dragging && draggingChannel >= 0) {
            float p = Math.max(0f, Math.min(1f, (mx - x) / (float) width));
            setting.setColor(draggingChannel, (int) (p * 255));
        }

        FontUtil.poppinsMedium.drawSmoothString(setting.getName(), x, y + 1, AppleClickGui.C_TEXT);

        int trackY = y + height - TRACK_H - 2;
        RenderUtils.drawRoundedRect(x, trackY, x + width, trackY + TRACK_H, TRACK_H / 2f, AppleClickGui.C_SLIDER_BG);

        for (int i = 0; i < 3; i++) {
            int thumbX = x + (int) (width * setting.getColor(i) / 255f) - THUMB_R;
            int thumbY = trackY + (TRACK_H / 2) - THUMB_R;
            RenderUtils.drawRoundedRect(thumbX, thumbY, thumbX + THUMB_R * 2, thumbY + THUMB_R * 2, THUMB_R, CHANNEL_COLORS[i]);
        }
    }

    @Override
    public void clicked(int mx, int my, int button) {
        dragging = true;
        float p = (mx - x) / (float) width;
        float minDist = Float.MAX_VALUE;
        draggingChannel = 0;
        for (int i = 0; i < 3; i++) {
            float dist = Math.abs(setting.getColor(i) / 255f - p);
            if (dist < minDist) { minDist = dist; draggingChannel = i; }
        }
    }

    @Override
    public void mouseReleased(int mx, int my, int button) {
        dragging = false;
        draggingChannel = -1;
    }
}