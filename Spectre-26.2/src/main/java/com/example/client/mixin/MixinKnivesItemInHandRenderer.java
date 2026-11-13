package com.example.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.spectre.client.module.impl.render.KnivesModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws a held sword as a knife while the Knives module is on. */
@Environment(EnvType.CLIENT)
@Mixin(ItemInHandRenderer.class)
public abstract class MixinKnivesItemInHandRenderer {
   @Inject(method = "renderItem", at = @At("HEAD"), cancellable = true)
   private void spectre$knife(
      LivingEntity entity, ItemStack stack, ItemDisplayContext context, PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci
   ) {
      KnivesModule knives = KnivesModule.live;
      if (knives != null && knives.replaces(stack, context)) {
         knives.submit(poseStack, collector, light, context);
         ci.cancel();
      }
   }
}
