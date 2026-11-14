package net.spectre.client.module.impl.render;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.spectre.client.chat.ModuleChat;
import net.spectre.client.event.EventHandler;
import net.spectre.client.event.impl.PacketReceiveEvent;
import net.spectre.client.event.impl.Render2DEvent;
import net.spectre.client.event.impl.Render3DEvent;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.gui.WorldLabel;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ColorSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.spectre.client.util.FoglessLines;
import net.spectre.client.util.SoundClues;
import net.spectre.client.util.SoundClues.Group;
import net.spectre.client.util.TracerEngine;

/**
 * Marks the position of every sound the server sends, through walls. A chest lid, a door or a piston
 * behind stone still makes the server send its sound, so the mark shows where someone is busy.
 */
@Environment(EnvType.CLIENT)
public final class SoundEspModule extends ClientModule {
   private static final int MAX_MARKS = 256;
   private static final int RING_SEGMENTS = 20;
   private static final int LABEL_CAP = 32;
   private static final long ALERT_GAP_MS = 4000L;
   private final EnumMap<Group, BooleanSetting> shown = new EnumMap<>(Group.class);
   private final EnumMap<Group, ColorSetting> colors = new EnumMap<>(Group.class);
   private final NumberSetting lifetime = this.addSetting("lifetime", new NumberSetting("Mark lifetime (s)", 8.0F, 1.0F, 60.0F).step(1.0F));
   private final NumberSetting range = this.addSetting("range", new NumberSetting("Range", 96.0F, 8.0F, 256.0F).step(8.0F));
   private final NumberSetting ignoreNear = this.addSetting("ignore-near", new NumberSetting("Ignore within", 3.0F, 0.0F, 16.0F).step(0.5F));
   private final BooleanSetting labels = this.addSetting("labels", new BooleanSetting("Labels", true));
   private final BooleanSetting tracers = this.addSetting("tracers", new BooleanSetting("Tracers", false));
   private final BooleanSetting storageAlert = this.addSetting("storage-alert", new BooleanSetting("Chat alert for storage sounds", true));
   private final List<Mark> marks = new ArrayList<>();
   private long lastAlertMs;

   public SoundEspModule() {
      super("sound-esp", "Sound ESP", "Marks where the server plays sounds, through walls. Chests, doors, pistons and block placing give away players you cannot see.", ModuleCategory.BASE_RAID);
      this.usesNotifications();
      this.group(Group.STORAGE, true, 0xFFFFCA54);
      this.group(Group.REDSTONE, true, 0xFFFF547D);
      this.group(Group.BUILDING, true, 0xFF51E5FF);
      this.group(Group.WORKSTATION, true, 0xFFFF944D);
      this.group(Group.PLAYER, true, 0xFF91F56B);
      this.group(Group.MOB, false, 0xFFB78AFF);
      this.group(Group.OTHER, false, 0xFFA3A3AB);
   }

   private void group(Group group, boolean on, int argb) {
      String id = group.name().toLowerCase(java.util.Locale.ROOT);
      BooleanSetting toggle = this.addSetting("show-" + id, new BooleanSetting(group.label, on));
      ColorSetting color = this.addSetting("color-" + id, new ColorSetting(group.label + " color", argb));
      color.visibleWhen(toggle::get);
      this.shown.put(group, toggle);
      this.colors.put(group, color);
   }

   @Override
   protected void onEnable() {
      this.marks.clear();
      this.on(PacketReceiveEvent.TYPE, this::onPacket);
   }

   @Override
   protected void onDisable() {
      this.marks.clear();
   }

   private void onPacket(PacketReceiveEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null) {
         return;
      }

      if (event.packet instanceof ClientboundSoundPacket sound) {
         if (sound.getSound() != null && sound.getSound().value() != null) {
            this.heard(mc, new Vec3(sound.getX(), sound.getY(), sound.getZ()), sound.getSource(), sound.getSound().value().location().getPath());
         }
      } else if (event.packet instanceof ClientboundSoundEntityPacket sound) {
         Entity entity = mc.level.getEntity(sound.getId());
         if (entity != null && entity != mc.player && sound.getSound() != null && sound.getSound().value() != null) {
            this.heard(mc, entity.position(), sound.getSource(), sound.getSound().value().location().getPath());
         }
      }
   }

   private void heard(Minecraft mc, Vec3 pos, SoundSource source, String path) {
      Group group = SoundClues.group(source, path);
      if (!this.shown.get(group).get()) {
         return;
      }

      double distanceSq = pos.distanceToSqr(mc.player.position());
      double near = this.ignoreNear.get();
      double far = this.range.get();
      if (distanceSq < near * near || distanceSq > far * far) {
         return;
      }

      long now = System.currentTimeMillis();
      String label = SoundClues.label(path);

      // A repeating sound in one spot refreshes its mark instead of stacking new ones.
      for (Mark mark : this.marks) {
         if (mark.label.equals(label) && mark.pos.distanceToSqr(pos) < 1.0) {
            mark.bornMs = now;
            mark.count++;
            return;
         }
      }

      if (this.marks.size() >= MAX_MARKS) {
         this.marks.remove(0);
      }

      this.marks.add(new Mark(pos, group, label, now));
      if (group == Group.STORAGE && this.storageAlert.get() && now - this.lastAlertMs >= ALERT_GAP_MS) {
         this.lastAlertMs = now;
         ModuleChat.send(this, ModuleChat.body().name(label).text(" heard at ").coords((int)Math.floor(pos.x), (int)Math.floor(pos.y), (int)Math.floor(pos.z))
            .text(" (").value(Math.round(Math.sqrt(distanceSq)) + "m").text(")"));
      }
   }

   @EventHandler
   public void onTick(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         this.marks.clear();
         return;
      }

      long cutoff = System.currentTimeMillis() - (long)(this.lifetime.get() * 1000.0F);
      Iterator<Mark> it = this.marks.iterator();

      while (it.hasNext()) {
         if (it.next().bornMs < cutoff) {
            it.remove();
         }
      }
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null || this.marks.isEmpty()) {
         return;
      }

      long now = System.currentTimeMillis();
      float life = this.lifetime.get() * 1000.0F;
      FoglessLines lines = FoglessLines.forFrame(event.partialTick);

      for (Mark mark : this.marks) {
         float age = Math.min(1.0F, (now - mark.bornMs) / life);
         int rgb = this.colors.get(mark.group).argb() | 0xFF000000;
         int alpha = Math.round(255.0F * (1.0F - age * age));
         if (alpha <= 4) {
            continue;
         }

         AABB core = new AABB(mark.pos.x - 0.25, mark.pos.y - 0.25, mark.pos.z - 0.25, mark.pos.x + 0.25, mark.pos.y + 0.25, mark.pos.z + 0.25);
         if (event.frustum == null || event.frustum.isVisible(core.inflate(2.0))) {
            lines.box(core, ARGB.color(alpha, rgb), 1.8F);
            // A ripple that spreads out and fades, restarting every second while the mark is alive.
            float ripple = (now - mark.bornMs) % 1000L / 1000.0F;
            ring(lines, mark.pos, 0.4 + ripple * 1.4, ARGB.color(Math.round(alpha * (1.0F - ripple)), rgb));
         }

         if (this.tracers.get()) {
            TracerEngine.draw(mark.pos, ARGB.color(alpha, rgb));
         }
      }
   }

   private static void ring(FoglessLines lines, Vec3 center, double radius, int argb) {
      Vec3 previous = center.add(radius, 0.0, 0.0);

      for (int i = 1; i <= RING_SEGMENTS; i++) {
         double angle = Math.PI * 2.0 * i / RING_SEGMENTS;
         Vec3 next = center.add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
         lines.line(previous, next, argb, 1.5F);
         previous = next;
      }
   }

   @EventHandler
   public void onRender2D(Render2DEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (!this.labels.get() || this.marks.isEmpty() || mc.level == null || mc.player == null || mc.gui.screen() != null || mc.gui.hud.isHidden()) {
         return;
      }

      GuiGraphicsExtractor graphics = event.graphics;
      Font font = mc.font;
      Vec3 eye = mc.player.position();
      int drawn = 0;

      // Newest marks last in the list; label those first so a busy area keeps its freshest names.
      for (int i = this.marks.size() - 1; i >= 0 && drawn < LABEL_CAP; i--) {
         Mark mark = this.marks.get(i);
         float[] screen = WorldLabel.project(mc, mark.pos.add(0.0, 0.6, 0.0), graphics.guiWidth(), graphics.guiHeight());
         if (screen == null) {
            continue;
         }

         String name = mark.count > 1 ? mark.label + " x" + mark.count : mark.label;
         String distance = Math.round(Math.sqrt(mark.pos.distanceToSqr(eye))) + "m";
         int nameW = font.width(name), w = nameW + 5 + font.width(distance) + 8;
         int x = Math.round(screen[0]) - w / 2, y = Math.round(screen[1]) - 12;
         int color = this.colors.get(mark.group).argb() | 0xFF000000;
         graphics.fill(x, y, x + w, y + 12, 0xB4101218);
         graphics.fill(x, y, x + 2, y + 12, color);
         graphics.text(font, name, x + 5, y + 2, 0xFFE9E9EE, true);
         graphics.text(font, distance, x + 5 + nameW + 5, y + 2, color, true);
         drawn++;
      }
   }

   private static final class Mark {
      final Vec3 pos;
      final Group group;
      final String label;
      long bornMs;
      int count = 1;

      Mark(Vec3 pos, Group group, String label, long bornMs) {
         this.pos = pos;
         this.group = group;
         this.label = label;
         this.bornMs = bornMs;
      }
   }
}
