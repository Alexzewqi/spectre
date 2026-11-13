package net.spectre.client.module.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ModeSetting;
import net.spectre.client.module.setting.NumberSetting;

/**
 * Draws any sword you hold as a knife. Five original designs in common real-world knife shapes,
 * built from geometry at runtime, with a choice of finish. Swords in the inventory, on the ground
 * and in item frames keep their normal look.
 */
@Environment(EnvType.CLIENT)
public final class KnivesModule extends ClientModule {
   private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("spectre", "textures/cosmetic/headdress_base.png");
   // Palette slots the mesh is painted with; the blade and guard catch the moving glint.
   private static final int BLADE = 0, GRIP = 1, ACCENT = 2, GUARD = 3, EDGE = 4;
   private static final String STYLE_TACTICAL = "Tactical", STYLE_CLAW = "Claw", STYLE_FOLDING = "Folding", STYLE_HUNTING = "Hunting", STYLE_DAGGER = "Dagger";
   private static final String FINISH_STEEL = "Steel", FINISH_BLACK = "Blackened", FINISH_ACCENT = "Accent", FINISH_GOLD = "Gold", FINISH_FROST = "Frost";
   /** The knife being drawn this frame while the module is on, or null. */
   public static volatile KnivesModule live;
   private final ModeSetting style = this.addSetting("style", new ModeSetting("Knife", STYLE_CLAW, STYLE_TACTICAL, STYLE_CLAW, STYLE_FOLDING, STYLE_HUNTING, STYLE_DAGGER));
   private final ModeSetting finish = this.addSetting("finish", new ModeSetting("Finish", FINISH_STEEL, FINISH_STEEL, FINISH_BLACK, FINISH_ACCENT, FINISH_GOLD, FINISH_FROST));
   private final BooleanSetting glint = this.addSetting("glint", new BooleanSetting("Moving glint", true));
   private final BooleanSetting thirdPerson = this.addSetting("third-person", new BooleanSetting("Show in third person", true));
   private final NumberSetting size = this.addSetting("size", new NumberSetting("Size", 1.0F, 0.6F, 1.4F).step(0.05F));
   private CosmeticMesh mesh;
   private String builtStyle;

   public KnivesModule() {
      super("knives", "Knives", "Shows any sword you hold as a knife. Five original designs and five finishes. Only you see it.", ModuleCategory.VISUALS);
   }

   @Override
   protected void onEnable() {
      live = this;
   }

   @Override
   protected void onDisable() {
      if (live == this) {
         live = null;
      }
   }

   /** Whether this stack, held in this context, should be drawn as a knife instead of its item model. */
   public boolean replaces(ItemStack stack, ItemDisplayContext context) {
      if (stack.isEmpty() || !stack.is(holder -> holder.is(ItemTags.SWORDS))) {
         return false;
      }

      return switch (context) {
         case FIRST_PERSON_RIGHT_HAND, FIRST_PERSON_LEFT_HAND -> true;
         case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND -> this.thirdPerson.get();
         default -> false;
      };
   }

   /** Submits the knife where the item model would have gone, using the same hand placement as a sword. */
   public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, ItemDisplayContext context) {
      String current = this.style.get();
      if (this.mesh == null || !current.equals(this.builtStyle)) {
         this.mesh = build(current);
         this.builtStyle = current;
      }

      CosmeticMesh knife = this.mesh;
      CosmeticMesh.Look look = this.look();
      boolean left = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
      boolean first = context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
      poseStack.pushPose();
      // The handheld item placement vanilla gives a sword (models/item/handheld.json).
      if (first) {
         poseStack.translate(1.13F / 16.0F, 3.2F / 16.0F, 1.13F / 16.0F);
         poseStack.mulPose(Axis.YP.rotationDegrees(left ? 90.0F : -90.0F));
         poseStack.mulPose(Axis.ZP.rotationDegrees(left ? -25.0F : 25.0F));
         poseStack.scale(0.68F, 0.68F, 0.68F);
      } else {
         poseStack.translate(0.0F, 4.0F / 16.0F, 0.5F / 16.0F);
         poseStack.mulPose(Axis.YP.rotationDegrees(left ? 90.0F : -90.0F));
         poseStack.mulPose(Axis.ZP.rotationDegrees(left ? -55.0F : 55.0F));
         poseStack.scale(0.85F, 0.85F, 0.85F);
      }

      // Item space runs 0..1 with the grip at the lower left and the tip toward the upper right.
      poseStack.translate(-0.5F, -0.5F, 0.0F);
      poseStack.translate(3.0F / 16.0F, 3.0F / 16.0F, 0.0F);
      poseStack.mulPose(Axis.ZP.rotationDegrees(-45.0F));
      float scale = this.size.get();
      poseStack.scale(scale, scale, scale);
      RenderType type = RenderTypes.entityCutout(TEXTURE);
      collector.submitCustomGeometry(poseStack, type, (pose, out) -> knife.emit(pose, out, light, look));
      poseStack.popPose();
   }

   private CosmeticMesh.Look look() {
      int[] palette = switch (this.finish.get()) {
         case FINISH_BLACK -> new int[]{0xFF2C2D31, 0xFF141416, 0xFFE0504A, 0xFF3A3B40, 0xFF8C8F96};
         case FINISH_ACCENT -> {
            int accent = CosmeticAccent.argb();
            yield new int[]{accent, 0xFF17171A, 0xFFFFFFFF, 0xFF3A3B40, 0xFFF4F4F6};
         }
         case FINISH_GOLD -> new int[]{0xFFE3B65A, 0xFF2A1E14, 0xFF8B1E2E, 0xFFB8892F, 0xFFFFF0C8};
         case FINISH_FROST -> new int[]{0xFFBFE6F5, 0xFF26323C, 0xFF6FD3FF, 0xFF8FA6B4, 0xFFFFFFFF};
         default -> new int[]{0xFFB9BEC6, 0xFF232428, 0xFF56DCCB, 0xFF6E7179, 0xFFF2F4F7};
      };
      float gain = 0.0F, sweepCos = 1.0F, sweepSin = 0.0F;
      if (this.glint.get()) {
         float t = (System.nanoTime() % 3_000_000_000L) / 3.0E9F;
         float angle = t * Mth.TWO_PI;
         sweepCos = Mth.cos(angle);
         sweepSin = Mth.sin(angle);
         gain = 0.55F;
      }

      return new CosmeticMesh.Look(palette, false, sweepCos, sweepSin, gain, 1.0F);
   }

   // ---------------------------------------------------------------- geometry, in item pixels

   /**
    * Every knife lies along +Y from the butt (y = 0) to the tip, in the XY plane, centred on z = 0.
    * The spine is on the -X side and the cutting edge on the +X side.
    */
   private static CosmeticMesh build(String style) {
      CosmeticMesh.Builder b = new CosmeticMesh.Builder();
      switch (style) {
         case STYLE_TACTICAL -> tactical(b);
         case STYLE_FOLDING -> folding(b);
         case STYLE_HUNTING -> hunting(b);
         case STYLE_DAGGER -> dagger(b);
         default -> claw(b);
      }

      return new CosmeticMesh(b);
   }

   /** Straight drop-point blade, finger guard and a ribbed grip. */
   private static void tactical(CosmeticMesh.Builder b) {
      grip(b, 0.0F, 6.0F, 1.6F, 0.75F, true);
      b.box(-1.0F, 6.0F, -0.55F, 2.3F, 6.8F, 0.55F, GUARD);
      float[][] profile = {
         {6.8F, -0.9F, 1.3F}, {9.0F, -0.9F, 1.35F}, {11.5F, -0.85F, 1.25F}, {13.5F, -0.7F, 1.0F}, {15.0F, -0.3F, 0.6F}, {16.2F, 0.25F, 0.25F}
      };
      blade(b, profile, 0.32F);
      // Fuller: a shallow groove line along the flat.
      b.box(-0.45F, 8.0F, -0.34F, -0.15F, 13.0F, 0.34F, EDGE);
   }

   /** A short curved blade sweeping forward from the grip, with a finger ring at the butt. */
   private static void claw(CosmeticMesh.Builder b) {
      ring(b, 0.0F, -0.4F, 1.15F, 1.85F, 0.5F, GUARD);
      grip(b, 1.4F, 6.0F, 1.5F, 0.7F, false);
      b.box(-1.0F, 6.0F, -0.5F, 1.6F, 6.6F, 0.5F, GUARD);
      // The blade bends toward the edge side as it climbs, ending in a hooked point.
      int steps = 9;
      float[][] profile = new float[steps + 1][];
      for (int i = 0; i <= steps; i++) {
         float t = i / (float)steps;
         float y = 6.6F + t * 7.4F;
         float bend = 3.2F * t * t;
         float width = Mth.lerp(t, 2.1F, 0.25F);
         profile[i] = new float[]{y, bend - width * 0.35F, bend + width * 0.65F};
      }

      blade(b, profile, 0.3F);
   }

   /** A folding knife shown open: two split handle halves around a slim blade. */
   private static void folding(CosmeticMesh.Builder b) {
      b.box(-0.95F, 0.0F, -0.65F, 0.95F, 0.8F, 0.65F, GUARD);
      b.box(-0.95F, 0.8F, -0.65F, 0.95F, 6.4F, -0.2F, GRIP);
      b.box(-0.95F, 0.8F, 0.2F, 0.95F, 6.4F, 0.65F, GRIP);
      // Cut-outs show as accent windows on both halves.
      for (int i = 0; i < 3; i++) {
         float y = 1.8F + i * 1.5F;
         b.box(-0.45F, y, -0.7F, 0.45F, y + 0.8F, -0.6F, ACCENT);
         b.box(-0.45F, y, 0.6F, 0.45F, y + 0.8F, 0.7F, ACCENT);
      }

      b.box(-0.95F, 6.4F, -0.65F, 0.95F, 7.0F, 0.65F, GUARD);
      float[][] profile = {{7.0F, -0.7F, 0.9F}, {10.0F, -0.7F, 0.95F}, {13.0F, -0.6F, 0.8F}, {14.8F, -0.2F, 0.45F}, {16.0F, 0.1F, 0.1F}};
      blade(b, profile, 0.26F);
   }

   /** A broad clip-point hunting blade with a wooden-looking grip and a brass bolster. */
   private static void hunting(CosmeticMesh.Builder b) {
      b.box(-1.1F, 0.0F, -0.8F, 1.3F, 0.9F, 0.8F, GUARD);
      grip(b, 0.9F, 6.0F, 1.9F, 0.8F, false);
      b.box(-1.5F, 6.0F, -0.85F, 2.6F, 6.9F, 0.85F, GUARD);
      float[][] profile = {
         {6.9F, -1.1F, 1.8F}, {9.5F, -1.1F, 2.0F}, {12.0F, -1.0F, 1.85F}, {13.2F, -0.2F, 1.5F}, {14.6F, 0.4F, 1.0F}, {15.8F, 0.75F, 0.75F}
      };
      blade(b, profile, 0.36F);
   }

   /** A symmetric double-edged dagger with a cross guard. */
   private static void dagger(CosmeticMesh.Builder b) {
      b.box(-0.8F, 0.0F, -0.7F, 0.8F, 0.8F, 0.7F, GUARD);
      grip(b, 0.8F, 5.4F, 1.3F, 0.65F, true);
      b.box(-2.6F, 5.4F, -0.6F, 2.6F, 6.1F, 0.6F, GUARD);
      b.box(-0.35F, 5.5F, -0.65F, 0.35F, 6.0F, 0.65F, ACCENT);
      int steps = 6;
      for (int i = 0; i < steps; i++) {
         float t0 = i / (float)steps, t1 = (i + 1) / (float)steps;
         float y0 = 6.1F + t0 * 10.0F, y1 = 6.1F + t1 * 10.0F;
         float w0 = Mth.lerp(t0 * t0, 1.2F, 0.05F), w1 = Mth.lerp(t1 * t1, 1.2F, 0.05F);
         // Raised centre ridge with bevels falling to an edge on both sides.
         bevel(b, 0.0F, y0, 0.0F, y1, -w0, y0, -w1, y1, 0.34F, 0.05F, BLADE, EDGE);
         bevel(b, 0.0F, y0, 0.0F, y1, w0, y0, w1, y1, 0.34F, 0.05F, BLADE, EDGE);
      }
   }

   /** A grip from y0 to y1, {@code width} across and {@code depth} thick, with an inlay stripe and optional ribs. */
   private static void grip(CosmeticMesh.Builder b, float y0, float y1, float width, float depth, boolean ribs) {
      float h = width * 0.5F;
      b.box(-h, y0, -depth, h, y1, depth, GRIP);
      b.box(-h * 0.25F, y0 + 0.4F, -depth - 0.06F, h * 0.25F, y1 - 0.4F, -depth, ACCENT);
      b.box(-h * 0.25F, y0 + 0.4F, depth, h * 0.25F, y1 - 0.4F, depth + 0.06F, ACCENT);
      if (ribs) {
         for (float y = y0 + 0.6F; y < y1 - 0.4F; y += 0.9F) {
            b.box(h, y, -depth * 0.8F, h + 0.18F, y + 0.4F, depth * 0.8F, GRIP);
         }
      }
   }

   /**
    * A blade from a profile of {y, spineX, edgeX} rows. Each slice is a thick spine half and a bevel
    * that thins to the cutting edge, so the blade catches light like a ground edge.
    */
   private static void blade(CosmeticMesh.Builder b, float[][] profile, float thickness) {
      for (int i = 0; i + 1 < profile.length; i++) {
         float[] p0 = profile[i], p1 = profile[i + 1];
         float m0 = Mth.lerp(0.5F, p0[1], p0[2]), m1 = Mth.lerp(0.5F, p1[1], p1[2]);
         band(b, p0[1], p0[0], p1[1], p1[0], m0, p0[0], m1, p1[0], thickness, thickness, BLADE);
         bevel(b, m0, p0[0], m1, p1[0], p0[2], p0[0], p1[2], p1[0], thickness, 0.05F, BLADE, EDGE);
      }
   }

   /** A slice falling from {@code thick} at the inner line to {@code thin} at the outer line, with a bright edge strip. */
   private static void bevel(
      CosmeticMesh.Builder b, float ix0, float iy0, float ix1, float iy1, float ox0, float oy0, float ox1, float oy1, float thick, float thin, int mat, int edgeMat
   ) {
      float ex0 = Mth.lerp(0.8F, ix0, ox0), ey0 = Mth.lerp(0.8F, iy0, oy0), ex1 = Mth.lerp(0.8F, ix1, ox1), ey1 = Mth.lerp(0.8F, iy1, oy1);
      float mid = Mth.lerp(0.8F, thick, thin);
      band(b, ix0, iy0, ix1, iy1, ex0, ey0, ex1, ey1, thick, mid, mat);
      band(b, ex0, ey0, ex1, ey1, ox0, oy0, ox1, oy1, mid, thin, edgeMat);
   }

   /**
    * A four-sided slice between an inner line (a0 to a1) and an outer line (b0 to b1) in the XY plane,
    * {@code da} thick along the inner line and {@code db} along the outer one.
    */
   private static void band(CosmeticMesh.Builder b, float ax0, float ay0, float ax1, float ay1, float bx0, float by0, float bx1, float by1, float da, float db, int mat) {
      float[] c = new float[]{
         ax0, ay0, -da, bx0, by0, -db, bx0, by0, db, ax0, ay0, da,
         ax1, ay1, -da, bx1, by1, -db, bx1, by1, db, ax1, ay1, da
      };
      b.prism(c, mat);
   }

   /** A flat ring in the XY plane, built from short slices. */
   private static void ring(CosmeticMesh.Builder b, float cx, float cy, float inner, float outer, float depth, int mat) {
      int segments = 14;
      for (int i = 0; i < segments; i++) {
         float a0 = Mth.TWO_PI * i / segments, a1 = Mth.TWO_PI * (i + 1) / segments;
         band(
            b,
            cx + Mth.cos(a0) * inner, cy + Mth.sin(a0) * inner, cx + Mth.cos(a1) * inner, cy + Mth.sin(a1) * inner,
            cx + Mth.cos(a0) * outer, cy + Mth.sin(a0) * outer, cx + Mth.cos(a1) * outer, cy + Mth.sin(a1) * outer,
            depth, depth, mat
         );
      }
   }
}
