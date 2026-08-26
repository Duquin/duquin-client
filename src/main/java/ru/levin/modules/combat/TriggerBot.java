package ru.levin.modules.combat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.Hand;
import ru.levin.events.Event;
import ru.levin.events.impl.EventUpdate;
import ru.levin.manager.ClientManager;
import ru.levin.manager.Manager;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.render.littlePet.GhostWolfEntity;
import ru.levin.modules.setting.BooleanSetting;
import ru.levin.modules.setting.MultiSetting;
import ru.levin.modules.setting.SliderSetting;

import java.util.Arrays;

@FunctionAnnotation(name = "TriggerBot", desc = "Автоматически бьёт то, на что смотришь", type = Type.Combat)
public class TriggerBot extends Function {

    private final SliderSetting range = new SliderSetting("Радиус", 3.5, 1.8, 6.0, 0.1);
    private final SliderSetting cps = new SliderSetting("CPS", 12.0, 1.0, 20.0, 1.0);

    private final MultiSetting targets = new MultiSetting(
            "Цели",
            Arrays.asList("Игроки"),
            new String[]{"Игроки", "Мобы", "Монстры", "Жители"}
    );

    private final BooleanSetting onlyPlayers = new BooleanSetting("Только игроки", true);
    private final BooleanSetting raytrace = new BooleanSetting("Проверять прицел", true);
    private final BooleanSetting autoBlock = new BooleanSetting("Авто щит", false);

    private long lastAttackMs = 0;

    public TriggerBot() {
        addSettings(range, cps, targets, onlyPlayers, raytrace, autoBlock);
    }

    @Override
    public void onEvent(Event event) {
        if (!(event instanceof EventUpdate)) return;
        if (mc.player == null || mc.world == null || mc.player.isDead()) return;

        long now = System.currentTimeMillis();
        long delay = (long) (1000.0 / cps.get().doubleValue());
        if (now - lastAttackMs < delay) return;

        if (mc.crosshairTarget == null || mc.crosshairTarget.getType() != net.minecraft.util.hit.HitResult.Type.ENTITY) return;
        Entity crosshair = ((EntityHitResult) mc.crosshairTarget).getEntity();

        if (!(crosshair instanceof LivingEntity living)) return;
        if (!isValidTarget(living)) return;

        double dist = mc.player.distanceTo(living);
        if (dist > range.get().doubleValue()) return;

        mc.interactionManager.attackEntity(mc.player, living);
        mc.player.swingHand(Hand.MAIN_HAND);
        ClientManager.registerClick();
        lastAttackMs = now;
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (entity == mc.player) return false;
        if (entity.isDead() || !entity.isAlive()) return false;
        if (entity instanceof ArmorStandEntity) return false;
        if (Manager.FUNCTION_MANAGER.antiBot.check(entity)) return false;
        if (Manager.FRIEND_MANAGER.isFriend(entity.getName().getString())) return false;
        if (Manager.FUNCTION_MANAGER.littleSnickers.state && (entity instanceof GhostWolfEntity)) return false;

        if (entity instanceof PlayerEntity) {
            return targets.get("Игроки");
        } else if (entity instanceof Monster) {
            return targets.get("Монстры");
        } else if (entity instanceof MobEntity || entity instanceof AnimalEntity) {
            return targets.get("Мобы");
        } else if (entity instanceof VillagerEntity) {
            return targets.get("Жители");
        }
        return false;
    }
}
