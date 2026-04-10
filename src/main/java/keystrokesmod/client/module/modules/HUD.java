package keystrokesmod.client.module.modules;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.lwjgl.opengl.GL11;

import com.google.common.eventbus.Subscribe;

import keystrokesmod.client.event.impl.Render2DEvent;
import keystrokesmod.client.main.Raven;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.setting.Setting;
import keystrokesmod.client.module.modules.client.GuiModule;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import keystrokesmod.client.utils.RenderUtils;
import keystrokesmod.client.utils.Utils;
import keystrokesmod.client.utils.font.FontUtil;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.fml.client.config.GuiButtonExt;

public class HUD extends Module {
    public static TickSetting editPosition, dropShadow;
    private static int hudX = 5;
    private static int hudY = 70;
    public static boolean e;

    public static Utils.HUD.PositionMode positionMode;
    public static boolean showedError;
    public static final String HUDX_prefix = "HUDX~ ";
    public static final String HUDY_prefix = "HUDY~ ";

    public HUD() {
        super("HUD", ModuleCategory.render);
        this.registerSetting(editPosition = new TickSetting("Edit position", false));
        this.registerSetting(dropShadow = new TickSetting("Drop shadow", true));
        showedError = false;
        showInHud = false;
    }

    @Override
	public void guiButtonToggled(Setting b) {
        if (b == editPosition) {
            editPosition.disable();
            mc.displayGuiScreen(new EditHudPositionScreen());
        }
    }

    @Override
	public void onEnable() {
        Raven.moduleManager.sort();
    }

    @Subscribe
    public void onRender2D(Render2DEvent ev) {
        if (Utils.Player.isPlayerInGame()) {
            if ((mc.currentScreen != null) || mc.gameSettings.showDebugInfo)
				return;
            if (!e) {
                ScaledResolution sr = new ScaledResolution(mc);
                positionMode = Utils.HUD.getPostitionMode(hudX, hudY, sr.getScaledWidth(), sr.getScaledHeight());
                if ((positionMode == Utils.HUD.PositionMode.UPLEFT) || (positionMode == Utils.HUD.PositionMode.UPRIGHT)) {
                    Raven.moduleManager.sortShortLong();
                } else if ((positionMode == Utils.HUD.PositionMode.DOWNLEFT)
                        || (positionMode == Utils.HUD.PositionMode.DOWNRIGHT)) {
                    Raven.moduleManager.sortLongShort();
                }
                e = true;
            }
            int margin = 2;
            int y = hudY;

            List<Module> en = new ArrayList<>(Raven.moduleManager.getModules());
            if (en.isEmpty())
                return;

            float alScale = GuiModule.arraylistScale != null ? (float) GuiModule.arraylistScale.getInput() : 1f;
            int fontLineH = (int) FontUtil.poppinsMedium.getHeight() + 3;

            // Max unscaled module name width (for right-align and boundary checks)
            int maxUnscaledW = 0;
            for (Module m : en)
                if (m.isEnabled() && m.showInHud() && m.moduleCategory() != Module.ModuleCategory.category)
                    maxUnscaledW = Math.max(maxUnscaledW, (int) FontUtil.poppinsMedium.getStringWidth(m.getName()));

            int textBoxWidth  = (int) (maxUnscaledW * alScale);
            int textBoxHeight = 0;
            for (Module m : en)
                if (m.isEnabled() && m.showInHud() && m.moduleCategory() != Module.ModuleCategory.category)
                    textBoxHeight += (int) (fontLineH * alScale);

            if (hudX < 0) hudX = margin;
            if (hudY < 0) hudY = margin;

            if ((hudX + textBoxWidth) > (mc.displayWidth / 2))
                hudX = (mc.displayWidth / 2) - textBoxWidth - margin;
            if ((hudY + textBoxHeight) > (mc.displayHeight / 2))
                hudY = (mc.displayHeight / 2) - textBoxHeight;

            boolean right = (positionMode == Utils.HUD.PositionMode.DOWNRIGHT)
                    || (positionMode == Utils.HUD.PositionMode.UPRIGHT);

            // Apply scale transform around HUD origin
            GL11.glPushMatrix();
            GL11.glTranslatef(hudX, hudY, 0);
            GL11.glScalef(alScale, alScale, 1f);
            GL11.glTranslatef(-hudX, -hudY, 0);

            // Compute text color from HSB slider
            float hue = GuiModule.arraylistColor != null ? (float) GuiModule.arraylistColor.getInput() : 0f;
            int textColor;
            if (hue > 0f) {
                textColor = Color.HSBtoRGB(hue / 360f, 0.7f, 1.0f) | 0xFF000000;
            } else {
                textColor = 0xFFFFFFFF;
            }
            boolean showBg = GuiModule.arraylistBackground != null && GuiModule.arraylistBackground.isToggled();

            int renderY = hudY;
            for (Module m : en)
                if (m.isEnabled() && m.showInHud() && m.moduleCategory() != Module.ModuleCategory.category) {
                    float unscaledW = (float) FontUtil.poppinsMedium.getStringWidth(m.getName());
                    float tx = right ? (float) hudX + (maxUnscaledW - unscaledW) : (float) hudX;
                    if (showBg) {
                        Gui.drawRect((int) tx - 1, renderY - 1,
                                (int) tx + (int) unscaledW + 1, renderY + fontLineH - 2,
                                0x4D000000);
                    }
                    // shadow at 0.5px offset, 70% black
                    FontUtil.poppinsMedium.drawSmoothString(m.getName(), tx + 0.5f, renderY + 0.5f, 0xB3000000);
                    FontUtil.poppinsMedium.drawSmoothString(m.getName(), tx, renderY, textColor);
                    renderY += fontLineH;
                }

            GL11.glPopMatrix();
        }

    }

    static class EditHudPositionScreen extends GuiScreen {
        final String hudTextExample = "This is an-Example-HUD";
        GuiButtonExt resetPosButton;
        boolean mouseDown;
        int textBoxStartX;
        int textBoxStartY;
        ScaledResolution sr;
        int textBoxEndX;
        int textBoxEndY;
        int marginX = 5;
        int marginY = 70;
        int lastMousePosX;
        int lastMousePosY;
        int sessionMousePosX;
        int sessionMousePosY;

        @Override
		public void initGui() {
            super.initGui();
            this.buttonList
                    .add(this.resetPosButton = new GuiButtonExt(1, this.width - 90, 5, 85, 20, "Reset position"));
            this.marginX = hudX;
            this.marginY = hudY;
            sr = new ScaledResolution(mc);
            positionMode = Utils.HUD.getPostitionMode(marginX, marginY, sr.getScaledWidth(), sr.getScaledHeight());
            e = false;
        }

        @Override
		public void drawScreen(int mX, int mY, float pt) {
            drawRect(0, 0, this.width, this.height, -1308622848);
            drawRect(0, this.height / 2, this.width, (this.height / 2) + 1, 0x9936393f);
            drawRect(this.width / 2, 0, (this.width / 2) + 1, this.height, 0x9936393f);
            int textBoxStartX = this.marginX;
            int textBoxStartY = this.marginY;
            int textBoxEndX = textBoxStartX + 50;
            int textBoxEndY = textBoxStartY + 32;
            this.drawArrayList(this.mc.fontRendererObj, this.hudTextExample);
            this.textBoxStartX = textBoxStartX;
            this.textBoxStartY = textBoxStartY;
            this.textBoxEndX = textBoxEndX;
            this.textBoxEndY = textBoxEndY;
            hudX = textBoxStartX;
            hudY = textBoxStartY;
            ScaledResolution res = new ScaledResolution(this.mc);
            int descriptionOffsetX = (res.getScaledWidth() / 2) - 84;
            int descriptionOffsetY = (res.getScaledHeight() / 2) - 20;
            Utils.HUD.drawColouredText("Edit the HUD position by dragging.", '-', descriptionOffsetX,
                    descriptionOffsetY, 2L, 0L, true, this.mc.fontRendererObj);

            try {
                this.handleInput();
            } catch (IOException var12) {
            }

            super.drawScreen(mX, mY, pt);
        }

        private void drawArrayList(FontRenderer fr, String t) {
            int x = this.textBoxStartX;
            int gap = this.textBoxEndX - this.textBoxStartX;
            int y = this.textBoxStartY;
            double marginY = fr.FONT_HEIGHT + 2;
            String[] var4 = t.split("-");
            ArrayList<String> var5 = Utils.Java.toArrayList(var4);
            if ((positionMode == Utils.HUD.PositionMode.UPLEFT) || (positionMode == Utils.HUD.PositionMode.UPRIGHT))
				var5.sort((o1, o2) -> Utils.mc.fontRendererObj.getStringWidth(o2)
                        - Utils.mc.fontRendererObj.getStringWidth(o1));
			else if ((positionMode == Utils.HUD.PositionMode.DOWNLEFT)
                    || (positionMode == Utils.HUD.PositionMode.DOWNRIGHT))
				var5.sort(Comparator.comparingInt(o2 -> Utils.mc.fontRendererObj.getStringWidth(o2)));

            if ((positionMode == Utils.HUD.PositionMode.DOWNRIGHT) || (positionMode == Utils.HUD.PositionMode.UPRIGHT))
				for (String s : var5) {
                    fr.drawString(s, (float) x + (gap - fr.getStringWidth(s)), (float) y, Color.white.getRGB(),
                            dropShadow.isToggled());
                    y += marginY;
                }
			else
				for (String s : var5) {
                    fr.drawString(s, (float) x, (float) y, Color.white.getRGB(), dropShadow.isToggled());
                    y += marginY;
                }
        }

        @Override
		protected void mouseClickMove(int mousePosX, int mousePosY, int clickedMouseButton, long timeSinceLastClick) {
            super.mouseClickMove(mousePosX, mousePosY, clickedMouseButton, timeSinceLastClick);
            if (clickedMouseButton == 0)
				if (this.mouseDown) {
                    this.marginX = this.lastMousePosX + (mousePosX - this.sessionMousePosX);
                    this.marginY = this.lastMousePosY + (mousePosY - this.sessionMousePosY);
                    sr = new ScaledResolution(mc);
                    positionMode = Utils.HUD.getPostitionMode(marginX, marginY, sr.getScaledWidth(),
                            sr.getScaledHeight());

                    // in the else if statement, we check if the mouse is clicked AND inside the
                    // "text box"
                } else if ((mousePosX > this.textBoxStartX) && (mousePosX < this.textBoxEndX)
                        && (mousePosY > this.textBoxStartY) && (mousePosY < this.textBoxEndY)) {
                    this.mouseDown = true;
                    this.sessionMousePosX = mousePosX;
                    this.sessionMousePosY = mousePosY;
                    this.lastMousePosX = this.marginX;
                    this.lastMousePosY = this.marginY;
                }
        }

        @Override
		protected void mouseReleased(int mX, int mY, int state) {
            super.mouseReleased(mX, mY, state);
            if (state == 0)
				this.mouseDown = false;

        }

        @Override
		public void actionPerformed(GuiButton b) {
            if (b == this.resetPosButton) {
                this.marginX = hudX = 5;
                this.marginY = hudY = 70;
            }

        }

        @Override
		public boolean doesGuiPauseGame() {
            return false;
        }
    }

    public static int getHudX() {
        return hudX;
    }

    public static int getHudY() {
        return hudY;
    }

    public static void setHudX(int hudX) {
        HUD.hudX = hudX;
    }

    public static void setHudY(int hudY) {
        HUD.hudY = hudY;
    }
}
