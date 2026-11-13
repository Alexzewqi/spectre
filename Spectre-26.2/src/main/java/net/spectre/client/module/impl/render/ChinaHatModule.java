package net.spectre.client.module.impl.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import net.spectre.client.event.EventHandler;
import net.spectre.client.event.impl.Render3DEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ColorSetting;
import net.spectre.client.module.setting.ModeSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.spectre.client.util.FoglessLines;

/** A translucent cone hat floating over a player's head, with a bright rim. */
@Environment(EnvType.CLIENT)
public final class ChinaHatModule extends ClientModule {
   private static final int SEGMENTS = 32;
   private static final String COLOR_ACCENT = "Accent", COLOR_CUSTOM = "Custom", COLOR_RAINBOW = "Rainbow";
   private final BooleanSetting others = this.addSetting("others", new BooleanSetting("On other players", false));
   private final BooleanSetting firstPerson = this.addSetting("first-person", new BooleanSetting("Show in first person", false));
   private final NumberSetting radius = this.addSetting("radius", new NumberSetting("Radius", 0.6F, 0.3F, 1.2F).step(0.05F));
   private final NumberSetting tall = this.addSetting("height", new NumberSetting("Height", 0.3F, 0.1F, 0.8F).step(0.05F));
   private final NumberSetting opacity = this.addSetting("opacity", new NumberSetting("Opacity", 0.45F, 0.1F, 1.0F).step(0.05F));
   private final ModeSetting colorMode = this.addSetting("color-mode", new ModeSetting("Color", COLOR_ACCENT, COLOR_ACCENT, COLOR_CUSTOM, COLOR_RAINBOW));
   private final ColorSetting color = this.addSetting("color", new ColorSetting("Custom color", 0xFFD56BEA));

   public ChinaHatModule() {
      super("china-hat", "China Hat", "A glowing cone hat over your head, and over other players if you want.", ModuleCategory.VISUALS);
      this.color.visibleWhen(() -> this.colorMode.is(COLOR_CUSTOM));
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null) {
         return;
      }

      FoglessLines lines = FoglessLines.forFrame(event.partialTick);
      double time = System.nanoTime() / 1.0E9;

      for (AbstractClientPlayer player : mc.level.players()) {
         boolean mine = player == mc.player;
         if (!mine && !this.others.get() || player.isInvisible() || !player.isAlive()) {
            continue;
         }

         if (mine && mc.options.getCameraType().isFirstPerson() && !this.firstPerson.get()) {
            continue;
         }

         Vec3 base = player.getPosition(event.partialTick).add(0.0, player.getBbHeight() + 0.08, 0.0);
         Vec3 apex = base.add(0.0, this.tall.get(), 0.0);
         double r = this.radius.get();
         Vec3 previous = null;
         int previousRgb = 0;

         for (int i = 0; i <= SEGMENTS; i++) {
            double angle = Math.PI * 2.0 * i / SEGMENTS;
            Vec3 rim = base.add(Math.cos(angle) * r, 0.0, Math.sin(angle) * r);
            int rgb = this.rgb(i / (float)SEGMENTS, time);
            if (previous != null) {
               int fill = ARGB.color(Math.round(255.0F * this.opacity.get()), previousRgb);
               // A triangle is a quad with two corners on the apex; drawn both ways round so it shows from below too.
               Gizmos.rect(apex, previous, rim, apex, GizmoStyle.fill(fill));
               Gizmos.rect(apex, rim, previous, apex, GizmoStyle.fill(fill));
               lines.line(previous, rim, ARGB.color(255, previousRgb), 2.0F);
            }

            previous = rim;
            previousRgb = rgb;
         }
      }
   }

   private int rgb(float around, double time) {
      if (this.colorMode.is(COLOR_RAINBOW)) {
         return java.awt.Color.HSBtoRGB((float)((around + time * 0.2) % 1.0), 0.65F, 1.0F) & 0xFFFFFF;
      } else if (this.colorMode.is(COLOR_CUSTOM)) {
         return this.color.argb() & 0xFFFFFF;
      } else {
         return CosmeticAccent.argb() & 0xFFFFFF;
      }
   }
}
