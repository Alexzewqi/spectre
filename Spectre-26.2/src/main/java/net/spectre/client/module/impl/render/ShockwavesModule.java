package net.spectre.client.module.impl.render;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.spectre.client.event.EventHandler;
import net.spectre.client.event.EventPriority;
import net.spectre.client.event.Stage;
import net.spectre.client.event.impl.PlayerAttackEntityEvent;
import net.spectre.client.event.impl.Render3DEvent;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ColorSetting;
import net.spectre.client.module.setting.ModeSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.spectre.client.util.FoglessLines;

/**
 * Expanding rings of light. A hit sends a ring out across the ground under the target, a kill sends
 * a bigger double ring with a vertical one, and landing from a fall sends one out around your feet.
 */
@Environment(EnvType.CLIENT)
public final class ShockwavesModule extends ClientModule {
   private static final int SEGMENTS = 48;
   private static final int MAX_WAVES = 64;
   private static final String COLOR_ACCENT = "Accent";
   private static final String COLOR_CUSTOM = "Custom";
   private static final String COLOR_RAINBOW = "Rainbow";
   private final BooleanSetting onHit = this.addSetting("on-hit", new BooleanSetting("On hit", true));
   private final BooleanSetting onKill = this.addSetting("on-kill", new BooleanSetting("On kill", true));
   private final BooleanSetting onLand = this.addSetting("on-land", new BooleanSetting("On landing", true));
   private final BooleanSetting playersOnly = this.addSetting("players-only", new BooleanSetting("Players only", false));
   private final NumberSetting size = this.addSetting("size", new NumberSetting("Size", 2.5F, 0.5F, 8.0F).step(0.25F));
   private final NumberSetting duration = this.addSetting("duration", new NumberSetting("Duration (s)", 0.7F, 0.2F, 2.0F).step(0.05F));
   private final NumberSetting thickness = this.addSetting("thickness", new NumberSetting("Thickness", 3.0F, 1.0F, 8.0F).step(0.5F));
   private final NumberSetting rings = this.addSetting("rings", new NumberSetting("Rings per wave", 2.0F, 1.0F, 4.0F).step(1.0F));
   private final NumberSetting minFall = this.addSetting("min-fall", new NumberSetting("Landing: min fall", 2.0F, 0.5F, 10.0F).step(0.5F));
   private final ModeSetting colorMode = this.addSetting("color-mode", new ModeSetting("Color", COLOR_ACCENT, COLOR_ACCENT, COLOR_CUSTOM, COLOR_RAINBOW));
   private final ColorSetting color = this.addSetting("color", new ColorSetting("Custom color", 0xFFD56BEA));
   private final List<Wave> waves = new ArrayList<>();
   private final List<Watched> watched = new ArrayList<>();
   private double fallStart = Double.NaN;

   public ShockwavesModule() {
      super("shockwaves", "Shockwaves", "Expanding rings of light when you hit or kill something and when you land from a fall.", ModuleCategory.VISUALS);
      this.color.visibleWhen(() -> this.colorMode.is(COLOR_CUSTOM));
      this.minFall.visibleWhen(this.onLand::get);
   }

   @Override
   protected void onEnable() {
      this.waves.clear();
      this.watched.clear();
      this.fallStart = Double.NaN;
      this.on(PlayerAttackEntityEvent.TYPE, Stage.PRE, EventPriority.LOWEST, this::onAttack);
   }

   @Override
   protected void onDisable() {
      this.waves.clear();
      this.watched.clear();
   }

   private void onAttack(PlayerAttackEntityEvent event) {
      Minecraft mc = Minecraft.getInstance();
      Entity target = event.target;
      if (!mc.isSameThread() || mc.player == null || target == null || target == mc.player || !(target instanceof LivingEntity living)) {
         return;
      }

      if (this.playersOnly.get() && !(target instanceof Player)) {
         return;
      }

      if (this.onHit.get()) {
         this.add(new Wave(target.position().add(0.0, 0.05, 0.0), this.size.get(), false));
      }

      if (this.onKill.get()) {
         // Watch the target briefly; if it dies, that was the killing blow.
         this.watched.removeIf(w -> w.entity == living);
         this.watched.add(new Watched(living, System.nanoTime()));
      }
   }

   private void add(Wave wave) {
      if (this.waves.size() >= MAX_WAVES) {
         this.waves.remove(0);
      }

      this.waves.add(wave);
   }

   @EventHandler
   public void onTick(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player == null || mc.level == null) {
         this.waves.clear();
         this.watched.clear();
         return;
      }

      long now = System.nanoTime();
      Iterator<Watched> it = this.watched.iterator();

      while (it.hasNext()) {
         Watched w = it.next();
         if (w.entity.isDeadOrDying() || w.entity.isRemoved() && w.entity.getHealth() <= 0.0F) {
            this.add(new Wave(w.entity.position().add(0.0, 0.05, 0.0), this.size.get() * 1.8F, true));
            it.remove();
         } else if (now - w.since > 1_500_000_000L) {
            it.remove();
         }
      }

      if (this.onLand.get()) {
         if (!player.onGround() && !player.isInWater() && !player.onClimbable() && !player.getAbilities().flying) {
            if (Double.isNaN(this.fallStart) || player.getY() > this.fallStart) {
               this.fallStart = player.getY();
            }
         } else {
            if (player.onGround() && !Double.isNaN(this.fallStart) && this.fallStart - player.getY() >= this.minFall.get()) {
               float scale = (float)Math.min(2.0, 0.8 + (this.fallStart - player.getY()) / 10.0);
               this.add(new Wave(player.position().add(0.0, 0.05, 0.0), this.size.get() * scale, false));
            }

            this.fallStart = Double.NaN;
         }
      }
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      if (this.waves.isEmpty()) {
         return;
      }

      long now = System.nanoTime();
      float life = this.duration.get() * 1.0E9F;
      FoglessLines lines = FoglessLines.forFrame(event.partialTick);
      float width = this.thickness.get();
      int count = this.rings.getInt();
      Iterator<Wave> it = this.waves.iterator();

      while (it.hasNext()) {
         Wave wave = it.next();
         float age = (now - wave.bornNanos) / life;
         if (age >= 1.0F) {
            it.remove();
            continue;
         }

         if (event.frustum != null && !event.frustum.isVisible(new net.minecraft.world.phys.AABB(wave.pos, wave.pos).inflate(wave.size * 1.2))) {
            continue;
         }

         int rgb = this.rgb(wave.pos, now);
         for (int ring = 0; ring < count; ring++) {
            // Later rings start a little behind the first, so a wave reads as a ripple.
            float t = age - ring * 0.14F;
            if (t <= 0.0F) {
               continue;
            }

            float eased = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
            double radius = wave.size * eased;
            int alpha = Math.round(255.0F * (1.0F - t) * (ring == 0 ? 1.0F : 0.6F));
            if (alpha < 4) {
               continue;
            }

            int argb = ARGB.color(alpha, rgb);
            ring(lines, wave.pos, radius, true, ARGB.color(Math.round(alpha * 0.25F), rgb), width * 3.0F);
            ring(lines, wave.pos, radius, true, argb, width);
            if (wave.kill && ring == 0) {
               // A kill also stands a ring upright through the target.
               ring(lines, wave.pos.add(0.0, 1.0, 0.0), radius * 0.7, false, argb, width);
            }
         }
      }
   }

   private int rgb(Vec3 pos, long now) {
      if (this.colorMode.is(COLOR_RAINBOW)) {
         float hue = (float)((now / 1.0E9 * 0.25 + (pos.x + pos.z) * 0.05) % 1.0);
         return java.awt.Color.HSBtoRGB(hue, 0.7F, 1.0F) & 0xFFFFFF;
      } else if (this.colorMode.is(COLOR_CUSTOM)) {
         return this.color.argb() & 0xFFFFFF;
      } else {
         return CosmeticAccent.argb() & 0xFFFFFF;
      }
   }

   private static void ring(FoglessLines lines, Vec3 center, double radius, boolean flat, int argb, float width) {
      Vec3 previous = null;

      for (int i = 0; i <= SEGMENTS; i++) {
         double angle = Math.PI * 2.0 * i / SEGMENTS;
         double a = Math.cos(angle) * radius, b = Math.sin(angle) * radius;
         Vec3 next = flat ? center.add(a, 0.0, b) : center.add(a, b, 0.0);
         if (previous != null) {
            lines.line(previous, next, argb, width);
         }

         previous = next;
      }
   }

   private static final class Wave {
      final Vec3 pos;
      final double size;
      final boolean kill;
      final long bornNanos = System.nanoTime();

      Wave(Vec3 pos, double size, boolean kill) {
         this.pos = pos;
         this.size = size;
         this.kill = kill;
      }
   }

   private record Watched(LivingEntity entity, long since) {
   }
}
