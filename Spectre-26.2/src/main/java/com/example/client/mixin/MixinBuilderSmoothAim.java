package com.example.client.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MouseHandler;
import net.spectre.client.builder.SmoothAim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs the builder's aim once per frame, right where vanilla applies mouse look. */
@Environment(EnvType.CLIENT)
@Mixin(MouseHandler.class)
public abstract class MixinBuilderSmoothAim {
   @Inject(method = "handleAccumulatedMovement", at = @At("RETURN"))
   private void spectre$smoothAim(CallbackInfo ci) {
      SmoothAim.frame();
   }
}
