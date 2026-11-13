package net.spectre.client.util;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.spectre.client.gui.WorldLabel;
import org.joml.Matrix3x2fStack;

/**
 * Shared extras for the base-finding modules: a text log of every flagged chunk (so finds survive a
 * relog) and on-screen labels that name each flagged area and show how far away it is.
 */
@Environment(EnvType.CLIENT)
public final class BaseFinds {
   private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
   private static final Map<String, LongOpenHashSet> SEEN = new HashMap<>();
   private static final int LABEL_CAP = 24;

   private BaseFinds() {
   }

   public static Path logFile() {
      return FabricLoader.getInstance().getGameDir().resolve("spectre").resolve("base-finds.txt");
   }

   /** Appends chunks this module has not logged yet (this session, per server and dimension) to the finds file. */
   public static void track(LongSet chunks, String module) {
      Minecraft mc = Minecraft.getInstance();
      if (chunks.isEmpty() || mc.level == null) {
         return;
      }

      String server = mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "singleplayer";
      String dimension = mc.level.dimension().identifier().toString();
      LongOpenHashSet seen = SEEN.computeIfAbsent(module + "|" + server + "|" + dimension, k -> new LongOpenHashSet());
      StringBuilder fresh = null;
      LongIterator it = chunks.iterator();

      while (it.hasNext()) {
         long key = it.nextLong();
         if (seen.add(key)) {
            if (fresh == null) {
               fresh = new StringBuilder();
            }

            int x = (ChunkPos.getX(key) << 4) + 8;
            int z = (ChunkPos.getZ(key) << 4) + 8;
            fresh.append(LocalDateTime.now().format(STAMP)).append("  ").append(module).append("  ").append(server).append("  ").append(dimension)
               .append("  x=").append(x).append(" z=").append(z).append(System.lineSeparator());
         }
      }

      if (fresh != null) {
         try {
            Path file = logFile();
            Files.createDirectories(file.getParent());
            Files.writeString(file, fresh, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
         } catch (Exception ignored) {
            // Logging is a convenience; never let a full disk or a locked file break rendering.
         }
      }
   }

   /**
    * Draws one label per connected group of flagged chunks, floating above its centre: the module name,
    * the group's size and the horizontal distance from the player. Nearest groups first, capped.
    */
   public static void labels(GuiGraphicsExtractor graphics, LongSet chunks, String title, int argb) {
      Minecraft mc = Minecraft.getInstance();
      if (chunks.isEmpty() || mc.player == null || mc.gui.hud.isHidden()) {
         return;
      }

      LongOpenHashSet left = new LongOpenHashSet(chunks);
      LongArrayList stack = new LongArrayList();
      java.util.List<double[]> groups = new java.util.ArrayList<>();

      while (!left.isEmpty()) {
         long start = left.iterator().nextLong();
         left.remove(start);
         stack.push(start);
         double sx = 0.0, sz = 0.0;
         int n = 0;

         while (!stack.isEmpty()) {
            long key = stack.popLong();
            int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
            sx += (cx << 4) + 8.0;
            sz += (cz << 4) + 8.0;
            n++;
            push(left, stack, ChunkPos.pack(cx + 1, cz));
            push(left, stack, ChunkPos.pack(cx - 1, cz));
            push(left, stack, ChunkPos.pack(cx, cz + 1));
            push(left, stack, ChunkPos.pack(cx, cz - 1));
         }

         double x = sx / n, z = sz / n;
         double dx = x - mc.player.getX(), dz = z - mc.player.getZ();
         groups.add(new double[]{x, z, n, Math.sqrt(dx * dx + dz * dz)});
      }

      groups.sort(java.util.Comparator.comparingDouble(g -> g[3]));
      Font font = mc.font;
      int shown = Math.min(groups.size(), LABEL_CAP);

      for (int i = shown - 1; i >= 0; i--) {
         double[] g = groups.get(i);
         float[] screen = WorldLabel.project(mc, new Vec3(g[0], ChunkOverlay.MARKER_Y + 7.0, g[1]), graphics.guiWidth(), graphics.guiHeight());
         if (screen == null) {
            continue;
         }

         String distance = Math.round(g[3]) + "m";
         String detail = (int)g[2] == 1 ? "1 chunk" : (int)g[2] + " chunks";
         int titleW = font.width(title), distW = font.width(distance), detailW = font.width(detail);
         int row2 = distW + 6 + detailW;
         int w = Math.max(titleW, row2) + 10, h = 9 + 9 + 9;
         float scale = (float)Math.max(0.6, Math.min(1.0, 48.0 / Math.max(1.0, g[3])  + 0.55));
         Matrix3x2fStack pose = graphics.pose();
         pose.pushMatrix();
         pose.translate(screen[0], screen[1]);
         pose.scale(scale, scale);
         int x0 = -w / 2, y0 = -h;
         graphics.fill(x0 + 1, y0 + 1, x0 + w + 1, y0 + h + 1, 0x40000000);
         graphics.fill(x0, y0, x0 + w, y0 + h, 0xB4101218);
         // Accent strip in the module's chunk colour down the left edge.
         graphics.fill(x0, y0, x0 + 2, y0 + h, argb | 0xFF000000);
         graphics.text(font, title, x0 + 6, y0 + 4, 0xFFE9E9EE, true);
         graphics.text(font, distance, x0 + 6, y0 + 15, argb | 0xFF000000, true);
         graphics.text(font, detail, x0 + 6 + distW + 6, y0 + 15, 0xFFA3A3AB, true);
         pose.popMatrix();
      }
   }

   private static void push(LongOpenHashSet left, LongArrayList stack, long key) {
      if (left.remove(key)) {
         stack.push(key);
      }
   }
}
