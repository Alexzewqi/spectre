package net.spectre.client.builder;

import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * Turns the player toward the builder's target every rendered frame, the same way
 * mouse look does (Entity.turn moves both the current and previous rotation), so the
 * camera glides instead of jumping once per tick. Speed eases out near the target and
 * varies a little per target, with no overshoot.
 */
@Environment(EnvType.CLIENT)
public final class SmoothAim {
   private static final float MIN_SPEED = 45.0F;   // degrees per second near the target
   private static final float MAX_SPEED = 420.0F;  // degrees per second on long turns
   private static final float EASE = 11.0F;        // speed per degree remaining
   private static final float SETTLED = 0.3F;      // degrees that count as "on target"
   private static final long IDLE_NANOS = 250_000_000L;
   private static float targetYaw;
   private static float targetPitch;
   private static float pace = 1.0F;
   private static long lastFrame;
   private static long lastRequest;

   private SmoothAim() {
   }

   /**
    * Sets the target and reports whether the view has already settled on it.
    * Called from tick code; the actual turning happens in {@link #frame()}.
    */
   static boolean toward(Minecraft mc, float yaw, float pitch) {
      pitch = Mth.clamp(pitch, -90.0F, 90.0F);
      long now = System.nanoTime();
      boolean fresh = now - lastRequest > IDLE_NANOS
         || Math.abs(Mth.wrapDegrees(yaw - targetYaw)) > 3.0F || Math.abs(pitch - targetPitch) > 3.0F;
      if (fresh) {
         // A new target gets its own pace, like a person not turning identically every time.
         pace = 0.85F + ThreadLocalRandom.current().nextFloat() * 0.3F;
      }
      targetYaw = yaw;
      targetPitch = pitch;
      lastRequest = now;
      return remaining(mc) < SETTLED;
   }

   /** The heading the view is settling on, so planning doesn't depend on a mid-turn frame. */
   static float heading(Minecraft mc) {
      return System.nanoTime() - lastRequest <= IDLE_NANOS ? targetYaw : mc.player.getYRot();
   }

   static float remaining(Minecraft mc) {
      if (mc.player == null) {
         return 0.0F;
      }
      float dy = Mth.wrapDegrees(targetYaw - mc.player.getYRot());
      float dp = targetPitch - mc.player.getXRot();
      return (float) Math.hypot(dy, dp);
   }

   /** Per-frame step; does nothing unless the builder asked for a target recently. */
   public static void frame() {
      Minecraft mc = Minecraft.getInstance();
      long now = System.nanoTime();
      float dt = lastFrame == 0L ? 0.016F : Math.min(0.05F, (now - lastFrame) / 1.0E9F);
      lastFrame = now;
      if (mc.player == null || now - lastRequest > IDLE_NANOS) {
         return;
      }
      float dy = Mth.wrapDegrees(targetYaw - mc.player.getYRot());
      float dp = targetPitch - mc.player.getXRot();
      float dist = (float) Math.hypot(dy, dp);
      if (dist < 0.05F) {
         return;
      }
      float speed = Mth.clamp(dist * EASE, MIN_SPEED, MAX_SPEED) * pace;
      float k = Math.min(1.0F, speed * dt / dist);
      // Entity.turn scales its input by 0.15, as for mouse deltas.
      mc.player.turn(dy * k / 0.15, dp * k / 0.15);
   }
}
