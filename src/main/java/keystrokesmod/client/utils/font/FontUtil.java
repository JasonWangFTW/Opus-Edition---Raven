package keystrokesmod.client.utils.font;

import java.awt.Font;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import keystrokesmod.client.module.modules.HUD;

public class FontUtil {
    public static volatile int completed;

    public static FontRenderer normal;
    public static FontRenderer two;
    public static FontRenderer small;

    // Apple GUI fonts — Gilroy (kept for legacy/other GUIs)
    public static FontRenderer guiTitle;
    public static FontRenderer guiModule;
    public static FontRenderer guiSetting;

    // Poppins — used throughout the Apple click GUI and arraylist HUD
    public static FontRenderer poppinsBold;    // 14px bold   — module names
    public static FontRenderer poppinsMedium;  // 13px plain  — setting labels
    public static FontRenderer poppinsRegular; // 11px plain  — values / descriptions

    private static Font normal_;
    private static Font two_;
    private static Font small_;
    private static Font guiTitle_;
    private static Font guiModule_;
    private static Font guiSetting_;

    private static Font poppinsBold_;
    private static Font poppinsMedium_;
    private static Font poppinsRegular_;

    private static Font getFont(Map<String, Font> locationMap, String location, int size, int fonttype) {
        Font font = null;

        try {
            if (locationMap.containsKey(location))
                font = locationMap.get(location).deriveFont(Font.PLAIN, size);
            else {
                InputStream is = HUD.class.getResourceAsStream("/assets/raindots/fonts/" + location);
                assert is != null;
                font = Font.createFont(Font.TRUETYPE_FONT, is);
                locationMap.put(location, font);
                font = font.deriveFont(fonttype, size);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error loading font");
            font = new Font("comfortaa", Font.PLAIN, +size);
        }

        return font;
    }

    public static boolean hasLoaded() {
        return completed >= 3;
    }

    public static void bootstrap() {
        new Thread(() -> {
            Map<String, Font> locationMap = new HashMap<>();
            normal_     = getFont(locationMap, "gilroy.otf",     19, Font.PLAIN);
            two_        = getFont(locationMap, "gilroy.otf",     30, Font.PLAIN);
            small_      = getFont(locationMap, "gilroybold.otf", 14, Font.BOLD);
            guiTitle_   = getFont(locationMap, "gilroybold.otf", 20, Font.BOLD);
            guiModule_  = getFont(locationMap, "gilroy.otf",     14, Font.PLAIN);
            guiSetting_ = getFont(locationMap, "gilroy.otf",     12, Font.PLAIN);
            // Poppins — Bold must be first so the cache entry is created with the correct fonttype
            poppinsBold_    = getFont(locationMap, "Poppins.ttf", 14, Font.BOLD);
            poppinsMedium_  = getFont(locationMap, "Poppins.ttf", 13, Font.PLAIN);
            poppinsRegular_ = getFont(locationMap, "Poppins.ttf", 11, Font.PLAIN);
            completed++;
        }).start();
        new Thread(() -> {
            Map<String, Font> locationMap = new HashMap<>();
            completed++;
        }).start();
        new Thread(() -> {
            Map<String, Font> locationMap = new HashMap<>();
            completed++;
        }).start();

        while (!hasLoaded())
            try {
                // noinspection BusyWait
                Thread.sleep(5);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        normal     = new FontRenderer(normal_,     true, true);
        two        = new FontRenderer(two_,        true, true);
        small      = new FontRenderer(small_,      true, true);
        guiTitle   = new FontRenderer(guiTitle_,   true, true);
        guiModule  = new FontRenderer(guiModule_,  true, true);
        guiSetting = new FontRenderer(guiSetting_, true, true);

        poppinsBold    = new FontRenderer(poppinsBold_,    true, true);
        poppinsMedium  = new FontRenderer(poppinsMedium_,  true, true);
        poppinsRegular = new FontRenderer(poppinsRegular_, true, true);
    }
}
