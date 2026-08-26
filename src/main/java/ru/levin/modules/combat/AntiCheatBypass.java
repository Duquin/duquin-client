package ru.levin.modules.combat;

import net.minecraft.client.network.ClientPlayerEntity;
import ru.levin.events.Event;
import ru.levin.events.impl.EventUpdate;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.BooleanSetting;
import ru.levin.modules.setting.SliderSetting;

@FunctionAnnotation(name = "AntiCheatBypass", desc = "Обход античита на серверах", type = Type.Combat)
public class AntiCheatBypass extends Function {

    public final BooleanSetting smoothRotation = new BooleanSetting("Плавные повороты", true);
    public final SliderSetting rotationSpeed = new SliderSetting("Скорость поворота", 80.0, 20.0, 180.0, 10.0, () -> smoothRotation.get());
    public final BooleanSetting legitSwing = new BooleanSetting("Легит свинг", true);
    public final BooleanSetting noSprintHit = new BooleanSetting("Откл спринт при ударе", true);
    public final BooleanSetting reachFix = new BooleanSetting("Фикс дистанции", true);
    public final SliderSetting maxReach = new SliderSetting("Макс дистанция", 3.0, 2.5, 3.5, 0.05, () -> reachFix.get());
    public final BooleanSetting randomDelay = new BooleanSetting("Рандом задержки", true);
    public final SliderSetting minCps = new SliderSetting("Мин CPS", 8.0, 3.0, 15.0, 1.0, () -> randomDelay.get());
    public final SliderSetting maxCps = new SliderSetting("Макс CPS", 14.0, 5.0, 20.0, 1.0, () -> randomDelay.get());
    public final BooleanSetting hitGroundCheck = new BooleanSetting("Проверка земли", true);
    public final BooleanSetting autoBlock = new BooleanSetting("Авто блок", false);

    public AntiCheatBypass() {
        addSettings(smoothRotation, rotationSpeed, legitSwing, noSprintHit,
                reachFix, maxReach, randomDelay, minCps, maxCps,
                hitGroundCheck, autoBlock);
    }

    @Override
    public void onEvent(Event event) {
        if (!(event instanceof EventUpdate)) return;
        if (mc.player == null || mc.world == null) return;
    }

    public boolean canAttack() {
        ClientPlayerEntity player = mc.player;
        if (player == null) return false;

        if (noSprintHit.get() && player.isSprinting()) {
            player.setSprinting(false);
        }

        if (hitGroundCheck.get() && !player.isOnGround() && player.fallDistance > 0) {
            return false;
        }

        return true;
    }

    public double getMaxReach() {
        return reachFix.get() ? maxReach.get().doubleValue() : 3.5;
    }

    public long getAttackDelay() {
        if (!randomDelay.get()) return 50L;
        double min = minCps.get().doubleValue();
        double max = maxCps.get().doubleValue();
        if (min > max) { double t = min; min = max; max = t; }
        double cps = min + Math.random() * (max - min);
        return (long) (1000.0 / cps);
    }
}
