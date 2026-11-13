package net.spectre.client.gui;

import net.spectre.client.config.ThemeStore;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** Soft interface sounds for the Spectre GUI, all played through Minecraft's UI channel. */
@Environment(EnvType.CLIENT)
public final class GuiSounds {
   private static int lastHover;
   private static long lastHoverAt;

   private GuiSounds() {
   }

   public static void open() {
      play(SoundEvents.UI_TOAST_IN, 1.25F, 0.55F);
   }

   public static void close() {
      play(SoundEvents.UI_TOAST_OUT, 1.35F, 0.45F);
   }

   public static void click() {
      play(SoundEvents.UI_BUTTON_CLICK.value(), 1.7F, 0.22F);
   }

   /** Rising chime for on, lower one for off. */
   public static void toggle(boolean on) {
      play(SoundEvents.NOTE_BLOCK_PLING.value(), on ? 1.6F : 1.05F, 0.28F);
   }

   /** A faint tick when the pointer moves onto a new item; rate-limited and off by default. */
   public static void hover(int id) {
      if (id == lastHover) return;
      lastHover = id;
      long now = System.currentTimeMillis();
      if (now - lastHoverAt < 45 || !ThemeStore.get().hoverSounds.get()) return;
      lastHoverAt = now;
      play(SoundEvents.UI_BUTTON_CLICK.value(), 2.0F, 0.06F);
   }

   private static void play(SoundEvent sound, float pitch, float volume) {
      if (!ThemeStore.get().uiSounds.get()) return;
      Minecraft mc = Minecraft.getInstance();
      if (mc.getSoundManager() != null) mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
   }
}
