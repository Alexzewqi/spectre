package net.spectre.client.module.impl.render;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.StringJoiner;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.spectre.client.chat.ModuleChat;
import net.spectre.client.event.EventHandler;
import net.spectre.client.event.impl.PacketReceiveEvent;
import net.spectre.client.event.impl.Render2DEvent;
import net.spectre.client.event.impl.Render3DEvent;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.gui.ChallengeToast;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.ActionSetting;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ColorSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.spectre.client.util.BaseFinds;
import net.spectre.client.util.ChunkAreas;
import net.spectre.client.util.ChunkOverlay;
import net.spectre.client.util.FoglessLines;
import net.spectre.client.util.SoundClues;

/**
 * Base finding from live activity instead of from the blocks in a chunk. The other finders read what
 * a chunk contains; this one listens for what the server says is happening right now: chest lids and
 * pistons moving, spawners firing, someone else's mining cracks, and block and player sounds. Each of
 * those only happens with a player close by, so the chunks collect evidence points, the points fade
 * over time, and a chunk is flagged once enough of them add up.
 */
@Environment(EnvType.CLIENT)
public final class EchoFinderModule extends ClientModule {
   private static final float SCORE_CAP = 60.0F;
   private static final long REPEAT_GAP_MS = 2000L;
   private static final int MAX_POINTS = 512;
   private static final long POINT_LIFE_MS = 45000L;
   private static final int ENTITY_SCAN_TICKS = 50;
   private final NumberSetting needed = this.addSetting("evidence-needed", new NumberSetting("Evidence needed", 8.0F, 2.0F, 40.0F).step(1.0F));
   private final NumberSetting memory = this.addSetting("memory", new NumberSetting("Evidence half-life (min)", 10.0F, 1.0F, 60.0F).step(1.0F));
   private final BooleanSetting keepFlagged = this.addSetting("keep-flagged", new BooleanSetting("Keep flagged chunks", true));
   private final NumberSetting ignoreNear = this.addSetting("ignore-near", new NumberSetting("Ignore within", 8.0F, 0.0F, 32.0F).step(1.0F));
   private final BooleanSetting undergroundOnly = this.addSetting("underground-only", new BooleanSetting("Underground only", false));
   private final NumberSetting undergroundY = this.addSetting("underground-y", new NumberSetting("Underground below Y", 50.0F, -64.0F, 128.0F).step(1.0F));
   private final EnumMap<Signal, BooleanSetting> sources = new EnumMap<>(Signal.class);
   private final NumberSetting packSize = this.addSetting("pack-size", new NumberSetting("Mob pack size", 6.0F, 3.0F, 30.0F).step(1.0F));
   private final BooleanSetting showPoints = this.addSetting("show-points", new BooleanSetting("Show activity points", true));
   private final ColorSetting pointColor = this.addSetting("point-color", new ColorSetting("Point color", 0xFFFFE066));
   private final ColorSetting chunkColor = this.addSetting("chunk-color", new ColorSetting("Chunk color", 0x6034D5FF));
   private final BooleanSetting chunkTracers = this.addSetting("chunk-tracers", new BooleanSetting("Chunk tracers", false));
   private final BooleanSetting distanceLabels = this.addSetting("distance-labels", new BooleanSetting("Distance labels", true));
   private final BooleanSetting logFinds = this.addSetting("log-finds", new BooleanSetting("Save finds to file", true));
   private final Long2ObjectOpenHashMap<Evidence> evidence = new Long2ObjectOpenHashMap<>();
   private final Long2LongOpenHashMap lastCounted = new Long2LongOpenHashMap();
   private final LongOpenHashSet flagged = new LongOpenHashSet();
   private final ArrayDeque<Point> points = new ArrayDeque<>();
   private final ChunkAreas chunkAreas = new ChunkAreas();
   private final IntOpenHashSet countedEntities = new IntOpenHashSet();
   private final Map<PackKey, PackTally> packs = new HashMap<>();
   private int entityScanAge;
   private ClientLevel lastLevel;
   private long lastDecayMs;

   public EchoFinderModule() {
      super("echo-finder", "Echo Finder", "Flags chunks where the server reports live activity you cannot see: chest lids, pistons, spawners, mining, block sounds, mob packs, placed entities and other players. Evidence adds up per chunk and fades over time. Works without the blocks being loaded; a hint, not proof of a base.", ModuleCategory.BASE_RAID);
      this.usesNotifications();
      this.undergroundY.visibleWhen(this.undergroundOnly::get);
      this.pointColor.visibleWhen(this.showPoints::get);

      for (Signal signal : Signal.values()) {
         this.sources.put(signal, this.addSetting("use-" + signal.id, new BooleanSetting(signal.label, true)));
      }

      this.addSetting("clear", new ActionSetting("Clear evidence", this::clearAll));
   }

   @Override
   protected void onEnable() {
      this.clearAll();
      this.lastLevel = Minecraft.getInstance().level;
      this.on(PacketReceiveEvent.TYPE, this::onPacket);
   }

   @Override
   protected void onDisable() {
      this.clearAll();
      this.lastLevel = null;
   }

   private void clearAll() {
      this.evidence.clear();
      this.lastCounted.clear();
      this.flagged.clear();
      this.points.clear();
      this.countedEntities.clear();
      this.lastDecayMs = System.currentTimeMillis();
   }

   private void onPacket(PacketReceiveEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null || mc.level != this.lastLevel) {
         return;
      }

      if (event.packet instanceof ClientboundBlockEventPacket block) {
         Signal signal = blockEventSignal(block.getBlock());
         if (signal != null) {
            this.record(mc, signal, Vec3.atCenterOf(block.getPos()), signal.weight);
         }
      } else if (event.packet instanceof ClientboundBlockDestructionPacket crack) {
         // Cracks from our own mining are drawn locally and never sent back, so these are someone else's.
         if (crack.getId() != mc.player.getId()) {
            this.record(mc, Signal.MINING, Vec3.atCenterOf(crack.getPos()), Signal.MINING.weight);
         }
      } else if (event.packet instanceof ClientboundLevelEventPacket level) {
         if (!level.isGlobalEvent()) {
            int weight = levelEventWeight(level.getType(), level.getData());
            if (weight > 0) {
               this.record(mc, level.getType() == 2001 ? Signal.MINING : Signal.MACHINES, Vec3.atCenterOf(level.getPos()), weight);
            }
         }
      } else if (event.packet instanceof ClientboundSoundPacket sound) {
         if (sound.getSound() != null && sound.getSound().value() != null) {
            SoundClues.Group group = SoundClues.group(sound.getSource(), sound.getSound().value().location().getPath());
            int weight = SoundClues.weight(group);
            if (weight > 0) {
               this.record(mc, Signal.SOUNDS, new Vec3(sound.getX(), sound.getY(), sound.getZ()), weight);
            }
         }
      }
   }

   private static Signal blockEventSignal(Block block) {
      if (block instanceof AbstractChestBlock<?> || block instanceof ShulkerBoxBlock) {
         return Signal.STORAGE;
      } else if (block instanceof SpawnerBlock) {
         return Signal.SPAWNERS;
      } else if (block instanceof PistonBaseBlock || block == Blocks.NOTE_BLOCK || block == Blocks.BELL) {
         return Signal.MACHINES;
      } else {
         return null;
      }
   }

   /** World events that need a player or a player-built machine. Anything not listed counts for nothing. */
   private static int levelEventWeight(int type, int data) {
      return switch (type) {
         case 1000, 1001, 1002 -> 2; // dispenser or dropper fired
         case 1010, 1011 -> 3; // jukebox started or stopped
         case 1029, 1030, 1031 -> 3; // anvil broke, was used, landed
         case 1035 -> 3; // brewing stand finished
         case 1042, 1043, 1044 -> 3; // grindstone, lectern page, smithing table
         case 1500 -> 2; // composter filled
         case 1503 -> 2; // eye placed in an end portal frame
         case 2001 -> naturalBreak(Block.stateById(data)) ? 0 : 2; // a block was broken
         case 2004 -> 3; // spawner spawned a mob
         case 3002, 3003, 3004, 3005 -> 2; // copper waxed or scraped
         default -> 0;
      };
   }

   /** Sheep graze and rabbits eat crops with the same break event; those say nothing about players. */
   private static boolean naturalBreak(BlockState state) {
      return state.isAir() || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN)
         || state.is(Blocks.CARROTS) || state.getBlock() instanceof LeavesBlock;
   }

   private void record(Minecraft mc, Signal signal, Vec3 pos, int weight) {
      if (!this.sources.get(signal).get()) {
         return;
      }

      if (this.undergroundOnly.get() && pos.y >= this.undergroundY.get()) {
         return;
      }

      double near = this.ignoreNear.get();
      if (pos.distanceToSqr(mc.player.position()) < near * near) {
         return;
      }

      long now = System.currentTimeMillis();
      BlockPos block = BlockPos.containing(pos);
      // One spot counts once per gap, so a fast piston clock cannot flag a chunk on its own in a second.
      long spot = block.asLong() * 31L + signal.ordinal();
      if (now - this.lastCounted.get(spot) < REPEAT_GAP_MS) {
         return;
      }

      this.lastCounted.put(spot, now);
      long key = ChunkPos.pack(block.getX() >> 4, block.getZ() >> 4);
      Evidence chunk = this.evidence.get(key);
      if (chunk == null) {
         chunk = new Evidence();
         this.evidence.put(key, chunk);
      }

      chunk.add(signal, weight, pos.y);
      if (this.points.size() >= MAX_POINTS) {
         this.points.removeFirst();
      }

      this.points.addLast(new Point(pos, now));
      if (chunk.score >= this.needed.get() && this.flagged.add(key)) {
         this.announce(key, chunk);
      }
   }

   private void announce(long key, Evidence chunk) {
      int x = (ChunkPos.getX(key) << 4) + 8, z = (ChunkPos.getZ(key) << 4) + 8, y = chunk.averageY();
      if (this.notificationsOn()) {
         ModuleChat.send(this, ModuleChat.body().text("Activity at ").coords(x, y, z).text(": ").value(chunk.summary()));
         ChallengeToast.addOrUpdate(new ItemStack(Items.BELL), this.name(), Component.literal(x + ", " + y + ", " + z), true);
      }
   }

   @EventHandler
   public void onTick(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != this.lastLevel) {
         this.clearAll();
         this.lastLevel = mc.level;
      }

      if (mc.level != null && mc.player != null && ++this.entityScanAge >= ENTITY_SCAN_TICKS) {
         this.entityScanAge = 0;
         this.scanEntities(mc);
      }

      long now = System.currentTimeMillis();
      long elapsed = now - this.lastDecayMs;
      if (elapsed < 1000L) {
         return;
      }

      this.lastDecayMs = now;
      float keep = (float)Math.pow(0.5, elapsed / (this.memory.get() * 60000.0));
      float bar = this.needed.get();
      ObjectIterator<Long2ObjectMap.Entry<Evidence>> it = this.evidence.long2ObjectEntrySet().fastIterator();

      while (it.hasNext()) {
         Long2ObjectMap.Entry<Evidence> entry = it.next();
         Evidence chunk = entry.getValue();
         chunk.score *= keep;
         long key = entry.getLongKey();
         if (chunk.score >= bar) {
            if (this.flagged.add(key)) {
               this.announce(key, chunk);
            }
         } else if (!this.keepFlagged.get()) {
            // Without "keep", a chunk drops off once its evidence has faded to half the bar.
            if (chunk.score < bar * 0.5F) {
               this.flagged.remove(key);
            }

            if (chunk.score < 0.25F) {
               it.remove();
            }
         } else if (chunk.score < 0.25F && !this.flagged.contains(key)) {
            it.remove();
         }
      }

      long pointCutoff = now - POINT_LIFE_MS;
      while (!this.points.isEmpty() && this.points.peekFirst().bornMs < pointCutoff) {
         this.points.removeFirst();
      }

      if (this.lastCounted.size() > 4096) {
         this.lastCounted.clear();
      }
   }

   /**
    * Entities are tracked by the server separately from blocks, so they often still arrive where the
    * blocks around them are hidden. Packs of one mob type in one chunk (a farm or grinder), placed
    * entities and other players all count as evidence at the spot they stand.
    */
   private void scanEntities(Minecraft mc) {
      this.packs.clear();
      boolean wantPacks = this.sources.get(Signal.MOB_PACKS).get();

      for (Entity entity : mc.level.entitiesForRendering()) {
         if (entity == mc.player || !entity.isAlive()) {
            continue;
         }

         if (entity instanceof ArmorStand || entity instanceof ItemFrame || entity instanceof AbstractMinecartContainer) {
            // Placed things do not move; count each one once.
            if (this.countedEntities.add(entity.getId())) {
               this.record(mc, Signal.PLACED, entity.position(), Signal.PLACED.weight);
            }
         } else if (entity instanceof Player) {
            this.record(mc, Signal.PLAYERS, entity.position(), Signal.PLAYERS.weight);
         } else if (wantPacks && entity instanceof Mob) {
            long chunk = ChunkPos.pack(entity.getBlockX() >> 4, entity.getBlockZ() >> 4);
            this.packs.computeIfAbsent(new PackKey(chunk, entity.getType()), k -> new PackTally()).add(entity.position());
         }
      }

      int pack = this.packSize.getInt();

      for (PackTally tally : this.packs.values()) {
         if (tally.count >= pack) {
            this.record(mc, Signal.MOB_PACKS, new Vec3(tally.x / tally.count, tally.y / tally.count, tally.z / tally.count), Signal.MOB_PACKS.weight);
         }
      }

      if (this.countedEntities.size() > 8192) {
         this.countedEntities.clear();
      }
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null) {
         return;
      }

      if (!this.flagged.isEmpty()) {
         BaseFinds.publish(this.name(), this.flagged);
         ChunkOverlay.draw(this.flagged, this.chunkColor.argb(), event.partialTick);
         if (this.logFinds.get()) {
            BaseFinds.track(this.flagged, this.name());
         }

         if (this.chunkTracers.get()) {
            this.chunkAreas.drawTracers(this.flagged, ChunkOverlay.MARKER_Y, ARGB.opaque(this.chunkColor.argb()));
         }
      }

      if (this.showPoints.get() && !this.points.isEmpty()) {
         long now = System.currentTimeMillis();
         int rgb = this.pointColor.argb() | 0xFF000000;
         FoglessLines lines = FoglessLines.forFrame(event.partialTick);
         Iterator<Point> it = this.points.iterator();

         while (it.hasNext()) {
            Point point = it.next();
            float age = Math.min(1.0F, (now - point.bornMs) / (float)POINT_LIFE_MS);
            AABB box = new AABB(point.pos.x - 0.3, point.pos.y - 0.3, point.pos.z - 0.3, point.pos.x + 0.3, point.pos.y + 0.3, point.pos.z + 0.3);
            if (event.frustum == null || event.frustum.isVisible(box)) {
               lines.box(box, ARGB.color(Math.round(255.0F * (1.0F - age)), rgb), 1.6F);
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

   /** What kind of packet the evidence came from; each can be switched off on servers where it is noisy. */
   private enum Signal {
      STORAGE("storage", "Chest and shulker lids", 4),
      SPAWNERS("spawners", "Spawners firing", 3),
      MACHINES("machines", "Pistons and machines", 2),
      MINING("mining", "Other players mining", 3),
      SOUNDS("sounds", "Block and player sounds", 2),
      MOB_PACKS("mob-packs", "Mob packs (farms)", 3),
      PLACED("placed", "Frames, stands, storage carts", 3),
      PLAYERS("players", "Other players", 2);

      final String id;
      final String label;
      final int weight;

      Signal(String id, String label, int weight) {
         this.id = id;
         this.label = label;
         this.weight = weight;
      }
   }

   private static final class Evidence {
      float score;
      double sumY;
      int hits;
      final EnumMap<Signal, Integer> kinds = new EnumMap<>(Signal.class);

      void add(Signal signal, int weight, double y) {
         this.score = Math.min(SCORE_CAP, this.score + weight);
         this.sumY += y;
         this.hits++;
         this.kinds.merge(signal, 1, Integer::sum);
      }

      int averageY() {
         return this.hits == 0 ? 0 : (int)Math.round(this.sumY / this.hits);
      }

      String summary() {
         StringJoiner joiner = new StringJoiner(", ");

         for (Map.Entry<Signal, Integer> entry : this.kinds.entrySet()) {
            joiner.add(entry.getValue() + "x " + entry.getKey().label.toLowerCase(java.util.Locale.ROOT));
         }

         return joiner.toString();
      }
   }

   private record PackKey(long chunk, EntityType<?> type) {
   }

   private static final class PackTally {
      int count;
      double x;
      double y;
      double z;

      void add(Vec3 pos) {
         this.count++;
         this.x += pos.x;
         this.y += pos.y;
         this.z += pos.z;
      }
   }

   private record Point(Vec3 pos, long bornMs) {
   }
}
