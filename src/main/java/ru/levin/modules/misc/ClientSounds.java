package ru.levin.modules.misc;
import ru.levin.modules.setting.ModeSetting;
import ru.levin.modules.setting.SliderSetting;
import ru.levin.events.Event;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;

@FunctionAnnotation(name = "ClientSounds", desc = "Звуки", type = Type.Misc)
public class ClientSounds extends Function {
    public final ModeSetting mode = new ModeSetting("Мод", "Type-1", "Type-1", "Type-2", "Type-3","Type-4");
    public final SliderSetting volume = new SliderSetting("Громкость", 100f, 1f, 100f,1f);


    public ClientSounds() {
        addSettings(mode,volume);
    }

    @Override
    public void onEvent(Event event) {
    }
}
