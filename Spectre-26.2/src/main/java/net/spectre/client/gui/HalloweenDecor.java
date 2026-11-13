package net.spectre.client.gui;

import imgui.ImDrawList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.spectre.client.config.ThemeStore;

/** Seasonal decorations for the click GUI, drawn only while the "Halloween" theme preset is active. */
@Environment(EnvType.CLIENT)
public final class HalloweenDecor {
   // ImGui colours are ABGR: these read as orange, dark green and near-black.
   private static final int PUMPKIN = 0xFF1A7AFF;
   private static final int PUMPKIN_DARK = 0xFF0A4AC8;
   private static final int STEM = 0xFF2A6A3A;
   private static final int GLOW = 0xFF4AD8FF;
   private static final int BAT = 0xFF14080F;

   private HalloweenDecor() {
   }

   /** The Halloween preset is only offered, and only decorates, during October. */
   public static boolean inSeason() {
      return java.time.LocalDate.now().getMonth() == java.time.Month.OCTOBER;
   }

   public static boolean active() {
      if (!inSeason()) {
         return false;
      }
      ThemeStore theme = ThemeStore.get();
      for (ThemeStore.Preset preset : ThemeStore.PRESETS) {
         if (preset.name().equals("Halloween")) {
            return theme.matchesPreset(preset);
         }
      }
      return false;
   }

   /** A jack-o'-lantern with a softly flickering candle glow. */
   public static void pumpkin(ImDrawList dl, float cx, float cy, float r, float alpha) {
      float flicker = 0.75F + 0.25F * (float) Math.sin(System.nanoTime() / 1.0E9 * 9.0) * (float) Math.sin(System.nanoTime() / 1.0E9 * 3.7);
      for (int i = 3; i >= 1; i--) {
         dl.addCircleFilled(cx, cy, r + SpectreWidgets.s(3F) * i, col(PUMPKIN, 0.05F * alpha * flicker), 32);
      }
      // Body as three overlapping lobes, with darker ridges between them.
      dl.addEllipseFilled(cx - r * 0.42F, cy, r * 0.62F, r * 0.88F, col(PUMPKIN_DARK, alpha), 0, 24);
      dl.addEllipseFilled(cx + r * 0.42F, cy, r * 0.62F, r * 0.88F, col(PUMPKIN_DARK, alpha), 0, 24);
      dl.addEllipseFilled(cx, cy, r * 0.66F, r * 0.92F, col(PUMPKIN, alpha), 0, 24);
      dl.addRectFilled(cx - r * 0.12F, cy - r * 1.18F, cx + r * 0.14F, cy - r * 0.78F, col(STEM, alpha), r * 0.08F);
      // Carved face lit from inside.
      int lit = col(GLOW, alpha * flicker);
      dl.addTriangleFilled(cx - r * 0.52F, cy - r * 0.08F, cx - r * 0.18F, cy - r * 0.08F, cx - r * 0.35F, cy - r * 0.42F, lit);
      dl.addTriangleFilled(cx + r * 0.18F, cy - r * 0.08F, cx + r * 0.52F, cy - r * 0.08F, cx + r * 0.35F, cy - r * 0.42F, lit);
      dl.addTriangleFilled(cx - r * 0.5F, cy + r * 0.22F, cx + r * 0.5F, cy + r * 0.22F, cx, cy + r * 0.58F, lit);
      dl.addRectFilled(cx - r * 0.1F, cy + r * 0.2F, cx + r * 0.1F, cy + r * 0.34F, col(PUMPKIN, alpha));
   }

   /** A cobweb tucked into a corner; sx/sy (+1 or -1) point from the corner into the frame. */
   public static void cobweb(ImDrawList dl, float x, float y, float size, int sx, int sy, float alpha) {
      int thread = col(0xFFE8E0EC, 0.16F * alpha);
      int spokes = 5;
      float[] px = new float[spokes];
      float[] py = new float[spokes];
      for (int i = 0; i < spokes; i++) {
         double a = Math.PI * 0.5 * i / (spokes - 1);
         px[i] = (float) Math.cos(a) * sx;
         py[i] = (float) Math.sin(a) * sy;
         dl.addLine(x, y, x + px[i] * size, y + py[i] * size, thread, SpectreWidgets.s(1F));
      }
      for (int ring = 1; ring <= 4; ring++) {
         float d = size * ring / 4.4F;
         for (int i = 0; i < spokes - 1; i++) {
            // Sagging threads: the midpoint of each strand dips toward the corner.
            float mx = (px[i] + px[i + 1]) * 0.5F * d * 0.82F, my = (py[i] + py[i + 1]) * 0.5F * d * 0.82F;
            dl.addBezierQuadratic(x + px[i] * d, y + py[i] * d, x + mx, y + my, x + px[i + 1] * d, y + py[i + 1] * d, thread, SpectreWidgets.s(1F), 8);
         }
      }
   }

   /** A few bats drifting across the given area on slow looping paths. */
   public static void bats(ImDrawList dl, float x, float y, float w, float h, float alpha) {
      double t = System.nanoTime() / 1.0E9;
      for (int i = 0; i < 3; i++) {
         double speed = 0.035 + i * 0.012, phase = i * 0.37;
         double u = (t * speed + phase) % 1.0;
         float bx = x + (float) (u * (w + SpectreWidgets.s(60F))) - SpectreWidgets.s(30F);
         float by = y + h * (0.18F + 0.22F * i) + (float) Math.sin(t * 1.3 + i * 2.0) * SpectreWidgets.s(10F);
         float size = SpectreWidgets.s(10F + i * 2F);
         float flap = (float) Math.sin(t * (9.0 + i * 1.7));
         bat(dl, bx, by, size, flap, alpha * 0.85F);
      }
   }

   private static void bat(ImDrawList dl, float cx, float cy, float s, float flap, float alpha) {
      // A faint orange halo so the silhouette reads against dark skies too.
      dl.addCircleFilled(cx, cy, s * 1.5F, col(PUMPKIN, 0.07F * alpha), 16);
      int c = col(BAT, alpha);
      float lift = flap * s * 0.55F;
      dl.addTriangleFilled(cx, cy, cx - s * 1.6F, cy - lift - s * 0.2F, cx - s * 0.6F, cy + s * 0.35F, c);
      dl.addTriangleFilled(cx, cy, cx + s * 1.6F, cy - lift - s * 0.2F, cx + s * 0.6F, cy + s * 0.35F, c);
      dl.addEllipseFilled(cx, cy + s * 0.1F, s * 0.32F, s * 0.45F, c, 0, 12);
      dl.addTriangleFilled(cx - s * 0.3F, cy - s * 0.25F, cx - s * 0.12F, cy - s * 0.25F, cx - s * 0.24F, cy - s * 0.6F, c);
      dl.addTriangleFilled(cx + s * 0.12F, cy - s * 0.25F, cx + s * 0.3F, cy - s * 0.25F, cx + s * 0.24F, cy - s * 0.6F, c);
   }

   private static int col(int abgr, float alpha) {
      return SpectreWidgets.c(SpectreWidgets.withAlpha(abgr, Math.max(0F, Math.min(1F, alpha))));
   }
}
