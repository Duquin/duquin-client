package ru.levin.modules.render;

import net.minecraft.block.entity.*;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import ru.levin.events.Event;
import ru.levin.events.impl.EventUpdate;
import ru.levin.events.impl.render.EventRender2D;
import ru.levin.events.impl.render.EventRender3D;
import ru.levin.manager.fontManager.FontUtils;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.BooleanSetting;
import ru.levin.modules.setting.MultiSetting;
import ru.levin.modules.setting.SliderSetting;
import ru.levin.util.color.ColorUtil;
import ru.levin.util.render.Render3DUtil;

import org.joml.Vector4f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static ru.levin.util.render.RenderUtil.drawRoundedRect;

@FunctionAnnotation(name = "BaseFinder", desc = "Ищет базы по скоплениям тайл-энтити в чанках", type = Type.Render)
public class BaseFinder extends Function {

    private final MultiSetting blocks = new MultiSetting(
            "Что искать",
            Arrays.asList("Сундуки", "Шалкеры", "Печи", "Кровати", "Воронки", "Спавнеры", "Эндер-сундуки", "Стол зачарований", "Варочная стойка"),
            new String[]{"Сундуки", "Шалкеры", "Печи", "Кровати", "Воронки", "Спавнеры"}
    );
    private final SliderSetting density = new SliderSetting("Плотность базы", 6f, 2f, 24f, 1f);
    private final SliderSetting clusterRange = new SliderSetting("Радиус скопления", 20f, 8f, 48f, 2f);
    private final SliderSetting chunkRadius = new SliderSetting("Радиус чанков", 3f, 1f, 8f, 1f);
    private final BooleanSetting chatNotify = new BooleanSetting("Уведомления в чат", true);

    private final List<BlockPos> matched = new ArrayList<>();
    private final List<Base> bases = new ArrayList<>();
    private final Set<Long> knownBases = new HashSet<>();
    private int scanTimer = 0;

    private record Base(double x, double y, double z, int count) {}

    @Override
    public void onEvent(Event event) {
        if (event instanceof EventUpdate && ++scanTimer >= 40) {
            scanTimer = 0;
            scan();
        }

        if (event instanceof EventRender3D) {
            for (BlockPos pos : matched) {
                Render3DUtil.drawBox(new net.minecraft.util.math.Box(pos), ColorUtil.getColorStyle(360), 1.2f, true, false, false);
            }
        }

        if (event instanceof EventRender2D render2D) {
            drawHud(render2D);
        }
    }

    private boolean isEnabled(BlockEntity be) {
        if ((be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity) && blocks.get("Сундуки")) return true;
        if (be instanceof ShulkerBoxBlockEntity && blocks.get("Шалкеры")) return true;
        if (be instanceof FurnaceBlockEntity && blocks.get("Печи")) return true;
        if (be instanceof BedBlockEntity && blocks.get("Кровати")) return true;
        if (be instanceof HopperBlockEntity && blocks.get("Воронки")) return true;
        if (be instanceof MobSpawnerBlockEntity && blocks.get("Спавнеры")) return true;
        if (be instanceof EnderChestBlockEntity && blocks.get("Эндер-сундуки")) return true;
        if (be instanceof EnchantingTableBlockEntity && blocks.get("Стол зачарований")) return true;
        if (be instanceof BrewingStandBlockEntity && blocks.get("Варочная стойка")) return true;
        return false;
    }

    private void scan() {
        matched.clear();

        int radius = chunkRadius.get().intValue();
        int playerChunkX = mc.player.getChunkPos().x;
        int playerChunkZ = mc.player.getChunkPos().z;

        ClientChunkManager chunks = (ClientChunkManager) mc.world.getChunkManager();

        for (int cx = playerChunkX - radius; cx <= playerChunkX + radius; cx++) {
            for (int cz = playerChunkZ - radius; cz <= playerChunkZ + radius; cz++) {
                WorldChunk chunk = chunks.getChunk(cx, cz, net.minecraft.world.chunk.ChunkStatus.FULL, false);
                if (chunk == null) continue;

                for (var entry : chunk.getBlockEntities().entrySet()) {
                    if (isEnabled(entry.getValue())) {
                        matched.add(entry.getKey().toImmutable());
                    }
                }
            }
        }

        buildClusters();
    }

    private void buildClusters() {
        bases.clear();
        double range = clusterRange.get().floatValue();
        double rangeSq = range * range;

        List<BlockPos> pool = new ArrayList<>(matched);
        while (!pool.isEmpty()) {
            BlockPos first = pool.remove(pool.size() - 1);
            List<BlockPos> cluster = new ArrayList<>();
            cluster.add(first);

            for (int i = pool.size() - 1; i >= 0; i--) {
                BlockPos p = pool.get(i);
                for (BlockPos c : cluster) {
                    double dx = p.getSquaredDistance(c);
                    if (dx <= rangeSq) {
                        cluster.add(p);
                        pool.remove(i);
                        break;
                    }
                }
            }

            if (cluster.size() >= density.get().intValue()) {
                double sx = 0, sy = 0, sz = 0;
                for (BlockPos p : cluster) {
                    sx += p.getX();
                    sy += p.getY();
                    sz += p.getZ();
                }
                Base base = new Base(sx / cluster.size(), sy / cluster.size(), sz / cluster.size(), cluster.size());
                bases.add(base);
                notifyIfNew(base);
            }
        }

        bases.sort((a, b) -> Double.compare(distTo(a), distTo(b)));
    }

    private double distTo(Base b) {
        return mc.player.squaredDistanceTo(b.x, b.y, b.z);
    }

    private void notifyIfNew(Base base) {
        long key = ((long) Math.floor(base.x) & 0xFFFFFL) | (((long) Math.floor(base.z) & 0xFFFFFL) << 20) | (((long) Math.floor(base.y) & 0xFFFL) << 40);
        if (knownBases.add(key) && chatNotify.get()) {
            mc.inGameHud.getChatHud().addMessage(Text.literal(
                    "§c[BaseFinder] §fНайдена база: §c" + (int) base.x + " " + (int) base.y + " " + (int) base.z
                            + " §7(" + base.count + " блоков)"));
        }
    }

    private void drawHud(EventRender2D render2D) {
        if (bases.isEmpty()) return;

        var fontTitle = FontUtils.durman[15];
        var fontLine = FontUtils.durman[14];
        var matrices = render2D.getDrawContext().getMatrices();

        float screenWidth = mc.getWindow().getScaledWidth();

        List<String> lines = new ArrayList<>();
        float maxWidth = fontTitle.getWidth("BaseFinder [" + bases.size() + "]");

        int shown = Math.min(bases.size(), 8);
        for (int i = 0; i < shown; i++) {
            Base b = bases.get(i);
            String line = (int) b.x + " " + (int) b.y + " " + (int) b.z + " (" + b.count + ")";
            lines.add(line);
            maxWidth = Math.max(maxWidth, fontLine.getWidth(line));
        }

        float width = 10 + maxWidth + 10;
        float height = 8 + 16 + shown * 12 + 4;
        float x = screenWidth - width - 8;
        float y = 28;

        drawRoundedRect(matrices, x, y, width, height, new Vector4f(3, 3, 3, 3), new Color(22, 22, 22, 200).getRGB());

        fontTitle.renderGradientText(matrices, "BaseFinder [" + bases.size() + "]", x + 10, y + 6,
                ColorUtil.getColorStyle(180), ColorUtil.getColorStyle(30));

        float ly = y + 22;
        for (String line : lines) {
            fontLine.drawLeftAligned(matrices, line, x + 10, ly, -1);
            ly += 12;
        }
    }
}
