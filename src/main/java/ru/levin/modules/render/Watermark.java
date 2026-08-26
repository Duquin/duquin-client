package ru.levin.modules.render;

import org.joml.Vector4f;
import ru.levin.events.Event;
import ru.levin.events.impl.render.EventRender2D;
import ru.levin.manager.ClientManager;
import ru.levin.manager.fontManager.FontUtils;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.util.color.ColorUtil;

import java.awt.Color;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import static ru.levin.util.render.RenderUtil.drawRoundedRect;

@FunctionAnnotation(name = "Watermark", desc = "Показывает название клиента, FPS, пинг, CPS, координаты и время", type = Type.Render)
public class Watermark extends Function {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    public Watermark() {
        state = true;
    }

    @Override
    public void onEvent(Event event) {
        if (!(event instanceof EventRender2D render2D)) return;
        if (mc.player == null || mc.world == null) return;

        String textLogo = "Duquin Client";
        String textInfo = ClientManager.getFps() + " FPS | " + ClientManager.getPing() + " MS | "
                + ClientManager.getCps() + " CPS | " + LocalTime.now().format(TIME_FORMAT) + " | "
                + "X: " + (int) mc.player.getX() + " Y: " + (int) mc.player.getY() + " Z: " + (int) mc.player.getZ();

        var matrices = render2D.getDrawContext().getMatrices();
        var fontBig = FontUtils.durman[16];
        var fontSmall = FontUtils.durman[15];

        float logoWidth = fontBig.getWidth(textLogo);
        float infoWidth = fontSmall.getWidth(textInfo);

        float logoBoxWidth = 8 + logoWidth + 8;
        float infoBoxWidth = 6 + infoWidth + 6;
        float totalWidth = logoBoxWidth + infoBoxWidth;

        float x = mc.getWindow().getScaledWidth() / 2f - totalWidth / 2f;
        float y = 6;

        drawRoundedRect(matrices, x, y, logoBoxWidth, 18, new Vector4f(5, 5, 0, 0), ColorUtil.hud_color);
        drawRoundedRect(matrices, x + logoBoxWidth, y, infoBoxWidth, 18, new Vector4f(0, 0, 3, 3), new Color(22, 22, 22, 220).getRGB());

        fontBig.renderGradientText(matrices, textLogo, x + 8, y + 4, ColorUtil.getColorStyle(180), ColorUtil.getColorStyle(30));
        fontSmall.drawLeftAligned(matrices, textInfo, x + logoBoxWidth + 6, y + 4.5f, -1);
    }
}
