package net.spectre.client.module.impl.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ColorSetting;
import net.spectre.client.module.setting.ModeSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.PlayerSkin;

/** Client-side Spectre capes, drawn in code into a cape texture and worn by the local player. */
@Environment(EnvType.CLIENT)
public final class CapesModule extends ClientModule {
   private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("spectre", "cape/current");
   private static final ClientAsset.Texture ASSET = new ClientAsset.ResourceTexture(TEXTURE, TEXTURE);
   private final ModeSetting design = this.addSetting("design", new ModeSetting("Design", "Spectre", "Spectre", "Galaxy", "Aurora", "Ember", "Ocean", "Void", "Custom"));
   private final ColorSetting color = this.addSetting("color", new ColorSetting("Cape Color", 0xFF56DCCB));
   private final BooleanSetting elytra = this.addSetting("elytra", new BooleanSetting("On Elytra", true));
   private DynamicTexture texture;
   private String painted = "";

   public CapesModule() {
      super("capes", "Capes", "Wear a Spectre cape (only you see it).", ModuleCategory.VISUALS);
      this.color.visibleWhen(() -> this.design.is("Custom"));
   }

   /** Puts the cape on the local player's skin; called from the avatar render-state hook. */
   public PlayerSkin apply(PlayerSkin skin) {
      if (!this.ensureTexture()) return skin;
      return new PlayerSkin(skin.body(), ASSET, this.elytra.get() ? ASSET : skin.elytra(), skin.model(), skin.secure());
   }

   private boolean ensureTexture() {
      String key = this.design.get() + ":" + this.color.rgb();
      if (key.equals(this.painted) && this.texture != null) return true;
      Minecraft mc = Minecraft.getInstance();
      if (!mc.isSameThread()) return this.texture != null;
      NativeImage image = new NativeImage(64, 32, true);
      paint(image, this.design.get(), this.color.rgb());
      if (this.texture == null) {
         this.texture = new DynamicTexture(() -> "Spectre cape", image);
         mc.getTextureManager().register(TEXTURE, this.texture);
      } else {
         this.texture.setPixels(image);
         this.texture.upload();
      }
      this.painted = key;
      return true;
   }

   /** Fills the whole 64x32 sheet (cape and elytra regions) and adds an emblem on the cape's back face. */
   private static void paint(NativeImage image, String design, int custom) {
      for (int y = 0; y < 32; y++) {
         for (int x = 0; x < 64; x++) {
            double u = x / 64.0, v = y / 32.0;
            int argb = switch (design) {
               case "Galaxy" -> galaxy(x, y, u, v);
               case "Aurora" -> mix(0xFF0B1E3A, mix(0xFF2EE6A8, 0xFF8F5BFF, wave(u * 3 + v)), Math.pow(1 - v, 0.6));
               case "Ember" -> mix(0xFF2A0606, mix(0xFFFF6A00, 0xFFFFD000, v), Math.pow(v, 0.8) * (0.75 + 0.25 * wave(u * 9 + v * 4)));
               case "Ocean" -> mix(0xFF03172E, 0xFF1FB5D6, 0.35 + 0.45 * wave(v * 6 + Math.sin(u * 12) * 0.6));
               case "Void" -> mix(0xFF050507, 0xFF2A1240, 0.2 + 0.3 * wave(u * 4 - v * 3));
               case "Custom" -> mix(0xFF000000 | custom, 0xFF0A0A0E, v * 0.7);
               default -> mix(0xFF0E1A22, 0xFF56DCCB, 0.15 + 0.5 * Math.pow(1 - v, 2));
            };
            // Darker rim around the cape panel edges.
            if (x <= 22 && y <= 17 && (x == 0 || x == 11 || x == 22 || y == 0 || y == 17)) argb = mix(argb, 0xFF000000, 0.45);
            image.setPixel(x, y, argb);
         }
      }
      // Diamond emblem centred on the back face (x 1..10, y 1..16).
      int emblem = design.equals("Ember") ? 0xFFFFF2C0 : 0xFFF2FFFD;
      for (int y = 0; y < 7; y++) {
         int half = y < 4 ? y : 6 - y;
         for (int dx = -half; dx <= half; dx++) {
            int px = 5 + dx, py = 5 + y;
            if (px >= 1 && px <= 10) image.setPixel(px, py, Math.abs(dx) == half ? emblem : mix(emblem, 0xFF000000, 0.35));
         }
      }
   }

   private static int galaxy(int x, int y, double u, double v) {
      int base = mix(0xFF0A0418, 0xFF3A1670, 0.3 + 0.4 * wave(u * 2.5 + v * 1.5));
      long h = (x * 73856093L) ^ (y * 19349663L);
      h = (h ^ (h >>> 13)) * 0x5bd1e995L;
      if ((h & 31) == 0) return 0xFFFFFFFF;
      if ((h & 63) == 1) return 0xFFB9A8FF;
      return base;
   }

   private static double wave(double t) {
      return 0.5 + 0.5 * Math.sin(t * Math.PI);
   }

   private static int mix(int a, int b, double t) {
      t = Math.max(0, Math.min(1, t));
      return ARGB.color(255,
         (int) Math.round(ARGB.red(a) + (ARGB.red(b) - ARGB.red(a)) * t),
         (int) Math.round(ARGB.green(a) + (ARGB.green(b) - ARGB.green(a)) * t),
         (int) Math.round(ARGB.blue(a) + (ARGB.blue(b) - ARGB.blue(a)) * t));
   }
}
