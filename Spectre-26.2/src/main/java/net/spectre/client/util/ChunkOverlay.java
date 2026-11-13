package net.spectre.client.util;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Flagged-chunk highlight used by the base-finding modules: a softly breathing plane at {@link #MARKER_Y},
 * a brighter rim strip inside the outer border, a glowing border line, short corner posts and a fading
 * beacon beam rising from each chunk's centre so flagged chunks can be spotted from far away.
 */
@Environment(EnvType.CLIENT)
public final class ChunkOverlay {
   public static final double MARKER_Y = 63.0;
   private static final float EDGE_SHADE = 0.77F;
   private static final float LINE_WIDTH = 2.0F;
   private static final double RIM = 1.0;
   private static final double POST = 4.0;
   private static final double BEAM = 36.0;
   private static final int BEAM_STEPS = 6;

   private ChunkOverlay() {
   }

   public static void draw(LongSet chunks, int fill, float partialTick) {
      if (!chunks.isEmpty()) {
         double t = System.nanoTime() / 1.0E9;
         float breathe = 0.82F + 0.18F * (float)Math.sin(t * 2.2);
         int baseAlpha = ARGB.alpha(fill);
         GizmoStyle plane = GizmoStyle.fill(ARGB.color(Math.round(baseAlpha * breathe), fill));
         GizmoStyle rim = GizmoStyle.fill(ARGB.color(Math.min(255, Math.round(baseAlpha * 1.6F * breathe)), fill));
         int edge = ARGB.color(Math.min(255, baseAlpha * 3 / 2 + 60), ARGB.scaleRGB(fill, 0.77F));
         int bright = ARGB.color(255, ARGB.srgbLerp(0.35F, fill | 0xFF000000, 0xFFFFFFFF));
         int halo = ARGB.color(Math.min(255, baseAlpha / 2 + 20), fill);
         FoglessLines lines = FoglessLines.forFrame(partialTick);
         LongIterator it = chunks.iterator();

         while (it.hasNext()) {
            long key = it.nextLong();
            int cx = ChunkPos.getX(key);
            int cz = ChunkPos.getZ(key);
            double x0 = cx << 4;
            double z0 = cz << 4;
            double x1 = x0 + 16.0;
            double z1 = z0 + 16.0;
            Gizmos.cuboid(new AABB(x0, MARKER_Y, z0, x1, MARKER_Y, z1), plane).setAlwaysOnTop();
            boolean north = !chunks.contains(ChunkPos.pack(cx, cz - 1));
            boolean south = !chunks.contains(ChunkPos.pack(cx, cz + 1));
            boolean west = !chunks.contains(ChunkPos.pack(cx - 1, cz));
            boolean east = !chunks.contains(ChunkPos.pack(cx + 1, cz));
            if (north) {
               Gizmos.cuboid(new AABB(x0, MARKER_Y, z0, x1, MARKER_Y, z0 + RIM), rim).setAlwaysOnTop();
               edge(lines, x0, z0, x1, z0, edge, halo);
            }

            if (south) {
               Gizmos.cuboid(new AABB(x0, MARKER_Y, z1 - RIM, x1, MARKER_Y, z1), rim).setAlwaysOnTop();
               edge(lines, x0, z1, x1, z1, edge, halo);
            }

            if (west) {
               Gizmos.cuboid(new AABB(x0, MARKER_Y, z0, x0 + RIM, MARKER_Y, z1), rim).setAlwaysOnTop();
               edge(lines, x0, z0, x0, z1, edge, halo);
            }

            if (east) {
               Gizmos.cuboid(new AABB(x1 - RIM, MARKER_Y, z0, x1, MARKER_Y, z1), rim).setAlwaysOnTop();
               edge(lines, x1, z0, x1, z1, edge, halo);
            }

            // Posts on the outer corners of the flagged area.
            if (north && west) post(lines, x0, z0, edge);
            if (north && east) post(lines, x1, z0, edge);
            if (south && west) post(lines, x0, z1, edge);
            if (south && east) post(lines, x1, z1, edge);
            beam(lines, x0 + 8.0, z0 + 8.0, bright, fill, t + cx * 0.37 + cz * 0.61);
         }
      }
   }

   private static void edge(FoglessLines lines, double ax, double az, double bx, double bz, int color, int halo) {
      Vec3 a = new Vec3(ax, MARKER_Y, az), b = new Vec3(bx, MARKER_Y, bz);
      lines.line(a, b, halo, LINE_WIDTH * 3.5F);
      lines.line(a, b, color, LINE_WIDTH);
   }

   private static void post(FoglessLines lines, double x, double z, int color) {
      lines.line(new Vec3(x, MARKER_Y, z), new Vec3(x, MARKER_Y + POST, z), color, LINE_WIDTH);
   }

   /** A thin vertical beam that fades out with height, with a bright pulse travelling up it. */
   private static void beam(FoglessLines lines, double x, double z, int bright, int fill, double phase) {
      double pulse = phase * 0.5 % 1.0;
      for (int i = 0; i < BEAM_STEPS; i++) {
         double y0 = MARKER_Y + BEAM * i / BEAM_STEPS, y1 = MARKER_Y + BEAM * (i + 1) / BEAM_STEPS;
         float fade = 1.0F - (i + 0.5F) / BEAM_STEPS;
         double mid = (i + 0.5) / BEAM_STEPS;
         float lit = (float)Math.max(0.0, 1.0 - Math.abs(mid - pulse) * 5.0);
         int color = ARGB.color(Math.round(255 * fade * (0.45F + 0.55F * lit)), ARGB.srgbLerp(lit, fill | 0xFF000000, bright));
         lines.line(new Vec3(x, y0, z), new Vec3(x, y1, z), ARGB.color(Math.round(ARGB.alpha(color) * 0.3F), color), 5.0F);
         lines.line(new Vec3(x, y0, z), new Vec3(x, y1, z), color, 1.5F);
      }
   }
}
