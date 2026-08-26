package ru.levin.screens.dropdown;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.joml.Vector4i;
import org.lwjgl.glfw.GLFW;
import ru.levin.manager.ClientManager;
import ru.levin.manager.IMinecraft;
import ru.levin.manager.Manager;
import ru.levin.modules.Function;
import ru.levin.modules.Type;
import ru.levin.modules.setting.*;
import ru.levin.protect.AES;
import ru.levin.screens.dropdown.impl.*;
import ru.levin.screens.dropdown.search.SearchState;
import ru.levin.util.animations.impl.EaseInOutQuad;
import ru.levin.util.color.ColorUtil;
import ru.levin.manager.fontManager.FontUtils;
import ru.levin.util.math.MathUtil;
import ru.levin.util.render.RenderAddon;
import ru.levin.util.render.RenderUtil;
import ru.levin.util.render.Scissor;
import ru.levin.util.render.providers.ResourceProvider;
import java.awt.*;
import java.util.*;

import static ru.levin.util.render.RenderUtil.drawBlur;

public class ClickGUI extends Screen implements IMinecraft {
    private boolean isClose;

    private final int PANEL_WIDTH = 125;
    private final int PANEL_HEIGHT = 280;
    private final int PANEL_MARGIN = 8;

    private final Color GUI_COLOR = Manager.FUNCTION_MANAGER.clickGUI.getGuiColor();

    private final int TITLE_MARGIN_TOP = 5;
    private final int TITLE_HEIGHT = 20;

    private final int FUNCTION_HEIGHT = 20;

    private final int SCROLL_AREA_Y_OFFSET = TITLE_MARGIN_TOP + TITLE_HEIGHT;
    private final int SCROLL_AREA_HEIGHT = PANEL_HEIGHT - SCROLL_AREA_Y_OFFSET - 5;

    private final Set<Type> renderCategories = EnumSet.of(Type.Combat, Type.Move, Type.Render, Type.Player, Type.Misc);
    private static final Map<Type, Float> scrollOffsets = new HashMap<>();
    private static final Map<Type, Float> scrollTargets = new HashMap<>();
    private final Map<Function, Float> arrowRotationProgress = new HashMap<>();
    private final EaseInOutQuad animationOpen = new EaseInOutQuad(250, 1);
    private double animation;

    private final Map<Function, Float> expandProgress = new HashMap<>();

    private final SearchState searchState;
    private boolean functionBinding = false;
    private Function functions = null;

    private SliderSetting draggingSlider = null;
    private int draggingSliderX = 0;
    private int draggingSliderWidth = 0;

    private final BooleanSettingRenderer booleanSettingRenderer = new BooleanSettingRenderer();
    private final BindBooleanSettingRenderer bindbooleanSettingRenderer = new BindBooleanSettingRenderer();
    private final BindSettingRenderer bindSettingRenderer = new BindSettingRenderer();
    private final ModeSettingRenderer modeSettingRenderer = new ModeSettingRenderer();
    private final MultiSettingRenderer multiSettingRenderer = new MultiSettingRenderer();
    private final SliderSettingRenderer sliderSettingRenderer = new SliderSettingRenderer();
    private final TextSettingRenderer textSettingRenderer = new TextSettingRenderer();

    private final int SEARCH_HEIGHT = 20;
    private final int SEARCH_MARGIN_BOTTOM = 10;
    private final int SEARCH_MAX_WIDTH = 180;

    private final float SCROLL_SPEED = 12f;
    private final float SCROLL_LERP_FACTOR = 20f;
    private final float SCROLL_SMOOTH_FACTOR = 12f;

    public ClickGUI() {
        super(Text.literal("ClickGUI"));
        this.searchState = new SearchState();
        if (scrollOffsets.isEmpty() && scrollTargets.isEmpty()) {
            renderCategories.forEach(cat -> {
                scrollOffsets.put(cat, 0f);
                scrollTargets.put(cat, 0f);
            });
        }
    }

    @Override
    public void init() {
        super.init();

        isClose = false;
        animationOpen.setDirection(Direction.AxisDirection.POSITIVE);
        animationOpen.reset();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        animation = animationOpen.getOutput();
        if (isClose && animationOpen.finished(Direction.AxisDirection.NEGATIVE)) {
            super.close();
            return;
        }
        if (animation <= 0.01) return;

        super.render(ctx, mouseX, mouseY, delta);
        ctx.getMatrices().push();

        for (Type category : renderCategories) {
            float target = scrollTargets.get(category);
            float current = scrollOffsets.get(category);
            float newOffset = MathUtil.lerp(current, target, SCROLL_LERP_FACTOR);
            scrollOffsets.put(category, newOffset);
        }

        int totalWidth = renderCategories.size() * (PANEL_WIDTH + PANEL_MARGIN) - PANEL_MARGIN;
        int startX = (width - totalWidth) / 2;
        int startY = (height - PANEL_HEIGHT) / 2;

        RenderAddon.sizeAnimation(ctx.getMatrices(), width / 2, height / 2, animation);

        int idx = 0;
        for (Type category : renderCategories) {
            renderPanel(ctx, startX + idx++ * (PANEL_WIDTH + PANEL_MARGIN), startY, category, mouseX, mouseY);
        }

        Function hoveredFunction = getHoveredFunction(mouseX, mouseY, startX, startY);
        if (hoveredFunction != null && hoveredFunction.desc != null && !hoveredFunction.desc.isEmpty()) {
            drawDescription(ctx, hoveredFunction.desc, startY);
        }
        DescriptionRenderQueue.renderAll(ctx);

        renderSearchField(ctx);
        renderExpiryText(ctx);
        ctx.getMatrices().pop();
    }
    private Function getHoveredFunction(int mouseX, int mouseY, int startX, int startY) {
        int idx = 0;
        for (Type category : renderCategories) {
            int panelX = startX + idx++ * (PANEL_WIDTH + PANEL_MARGIN);

            if (mouseX < panelX || mouseX > panelX + PANEL_WIDTH || mouseY < startY || mouseY > startY + PANEL_HEIGHT) {
                continue;
            }

            float offset = scrollOffsets.get(category);

            float currentY = startY + SCROLL_AREA_Y_OFFSET - offset;

            for (Function function : Manager.FUNCTION_MANAGER.getFunctions(category)) {
                if (!isFunctionVisible(function)) continue;

                int functionHeight = FUNCTION_HEIGHT;

                int fullSettingsHeight = computeSettingsHeight(function);
                float progress = expandProgress.getOrDefault(function, function.expanded ? 1f : 0f);
                int animatedSettingsHeight = (int) Math.round(fullSettingsHeight * progress);

                int totalHeight = functionHeight + animatedSettingsHeight;

                if (mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH && mouseY >= currentY && mouseY <= currentY + functionHeight && mouseY >= startY + SCROLL_AREA_Y_OFFSET &&
                        mouseY <= startY + SCROLL_AREA_Y_OFFSET + SCROLL_AREA_HEIGHT) {
                    return function;
                }

                if (animatedSettingsHeight > 0) {
                    float settingY = currentY + functionHeight;
                    int remaining = animatedSettingsHeight;

                    for (Setting setting : function.getSettings()) {
                        if (!setting.isVisible()) continue;

                        int settingHeight = getSettingRendererHeight(setting, PANEL_WIDTH - 20);
                        if (settingHeight <= 0) continue;

                        int visible = Math.max(0, Math.min(settingHeight, remaining));
                        if (visible <= 0) break;

                        if (mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH &&
                                mouseY >= settingY && mouseY <= settingY + visible &&
                                mouseY >= startY + SCROLL_AREA_Y_OFFSET &&
                                mouseY <= startY + SCROLL_AREA_Y_OFFSET + SCROLL_AREA_HEIGHT) {
                            return function;
                        }

                        settingY += settingHeight + 1;
                        remaining -= (settingHeight + 1);
                        if (remaining <= 0) break;
                    }
                }

                currentY += totalHeight;
            }
        }
        return null;
    }

    private void drawDescription(DrawContext ctx, String desc, int startY) {
        int descWidth = (int) FontUtils.durman[19].getWidth(desc);
        int descHeight = 20;

        int descX = (width - descWidth) / 2;
        int descY = startY - descHeight - 10;


        if (Manager.FUNCTION_MANAGER.clickGUI.blur.get() && Manager.FUNCTION_MANAGER.clickGUI.blurSetting.get("Описание")) {
            drawBlur(ctx.getMatrices(), descX - 6, descY - 3.5f, descWidth + 12, descHeight, 12, 8, -1);
        }

        RenderUtil.drawRoundedRect(ctx.getMatrices(), descX - 6, descY - 3.5f, descWidth + 12, descHeight, 6, GUI_COLOR.getRGB());
        FontUtils.durman[19].drawLeftAligned(ctx.getMatrices(), desc, descX, descY, Color.WHITE.getRGB());
    }

    private float updateExpandAnimation(Function f) {
        float target = f.expanded ? 1f : 0f;
        float prog = expandProgress.getOrDefault(f, target);

        prog = MathUtil.lerp(prog, target, 15f);

        if (Math.abs(target - prog) < 0.001f) prog = target;
        expandProgress.put(f, prog);

        return prog;
    }

    private void renderPanel(DrawContext ctx, int x, int y, Type category, int mouseX, int mouseY) {
        if (Manager.FUNCTION_MANAGER.clickGUI.blur.get() && Manager.FUNCTION_MANAGER.clickGUI.blurSetting.get("Панели")) {
            drawBlur(ctx.getMatrices(), x, y, PANEL_WIDTH, PANEL_HEIGHT, 12, 8, -1);
        }
        RenderUtil.drawRoundedRect(ctx.getMatrices(), x, y, PANEL_WIDTH, PANEL_HEIGHT, 12, GUI_COLOR.getRGB());

        String title = category.name();
        String icon = category.icon;
        FontUtils.sf_bold[20].drawLeftAligned(ctx.getMatrices(), title, x + (PANEL_WIDTH - (int) FontUtils.sf_bold[20].getWidth(title)) / 2, y + TITLE_MARGIN_TOP + 2, Color.WHITE.getRGB());
        FontUtils.icomoon[20].drawLeftAligned(ctx.getMatrices(), icon, x + (PANEL_WIDTH - (int) FontUtils.sf_bold[20].getWidth(icon)) / 2 - 50, y + TITLE_MARGIN_TOP + 2, Color.WHITE.getRGB());

        {
            int maxBefore = calculateMaxScroll(category);
            float clampedTarget = MathHelper.clamp(scrollTargets.get(category), 0f, (float) maxBefore);
            float clampedOffset = MathHelper.clamp(scrollOffsets.get(category), 0f, (float) maxBefore);
            scrollTargets.put(category, clampedTarget);
            scrollOffsets.put(category, clampedOffset);
        }

        float offset = scrollOffsets.compute(category, (k, v) -> {
            float target = scrollTargets.get(k);
            float current = v;
            float lerped = MathUtil.lerp(current, target, SCROLL_LERP_FACTOR);
            float smoothed = MathUtil.lerp(current, lerped, SCROLL_SMOOTH_FACTOR);

            return smoothed;
        });

        renderScrollbar(ctx, x, y, category, offset);
        ctx.getMatrices().push();
        Scissor.push();
        Scissor.setFromComponentCoordinates(x, y + SCROLL_AREA_Y_OFFSET, PANEL_WIDTH, SCROLL_AREA_HEIGHT);

        float currentY = y + SCROLL_AREA_Y_OFFSET - offset;

        for (Function f : Manager.FUNCTION_MANAGER.getFunctions(category)) {
            if (!isFunctionVisible(f)) continue;

            int functionHeight = FUNCTION_HEIGHT;
            float prog = updateExpandAnimation(f);

            int fullSettingsHeight = computeSettingsHeight(f);
            int animatedSettingsHeight = (int) (fullSettingsHeight * prog);
            int totalHeight = functionHeight + animatedSettingsHeight;

            if (currentY + totalHeight < y + SCROLL_AREA_Y_OFFSET || currentY > y + PANEL_HEIGHT) {
                currentY += totalHeight;
                continue;
            }

            ru.levin.modules.render.ClickGUI clickGUI = Manager.FUNCTION_MANAGER.clickGUI;
            int col1 = f.state ? ColorUtil.getColorStyle(30) : new Color(198, 198, 198).getRGB();
            int col2 = f.state ? ColorUtil.getColorStyle(120) : col1;

            int colorModule = f.state ? ColorUtil.getColorStyle(30, clickGUI.alphaModules.get().intValue()) : new Color(198, 198, 198, clickGUI.alphaModules.get().intValue()).getRGB();
            int colorModule2 = f.state ? ColorUtil.getColorStyle(120, clickGUI.alphaModules.get().intValue()) : colorModule;

            if (clickGUI.filling.get()) {
                RenderUtil.rectRGB(ctx.getMatrices(), x + 4, currentY - 1, PANEL_WIDTH - 8, totalHeight - 1, clickGUI.rounding.get().intValue(), colorModule2, colorModule2, colorModule2, colorModule2);
            }
            if (clickGUI.strike.get()) {
                RenderUtil.drawRoundedBorder(ctx.getMatrices(), x + 4, currentY - 1, PANEL_WIDTH - 8, totalHeight - 1, clickGUI.rounding.get().intValue(), 0f, colorModule2);
            }

            String textToRender;
            if (functionBinding && functions == f) {
                int bindCode = f.getBindCode();
                String keyName = bindCode != 0 ? ClientManager.getKey(bindCode) : "";
                textToRender = bindCode == 0 ? "Binding..." : "Binding... [" + keyName + "]";
            } else {
                textToRender = f.name;
            }

            FontUtils.sf_medium[16].renderGradientText(ctx.getMatrices(), textToRender, x + 10, currentY + 3, col1, col2);

            if (animatedSettingsHeight > 0) {
                float settingY = currentY + functionHeight;
                ctx.getMatrices().push();
                Scissor.push();
                Scissor.setFromComponentCoordinates(x + 1, (int) settingY, PANEL_WIDTH - 2, animatedSettingsHeight);

                for (Setting setting : f.getSettings()) {
                    if (!setting.isVisible()) continue;
                    int settingHeight = 0;

                    if (setting instanceof BooleanSetting booleanSetting) {
                        settingHeight = booleanSettingRenderer.getHeight();
                        booleanSettingRenderer.render(ctx, booleanSetting, x + 10, (int) settingY, PANEL_WIDTH - 20, settingHeight);
                    } else if (setting instanceof BindBooleanSetting bindBooleanSetting) {
                        settingHeight = bindbooleanSettingRenderer.getHeight();
                        bindbooleanSettingRenderer.render(ctx, bindBooleanSetting, x + 10, (int) settingY, PANEL_WIDTH - 20, settingHeight);
                    } else if (setting instanceof BindSetting bindSetting) {
                        settingHeight = bindSettingRenderer.getHeight();
                        bindSettingRenderer.render(ctx, bindSetting, x + 10, (int) settingY - 2, PANEL_WIDTH - 20, settingHeight);
                    } else if (setting instanceof ModeSetting modeSetting) {
                        settingHeight = modeSettingRenderer.getHeight(modeSetting, PANEL_WIDTH - 20);
                        modeSettingRenderer.render(ctx, modeSetting, x + 10, (int) settingY, PANEL_WIDTH - 20, settingHeight);
                    } else if (setting instanceof MultiSetting multiSetting) {
                        settingHeight = multiSettingRenderer.getHeight(multiSetting, PANEL_WIDTH - 20);
                        multiSettingRenderer.render(ctx, multiSetting, x + 10, (int) settingY, PANEL_WIDTH - 20, settingHeight);
                    } else if (setting instanceof SliderSetting sliderSetting) {
                        settingHeight = sliderSettingRenderer.getHeight();
                        sliderSettingRenderer.render(ctx, sliderSetting, x + 10, (int) settingY - 2, PANEL_WIDTH - 20, settingHeight);
                    } else if (setting instanceof TextSetting textSetting) {
                        settingHeight = textSettingRenderer.getHeight();
                        textSettingRenderer.render(ctx, textSetting, x + 10, (int) settingY, PANEL_WIDTH - 20, settingHeight);
                    }

                    settingY += settingHeight + 1;
                }

                Scissor.pop();
                ctx.getMatrices().pop();
            }

            boolean hasSettings = !f.getSettings().isEmpty();
            if (hasSettings) {
                float currentProgress = arrowRotationProgress.getOrDefault(f, f.expanded ? 1f : 0f);
                currentProgress = MathUtil.lerp(currentProgress, f.expanded ? 1f : 0f, 15f);
                if (Math.abs((f.expanded ? 1f : 0f) - currentProgress) < 0.001f) currentProgress = f.expanded ? 1f : 0f;
                arrowRotationProgress.put(f, currentProgress);

                int arrowX = x + PANEL_WIDTH - 15;
                int arrowY = (int) (currentY + FUNCTION_HEIGHT / 2 - 2);

                ctx.getMatrices().push();
                ctx.getMatrices().translate(arrowX, arrowY, 0);
                float angleRad = (float) Math.toRadians(90.0f * currentProgress);
                Quaternionf rotation = new Quaternionf().fromAxisAngleRad(new Vector3f(0, 0, 1), angleRad);
                ctx.getMatrices().multiply(rotation);
                FontUtils.sf_medium[16].drawLeftAligned(ctx.getMatrices(), "→", -4, -FontUtils.sf_medium[16].getHeight() / 2, col1);
                ctx.getMatrices().pop();
            }

            currentY += totalHeight;
        }

        clampScrollForCategory(category);
        Scissor.pop();
        ctx.getMatrices().pop();
    }

    private void renderScrollbar(DrawContext ctx, int x, int y, Type category, float offset) {
        int maxScroll = calculateMaxScroll(category);
        if (maxScroll <= 0) return;

        int scrollbarWidth = 3;
        int scrollbarX = x + PANEL_WIDTH - scrollbarWidth - 1;

        int scrollbarHeight = SCROLL_AREA_HEIGHT - 30;
        int scrollbarY = y + SCROLL_AREA_Y_OFFSET + 15;

        int scrollbarBgColor = new Color(0, 0, 0, 50).getRGB();
        RenderUtil.drawRoundedRect(ctx.getMatrices(), scrollbarX, scrollbarY, scrollbarWidth, scrollbarHeight, 1, scrollbarBgColor);

        float scrollProgress = offset / maxScroll;
        int thumbHeight = Math.max(6, (int) (scrollbarHeight * (SCROLL_AREA_HEIGHT / (float) (SCROLL_AREA_HEIGHT + maxScroll))));
        int thumbY = scrollbarY + (int) (scrollProgress * (scrollbarHeight - thumbHeight));

        int thumbColor = new Color(255, 255, 255, 150).getRGB();
        RenderUtil.drawRoundedRect(ctx.getMatrices(), scrollbarX, thumbY, scrollbarWidth, thumbHeight, 1, thumbColor);
    }
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int totalWidth = renderCategories.size() * (PANEL_WIDTH + PANEL_MARGIN) - PANEL_MARGIN;
        int startX = (width - totalWidth) / 2;
        int startY = (height - PANEL_HEIGHT) / 2;

        int i = 0;
        for (Type category : renderCategories) {
            int px = startX + i++ * (PANEL_WIDTH + PANEL_MARGIN);
            if (mouseX >= px && mouseX <= px + PANEL_WIDTH && mouseY >= startY + SCROLL_AREA_Y_OFFSET && mouseY <= startY + SCROLL_AREA_Y_OFFSET + SCROLL_AREA_HEIGHT) {
                int maxScroll = calculateMaxScroll(category);
                if (maxScroll > 0) {
                    scrollTargets.compute(category, (k, v) -> Math.max(0, Math.min(v - (float) scrollY * SCROLL_SPEED, maxScroll)));
                    return true;
                }
                return false;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private int calculateMaxScroll(Type category) {
        int totalHeight = 0;
        for (Function f : Manager.FUNCTION_MANAGER.getFunctions(category)) {
            if (!isFunctionVisible(f)) continue;
            int functionHeight = FUNCTION_HEIGHT;

            int fullSettingsHeight = computeSettingsHeight(f);
            float prog = expandProgress.getOrDefault(f, f.expanded ? 1f : 0f);
            int animated = (int) Math.round(fullSettingsHeight * prog);

            totalHeight += functionHeight + animated;
        }
        int maxScroll = totalHeight - SCROLL_AREA_HEIGHT;
        return Math.max(0, maxScroll);
    }

    @Override
    public boolean charTyped(char c, int keyCode) {
        if (searchState.focused) {
            String prevText = searchState.text;
            if (searchState.text.length() < 30) {
                String before = searchState.text.substring(0, searchState.cursorPosition);
                String after = searchState.text.substring(searchState.cursorPosition);
                searchState.text = before + c + after;
                searchState.cursorPosition++;
            }
            if (!prevText.equals(searchState.text)) {
                resetScrollForAllCategories();
            }
            return true;
        }

        for (Type category : renderCategories) {
            for (Function function : Manager.FUNCTION_MANAGER.getFunctions(category)) {
                if (!function.expanded) continue;
                for (Setting setting : function.getSettings()) {
                    if (setting instanceof TextSetting textSetting && textSetting.isFocused()) {
                        if (textSettingRenderer.charTyped(textSetting, c, keyCode)) {
                            return true;
                        }
                    }
                }
            }
        }

        return super.charTyped(c, keyCode);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isClose) {
            return true;
        }
        for (Type category : renderCategories) {
            for (Function function : Manager.FUNCTION_MANAGER.getFunctions(category)) {
                if (!isFunctionVisible(function)) continue;
                if (function.expanded) {
                    for (Setting setting : function.getSettings()) {
                        if (!setting.isVisible()) continue;
                        if (setting instanceof BindBooleanSetting bindBooleanSetting) {
                            if (bindBooleanSetting.isListeningForBind()) {
                                if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE) {
                                    bindBooleanSetting.setKey(0);
                                } else {
                                    bindBooleanSetting.setKey(keyCode);
                                }
                                bindBooleanSetting.setListeningForBind(false);
                                return true;
                            }
                        }
                        if (setting instanceof BindSetting bindSetting) {
                            if (bindSetting.isBinding()) {
                                if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE) {
                                    bindSetting.setKey(-1);
                                } else {
                                    bindSetting.setKey(keyCode);
                                }
                                bindSetting.setBinding(false);
                                return true;
                            }
                        }
                        if (setting instanceof TextSetting textSetting) {
                            if (textSetting.isFocused()) {
                                if (textSettingRenderer.keyPressed(textSetting, keyCode, scanCode, modifiers)) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }

        if (functionBinding && functions != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE) {
                functions.setBindCode(0);
            } else {
                functions.setBindCode(keyCode);
            }
            functionBinding = false;
            functions = null;
            return true;
        }

        if (searchState.focused) {
            String prevText = searchState.text;
            switch (keyCode) {
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (searchState.cursorPosition > 0 && !searchState.text.isEmpty()) {
                        searchState.text = searchState.text.substring(0, searchState.cursorPosition - 1) + searchState.text.substring(searchState.cursorPosition);
                        searchState.cursorPosition--;
                    }
                    if (!prevText.equals(searchState.text)) {
                        resetScrollForAllCategories();
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_DELETE -> {
                    if (searchState.cursorPosition < searchState.text.length()) {
                        searchState.text = searchState.text.substring(0, searchState.cursorPosition) + searchState.text.substring(searchState.cursorPosition + 1);
                    }
                    if (!prevText.equals(searchState.text)) {
                        resetScrollForAllCategories();
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_LEFT -> {
                    if (searchState.cursorPosition > 0) searchState.cursorPosition--;
                    return true;
                }
                case GLFW.GLFW_KEY_RIGHT -> {
                    if (searchState.cursorPosition < searchState.text.length()) searchState.cursorPosition++;
                    return true;
                }
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_ESCAPE -> {
                    searchState.focused = false;
                    return true;
                }
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void resetScrollForAllCategories() {
        for (Type category : renderCategories) {
            scrollTargets.put(category, 0f);
            scrollOffsets.put(category, 0f);
        }
    }

    @Override
    public void tick() {
        super.tick();
        long currentTime = System.currentTimeMillis();
        if (currentTime - searchState.lastCursorBlink >= 500) {
            searchState.cursorVisible = !searchState.cursorVisible;
            searchState.lastCursorBlink = currentTime;
        }
    }

    @Override
    public void close() {
        if (isClose) {
            return;
        }
        isClose = true;
        animationOpen.setDirection(Direction.AxisDirection.NEGATIVE);
        animationOpen.reset();
    }

    private int getSearchWidth() {
        return SEARCH_MAX_WIDTH;
    }

    private int getSearchX(int searchWidth) {
        return (width - searchWidth) / 2;
    }

    private int getSearchY() {
        return (height + PANEL_HEIGHT) / 2 + SEARCH_MARGIN_BOTTOM;
    }

    private void renderSearchField(DrawContext ctx) {
        int searchWidth = getSearchWidth();
        int searchX = getSearchX(searchWidth);
        int searchY = getSearchY();
        ru.levin.modules.render.ClickGUI clickGUI = Manager.FUNCTION_MANAGER.clickGUI;
        if (clickGUI.blur.get() && clickGUI.blurSetting.get("Поиск")) {
            drawBlur(ctx.getMatrices(), searchX, searchY, searchWidth, SEARCH_HEIGHT, 6, 12, -1);
        }
        RenderUtil.drawRoundedRect(ctx.getMatrices(), searchX, searchY, searchWidth, SEARCH_HEIGHT, 6, GUI_COLOR.getRGB());

        String displayText;
        int textColor;
        int textX;

        if (searchState.text.isEmpty() && !searchState.focused) {
            displayText = "Поиск...";
            textColor = new Color(255, 255, 255, 120).getRGB();
            int textWidth = (int) FontUtils.sf_medium[18].getWidth(displayText);
            textX = searchX + (searchWidth - textWidth) / 2;
        } else {
            String text = searchState.text;
            if (searchState.focused && searchState.cursorVisible) {
                int pos = Math.min(searchState.cursorPosition, text.length());
                text = text.substring(0, pos) + "|" + text.substring(pos);
            }
            displayText = text;
            textColor = Color.WHITE.getRGB();
            textX = searchX + 6;
        }
        int textY = (int) (searchY + (SEARCH_HEIGHT - FontUtils.sf_medium[18].getHeight()) / 2);
        FontUtils.sf_medium[18].drawLeftAligned(ctx.getMatrices(), displayText, textX, textY, textColor);
    }

public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingSlider != null && button == 0) {
            sliderSettingRenderer.mouseDragged(draggingSlider, mouseX, draggingSliderX, draggingSliderWidth);
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingSlider != null) {
            sliderSettingRenderer.mouseReleased(draggingSlider);
            draggingSlider = null;
            return true;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }



    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int totalWidth = renderCategories.size() * (PANEL_WIDTH + PANEL_MARGIN) - PANEL_MARGIN;
        int startX = (width - totalWidth) / 2;
        int startY = (height - PANEL_HEIGHT) / 2;
        int searchWidth = getSearchWidth();
        int searchX = getSearchX(searchWidth);
        int searchY = getSearchY();

        if (mouseX >= searchX && mouseX <= searchX + searchWidth && mouseY >= searchY && mouseY <= searchY + SEARCH_HEIGHT) {
            searchState.focused = true;
            searchState.cursorPosition = searchState.text.length();
            return true;
        } else {
            searchState.focused = false;
        }
        if (functionBinding && functions != null) {
            int code = -(button + 2);
            functions.setBindCode(code);
            functionBinding = false;
            functions = null;
            return true;
        }

        for (Type category : renderCategories) {
            for (Function function : Manager.FUNCTION_MANAGER.getFunctions(category)) {
                if (!function.expanded) continue;
                for (Setting setting : function.getSettings()) {
                    if (!setting.isVisible()) continue;
                    if (setting instanceof BindBooleanSetting bindBooleanSetting) {
                        if (bindBooleanSetting.isListeningForBind()) {
                            int code = -(button + 2);
                            bindBooleanSetting.setKey(code);
                            bindBooleanSetting.setListeningForBind(false);
                            return true;
                        }
                    }
                    if (setting instanceof BindSetting bindSetting) {
                        if (bindSetting.isBinding()) {
                            int code = -(button + 2);
                            bindSetting.setKey(code);
                            bindSetting.setBinding(false);
                            return true;
                        }
                    }
                }
            }
        }

        int idx = 0;
        for (Type category : renderCategories) {
            int panelX = startX + idx++ * (PANEL_WIDTH + PANEL_MARGIN);
            float offset = scrollOffsets.get(category);

            float currentY = startY + SCROLL_AREA_Y_OFFSET - offset;

            for (Function function : Manager.FUNCTION_MANAGER.getFunctions(category)) {
                if (!isFunctionVisible(function)) continue;

                int functionHeight = FUNCTION_HEIGHT;

                int fullSettingsHeight = computeSettingsHeight(function);
                float prog = expandProgress.getOrDefault(function, function.expanded ? 1f : 0f);
                int animatedSettingsHeight = (int) Math.round(fullSettingsHeight * prog);

                int totalHeight = functionHeight + animatedSettingsHeight;

                if (currentY + totalHeight < startY + SCROLL_AREA_Y_OFFSET) {
                    currentY += totalHeight;
                    continue;
                }
                if (currentY > startY + SCROLL_AREA_Y_OFFSET + SCROLL_AREA_HEIGHT) {
                    break;
                }

                if (mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH && mouseY >= currentY && mouseY <= currentY + functionHeight && mouseY >= startY + SCROLL_AREA_Y_OFFSET && mouseY <= startY + SCROLL_AREA_Y_OFFSET + SCROLL_AREA_HEIGHT) {
                    if (button == 0) {
                        function.toggle();
                        return true;
                    } else if (button == 1) {
                        function.expanded = !function.expanded;
                        clampScrollForCategory(category);
                        return true;
                    } else if (button == 2) {
                        functionBinding = true;
                        functions = function;
                        return true;
                    }
                }

                if (animatedSettingsHeight > 0) {
                    float settingY = currentY + functionHeight;
                    int remaining = animatedSettingsHeight;

                    for (Setting setting : function.getSettings()) {
                        if (!setting.isVisible()) continue;

                        int settingHeight = getSettingRendererHeight(setting, PANEL_WIDTH - 20);
                        if (settingHeight <= 0) continue;

                        int visible = Math.max(0, Math.min(settingHeight, remaining));
                        if (visible <= 0) break;

                        if (mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH && mouseY >= settingY && mouseY <= settingY + visible && mouseY >= startY + SCROLL_AREA_Y_OFFSET && mouseY <= startY + SCROLL_AREA_Y_OFFSET + SCROLL_AREA_HEIGHT) {

                            int settingX = panelX + 10;
                            int settingWidth = PANEL_WIDTH - 20;

                            if (setting instanceof BooleanSetting booleanSetting) {
                                if (booleanSettingRenderer.mouseClicked(booleanSetting, mouseX, mouseY, button, settingX, (int) settingY, settingWidth, visible)) {
                                    return true;
                                }
                            } else if (setting instanceof BindBooleanSetting bindBooleanSetting) {
                                if (bindbooleanSettingRenderer.mouseClicked(bindBooleanSetting, mouseX, mouseY, button, settingX, (int) settingY, settingWidth, visible)) {
                                    return true;
                                }
                            } else if (setting instanceof BindSetting bindSetting) {
                                if (bindSettingRenderer.mouseClicked(bindSetting, mouseX, mouseY, button, settingX, (int) settingY  - 2, settingWidth, visible)) {
                                    return true;
                                }
                            } else if (setting instanceof ModeSetting modeSetting) {
                                if (modeSettingRenderer.mouseClicked(modeSetting, mouseX, mouseY, button, settingX, (int) settingY, settingWidth, visible)) {
                                    return true;
                                }
                            } else if (setting instanceof MultiSetting multiSetting) {
                                if (multiSettingRenderer.mouseClicked(multiSetting, mouseX, mouseY, button, settingX, (int) settingY, settingWidth, visible)) {
                                    return true;
                                }
                            } else if (setting instanceof SliderSetting sliderSetting) {
                                if (sliderSettingRenderer.mouseClicked(sliderSetting, mouseX, mouseY, button, settingX, (int) settingY - 2, settingWidth, visible)) {
                                    draggingSlider = sliderSetting;
                                    draggingSliderX = settingX;
                                    draggingSliderWidth = settingWidth;
                                    return true;
                                }
                            } else if (setting instanceof TextSetting textSetting) {
                                if (textSettingRenderer.mouseClicked(textSetting, mouseX, mouseY, button, settingX, (int) settingY, settingWidth, visible)) {
                                    return true;
                                }
                            }
                        }

                        settingY += settingHeight + 1;
                        remaining -= (settingHeight + 1);
                        if (remaining <= 0) break;
                    }
                }

                currentY += totalHeight;
            }
        }

        for (Type category : renderCategories) {
            for (Function function : Manager.FUNCTION_MANAGER.getFunctions(category)) {
                if (!function.expanded) continue;
                for (Setting setting : function.getSettings()) {
                    if (setting instanceof TextSetting textSetting) {
                        textSetting.setFocused(false);
                    }
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }
    private void renderExpiryText(DrawContext context) {
        String text = Manager.USER_PROFILE.getExpiry();
        int margin = 5;
        int textHeight = (int) FontUtils.durman[18].getHeight();
        int screenHeight = mc.getWindow().getScaledHeight();

        FontUtils.durman[18].drawLeftAligned(context.getMatrices(), "Окончание - " + text, margin + 4, screenHeight - textHeight - margin, Color.WHITE.getRGB());
    }

    private boolean isFunctionVisible(Function function) {
        String searchTextLower = searchState.text.toLowerCase();
        return searchTextLower.isEmpty() || function.name.toLowerCase().contains(searchTextLower) || function.keywords.toLowerCase().contains(searchTextLower);
    }

    @Override
    public void renderBackground(DrawContext drawContext, int mouseX, int mouseY, float delta) {

    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private int computeSettingsHeight(Function f) {
        int settingsHeight = 0;
        for (Setting setting : f.getSettings()) {
            if (!setting.isVisible()) continue;
            if (setting instanceof BooleanSetting) {
                settingsHeight += booleanSettingRenderer.getHeight() + 1;
            } else if (setting instanceof BindBooleanSetting) {
                settingsHeight += bindbooleanSettingRenderer.getHeight() + 1;
            } else if (setting instanceof BindSetting) {
                settingsHeight += bindSettingRenderer.getHeight() + 1;
            } else if (setting instanceof ModeSetting) {
                settingsHeight += modeSettingRenderer.getHeight((ModeSetting) setting, PANEL_WIDTH - 20) + 2;
            } else if (setting instanceof MultiSetting) {
                settingsHeight += multiSettingRenderer.getHeight((MultiSetting) setting, PANEL_WIDTH - 20) + 2;
            } else if (setting instanceof SliderSetting) {
                settingsHeight += sliderSettingRenderer.getHeight() + 1;
            } else if (setting instanceof TextSetting) {
                settingsHeight += textSettingRenderer.getHeight() + 1;
            }
        }
        return Math.max(0, settingsHeight);
    }

    private int getSettingRendererHeight(Setting setting, int width) {
        if (!setting.isVisible()) return 0;
        if (setting instanceof BooleanSetting) {
            return booleanSettingRenderer.getHeight();
        } else if (setting instanceof BindBooleanSetting) {
            return bindbooleanSettingRenderer.getHeight();
        } else if (setting instanceof BindSetting) {
            return bindSettingRenderer.getHeight();
        } else if (setting instanceof ModeSetting modeSetting) {
            return modeSettingRenderer.getHeight(modeSetting, width);
        } else if (setting instanceof MultiSetting multiSetting) {
            return multiSettingRenderer.getHeight(multiSetting, width);
        } else if (setting instanceof SliderSetting) {
            return sliderSettingRenderer.getHeight();
        } else if (setting instanceof TextSetting) {
            return textSettingRenderer.getHeight();
        }
        return 0;
    }

    private void clampScrollForCategory(Type category) {
        int maxScroll = calculateMaxScroll(category);
        float clampedTarget = MathHelper.clamp(scrollTargets.get(category), 0f, (float) maxScroll);
        float clampedOffset = MathHelper.clamp(scrollOffsets.get(category), 0f, (float) maxScroll);
        scrollTargets.put(category, clampedTarget);
        scrollOffsets.put(category, clampedOffset);
    }
}