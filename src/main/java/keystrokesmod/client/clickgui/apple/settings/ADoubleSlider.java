package keystrokesmod.client.clickgui.apple.settings;

import keystrokesmod.client.clickgui.apple.AppleClickGui;
import keystrokesmod.client.module.setting.impl.DoubleSliderSetting;
import keystrokesmod.client.utils.RenderUtils;
import keystrokesmod.client.utils.font.FontUtil;

public class ADoubleSlider extends ASettingComponent {

    private static final int TRACK_H = 3;
    private static final int THUMB_D = 8; // 8px diameter circle

    private final DoubleSliderSetting setting;
    private boolean dragging;
    private boolean draggingMax;

    public ADoubleSlider(DoubleSliderSetting setting) {
        this.setting = setting;
        this.height = 18;
    }

    @Override
    public void draw(int mx, int my) {
        if (dragging) {
            double range = setting.getMax() - setting.getMin();
            double v = setting.getMin() + Math.max(0, Math.min(1, (mx - x) / (double) width)) * range;
            if (draggingMax) setting.setValueMax(v);
            else             setting.setValueMin(v);
        }

        String label = setting.getName() + ": " + setting.getInputMin() + " - " + setting.getInputMax();
        FontUtil.poppinsMedium.drawSmoothString(label, x, y + 1, AppleClickGui.C_TEXT);

        int trackY = y + height - TRACK_H - 2;

        // Background track — gray
        RenderUtils.drawRoundedRect(x, trackY, x + width, trackY + TRACK_H, TRACK_H / 2f, AppleClickGui.C_SLIDER_BG);

        // Filled track — green between min and max handles
        double range = setting.getMax() - setting.getMin();
        float pMin = (float) ((setting.getInputMin() - setting.getMin()) / range);
        float pMax = (float) ((setting.getInputMax() - setting.getMin()) / range);
        int fillX  = x + (int) (width * pMin);
        int fillW  = (int) (width * (pMax - pMin));
        if (fillW > 0)
            RenderUtils.drawRoundedRect(fillX, trackY, fillX + fillW, trackY + TRACK_H, TRACK_H / 2f, AppleClickGui.C_SLIDER_FILL);

        // Thumbs — white circles at min and max positions
        int thumbY    = trackY + (TRACK_H / 2) - THUMB_D / 2;
        int minThumbX = x + (int) (width * pMin) - THUMB_D / 2;
        int maxThumbX = x + (int) (width * pMax) - THUMB_D / 2;
        RenderUtils.drawRoundedRect(minThumbX, thumbY, minThumbX + THUMB_D, thumbY + THUMB_D, THUMB_D / 2f, AppleClickGui.C_THUMB);
        RenderUtils.drawRoundedRect(maxThumbX, thumbY, maxThumbX + THUMB_D, thumbY + THUMB_D, THUMB_D / 2f, AppleClickGui.C_THUMB);
    }

    @Override
    public void clicked(int mx, int my, int button) {
        dragging = true;
        float p    = (mx - x) / (float) width;
        double range = setting.getMax() - setting.getMin();
        float pMin = (float) ((setting.getInputMin() - setting.getMin()) / range);
        float pMax = (float) ((setting.getInputMax() - setting.getMin()) / range);
        draggingMax = Math.abs(p - pMax) < Math.abs(p - pMin);
    }

    @Override
    public void mouseReleased(int mx, int my, int button) {
        dragging = false;
    }
}