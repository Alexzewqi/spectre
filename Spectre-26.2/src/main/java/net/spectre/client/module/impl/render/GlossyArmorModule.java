package net.spectre.client.module.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.ColorSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.util.ARGB;

/**
 * Polished, mirror-like worn armour. Every equipment layer gets the glint pass, and the armour
 * glint texture is replaced by soft specular streaks that sweep across the plates like reflected light.
 */
@Environment(EnvType.CLIENT)
public final class GlossyArmorModule extends ClientModule {
   public static volatile GlossyArmorModule active;
   private final ColorSetting shine = this.addSetting("shine-color", new ColorSetting("Shine Color", 0xFFE8F4FF));
   private final NumberSetting intensity = this.addSetting("intensity", new NumberSetting("Intensity", 0.85F, 0.1F, 1.0F).step(0.05F));
   private final NumberSetting sharpness = this.addSetting("sharpness", new NumberSetting("Sharpness", 0.6F, 0.1F, 1.0F).step(0.05F));
   private final NumberSetting base = this.addSetting("base-sheen", new NumberSetting("Base Sheen", 0.12F, 0.0F, 0.5F).step(0.01F));
   private final CustomGlintModule.GlintTexture armor = new CustomGlintModule.GlintTexture(ItemFeatureRenderer.ENCHANTED_GLINT_ARMOR);

   public GlossyArmorModule() {
      super("glossy-armor", "Glossy Armor", "Makes worn armour look polished and reflective with sweeping specular highlights.", ModuleCategory.VISUALS);
   }

   @Override
   protected void onEnable() {
      active = this;
      this.on(TickEvent.TYPE, event -> this.refresh());
   }

   @Override
   protected void onDisable() {
      active = null;
      Minecraft mc = Minecraft.getInstance();
      Runnable restore = () -> {
         if (active != null) return;
         this.armor.restore(mc);
         CustomGlintModule glint = CustomGlintModule.active;
         if (glint != null) glint.forgetArmor();
      };
      if (mc.isSameThread()) restore.run(); else mc.execute(restore);
   }

   private void refresh() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.gui.overlay() != null || RenderSystem.tryGetDevice() == null) return;
      int rgb = this.shine.rgb();
      float strength = this.intensity.get(), sharp = this.sharpness.get(), floor = this.base.get();
      long key = (long) (rgb & 0xFFFFFF) << 24 | (long) Math.round(strength * 255) << 16 | (long) Math.round(sharp * 255) << 8 | Math.round(floor * 255);
      this.armor.write(mc, key, () -> this.reflection(rgb, strength, sharp, floor));
   }

   /**
    * The glint pass is additive, so black leaves the armour untouched and bright pixels read as reflected light:
    * one wide diagonal highlight, a thin secondary glare beside it and a faint even sheen everywhere.
    */
   private int[] reflection(int rgb, float strength, float sharp, float floor) {
      int w = this.armor.width(), h = this.armor.height();
      int[] out = new int[w * h];
      float r = ARGB.red(rgb) / 255F, g = ARGB.green(rgb) / 255F, b = ARGB.blue(rgb) / 255F;
      double wide = 0.004 + (1 - sharp) * 0.02, thin = wide * 0.18;
      for (int y = 0; y < h; y++) {
         for (int x = 0; x < w; x++) {
            double u = (double) x / w, v = (double) y / h;
            double band = u + v * 0.55;
            band -= Math.floor(band);
            double main = Math.exp(-sq(band - 0.32) / wide);
            double glare = 0.55 * Math.exp(-sq(band - 0.52) / thin);
            double light = Math.min(1, floor + (main + glare) * strength);
            out[y * w + x] = ARGB.color(255, channel(light * r), channel(light * g), channel(light * b));
         }
      }
      return out;
   }

   private static double sq(double v) {
      return v * v;
   }

   private static int channel(double v) {
      return (int) Math.round(Math.max(0, Math.min(1, v)) * 255);
   }
}
