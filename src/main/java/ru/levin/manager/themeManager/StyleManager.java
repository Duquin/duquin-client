package ru.levin.manager.themeManager;

import net.minecraft.util.math.MathHelper;
import ru.levin.manager.IMinecraft;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@SuppressWarnings("All")
public class StyleManager implements IMinecraft {
    private final List<Style> styles = new CopyOnWriteArrayList<>();
    private Style currentStyle;

    public void init() {
        addStyle("Красный", "#FF0000", "#8B0000");
        if (!styles.isEmpty()) {
            currentStyle = styles.get(0);
        }
    }

    private void addStyle(String name, String... hexColors) {
        int[] colors = new int[hexColors.length];
        for (int i = 0; i < hexColors.length; i++) {
            colors[i] = HexColor.toColor(hexColors[i]);
        }
        styles.add(new Style(name, colors));
    }

    public void setTheme(Style style) {
        if (styles.contains(style)) {
            currentStyle = style;
        }
    }

    public Style getTheme() {
        return currentStyle;
    }

    public List<Style> getStyles() {
        return styles;
    }

    public int getFirstColor() {
        return currentStyle != null && currentStyle.colors.length > 0 ? currentStyle.colors[0] : -1;
    }

    public int getSecondColor() {
        return currentStyle != null && currentStyle.colors.length > 1 ? currentStyle.colors[1] : getFirstColor();
    }

    public static class HexColor {
        public static int toColor(String hexColor) {
            int rgb = Integer.parseInt(hexColor.substring(1), 16);
            return reAlphaInt(rgb, 255);
        }

        public static int reAlphaInt(int color, int alpha) {
            return (MathHelper.clamp(alpha, 0, 255) << 24) | (color & 0xFFFFFF);
        }
    }
}
