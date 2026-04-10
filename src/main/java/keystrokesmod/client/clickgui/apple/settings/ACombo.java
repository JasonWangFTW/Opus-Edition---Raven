package keystrokesmod.client.clickgui.apple.settings;

import keystrokesmod.client.clickgui.apple.AppleClickGui;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.setting.impl.ComboSetting;
import keystrokesmod.client.utils.font.FontUtil;

public class ACombo extends ASettingComponent {

    private final ComboSetting<?> setting;
    private final Module mod;

    public ACombo(ComboSetting<?> setting, Module mod) {
        this.setting = setting;
        this.mod = mod;
        this.height = 15;
    }

    @Override
    public void draw(int mx, int my) {
        float ty = y + (height - FontUtil.poppinsMedium.getHeight()) / 2f;
        FontUtil.poppinsMedium.drawSmoothString(setting.getName() + ":", x, ty, AppleClickGui.C_TEXT);

        String mode = setting.getMode().toString();
        float modeX = x + width - (float) FontUtil.poppinsRegular.getStringWidth(mode);
        // Green accent for the selected mode value
        FontUtil.poppinsRegular.drawSmoothString(mode, modeX, ty, AppleClickGui.C_ACCENT);
    }

    @Override
    public void clicked(int mx, int my, int button) {
        if (button == 0) setting.nextMode();
        else if (button == 1) setting.prevMode();
        mod.guiButtonToggled(setting);
    }
}