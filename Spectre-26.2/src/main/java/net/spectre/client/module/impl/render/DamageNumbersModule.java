package net.spectre.client.module.impl.render;

import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.spectre.client.event.EventHandler;
import net.spectre.client.event.impl.Render2DEvent;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.gui.WorldLabel;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ColorSetting;
import net.spectre.client.module.setting.NumberSetting;
import org.joml.Matrix3x2fStack;

/** Numbers that float up from an entity whenever its health changes: damage in one colour, healing in another. */
@Environment(EnvType.CLIENT)
public final class DamageNumbersModule extends ClientModule {
   private static final int MAX_NUMBERS = 96;
   private final BooleanSetting playersOnly = this.addSetting("players-only", new BooleanSetting("Players only", false));
   private final BooleanSetting showSelf = this.addSetting("show-self", new BooleanSetting("Show my own", false));
   private final BooleanSetting showHeals = this.addSetting("show-heals", new BooleanSetting("Show healing", true));
   private final NumberSetting range = this.addSetting("range", new NumberSetting("Range", 32.0F, 8.0F, 96.0F).step(4.0F));
   private final NumberSetting size = this.addSetting("size", new NumberSetting("Size", 1.5F, 0.75F, 3.0F).step(0.25F));
   private final NumberSetting duration = this.addSetting("duration", new NumberSetting("Duration (s)", 1.2F, 0.4F, 3.0F).step(0.1F));
   private final ColorSetting damageColor = this.addSetting("damage-color", new ColorSetting("Damage color", 0xFFFF5A5A));
   private final ColorSetting bigColor = this.addSetting("big-color", new ColorSetting("Big hit color", 0xFFFFC542));
   private final ColorSetting healColor = this.addSetting("heal-color", new ColorSetting("Heal color", 0xFF5EDB4A));
   private final Int2FloatOpenHashMap lastHealth = new Int2FloatOpenHashMap();
   private final List<Number> numbers = new ArrayList<>();

   public DamageNumbersModule() {
      super("damage-numbers", "Damage Numbers", "Floating numbers over anything that takes damage or heals near you.", ModuleCategory.VISUALS);
   }

   @Override
   protected void onEnable() {
      this.lastHealth.clear();
      this.numbers.clear();
   }

   @Override
   protected void onDisable() {
      this.lastHealth.clear();
      this.numbers.clear();
   }

   @EventHandler
   public void onTick(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null) {
         this.lastHealth.clear();
         this.numbers.clear();
         return;
      }

      double reach = this.range.get() * this.range.get();
      Int2FloatOpenHashMap seen = new Int2FloatOpenHashMap();

      for (Entity entity : mc.level.entitiesForRendering()) {
         if (!(entity instanceof LivingEntity living) || entity.distanceToSqr(mc.player) > reach) {
            continue;
         }

         if (entity == mc.player ? !this.showSelf.get() : this.playersOnly.get() && !(entity instanceof Player)) {
            continue;
         }

         // Absorption hearts take damage first, so they count as health here.
         float health = living.getHealth() + living.getAbsorptionAmount();
         seen.put(entity.getId(), health);
         if (this.lastHealth.containsKey(entity.getId())) {
            float change = health - this.lastHealth.get(entity.getId());
            if (change <= -0.05F || change >= 0.05F && this.showHeals.get()) {
               if (this.numbers.size() >= MAX_NUMBERS) {
                  this.numbers.remove(0);
               }

               double side = (entity.getId() * 31 + this.numbers.size() * 17) % 100 / 100.0 - 0.5;
               this.numbers.add(new Number(entity.position().add(side * 0.6, entity.getBbHeight() + 0.3, side * -0.6), change, System.nanoTime()));
            }
         }
      }

      this.lastHealth.clear();
      this.lastHealth.putAll(seen);
   }

   @EventHandler
   public void onRender2D(Render2DEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (this.numbers.isEmpty() || mc.level == null || mc.gui.hud.isHidden()) {
         return;
      }

      long now = System.nanoTime();
      float life = this.duration.get() * 1.0E9F;
      Font font = mc.font;
      Iterator<Number> it = this.numbers.iterator();

      while (it.hasNext()) {
         Number number = it.next();
         float age = (now - number.bornNanos) / life;
         if (age >= 1.0F) {
            it.remove();
            continue;
         }

         // Rise quickly, then slow; pop in slightly oversized and settle.
         float rise = 1.0F - (1.0F - age) * (1.0F - age);
         float[] screen = WorldLabel.project(mc, number.pos.add(0.0, rise * 0.9, 0.0), event.graphics.guiWidth(), event.graphics.guiHeight());
         if (screen == null) {
            continue;
         }

         float amount = Math.abs(number.change);
         String text = (number.change > 0.0F ? "+" : "") + (amount >= 10.0F ? String.valueOf(Math.round(number.change)) : String.format(Locale.ROOT, "%.1f", number.change)).replace("-", "");
         int rgb = (number.change > 0.0F ? this.healColor.argb() : amount >= 8.0F ? this.bigColor.argb() : this.damageColor.argb()) & 0xFFFFFF;
         int alpha = Math.round(255.0F * Math.min(1.0F, (1.0F - age) * 2.5F));
         float pop = 1.0F + 0.5F * Math.max(0.0F, 1.0F - age * 8.0F);
         float scale = this.size.get() * pop * (amount >= 8.0F ? 1.25F : 1.0F);
         Matrix3x2fStack pose = event.graphics.pose();
         pose.pushMatrix();
         pose.translate(screen[0], screen[1]);
         pose.scale(scale, scale);
         event.graphics.text(font, text, -font.width(text) / 2, -4, Math.max(8, alpha) << 24 | rgb, true);
         pose.popMatrix();
      }
   }

   private record Number(Vec3 pos, float change, long bornNanos) {
   }
}
