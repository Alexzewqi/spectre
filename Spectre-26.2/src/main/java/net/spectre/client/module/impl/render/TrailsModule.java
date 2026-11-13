package net.spectre.client.module.impl.render;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayDeque;
import java.util.Iterator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import net.spectre.client.event.EventHandler;
import net.spectre.client.event.impl.Render3DEvent;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ColorSetting;
import net.spectre.client.module.setting.ModeSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.spectre.client.util.FoglessLines;

/** A ribbon of light that follows where you (and optionally other players) have just moved, fading toward its tail. */
@Environment(EnvType.CLIENT)
public final class TrailsModule extends ClientModule {
   private static final String COLOR_ACCENT = "Accent", COLOR_CUSTOM = "Custom", COLOR_RAINBOW = "Rainbow";
   private final BooleanSetting self = this.addSetting("self", new BooleanSetting("My trail", true));
   private final BooleanSetting others = this.addSetting("others", new BooleanSetting("Other players", false));
   private final NumberSetting length = this.addSetting("length", new NumberSetting("Length (s)", 1.5F, 0.3F, 6.0F).step(0.1F));
   private final NumberSetting width = this.addSetting("width", new NumberSetting("Width", 3.0F, 1.0F, 8.0F).step(0.5F));
   private final NumberSetting height = this.addSetting("height", new NumberSetting("Height on body", 0.1F, 0.0F, 1.8F).step(0.1F));
   private final ModeSetting colorMode = this.addSetting("color-mode", new ModeSetting("Color", COLOR_ACCENT, COLOR_ACCENT, COLOR_CUSTOM, COLOR_RAINBOW));
   private final ColorSetting color = this.addSetting("color", new ColorSetting("Custom color", 0xFFD56BEA));
   private final Int2ObjectOpenHashMap<ArrayDeque<Point>> trails = new Int2ObjectOpenHashMap<>();

   public TrailsModule() {
      super("trails", "Trails", "A fading ribbon of light behind you, and behind other players if you want.", ModuleCategory.VISUALS);
      this.color.visibleWhen(() -> this.colorMode.is(COLOR_CUSTOM));
   }

   @Override
   protected void onEnable() {
      this.trails.clear();
   }

   @Override
   protected void onDisable() {
      this.trails.clear();
   }

   @EventHandler
   public void onTick(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null) {
         this.trails.clear();
         return;
      }

      long now = System.nanoTime();
      long keep = (long)(this.length.get() * 1.0E9);

      for (AbstractClientPlayer player : mc.level.players()) {
         boolean mine = player == mc.player;
         if (mine ? !this.self.get() : !this.others.get()) {
            continue;
         }

         ArrayDeque<Point> trail = this.trails.get(player.getId());
         if (trail == null) {
            trail = new ArrayDeque<>();
            this.trails.put(player.getId(), trail);
         }

         Vec3 pos = player.position().add(0.0, this.height.get(), 0.0);
         // Standing still adds nothing, so the ribbon shrinks away behind a player who stops.
         if (trail.isEmpty() || trail.peekLast().pos.distanceToSqr(pos) > 0.0004) {
            trail.addLast(new Point(pos, now));
         }
      }

      Iterator<ArrayDeque<Point>> it = this.trails.values().iterator();

      while (it.hasNext()) {
         ArrayDeque<Point> trail = it.next();
         while (!trail.isEmpty() && now - trail.peekFirst().bornNanos > keep) {
            trail.removeFirst();
         }

         if (trail.isEmpty()) {
            it.remove();
         }
      }
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      if (this.trails.isEmpty()) {
         return;
      }

      long now = System.nanoTime();
      float keep = this.length.get() * 1.0E9F;
      float lineWidth = this.width.get();
      FoglessLines lines = FoglessLines.forFrame(event.partialTick);

      for (ArrayDeque<Point> trail : this.trails.values()) {
         Point previous = null;

         for (Point point : trail) {
            if (previous != null) {
               float fresh = 1.0F - (now - point.bornNanos) / keep;
               if (fresh > 0.02F) {
                  int rgb = this.rgb(point, now);
                  lines.line(previous.pos, point.pos, ARGB.color(Math.round(70.0F * fresh), rgb), lineWidth * 3.0F);
                  lines.line(previous.pos, point.pos, ARGB.color(Math.round(255.0F * fresh), rgb), lineWidth * (0.4F + 0.6F * fresh));
               }
            }

            previous = point;
         }
      }
   }

   private int rgb(Point point, long now) {
      if (this.colorMode.is(COLOR_RAINBOW)) {
         float hue = (float)((point.bornNanos / 1.0E9 * 0.5) % 1.0);
         return java.awt.Color.HSBtoRGB(hue, 0.7F, 1.0F) & 0xFFFFFF;
      } else if (this.colorMode.is(COLOR_CUSTOM)) {
         return this.color.argb() & 0xFFFFFF;
      } else {
         return CosmeticAccent.argb() & 0xFFFFFF;
      }
   }

   private record Point(Vec3 pos, long bornNanos) {
   }
}
