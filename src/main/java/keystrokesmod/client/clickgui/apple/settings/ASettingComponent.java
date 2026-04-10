package keystrokesmod.client.clickgui.apple.settings;

public abstract class ASettingComponent {

    public boolean visable = true;
    protected int x, y, width, height;

    public void setPosition(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    public abstract void draw(int mx, int my);

    public void clicked(int mx, int my, int button) {}

    public void mouseReleased(int mx, int my, int button) {}

    public void keyTyped(char c, int key) {}

    public boolean mouseDown(int mx, int my, int button) {
        if (mx >= x && mx <= x + width && my >= y && my <= y + height) {
            clicked(mx, my, button);
            return true;
        }
        return false;
    }

    public int getHeight() {
        return height;
    }
}