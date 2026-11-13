package net.spectre.client.module.impl.render;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.spectre.client.chat.ModuleChat;
import net.spectre.client.event.EventHandler;
import net.spectre.client.event.impl.Render2DEvent;
import net.spectre.client.event.impl.Render3DEvent;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.gui.WorldLabel;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.ModuleRegistry;
import net.spectre.client.module.impl.misc.PlayerBypassModule;
import net.spectre.client.module.setting.ActionSetting;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ColorSetting;
import net.spectre.client.module.setting.ModeSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.spectre.client.util.BaseFinds;
import net.spectre.client.util.ChunkOverlay;
import net.spectre.client.util.FoglessLines;
import net.spectre.client.util.TracerEngine;

/**
 * One list for every base finder. Each finder flags chunks by its own method; this module remembers
 * all of them, joins neighbouring flagged chunks into sites, ranks the sites by how many different
 * methods agree, and points at the best one that has not been checked yet. Walking up to a site
 * ticks it off and moves the pointer to the next.
 */
@Environment(EnvType.CLIENT)
public final class BaseRadarModule extends ClientModule {
   private static final int REFRESH_TICKS = 10;
   private static final int JOIN_REACH = 2;
   private static final int MAX_METHODS = 30;
   private static final String SORT_BEST = "Best first";
   private static final String SORT_NEAR = "Nearest first";
   private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
   private final BooleanSetting autoStart = this.addSetting("auto-start", new BooleanSetting("Turn on all finders", true));
   private final ModeSetting sort = this.addSetting("sort", new ModeSetting("Order", SORT_BEST, SORT_BEST, SORT_NEAR));
   private final NumberSetting minMethods = this.addSetting("min-methods", new NumberSetting("Methods that must agree", 1.0F, 1.0F, 5.0F).step(1.0F));
   private final NumberSetting goal = this.addSetting("goal", new NumberSetting("Bases to find", 5.0F, 1.0F, 20.0F).step(1.0F));
   private final NumberSetting visitRadius = this.addSetting("visit-radius", new NumberSetting("Checked when within", 20.0F, 6.0F, 64.0F).step(2.0F));
   private final NumberSetting listSize = this.addSetting("list-size", new NumberSetting("List length", 5.0F, 1.0F, 12.0F).step(1.0F));
   private final BooleanSetting showList = this.addSetting("show-list", new BooleanSetting("Show list", true));
   private final BooleanSetting pointer = this.addSetting("pointer", new BooleanSetting("Tracer and beam to target", true));
   private final ColorSetting color = this.addSetting("color", new ColorSetting("Target color", 0xFF3DFFA8));
   private final Long2IntOpenHashMap methodsByChunk = new Long2IntOpenHashMap();
   private final LongOpenHashSet visited = new LongOpenHashSet();
   private final LongOpenHashSet announced = new LongOpenHashSet();
   private final List<String> methodNames = new ArrayList<>();
   private List<Site> sites = List.of();
   private Site target;
   private int checked;
   private int age;
   private ClientLevel lastLevel;

   public BaseRadarModule() {
      super("base-radar", "Base Radar", "Combines every base finder into one ranked list. Sites that several methods agree on come first, a tracer points at the next one, and walking up to a site ticks it off.", ModuleCategory.BASE_RAID);
      this.usesNotifications();
      this.addSetting("start-finders", new ActionSetting("Turn on all finders now", this::startFinders));
      this.addSetting("copy-target", new ActionSetting("Copy target coordinates", this::copyTarget));
      this.addSetting("skip-target", new ActionSetting("Skip current target", this::skipTarget));
      this.addSetting("clear", new ActionSetting("Forget all sites", this::clearAll));
   }

   @Override
   protected void onEnable() {
      this.clearAll();
      this.lastLevel = Minecraft.getInstance().level;
      if (this.autoStart.get()) {
         this.startFinders();
      }
   }

   @Override
   protected void onDisable() {
      this.clearAll();
      this.lastLevel = null;
   }

   private void startFinders() {
      for (Class<? extends ClientModule> type : List.of(EchoFinderModule.class, SoundEspModule.class, SusChunkFinderModule.class, ChunkScannerModule.class,
         PrimeChunkFinderModule.class, PlayerBypassModule.class)) {
         ClientModule module = ModuleRegistry.get(type);
         if (module != null && !module.isEnabled()) {
            module.setEnabled(true);
         }
      }
   }

   private void copyTarget() {
      if (this.target != null) {
         Minecraft.getInstance().keyboardHandler.setClipboard(Math.round(this.target.x) + " " + Math.round(this.target.z));
      }
   }

   private void skipTarget() {
      if (this.target != null) {
         this.visited.addAll(this.target.chunks);
         this.age = REFRESH_TICKS;
      }
   }

   private void clearAll() {
      this.methodsByChunk.clear();
      this.visited.clear();
      this.announced.clear();
      this.methodNames.clear();
      this.sites = List.of();
      this.target = null;
      this.checked = 0;
      this.age = REFRESH_TICKS;
   }

   @EventHandler
   public void onTick(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != this.lastLevel) {
         this.clearAll();
         this.lastLevel = mc.level;
      }

      if (mc.level == null || mc.player == null || ++this.age < REFRESH_TICKS) {
         return;
      }

      this.age = 0;
      // Finders drop chunks when they unload; the radar keeps them so a site survives flying past it.
      BaseFinds.live((name, chunks) -> {
         if (name.equals(this.name())) {
            return;
         }

         int index = this.methodNames.indexOf(name);
         if (index < 0) {
            if (this.methodNames.size() >= MAX_METHODS) {
               return;
            }

            index = this.methodNames.size();
            this.methodNames.add(name);
         }

         int bit = 1 << index;
         LongIterator it = chunks.iterator();

         while (it.hasNext()) {
            long key = it.nextLong();
            this.methodsByChunk.put(key, this.methodsByChunk.get(key) | bit);
         }
      });
      this.rebuild(mc);
   }

   private void rebuild(Minecraft mc) {
      double px = mc.player.getX(), pz = mc.player.getZ();
      LongOpenHashSet left = new LongOpenHashSet(this.methodsByChunk.keySet());
      LongArrayList stack = new LongArrayList();
      List<Site> next = new ArrayList<>();
      int need = this.minMethods.getInt();
      double reach = this.visitRadius.get();

      while (!left.isEmpty()) {
         long start = left.iterator().nextLong();
         left.remove(start);
         stack.push(start);
         Site site = new Site();

         while (!stack.isEmpty()) {
            long key = stack.popLong();
            int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
            site.chunks.add(key);
            site.methods |= this.methodsByChunk.get(key);
            site.x += (cx << 4) + 8.0;
            site.z += (cz << 4) + 8.0;

            for (int dx = -JOIN_REACH; dx <= JOIN_REACH; dx++) {
               for (int dz = -JOIN_REACH; dz <= JOIN_REACH; dz++) {
                  long near = ChunkPos.pack(cx + dx, cz + dz);
                  if (left.remove(near)) {
                     stack.push(near);
                  }
               }
            }
         }

         site.x /= site.chunks.size();
         site.z /= site.chunks.size();
         site.methodCount = Integer.bitCount(site.methods);
         site.distance = Math.sqrt((site.x - px) * (site.x - px) + (site.z - pz) * (site.z - pz));
         site.visited = anyIn(site.chunks, this.visited);
         if (!site.visited && site.distance <= reach + Math.sqrt(site.chunks.size()) * 8.0) {
            // Close enough to have looked at it: tick it off, including chunks that join it later.
            site.visited = true;
            this.visited.addAll(site.chunks);
            this.checked++;
            if (this.notificationsOn()) {
               ModuleChat.send(this, ModuleChat.body().text("Checked site ").value(this.checked + "/" + this.goal.getInt()).text(" at ")
                  .coords((int)Math.round(site.x), (int)ChunkOverlay.MARKER_Y, (int)Math.round(site.z)));
            }
         } else if (site.visited) {
            this.visited.addAll(site.chunks);
         }

         if (site.methodCount >= need) {
            next.add(site);
         }
      }

      Comparator<Site> order = this.sort.is(SORT_NEAR)
         ? Comparator.comparingDouble(s -> s.distance)
         : Comparator.<Site>comparingInt(s -> -s.methodCount).thenComparingDouble(s -> s.distance);
      next.sort(Comparator.<Site, Boolean>comparing(s -> s.visited).thenComparing(order));
      this.sites = next;
      this.target = next.isEmpty() || next.get(0).visited ? null : next.get(0);

      for (Site site : next) {
         if (!site.visited && site.methodCount >= 2 && !anyIn(site.chunks, this.announced)) {
            this.announced.addAll(site.chunks);
            if (this.notificationsOn()) {
               ModuleChat.send(this, ModuleChat.body().good(site.methodCount + " methods agree").text(" at ")
                  .coords((int)Math.round(site.x), (int)ChunkOverlay.MARKER_Y, (int)Math.round(site.z)).text(": ").value(this.methodList(site.methods)));
            }
         }
      }
   }

   private static boolean anyIn(LongOpenHashSet chunks, LongOpenHashSet set) {
      if (set.isEmpty()) {
         return false;
      }

      LongIterator it = chunks.iterator();

      while (it.hasNext()) {
         if (set.contains(it.nextLong())) {
            return true;
         }
      }

      return false;
   }

   private String methodList(int mask) {
      StringBuilder out = new StringBuilder();

      for (int i = 0; i < this.methodNames.size(); i++) {
         if ((mask & 1 << i) != 0) {
            if (out.length() > 0) {
               out.append(", ");
            }

            out.append(this.methodNames.get(i));
         }
      }

      return out.toString();
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      Minecraft mc = Minecraft.getInstance();
      Site site = this.target;
      if (mc.level == null || mc.player == null || site == null || !this.pointer.get()) {
         return;
      }

      int argb = this.color.argb() | 0xFF000000;
      Vec3 base = new Vec3(site.x, ChunkOverlay.MARKER_Y, site.z);
      TracerEngine.draw(base, argb);
      FoglessLines lines = FoglessLines.forFrame(event.partialTick);
      // A tall beam through the world height so the target reads from any depth or altitude.
      lines.line(new Vec3(site.x, -64.0, site.z), new Vec3(site.x, 320.0, site.z), argb & 0x55FFFFFF, 6.0F);
      lines.line(new Vec3(site.x, -64.0, site.z), new Vec3(site.x, 320.0, site.z), argb, 2.0F);
   }

   @EventHandler
   public void onRender2D(Render2DEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null || mc.gui.screen() != null || mc.gui.hud.isHidden()) {
         return;
      }

      GuiGraphicsExtractor graphics = event.graphics;
      Font font = mc.font;
      int accent = this.color.argb() | 0xFF000000;
      Site site = this.target;
      if (site != null && this.pointer.get()) {
         float[] screen = WorldLabel.project(mc, new Vec3(site.x, mc.player.getEyeY(), site.z), graphics.guiWidth(), graphics.guiHeight());
         if (screen != null) {
            String text = "TARGET " + Math.round(site.distance) + "m";
            int w = font.width(text) + 8, x = Math.round(screen[0]) - w / 2, y = Math.round(screen[1]) - 16;
            graphics.fill(x, y, x + w, y + 12, 0xB4101218);
            graphics.fill(x, y + 11, x + w, y + 12, accent);
            graphics.text(font, text, x + 4, y + 2, accent, true);
         }
      }

      if (!this.showList.get()) {
         return;
      }

      int rows = Math.min(this.listSize.getInt(), this.sites.size());
      String title = "BASE RADAR  " + this.checked + "/" + this.goal.getInt() + " checked";
      int width = Math.max(230, font.width(title) + 14), rowH = 11;
      int x0 = 6, y0 = graphics.guiHeight() / 4, height = 16 + Math.max(1, rows) * rowH + 4;
      graphics.fill(x0, y0, x0 + width, y0 + height, 0xB4101218);
      graphics.fill(x0, y0, x0 + 2, y0 + height, accent);
      graphics.text(font, title, x0 + 7, y0 + 4, this.checked >= this.goal.getInt() ? accent : 0xFFE9E9EE, true);
      if (rows == 0) {
         graphics.text(font, "No sites yet - keep moving", x0 + 7, y0 + 16, 0xFFA3A3AB, true);
         return;
      }

      float yaw = mc.player.getYRot();

      for (int i = 0; i < rows; i++) {
         Site row = this.sites.get(i);
         int y = y0 + 16 + i * rowH;
         double bearing = Math.toDegrees(Math.atan2(-(row.x - mc.player.getX()), row.z - mc.player.getZ()));
         int sector = Math.floorMod(Math.round((float)(bearing - yaw) / 45.0F), 8);
         String left = ARROWS[sector] + " " + Math.round(row.distance) + "m";
         String right = (row.visited ? "checked" : row.methodCount + (row.methodCount == 1 ? " method" : " methods")) + "  " + Math.round(row.x) + ", " + Math.round(row.z);
         int tone = row.visited ? 0xFF6E6E78 : row == site ? accent : 0xFFE9E9EE;
         graphics.text(font, left, x0 + 7, y, tone, true);
         graphics.text(font, right, x0 + width - 5 - font.width(right), y, row.visited ? 0xFF6E6E78 : 0xFFA3A3AB, true);
      }
   }

   private static final class Site {
      final LongOpenHashSet chunks = new LongOpenHashSet();
      int methods;
      int methodCount;
      double x;
      double z;
      double distance;
      boolean visited;
   }
}
