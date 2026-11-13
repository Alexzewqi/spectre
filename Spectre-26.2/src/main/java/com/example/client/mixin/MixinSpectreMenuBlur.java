package com.example.client.mixin;

import net.spectre.client.gui.ImGuiScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** While the Spectre GUI is open, the vanilla blur shader runs at the theme's blur strength instead of the video option. */
@Environment(EnvType.CLIENT)
@Mixin(Options.class)
public abstract class MixinSpectreMenuBlur {
   @Inject(method = "getMenuBackgroundBlurriness", at = @At("HEAD"), cancellable = true, require = 0)
   private void spectre$guiBlur(CallbackInfoReturnable<Integer> cir) {
      Minecraft mc = Minecraft.getInstance();
      if (mc != null && mc.gui != null && mc.gui.screen() instanceof ImGuiScreen) {
         cir.setReturnValue(ImGuiScreen.blurRadius());
      }
   }
}
