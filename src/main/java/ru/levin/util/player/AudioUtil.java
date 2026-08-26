package ru.levin.util.player;

import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import ru.levin.manager.IMinecraft;
import ru.levin.manager.Manager;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;

public class AudioUtil implements IMinecraft {

    public static void playSound(String name) {
        playResource("sounds/" + name);
    }

    public static void playResource(String path) {
        Identifier id = Identifier.of("duquin", path);
        mc.execute(() -> {
            try {
                Optional<Resource> resourceOpt = mc.getResourceManager().getResource(id);
                if (resourceOpt.isEmpty()) {
                    System.err.println("Sound resource not found: " + id);
                    return;
                }

                Resource resource = resourceOpt.get();
                try (InputStream in = resource.getInputStream()) {
                    byte[] soundData = in.readAllBytes();
                    float volume = (float) (Manager.FUNCTION_MANAGER.clientSounds.volume.get().intValue() / 100f);

                    new Thread(() -> playSoundFromBytes(soundData, volume)).start();
                }
            } catch (Exception e) {
                System.err.println("Error loading sound: " + e.getMessage());
            }
        });
    }

    private static void playSoundFromBytes(byte[] soundData, float volume) {
        try (AudioInputStream encoded = AudioSystem.getAudioInputStream(new ByteArrayInputStream(soundData))) {
            AudioFormat baseFormat = encoded.getFormat();
            AudioFormat pcmFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.getSampleRate(),
                    16,
                    baseFormat.getChannels(),
                    baseFormat.getChannels() * 2,
                    baseFormat.getSampleRate(),
                    false
            );

            try (AudioInputStream pcmStream = AudioSystem.getAudioInputStream(pcmFormat, encoded)) {
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, pcmFormat);
                try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
                    line.open(pcmFormat);

                    FloatControl gainControl = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                    float dB = (float) (20 * Math.log10(Math.max(volume, 0.001f)));
                    gainControl.setValue(Math.min(Math.max(dB, gainControl.getMinimum()), gainControl.getMaximum()));

                    line.start();

                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = pcmStream.read(buffer)) != -1) {
                        line.write(buffer, 0, read);
                    }
                    line.drain();
                }
            }
        } catch (Exception e) {
            System.err.println("Error playing sound: " + e.getMessage());
        }
    }


}