package keystrokesmod.client.clickgui.apple.settings;

import org.lwjgl.input.Keyboard;

import keystrokesmod.client.clickgui.apple.AppleClickGui;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.modules.client.GuiModule;
import keystrokesmod.client.utils.RenderUtils;
import keystrokesmod.client.utils.font.FontUtil;

public class ABind extends ASettingComponent {

    private final Module mod;
    private boolean listening;

    public ABind(Module mod) {
        this.mod = mod;
        this.height = 16;
    }

    @Override
    public void draw(int mx, int my) {
        boolean hover = mx >= x && mx <= x + width && my >= y && my <= y + height;
        if (hover || listening)
            RenderUtils.drawRoundedRect(x - 2, y, x + width + 2, y + height, 3, AppleClickGui.C_HOVER);

        String label = listening ? "Press a key..." : "Bind: " + mod.getBindAsString();
        // Green accent when listening, gray normally
        int color = listening ? AppleClickGui.C_ACCENT : AppleClickGui.C_TEXT_SEC;
        FontUtil.poppinsMedium.drawSmoothString(label, x, y + (height - FontUtil.poppinsMedium.getHeight()) / 2f, color);
    }

    @Override
    public void clicked(int mx, int my, int button) {
        if (button == 0) listening = true;
    }

    @Override
    public void keyTyped(char c, int key) {
        if (!listening) return;
        if (key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_0)
            mod.setbind(mod instanceof GuiModule ? 54 : 0);
        else
            mod.setbind(key);
        listening = false;
    }
}