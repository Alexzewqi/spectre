package net.spectre.client.gui;

import net.spectre.client.config.Surface;
import net.spectre.client.config.ThemeStore;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class GuiPalette {
   private static int accent = -624588;
   private static int accentHover = -486564;
   private static int panelBg = -349691361;
   private static int cardBg = -149022441;
   private static int tabBarBg = -149022441;
   private static float rounding = 10.0F;
   private static float animationSpeed = 1.0F;
   // Neutral control colours (chips, tracks, off switches) lightly tinted by the accent,
   // so they sit in the theme instead of reading as a cold blue-grey.
   private static int surface = SpectreTheme.GUI_SURFACE;
   private static int surfaceHover = SpectreTheme.GUI_SURFACE_HOVER;
   private static int switchOff = SpectreTheme.GUI_SWITCH_OFF;

   private GuiPalette() {
   }

   public static void refresh() {
      ThemeStore theme = ThemeStore.get();
      accent = abgr(theme.accent.argbWithAlpha(255));
      accentHover = SpectreTheme.lerpColor(accent, -1, 0.2F);
      surface = SpectreTheme.lerpColor(0xFF2B272E, accent, 0.10F);
      surfaceHover = SpectreTheme.lerpColor(0xFF39343D, accent, 0.16F);
      switchOff = SpectreTheme.lerpColor(0xFF48484E, accent, 0.04F);
      panelBg = fill(theme.panels);
      cardBg = fill(theme.cards);
      tabBarBg = fill(theme.tabBar);
      rounding = theme.rounding.get();
      animationSpeed = theme.animationSpeed.get();
   }

   public static int surface() {
      return surface;
   }

   public static int surfaceHover() {
      return surfaceHover;
   }

   public static int switchOff() {
      return switchOff;
   }

   /** Maps the legacy fixed neutral constants to their accent-tinted equivalents. */
   public static int neutral(int legacy) {
      return switch (legacy) {
         case SpectreTheme.GUI_SURFACE -> surface;
         case SpectreTheme.GUI_SURFACE_HOVER -> surfaceHover;
         case SpectreTheme.GUI_SWITCH_OFF -> switchOff;
         default -> legacy;
      };
   }

   public static int accent() {
      return accent;
   }

   public static int accentHover() {
      return accentHover;
   }

   public static int panelBg() {
      return panelBg;
   }

   public static int cardBg() {
      return cardBg;
   }

   public static int tabBarBg() {
      return tabBarBg;
   }

   public static int fill(Surface surface) {
      return abgr(surface.color.argbWithAlpha(Math.round(surface.opacity.get() * 255.0F)));
   }

   public static float rounding() {
      return rounding;
   }

   public static float animationSpeed() {
      return animationSpeed;
   }

   public static int abgr(int argb) {
      return argb & -16711936 | argb >> 16 & 0xFF | (argb & 0xFF) << 16;
   }
}
