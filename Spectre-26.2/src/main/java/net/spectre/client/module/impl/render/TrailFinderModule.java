package net.spectre.client.module.impl.render;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.spectre.client.chat.ModuleChat;
import net.spectre.client.event.EventHandler;
import net.spectre.client.event.impl.PacketAppliedEvent;
import net.spectre.client.event.impl.Render3DEvent;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.ActionSetting;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ColorSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.spectre.client.util.LevelUtil;

/**
 * Tells chunks someone has already been in from chunks nobody has loaded before, so a player's
 * travel route shows up as a trail leading to where they live.
 *
 * <p>Water and lava only start flowing once a chunk is loaded by a player. A chunk that arrives with
 * liquid already spread out was loaded earlier (visited). A chunk whose liquid starts to flow while we
 * watch was generated just now (fresh). Chunks with no flowing liquid in view stay unmarked.
 */
@Environment(EnvType.CLIENT)
public final class TrailFinderModule extends ClientModule {
   private static final long SCAN_BUDGET_NANOS = 2_000_000L;
   private static final int MEMORY_CAP = 60_000;
   private final BooleanSetting showVisited = this.addSetting("show-visited", new BooleanSetting("Show visited chunks", true));
   private final ColorSetting visitedColor = this.addSetting("visited-color", new ColorSetting("Visited color", 0x5034D58A));
   private final BooleanSetting showFresh = this.addSetting("show-fresh", new BooleanSetting("Show fresh chunks", true));
   private final ColorSetting freshColor = this.addSetting("fresh-color", new ColorSetting("Fresh color", 0x38FF4D5E));
   private final NumberSetting markerY = this.addSetting("marker-y", new NumberSetting("Marker Y", 63.0F, -64.0F, 320.0F).step(1.0F));
   private final NumberSetting drawRadius = this.addSetting("draw-radius", new NumberSetting("Draw radius (chunks)", 32.0F, 4.0F, 96.0F).step(4.0F));
   private final BooleanSetting trailAlert = this.addSetting("trail-alert", new BooleanSetting("Alert when a trail starts", true));
   private final LongOpenHashSet visited = new LongOpenHashSet();
   private final LongOpenHashSet fresh = new LongOpenHashSet();
   private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
   private final LongOpenHashSet queued = new LongOpenHashSet();
   private ClientLevel lastLevel;
   private boolean onTrail;

   public TrailFinderModule() {
      super("trail-finder", "Trail Finder", "Marks chunks someone has already loaded (green) apart from chunks generated just now (red), using how water and lava have flowed. Follow the green through the red to reach a base. Needs no underground blocks; useless on a fully pre-generated world.", ModuleCategory.BASE_RAID);
      this.usesNotifications();
      this.visitedColor.visibleWhen(this.showVisited::get);
      this.freshColor.visibleWhen(this.showFresh::get);
      this.addSetting("clear", new ActionSetting("Clear marks", this::clearAll));
   }

   @Override
   protected void onEnable() {
      this.clearAll();
      Minecraft mc = Minecraft.getInstance();
      this.lastLevel = mc.level;
      this.queueLoaded(mc);
   }

   @Override
   protected void onDisable() {
      this.clearAll();
      this.lastLevel = null;
   }

   private void clearAll() {
      this.visited.clear();
      this.fresh.clear();
      this.queue.clear();
      this.queued.clear();
      this.onTrail = false;
   }

   private void queueLoaded(Minecraft mc) {
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
      if (!this.visited.contains(key) && !this.fresh.contains(key) && this.queued.add(key)) {
         this.queue.enqueue(key);
      }
   }

   @EventHandler
   public void onPacketApplied(PacketAppliedEvent event) {
      if (event.packet instanceof ClientboundLevelChunkWithLightPacket chunk) {
         this.enqueue(ChunkPos.pack(chunk.getX(), chunk.getZ()));
      } else if (event.packet instanceof ClientboundBlockUpdatePacket block) {
         this.blockChanged(block.getPos(), block.getBlockState());
      } else if (event.packet instanceof ClientboundSectionBlocksUpdatePacket section) {
         section.runUpdates(this::blockChanged);
      }
   }

   /** Liquid that starts spreading after the chunk arrived means the chunk had never been loaded before. */
   private void blockChanged(BlockPos pos, BlockState state) {
      if (!flowing(state)) {
         return;
      }

      long key = ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4);
      if (!this.visited.contains(key)) {
         this.fresh.add(key);
      }
   }

   private static boolean flowing(BlockState state) {
      FluidState fluid = state.getFluidState();
      return !fluid.isEmpty() && !fluid.isSource();
   }

   @EventHandler
   public void onTick(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level != this.lastLevel) {
         this.clearAll();
         this.lastLevel = level;
         this.queueLoaded(mc);
      }

      if (level == null || mc.player == null) {
         return;
      }

      long deadline = System.nanoTime() + SCAN_BUDGET_NANOS;

      while (!this.queue.isEmpty() && System.nanoTime() < deadline) {
         long key = this.queue.dequeueLong();
         this.queued.remove(key);
         int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
         if (LevelUtil.isLoaded(level, cx, cz) && !this.fresh.contains(key) && arrivedFlowing(level.getChunk(cx, cz))) {
            this.visited.add(key);
         }
      }

      if (this.visited.size() + this.fresh.size() > MEMORY_CAP) {
         this.visited.clear();
         this.fresh.clear();
      }

      // Standing in a visited chunk with fresh ones around is the start of somebody's route.
      long here = ChunkPos.pack(mc.player.chunkPosition().x(), mc.player.chunkPosition().z());
      boolean now = this.visited.contains(here);
      if (now && !this.onTrail && this.trailAlert.get() && this.notificationsOn() && this.freshNearby(here)) {
         ModuleChat.send(this, ModuleChat.body().good("Trail").text(" - this chunk was loaded before. Follow the green."));
      }

      this.onTrail = now;
   }

   private boolean freshNearby(long key) {
      int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);

      for (int dx = -3; dx <= 3; dx++) {
         for (int dz = -3; dz <= 3; dz++) {
            if (this.fresh.contains(ChunkPos.pack(cx + dx, cz + dz))) {
               return true;
            }
         }
      }

      return false;
   }

   private static boolean arrivedFlowing(LevelChunk chunk) {
      for (LevelChunkSection section : chunk.getSections()) {
         if (section == null || section.hasOnlyAir() || !section.maybeHas(TrailFinderModule::flowing)) {
            continue;
         }

         // The palette can list a state no block uses any more, so confirm with a real block.
         for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
               for (int z = 0; z < 16; z++) {
                  if (flowing(section.getBlockState(x, y, z))) {
                     return true;
                  }
               }
            }
         }
      }

      return false;
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null) {
         return;
      }

      if (this.showVisited.get()) {
         this.draw(mc, this.visited, this.visitedColor.argb());
      }

      if (this.showFresh.get()) {
         this.draw(mc, this.fresh, this.freshColor.argb());
      }
   }

   private void draw(Minecraft mc, LongOpenHashSet chunks, int argb) {
      if (chunks.isEmpty()) {
         return;
      }

      GizmoStyle style = GizmoStyle.fill(argb);
      int px = mc.player.chunkPosition().x(), pz = mc.player.chunkPosition().z(), radius = this.drawRadius.getInt();
      double y = this.markerY.get();
      LongIterator it = chunks.iterator();

      while (it.hasNext()) {
         long key = it.nextLong();
         int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
         if (Math.abs(cx - px) <= radius && Math.abs(cz - pz) <= radius) {
            double x0 = cx << 4, z0 = cz << 4;
            // Inset slightly so neighbouring chunks read as separate tiles.
            Gizmos.cuboid(new AABB(x0 + 0.5, y, z0 + 0.5, x0 + 15.5, y, z0 + 15.5), style).setAlwaysOnTop();
         }
      }
   }
}
