package net.spectre.client.module.impl.client;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import net.spectre.client.gui.ImGuiScreen;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.ModeSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spectre's own theme: an original synthwave loop composed and synthesised in code (no audio files).
 * It is rendered once into memory, then streamed on a daemon thread that fades in and out as the
 * player enters and leaves the places chosen in "Play In".
 */
@Environment(EnvType.CLIENT)
public final class ThemeSongModule extends ClientModule {
   private static final Logger LOGGER = LoggerFactory.getLogger("Spectre");
   private static final int RATE = 32000;
   private static final float BPM = 100F;
   private static final String MENUS = "Menus", MENUS_GUI = "Menus + Spectre GUI", ALWAYS = "Always";
   private final ModeSetting where = this.addSetting("play-in", new ModeSetting("Play In", MENUS_GUI, MENUS, MENUS_GUI, ALWAYS));
   private final NumberSetting volume = this.addSetting("volume", new NumberSetting("Volume", 0.45F, 0.0F, 1.0F).step(0.05F));
   private final NumberSetting fade = this.addSetting("fade-seconds", new NumberSetting("Fade Seconds", 2.5F, 0.2F, 8.0F).step(0.1F));
   private static short[] song;
   private volatile Thread player;
   /** Set when the module turns off: the thread fades to silence, then exits. */
   private volatile boolean stopping;

   public ThemeSongModule() {
      super("theme-song", "Theme Song", "Plays Spectre's own synthwave theme on menus and in the Spectre GUI.", ModuleCategory.CLIENT);
   }

   @Override
   protected void onEnable() {
      this.stopping = false;
      // Re-enabling during a fade-out keeps the same thread, which simply fades back in.
      if (this.player != null && this.player.isAlive()) return;
      Thread thread = new Thread(this::run, "Spectre theme song");
      thread.setDaemon(true);
      this.player = thread;
      thread.start();
   }

   @Override
   protected void onDisable() {
      this.stopping = true;
   }

   private boolean wanted() {
      Minecraft mc = Minecraft.getInstance();
      if (where.is(ALWAYS)) return true;
      if (mc.level == null) return true;
      return where.is(MENUS_GUI) && mc.gui.screen() instanceof ImGuiScreen;
   }

   private void run() {
      AudioFormat format = new AudioFormat(RATE, 16, 1, true, false);
      try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
         line.open(format, RATE / 5 * 2);
         line.start();
         short[] pcm = song();
         byte[] chunk = new byte[RATE / 50 * 2];
         int pos = 0;
         float gain = 0;
         while (this.player == Thread.currentThread()) {
            boolean stop = this.stopping;
            float target = !stop && this.wanted() ? this.volume.get() : 0;
            if (gain == 0 && target == 0) {
               if (stop) break;
               // Silent: hold position so the song resumes where it left off.
               line.flush();
               Thread.sleep(100);
               continue;
            }
            // Linear ramp over "Fade Seconds", heard through a squared curve so it sounds even.
            float rate = 1F / (RATE * this.fade.get());
            for (int i = 0; i < chunk.length / 2; i++) {
               gain += Math.max(-rate, Math.min(rate, target - gain));
               int v = Math.round(pcm[pos] * gain * gain);
               chunk[i * 2] = (byte) v;
               chunk[i * 2 + 1] = (byte) (v >> 8);
               if (++pos == pcm.length) pos = 0;
            }
            line.write(chunk, 0, chunk.length);
         }
         line.drain();
      } catch (InterruptedException ignored) {
      } catch (Exception e) {
         LOGGER.warn("[Spectre] Theme song could not play", e);
      } finally {
         if (this.player == Thread.currentThread()) this.player = null;
      }
   }

   private static synchronized short[] song() {
      if (song == null) song = compose();
      return song;
   }

   // A minor, i–VI–III–VII (Am F C G), 16 bars looped. MIDI note numbers.
   private static final int[][] CHORDS = {{57, 60, 64}, {53, 57, 60}, {48, 52, 55}, {55, 59, 62}};
   private static final int[] BASS = {45, 41, 36, 43};
   // Lead: four 2-bar phrases of eighth notes, 0 = rest.
   private static final int[] LEAD = {
      76, 0, 74, 72, 74, 0, 76, 79,   77, 0, 76, 74, 72, 0, 0, 0,
      72, 0, 74, 76, 79, 0, 76, 74,   74, 0, 72, 71, 71, 0, 0, 0,
      76, 0, 79, 81, 79, 0, 76, 74,   77, 76, 74, 72, 74, 0, 72, 0,
      72, 74, 76, 79, 76, 0, 74, 72,  71, 0, 74, 0, 69, 0, 0, 0,
   };

   private static short[] compose() {
      int bars = 16;
      int beat = Math.round(RATE * 60 / BPM), bar = beat * 4, eighth = beat / 2, sixteenth = beat / 4;
      float[] mix = new float[bar * bars];
      for (int b = 0; b < bars; b++) {
         int chord = b / 2 % 4, start = b * bar;
         // Warm detuned pad holding the chord.
         for (int note : CHORDS[chord]) {
            tone(mix, start, bar, hz(note), 0.07F, Wave.SAW_SOFT, 0.25F, 0.9F, 0.0035);
         }
         // Pulsing bass on eighths.
         for (int e = 0; e < 8; e++) tone(mix, start + e * eighth, eighth, hz(BASS[chord]), 0.20F, Wave.SQUARE_SOFT, 0.005F, 0.6F, 0);
         // Sixteenth-note arpeggio, two octaves up the chord.
         for (int s = 0; s < 16; s++) {
            int[] c = CHORDS[chord];
            int note = c[s % 3] + 12 + (s % 6 >= 3 ? 12 : 0);
            tone(mix, start + s * sixteenth, sixteenth, hz(note), 0.045F, Wave.TRIANGLE, 0.003F, 0.35F, 0);
         }
         // Kick on every beat, soft hat on the off-beats.
         for (int k = 0; k < 4; k++) kick(mix, start + k * beat);
         for (int k = 0; k < 4; k++) hat(mix, start + k * beat + eighth);
         // Lead enters after the first four bars.
         if (b >= 4) {
            int phrase = (b - 4) % 8;
            for (int e = 0; e < 8; e++) {
               int note = LEAD[phrase * 8 + e];
               if (note == 0) continue;
               int len = eighth;
               while (e + 1 < 8 && LEAD[phrase * 8 + e + 1] == 0 && len < eighth * 3) { len += eighth; e++; }
               tone(mix, start + (e + 1) * eighth - len, len, hz(note), 0.11F, Wave.LEAD, 0.01F, 0.75F, 0.002);
            }
         }
      }
      // Simple echo for space, then soft clip.
      int delay = eighth * 3;
      for (int i = delay; i < mix.length; i++) mix[i] += mix[i - delay] * 0.28F;
      // Seamless loop: crossfade the last two seconds (echo tail included) into the opening,
      // so the song fades out into its own fade-in instead of jumping back to bar one.
      int cross = Math.min(RATE * 2, mix.length / 4), length = mix.length - cross;
      for (int i = 0; i < cross; i++) {
         float t = (float) i / cross;
         float in = (float) Math.sin(t * Math.PI / 2), out = (float) Math.cos(t * Math.PI / 2);
         mix[i] = mix[i] * in + mix[length + i] * out;
      }
      short[] out = new short[length];
      for (int i = 0; i < length; i++) out[i] = (short) Math.round(Math.tanh(mix[i] * 1.2) * 26000);
      return out;
   }

   private enum Wave { SAW_SOFT, SQUARE_SOFT, TRIANGLE, LEAD }

   private static float hz(int midi) {
      return (float) (440 * Math.pow(2, (midi - 69) / 12.0));
   }

   /** Adds one note: attack seconds, sustain level then release to the end; detune spreads two voices. */
   private static void tone(float[] mix, int start, int length, float hz, float amp, Wave wave, float attack, float sustain, double detune) {
      int att = Math.max(1, Math.round(attack * RATE)), rel = Math.min(length / 3, RATE / 20);
      for (int i = 0; i < length && start + i < mix.length; i++) {
         double t = (double) i / RATE;
         float env = i < att ? (float) i / att : i > length - rel ? sustain * (length - i) / rel : sustain + (1 - sustain) * (float) Math.exp(-(i - att) / (RATE * 0.15));
         double v = switch (wave) {
            case SAW_SOFT -> (saw(t * hz * (1 + detune)) + saw(t * hz * (1 - detune))) * 0.5;
            case SQUARE_SOFT -> Math.tanh(Math.sin(2 * Math.PI * hz * t) * 3) * 0.8;
            case TRIANGLE -> 1 - 4 * Math.abs((t * hz) % 1 - 0.5);
            case LEAD -> Math.sin(2 * Math.PI * hz * t + 0.6 * Math.sin(2 * Math.PI * 5.5 * t) * Math.min(1, t * 3)) * 0.7 + saw(t * hz * (1 + detune)) * 0.3;
         };
         mix[start + i] += (float) v * amp * env;
      }
   }

   private static double saw(double phase) {
      double p = phase % 1;
      // Gently rounded saw: fewer harsh harmonics than a pure ramp.
      return Math.tanh((2 * p - 1) * 1.6) / Math.tanh(1.6);
   }

   private static void kick(float[] mix, int start) {
      int len = RATE / 6;
      double phase = 0;
      for (int i = 0; i < len && start + i < mix.length; i++) {
         double t = (double) i / RATE;
         phase += (45 + 110 * Math.exp(-t * 30)) / RATE;
         mix[start + i] += (float) (Math.sin(2 * Math.PI * phase) * Math.exp(-t * 14) * 0.32);
      }
   }

   private static void hat(float[] mix, int start) {
      int len = RATE / 25;
      long seed = start * 2654435761L;
      float last = 0;
      for (int i = 0; i < len && start + i < mix.length; i++) {
         seed = seed * 6364136223846793005L + 1442695040888963407L;
         float noise = ((seed >>> 40) & 0xFFFF) / 32768F - 1;
         float high = noise - last;
         last = noise;
         mix[start + i] += high * 0.025F * (1 - (float) i / len);
      }
   }
}
