package net.spectre.client.module.impl.render;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.SculkCatalystBlock;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.SculkShriekerBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.TrialSpawnerBlock;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
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
import net.spectre.client.util.BlockEntityLeakTracker;
import net.spectre.client.util.ChunkAreas;
import net.spectre.client.util.ChunkOverlay;
import net.spectre.client.util.FoglessLines;
import net.spectre.client.util.LevelUtil;
import net.spectre.client.util.playerbypass.PlayerBypassLightTracker;

/**
 * A chunk finder for servers that hide the underground from players standing above it. It does not
 * read the hidden blocks. It adds up three things such a server still sends:
 *
 * <ol>
 * <li>Block entities. Chests, hoppers, furnaces and spawners travel in their own list beside the
 * block data, so they arrive even where the blocks themselves were swapped for stone.</li>
 * <li>Palette ghosts. Each chunk section starts with the list of block types it holds. When that list
 * names a chest or a hopper and no block in the section is one, the real block was hidden after the
 * list was written.</li>
 * <li>Zeroed sky light, as tracked for Player Bypass.</li>
 * </ol>
 *
 * Chests and spawners that belong to trial chambers and ancient cities are recognised by the vaults,
 * trial spawners and sculk around them and count for nothing.
 */
@Environment(EnvType.CLIENT)
public final class DeepScanModule extends ClientModule {
   private static final long SCAN_BUDGET_NANOS = 2_000_000L;
   private static final int REFRESH_TICKS = 20;
   private static final int STRUCTURE_REACH = 2;
   private static final int LIGHT_POINTS = 3;
   private static final int EVIDENCE_CAP = 3000;
   private static final List<GhostKind> GHOSTS = List.of(
      new GhostKind("hopper", 4, s -> s.getBlock() instanceof HopperBlock),
      new GhostKind("shulker box", 4, s -> s.getBlock() instanceof ShulkerBoxBlock),
      new GhostKind("ender chest", 4, s -> s.getBlock() instanceof EnderChestBlock),
      new GhostKind("furnace", 4, s -> s.getBlock() instanceof AbstractFurnaceBlock),
      new GhostKind("barrel", 3, s -> s.getBlock() instanceof BarrelBlock),
      new GhostKind("crafting table", 3, s -> s.getBlock() instanceof CraftingTableBlock),
      new GhostKind("bed", 4, s -> s.getBlock() instanceof BedBlock),
      new GhostKind("piston", 3, s -> s.getBlock() instanceof PistonBaseBlock),
      new GhostKind("redstone", 3, s -> s.getBlock() instanceof RedStoneWireBlock || s.getBlock() instanceof ObserverBlock),
      new GhostKind("chest", 2, s -> s.getBlock() instanceof AbstractChestBlock<?> && !(s.getBlock() instanceof EnderChestBlock)),
      new GhostKind("spawner", 2, s -> s.getBlock() instanceof SpawnerBlock),
      new GhostKind("torch", 1, s -> s.getBlock() instanceof TorchBlock)
   );
   private final NumberSetting needed = this.addSetting("evidence-needed", new NumberSetting("Evidence needed", 6.0F, 1.0F, 40.0F).step(1.0F));
   private final NumberSetting maxY = this.addSetting("max-y", new NumberSetting("Only below Y", 0.0F, -64.0F, 320.0F).step(1.0F));
   private final BooleanSetting useEntities = this.addSetting("use-block-entities", new BooleanSetting("Leaked block entities", true));
   private final BooleanSetting useGhosts = this.addSetting("use-ghosts", new BooleanSetting("Palette ghosts", true));
   private final BooleanSetting useLight = this.addSetting("use-light", new BooleanSetting("Zeroed sky light", true));
   private final BooleanSetting skipStructures = this.addSetting("skip-structures", new BooleanSetting("Ignore trial chambers and ancient cities", true));
   private final BooleanSetting showEvidence = this.addSetting("show-evidence", new BooleanSetting("Box the evidence blocks", true));
   private final ColorSetting evidenceColor = this.addSetting("evidence-color", new ColorSetting("Evidence color", 0xFFFFE066));
   private final ColorSetting chunkColor = this.addSetting("chunk-color", new ColorSetting("Chunk color", 0x60FF4FD8));
   private final BooleanSetting chunkTracers = this.addSetting("chunk-tracers", new BooleanSetting("Chunk tracers", true));
   private final BooleanSetting distanceLabels = this.addSetting("distance-labels", new BooleanSetting("Distance labels", true));
   private final BooleanSetting logFinds = this.addSetting("log-finds", new BooleanSetting("Save finds to file", true));
   private final Long2ObjectOpenHashMap<List<Ghost>> ghosts = new Long2ObjectOpenHashMap<>();
   private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
   private final LongOpenHashSet queued = new LongOpenHashSet();
   private final LongOpenHashSet announced = new LongOpenHashSet();
   private final ChunkAreas chunkAreas = new ChunkAreas();
   private LongOpenHashSet flagged = new LongOpenHashSet();
   private List<BlockPos> evidence = List.of();
   private ClientLevel lastLevel;
   private int age;

   public DeepScanModule() {
      super("deep-scan", "Deep Scan", "Finds underground bases from above on servers that hide the underground. Adds up leaked chest, hopper, furnace and spawner data, block types a section lists but does not show, and zeroed sky light. Ignores trial chambers and ancient cities.", ModuleCategory.BASE_RAID);
      this.usesNotifications();
      this.evidenceColor.visibleWhen(this.showEvidence::get);
      this.maxY.onChange(this::rescan);
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
      this.ghosts.clear();
      this.queue.clear();
      this.queued.clear();
      this.announced.clear();
      this.flagged = new LongOpenHashSet();
      this.evidence = List.of();
      this.age = REFRESH_TICKS;
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
         this.enqueue(ChunkPos.pack(chunk.getX(), chunk.getZ()));
      } else if (event.packet instanceof ClientboundForgetLevelChunkPacket) {
         // Finds are kept after a chunk unloads so a base stays marked while you travel to it.
         this.age = Math.max(this.age, REFRESH_TICKS - 2);
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
         if (LevelUtil.isLoaded(level, cx, cz)) {
            List<Ghost> found = this.findGhosts(level.getChunk(cx, cz));
            // A resend with the real blocks (after going below) must not erase what the hidden copy leaked.
            if (!found.isEmpty()) {
               this.ghosts.put(key, found);
            }
         }
      }

      if (++this.age >= REFRESH_TICKS) {
         this.age = 0;
         this.refresh();
      }
   }

   /** Block types a section's palette names although none of its 4096 blocks is one. */
   private List<Ghost> findGhosts(LevelChunk chunk) {
      List<Ghost> out = new ArrayList<>();
      LevelChunkSection[] sections = chunk.getSections();
      int minSectionY = chunk.getMinSectionY();
      int limit = this.maxY.getInt();

      for (int i = 0; i < sections.length; i++) {
         LevelChunkSection section = sections[i];
         int baseY = minSectionY + i << 4;
         if (section == null || baseY >= limit) {
            continue;
         }

         // A section big enough to use the global block list answers yes to anything; it tells us nothing.
         if (section.maybeHas(s -> s.is(Blocks.COMMAND_BLOCK))) {
            continue;
         }

         int wanted = 0;

         for (int k = 0; k < GHOSTS.size(); k++) {
            if (section.maybeHas(GHOSTS.get(k).test)) {
               wanted |= 1 << k;
            }
         }

         if (wanted == 0) {
            continue;
         }

         int missing = wanted;

         for (int y = 0; y < 16 && missing != 0; y++) {
            for (int x = 0; x < 16 && missing != 0; x++) {
               for (int z = 0; z < 16 && missing != 0; z++) {
                  BlockState state = section.getBlockState(x, y, z);
                  if (state.isAir()) {
                     continue;
                  }

                  for (int k = 0; k < GHOSTS.size(); k++) {
                     if ((missing & 1 << k) != 0 && GHOSTS.get(k).test.test(state)) {
                        missing &= ~(1 << k);
                     }
                  }
               }
            }
         }

         for (int k = 0; k < GHOSTS.size(); k++) {
            if ((missing & 1 << k) != 0) {
               out.add(new Ghost(GHOSTS.get(k), baseY + 8));
            }
         }
      }

      return out;
   }

   private void refresh() {
      Long2ObjectOpenHashMap<Tally> tallies = new Long2ObjectOpenHashMap<>();
      LongOpenHashSet structures = new LongOpenHashSet();
      int limit = this.maxY.getInt();
      boolean entities = this.useEntities.get();

      if (entities && this.skipStructures.get()) {
         for (Map.Entry<BlockPos, BlockEntityLeakTracker.Entry> entry : BlockEntityLeakTracker.view().entrySet()) {
            if (structureMarker(entry.getValue().block())) {
               structures.add(ChunkPos.pack(entry.getKey().getX() >> 4, entry.getKey().getZ() >> 4));
            }
         }
      }

      if (entities) {
         for (Map.Entry<BlockPos, BlockEntityLeakTracker.Entry> entry : BlockEntityLeakTracker.view().entrySet()) {
            BlockPos pos = entry.getKey();
            Block block = entry.getValue().block();
            if (pos.getY() >= limit || structureMarker(block)) {
               continue;
            }

            long key = ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4);
            int weight = weight(block);
            if (weight < 4 && nearStructure(structures, key)) {
               // Loot chests, barrels, dispensers and spawners that come with a generated structure.
               continue;
            }

            tally(tallies, key).add(block.getName().getString(), weight, pos.getY(), pos);
         }
      }

      if (this.useGhosts.get()) {
         this.ghosts.long2ObjectEntrySet().forEach(entry -> {
            if (!nearStructure(structures, entry.getLongKey())) {
               for (Ghost ghost : entry.getValue()) {
                  tally(tallies, entry.getLongKey()).add("hidden " + ghost.kind.name, ghost.kind.weight, ghost.y, null);
               }
            }
         });
      }

      float bar = this.needed.get();
      boolean light = this.useLight.get();
      LongOpenHashSet next = new LongOpenHashSet();
      List<BlockPos> boxes = new ArrayList<>();

      tallies.long2ObjectEntrySet().forEach(entry -> {
         Tally tally = entry.getValue();
         long key = entry.getLongKey();
         // Light alone is Player Bypass's job; here it only backs up a chunk that already has evidence.
         if (light && tally.score > 0 && PlayerBypassLightTracker.isFlagged(key)) {
            tally.score += LIGHT_POINTS;
            tally.names.merge("zeroed sky light", 1, Integer::sum);
         }

         if (tally.score >= bar) {
            next.add(key);
            if (boxes.size() < EVIDENCE_CAP) {
               boxes.addAll(tally.positions);
            }

            if (this.announced.add(key)) {
               this.announce(key, tally);
            }
         }
      });
      this.flagged = next;
      this.evidence = boxes;
   }

   private static Tally tally(Long2ObjectOpenHashMap<Tally> tallies, long key) {
      Tally tally = tallies.get(key);
      if (tally == null) {
         tally = new Tally();
         tallies.put(key, tally);
      }

      return tally;
   }

   private static boolean nearStructure(LongOpenHashSet structures, long key) {
      if (structures.isEmpty()) {
         return false;
      }

      int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);

      for (int dx = -STRUCTURE_REACH; dx <= STRUCTURE_REACH; dx++) {
         for (int dz = -STRUCTURE_REACH; dz <= STRUCTURE_REACH; dz++) {
            if (structures.contains(ChunkPos.pack(cx + dx, cz + dz))) {
               return true;
            }
         }
      }

      return false;
   }

   /** Blocks only world generation places: they mark a trial chamber, an ancient city or an archaeology site. */
   private static boolean structureMarker(Block block) {
      return block instanceof TrialSpawnerBlock || block instanceof VaultBlock || block instanceof SculkShriekerBlock || block instanceof SculkCatalystBlock
         || block instanceof SculkSensorBlock || block instanceof DecoratedPotBlock || block == Blocks.SUSPICIOUS_SAND || block == Blocks.SUSPICIOUS_GRAVEL;
   }

   /** Four points for things nobody finds underground unless a player put them there; less for loot the world also generates. */
   private static int weight(Block block) {
      if (block instanceof EnderChestBlock || block instanceof ShulkerBoxBlock || block instanceof AbstractFurnaceBlock || block instanceof BrewingStandBlock
         || block instanceof BedBlock || block instanceof SignBlock || block instanceof CrafterBlock || block instanceof AbstractBannerBlock
         || block instanceof HopperBlock || block == Blocks.BEACON || block == Blocks.ENCHANTING_TABLE || block == Blocks.MOVING_PISTON
         || block == Blocks.COMPARATOR || block == Blocks.LECTERN || block == Blocks.JUKEBOX || block == Blocks.CONDUIT || block == Blocks.DAYLIGHT_DETECTOR
         || block == Blocks.CHISELED_BOOKSHELF || block == Blocks.BELL) {
         return 4;
      } else if (block instanceof SpawnerBlock || block instanceof BarrelBlock || block instanceof DispenserBlock) {
         return 2;
      } else {
         return 1;
      }
   }

   private void announce(long key, Tally tally) {
      if (!this.notificationsOn()) {
         return;
      }

      int x = (ChunkPos.getX(key) << 4) + 8, z = (ChunkPos.getZ(key) << 4) + 8, y = tally.averageY();
      ModuleChat.send(this, ModuleChat.body().good("Base signs").text(" at ").coords(x, y, z).text(": ").value(tally.summary()));
      ChallengeToast.addOrUpdate(new ItemStack(Items.ENDER_EYE), this.name(), Component.literal(x + ", " + y + ", " + z), true);
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

      if (this.showEvidence.get()) {
         int argb = this.evidenceColor.argb() | 0xFF000000;
         Vec3 eye = mc.gameRenderer.mainCamera().position();
         FoglessLines lines = FoglessLines.forFrame(event.partialTick);

         for (BlockPos pos : this.evidence) {
            AABB box = new AABB(pos);
            if (box.distanceToSqr(eye) < 256.0 * 256.0 && (event.frustum == null || event.frustum.isVisible(box))) {
               lines.box(box, argb, 1.8F);
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

   private record GhostKind(String name, int weight, Predicate<BlockState> test) {
   }

   private record Ghost(GhostKind kind, int y) {
   }

   private static final class Tally {
      int score;
      long sumY;
      int hits;
      final Map<String, Integer> names = new LinkedHashMap<>();
      final List<BlockPos> positions = new ArrayList<>();

      void add(String name, int weight, int y, BlockPos pos) {
         this.score += weight;
         this.sumY += y;
         this.hits++;
         this.names.merge(name, 1, Integer::sum);
         if (pos != null) {
            this.positions.add(pos);
         }
      }

      int averageY() {
         return this.hits == 0 ? 0 : (int)Math.round((double)this.sumY / this.hits);
      }

      String summary() {
         StringJoiner joiner = new StringJoiner(", ");
         this.names.forEach((name, count) -> joiner.add(count > 1 ? count + "x " + name : name));
         return joiner.toString();
      }
   }
}
