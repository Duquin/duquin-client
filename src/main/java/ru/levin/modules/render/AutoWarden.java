package ru.levin.modules.render;

import net.minecraft.entity.mob.WardenEntity;
import ru.levin.events.Event;
import ru.levin.events.impl.EventUpdate;
import ru.levin.events.impl.render.EventRender3D;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.BooleanSetting;
import ru.levin.modules.setting.SliderSetting;
import ru.levin.util.color.ColorUtil;
import ru.levin.util.render.Render3DUtil;

@FunctionAnnotation(name = "AutoWarden", desc = "Отслеживает Вардена и предупреждает о нём", type = Type.Render)
public class AutoWarden extends Function {

    private final SliderSetting distance = new SliderSetting("Радиус обнаружения", 48f, 16f, 96f, 4f);
    private final BooleanSetting chatWarn = new BooleanSetting("Предупреждать в чат", true);
    private final BooleanSetting outline = new BooleanSetting("Подсветка", true);

    private WardenEntity warden = null;

    @Override
    public void onEvent(Event event) {
        if (event instanceof EventUpdate) {
            update();
        }

        if (event instanceof EventRender3D && outline.get() && warden != null && !warden.isRemoved()) {
            Render3DUtil.drawBox(warden.getBoundingBox().expand(0.05), ColorUtil.getColorStyle(360), 2f, true, false, false);
        }
    }

    private void update() {
        if (warden != null && (warden.isRemoved() || !warden.isAlive())) {
            warden = null;
        }

        if (mc.player.age % 10 == 0) {
            double dist = distance.get().floatValue();
            double maxSq = dist * dist;
            WardenEntity nearest = null;
            for (var entity : mc.world.getEntities()) {
                if (!(entity instanceof WardenEntity w)) continue;
                if (!w.isAlive()) continue;
                if (mc.player.squaredDistanceTo(w) > maxSq) continue;
                if (nearest == null || mc.player.squaredDistanceTo(w) < mc.player.squaredDistanceTo(nearest)) {
                    nearest = w;
                }
            }
            boolean appeared = nearest != null && warden == null;
            warden = nearest;
            if (appeared && chatWarn.get()) {
                mc.inGameHud.getChatHud().addMessage(net.minecraft.text.Text.literal(
                        "§5[AutoWarden] §fВарден рядом! §7X: " + (int) warden.getX()
                                + " Y: " + (int) warden.getY() + " Z: " + (int) warden.getZ()
                                + " (" + (int) mc.player.distanceTo(warden) + "м)"));
            }
        }
    }
}
