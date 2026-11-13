package net.spectre.client.module.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ColorSetting;
import net.spectre.client.module.setting.ModeSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.ARGB;

/**
 * First-person held item effects. "Outline" draws a crisp edge like the block outline
 * with a soft tint inside; "Glow" is the blurred, breathing aura.
 */
@Environment(EnvType.CLIENT)
public final class ItemOutlineModule extends ClientModule {
   public static volatile ItemOutlineModule active;
   private static final String OUTLINE = "Outline";
   private static final String GLOW = "Glow";
   private final ModeSetting style = this.addSetting("style", new ModeSetting("Style", OUTLINE, OUTLINE, GLOW));
   private final ModeSetting colorMode = this.addSetting("color", new ModeSetting("Color", "Theme", "Theme", "Manual"));
   private final ColorSetting lineColor = this.addSetting("line-color", new ColorSetting("Color Value", 0xFFC21CE0));
   private final NumberSetting width = this.addSetting("width", new NumberSetting("Outline Width", 2.0F, 1.0F, 6.0F).step(0.5F));
   private final NumberSetting opacity = this.addSetting("opacity", new NumberSetting("Outline Opacity", 100.0F, 10.0F, 100.0F).step(5.0F));
   private final NumberSetting fill = this.addSetting("fill", new NumberSetting("Inside Tint", 15.0F, 0.0F, 100.0F).step(5.0F));
   private final NumberSetting aura = this.addSetting("aura", new NumberSetting("Glow Strength", 65.0F, 0.0F, 100.0F).step(5.0F));
   private final NumberSetting reach = this.addSetting("reach", new NumberSetting("Glow Reach", 8.0F, 2.0F, 20.0F).step(1.0F));
   private final NumberSetting rim = this.addSetting("rim", new NumberSetting("Rim", 40.0F, 0.0F, 100.0F).step(5.0F));
   private final NumberSetting sheen = this.addSetting("sheen", new NumberSetting("Sheen", 25.0F, 0.0F, 100.0F).step(5.0F));
   private final BooleanSetting breathe = this.addSetting("breathe", new BooleanSetting("Breathe", true));
   private final BooleanSetting itemsOnly = this.addSetting("items-only", new BooleanSetting("Skip Empty Hand", true));

   public ItemOutlineModule() {
      super("item-outline", "Item Outline", "Outlines or glows the item in your hand.", ModuleCategory.VISUALS);
      this.lineColor.visibleWhen(() -> this.colorMode.is("Manual"));
      this.width.visibleWhen(this::outline);
      this.opacity.visibleWhen(this::outline);
      this.fill.visibleWhen(this::outline);
      this.aura.visibleWhen(() -> !this.outline());
      this.reach.visibleWhen(() -> !this.outline() && this.aura.get() > 0.0F);
      this.rim.visibleWhen(() -> !this.outline());
      this.sheen.visibleWhen(() -> !this.outline());
      this.breathe.visibleWhen(() -> !this.outline());
   }

   @Override
   protected void onEnable() {
      active = this;
   }

   @Override
   protected void onDisable() {
      active = null;
      if (RenderSystem.isOnRenderThread()) {
         HandAuraPass.release();
      } else {
         Minecraft.getInstance().execute(ItemOutlineModule::releaseIfOff);
      }
   }

   private static void releaseIfOff() {
      if (active == null) {
         HandAuraPass.release();
      }
   }

   public static boolean beginCapture() {
      ItemOutlineModule module = active;
      if (module == null || !module.shows()) {
         return false;
      }
      return module.outline() ? HandAuraPass.beginOutline() : HandAuraPass.begin();
   }

   public static void endCapture(boolean drawn) {
      if (!HandAuraPass.end() || !drawn) {
         return;
      }
      ItemOutlineModule module = active;
      float scale = Math.max(1.0F, Minecraft.getInstance().gameRenderer.mainRenderTarget().height / 1080.0F);
      if (module == null) {
         // Turned off mid-frame: still put the captured hand back on screen.
         HandAuraPass.compose(-1, 0.0F, 2.0F, 0.0F, 2.0F * scale, -1, 0.0F, 0.0F);
         return;
      }
      int color = module.colorArgb();
      if (module.outline()) {
         HandAuraPass.composeOutline(color, module.opacity.get() / 100.0F, color, module.fill.get() / 100.0F, module.width.get() * scale);
      } else {
         long now = System.nanoTime();
         float phase = (float)(now % 4400000000L / 4.4E9);
         HandAuraPass.compose(
            color, module.auraStrength(now), module.reach.get() * scale, module.rim.get() / 100.0F, 2.0F * scale,
            ARGB.srgbLerp(0.55F, color, -1), module.sheen.get() / 100.0F, phase
         );
      }
   }

   private boolean outline() {
      return this.style.is(OUTLINE);
   }

   private int colorArgb() {
      return this.colorMode.is("Manual") ? this.lineColor.argb() : CosmeticAccent.argb();
   }

   private float auraStrength(long now) {
      float strength = this.aura.get() / 100.0F;
      if (!this.breathe.get()) {
         return strength;
      }
      double cycle = now % 3200000000L / 3.2E9;
      float swell = (float)Math.sin(cycle * Math.PI);
      return strength * (0.6F + 0.4F * swell * swell);
   }

   private boolean shows() {
      if (!this.outline() && this.aura.get() <= 0.0F && this.rim.get() <= 0.0F) {
         return false;
      }
      if (!this.itemsOnly.get()) {
         return true;
      }
      LocalPlayer player = Minecraft.getInstance().player;
      return player != null && (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty());
   }
}
