package net.spectre.client.module.impl.render;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.spectre.client.chat.ModuleChat;
import net.spectre.client.event.EventHandler;
import net.spectre.client.event.impl.PacketAppliedEvent;
import net.spectre.client.event.impl.Render2DEvent;
import net.spectre.client.event.impl.Render3DEvent;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.gui.ChallengeToast;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ColorSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.spectre.client.util.BaseFinds;
import net.spectre.client.util.ChunkAreas;
import net.spectre.client.util.ChunkOverlay;
import net.spectre.client.util.FoglessLines;
import net.spectre.client.util.LevelUtil;

/**
 * Finds the way down to a base instead of the base. To get underground a player digs a shaft, and a
 * shaft is a one-block hole going straight down with solid wall on all four sides for many blocks,
 * often with a ladder, water or scaffolding in it. Natural caves almost never do that. The part of
 * a shaft above the hidden layer is ordinary terrain the server sends as it is, so it can be read
 * from the surface.
 */
@Environment(EnvType.CLIENT)
public final class ShaftFinderModule extends ClientModule {
   private static final long SCAN_BUDGET_NANOS = 2_500_000L;
   private static final int MAX_SHAFTS = 2000;
   private final NumberSetting minDepth = this.addSetting("min-depth", new NumberSetting("Minimum depth", 10.0F, 5.0F, 40.0F).step(1.0F));
   private final NumberSetting floorY = this.addSetting("floor-y", new NumberSetting("Scan down to Y", 0.0F, -64.0F, 60.0F).step(1.0F));
   private final BooleanSetting climbOnly = this.addSetting("climb-only", new BooleanSetting("Only with ladder, water or scaffolding", true));
   private final BooleanSetting reachFloor = this.addSetting("reach-floor", new BooleanSetting("Only shafts that reach the hidden layer", true));
   private final ColorSetting shaftColor = this.addSetting("shaft-color", new ColorSetting("Shaft color", 0xFFFFE066));
   private final ColorSetting chunkColor = this.addSetting("chunk-color", new ColorSetting("Chunk color", 0x60FF8A3C));
   private final BooleanSetting chunkTracers = this.addSetting("chunk-tracers", new BooleanSetting("Chunk tracers", true));
   private final BooleanSetting distanceLabels = this.addSetting("distance-labels", new BooleanSetting("Distance labels", true));
   private final BooleanSetting logFinds = this.addSetting("log-finds", new BooleanSetting("Save finds to file", true));
   private final Long2ObjectOpenHashMap<List<Shaft>> shafts = new Long2ObjectOpenHashMap<>();
   private final LongOpenHashSet flagged = new LongOpenHashSet();
   private final LongOpenHashSet announced = new LongOpenHashSet();
   private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
   private final LongOpenHashSet queued = new LongOpenHashSet();
   private final ChunkAreas chunkAreas = new ChunkAreas();
   private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
   private ClientLevel lastLevel;
   private int total;

   public ShaftFinderModule() {
      super("shaft-finder", "Shaft Finder", "Finds shafts worth following: by default only one-block holes with a ladder, water or scaffolding that run all the way down into the hidden layer, so abandoned dig-downs are ignored. Works from the surface because the upper part of a shaft is not hidden. Follow one down to reach a base.", ModuleCategory.BASE_RAID);
      this.usesNotifications();
      this.minDepth.onChange(this::rescan);
      this.floorY.onChange(this::rescan);
      this.climbOnly.onChange(this::rescan);
      this.reachFloor.onChange(this::rescan);
   }

   @Override
   protected void onEnable() {
      this.rescan();
   }

   @Override
   protected void onDisable() {
      this.clearAll();
      this.lastLevel = null;
   }

   private void clearAll() {
      this.shafts.clear();
      this.flagged.clear();
      this.announced.clear();
      this.queue.clear();
      this.queued.clear();
      this.total = 0;
   }

   private void rescan() {
      this.clearAll();
      Minecraft mc = Minecraft.getInstance();
      this.lastLevel = mc.level;
      if (mc.level == null || mc.player == null) {
         return;
      }

      int radius = mc.options.renderDistance().get() + 1;
      ChunkPos center = mc.player.chunkPosition();

      for (int x = center.x() - radius; x <= center.x() + radius; x++) {
         for (int z = center.z() - radius; z <= center.z() + radius; z++) {
            if (LevelUtil.isLoaded(mc.level, x, z)) {
               this.enqueue(ChunkPos.pack(x, z));
            }
         }
      }
   }

   private void enqueue(long key) {
      if (this.queued.add(key)) {
         this.queue.enqueue(key);
      }
   }

   @EventHandler
   public void onPacketApplied(PacketAppliedEvent event) {
      if (event.packet instanceof ClientboundLevelChunkWithLightPacket chunk) {
         // A shaft on a chunk border needs its neighbour loaded to see all four walls, so look again there too.
         int cx = chunk.getX(), cz = chunk.getZ();
         this.enqueue(ChunkPos.pack(cx, cz));
         this.enqueue(ChunkPos.pack(cx + 1, cz));
         this.enqueue(ChunkPos.pack(cx - 1, cz));
         this.enqueue(ChunkPos.pack(cx, cz + 1));
         this.enqueue(ChunkPos.pack(cx, cz - 1));
      }
   }

   @EventHandler
   public void onTick(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level != this.lastLevel) {
         this.rescan();
      }

      if (level == null || mc.player == null) {
         return;
      }

      long deadline = System.nanoTime() + SCAN_BUDGET_NANOS;

      while (!this.queue.isEmpty() && System.nanoTime() < deadline) {
         long key = this.queue.dequeueLong();
         this.queued.remove(key);
         int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
         if (!LevelUtil.isLoaded(level, cx, cz)) {
            continue;
         }

         List<Shaft> found = this.scan(level, level.getChunk(cx, cz));
         List<Shaft> before = this.shafts.get(key);
         if (found.isEmpty()) {
            // Keep earlier finds: a chunk resent with the lower layer hidden must not erase what was seen from below.
            if (before == null) {
               this.flagged.remove(key);
            }
         } else {
            this.total += found.size() - (before == null ? 0 : before.size());
            this.shafts.put(key, found);
            this.flagged.add(key);
            if (this.announced.add(key) && this.notificationsOn()) {
               Shaft first = found.get(0);
               ModuleChat.send(this, ModuleChat.body().name(first.kind).text(" at ").coords(first.x, first.top, first.z).text(", ").value(first.depth() + " blocks deep")
                  .text(found.size() > 1 ? " (+" + (found.size() - 1) + " more in this chunk)" : ""));
               ChallengeToast.addOrUpdate(new ItemStack(Items.LADDER), this.name(), Component.literal(first.x + ", " + first.top + ", " + first.z), true);
            }
         }
      }
   }

   private List<Shaft> scan(ClientLevel level, LevelChunk chunk) {
      List<Shaft> out = new ArrayList<>();
      if (this.total >= MAX_SHAFTS) {
         return out;
      }

      int need = this.minDepth.getInt();
      int floor = Math.max(level.getMinY(), this.floorY.getInt());
      int baseX = chunk.getPos().getMinBlockX(), baseZ = chunk.getPos().getMinBlockZ();

      for (int dx = 0; dx < 16; dx++) {
         for (int dz = 0; dz < 16; dz++) {
            int x = baseX + dx, z = baseZ + dz;
            int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, dx, dz) + 1;
            int run = 0, runTop = 0;
            boolean climb = false, water = false;

            for (int y = top; y >= floor; y--) {
               BlockState state = chunk.getBlockState(this.cursor.set(x, y, z));
               boolean walled = open(state) && this.wall(level, x + 1, y, z) && this.wall(level, x - 1, y, z) && this.wall(level, x, y, z + 1) && this.wall(level, x, y, z - 1);
               if (walled) {
                  if (run == 0) {
                     runTop = y;
                     climb = false;
                     water = false;
                  }

                  run++;
                  if (state.getBlock() instanceof LadderBlock || state.getBlock() instanceof ScaffoldingBlock || state.getBlock() instanceof VineBlock) {
                     climb = true;
                  } else if (!state.getFluidState().isEmpty()) {
                     water = true;
                  }
               }

               if (!walled || y == floor) {
                  // A shaft that stops short of the hidden layer is an abandoned dig; one that runs into it leads somewhere.
                  boolean reaches = walled && y == floor;
                  if (run >= need && (climb || water || !this.climbOnly.get()) && (reaches || !this.reachFloor.get())) {
                     out.add(new Shaft(x, z, runTop, runTop - run + 1, climb ? "Ladder shaft" : water ? "Water shaft" : "Dug shaft"));
                  }

                  run = 0;
               }
            }
         }
      }

      return out;
   }

   /** Something a player can pass down through: air, liquid, or a ladder, vine or scaffolding. */
   private static boolean open(BlockState state) {
      return state.isAir() || !state.getFluidState().isEmpty() || state.getBlock() instanceof LadderBlock || state.getBlock() instanceof ScaffoldingBlock
         || state.getBlock() instanceof VineBlock;
   }

   /** Ground or building blocks. Leaves and logs do not count, or gaps between tree trunks would look like shafts. */
   private boolean wall(ClientLevel level, int x, int y, int z) {
      if (!LevelUtil.isLoaded(level, x >> 4, z >> 4)) {
         return false;
      }

      BlockState state = level.getBlockState(this.cursor.set(x, y, z));
      return !open(state) && !(state.getBlock() instanceof LeavesBlock) && !state.is(BlockTags.LOGS) && state.getCollisionShape(level, this.cursor).isEmpty() == false;
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null || this.flagged.isEmpty()) {
         return;
      }

      BaseFinds.publish(this.name(), this.flagged);
      ChunkOverlay.draw(this.flagged, this.chunkColor.argb(), event.partialTick);
      if (this.logFinds.get()) {
         BaseFinds.track(this.flagged, this.name());
      }

      if (this.chunkTracers.get()) {
         this.chunkAreas.drawTracers(this.flagged, ChunkOverlay.MARKER_Y, ARGB.opaque(this.chunkColor.argb()));
      }

      int argb = this.shaftColor.argb() | 0xFF000000;
      Vec3 eye = mc.gameRenderer.mainCamera().position();
      FoglessLines lines = FoglessLines.forFrame(event.partialTick);

      for (List<Shaft> list : this.shafts.values()) {
         for (Shaft shaft : list) {
            AABB box = new AABB(shaft.x, shaft.bottom, shaft.z, shaft.x + 1.0, shaft.top + 1.0, shaft.z + 1.0);
            if (box.distanceToSqr(eye) < 256.0 * 256.0 && (event.frustum == null || event.frustum.isVisible(box))) {
               lines.box(box, argb, 2.0F);
            }
         }
      }
   }

   @EventHandler
   public void onRender2D(Render2DEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && this.distanceLabels.get() && mc.gui.screen() == null) {
         BaseFinds.labels(event.graphics, this.flagged, this.name(), this.chunkColor.argb());
      }
   }

   private record Shaft(int x, int z, int top, int bottom, String kind) {
      int depth() {
         return this.top - this.bottom + 1;
      }
   }
}
