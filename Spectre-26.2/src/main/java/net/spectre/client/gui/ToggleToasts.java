package net.spectre.client.gui;

import imgui.ImDrawList;
import imgui.ImFont;
import imgui.ImFontGlyph;
import imgui.ImGui;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.spectre.client.config.HudTheme;
import net.spectre.client.config.Surface;
import net.spectre.client.event.impl.ModuleToggleEvent;
import net.spectre.client.imgui.ImGuiManager;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleRegistry;
import net.spectre.client.module.impl.client.HudModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;

@Environment(EnvType.CLIENT)
public final class ToggleToasts {
   public static final String ID = "toggle_toasts";
   private static final float MARGIN = 10.0F;
   private static final float GAP = 6.0F;
   private static final float CAP_H = 28.0F;
   private static final float PAD_X = 12.0F;
   private static final float PAD_Y = 8.0F;
   private static final float LINE_GAP = 2.0F;
   private static final float GAP_DIV = 10.0F;
   private static final float DIV_H = 12.0F;
   private static final float DOT = 3.0F;
   private static final float GAP_DOT = 8.0F;
   private static final float CHIP_H = 16.0F;
   private static final float CHIP_PAD = 6.0F;
   private static final float CHIP_RADIUS = 4.0F;
   private static final float GAP_CHIP = 10.0F;
   private static final float CHAT_INPUT = 14.0F;
   private static final int MAX_SHOWN = 4;
   private static final float IN_SECONDS = 0.16F;
   private static final float HOLD_SECONDS = 2.0F;
   private static final float OUT_SECONDS = 0.18F;
   private static final float MAX_STEP = 0.033333335F;
   private static final float LIFT_SPEED = 14.0F;
   private static final float OFF_OUTLINE = 0.55F;
   private static final int CHIP_TEXT = -1;
   private static final Object OWNER = new Object();
   private static final Queue<ToggleToasts.Toggle> PENDING = new ConcurrentLinkedQueue<>();
   private static final List<ToggleToasts.Toast> TOASTS = new ArrayList<>();
   private static float lift = Float.NaN;

   private ToggleToasts() {
   }

   public static void install() {
      ModuleToggleEvent.TYPE.listen(OWNER, event -> show(event.module, event.enabled));
   }

   public static void show(ClientModule module, boolean enabled) {
      if (module.toggleMessageOn()) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null && mc.level != null) {
            PENDING.add(new ToggleToasts.Toggle(module, enabled));
         }
      }
   }

   public static boolean hasWork() {
      return !TOASTS.isEmpty() || !PENDING.isEmpty();
   }

   public static void render() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         PENDING.clear();
         TOASTS.clear();
         lift = Float.NaN;
      } else {
         float dt = Math.min(ImGuiManager.getInstance().frameDelta(), 0.033333335F);
         step(dt);

         ToggleToasts.Toggle toggle;
         while ((toggle = PENDING.poll()) != null) {
            add(toggle);
         }

         if (TOASTS.isEmpty()) {
            lift = Float.NaN;
         } else {
            float s = ImGuiManager.getInstance().uiScale();
            lift = Motion.step(lift, liftTarget(mc, s), dt, 14.0F);
            if (!hidden(mc)) {
               draw(s);
            }
         }
      }
   }

   private static void add(ToggleToasts.Toggle toggle) {
      ToggleToasts.Toast toast = null;

      for (ToggleToasts.Toast existing : TOASTS) {
         if (existing.module == toggle.module()) {
            toast = existing;
            break;
         }
      }

      if (toast == null) {
         TOASTS.add(toast = new ToggleToasts.Toast(toggle.module()));
      }

      toast.enabled = toggle.enabled();
      toast.leaving = false;
      toast.hold = 2.0F;
      int alive = 1;

      for (int i = TOASTS.size() - 1; i >= 0; i--) {
         ToggleToasts.Toast other = TOASTS.get(i);
         if (other != toast && !other.leaving) {
            if (++alive > 4) {
               other.leaving = true;
            }
         }
      }
   }

   private static void step(float dt) {
      for (int i = TOASTS.size() - 1; i >= 0; i--) {
         ToggleToasts.Toast toast = TOASTS.get(i);
         if (toast.leaving) {
            toast.shown -= dt / 0.18F;
            if (toast.shown <= 0.0F) {
               TOASTS.remove(i);
            }
         } else if (toast.shown < 1.0F) {
            toast.shown = Math.min(1.0F, toast.shown + dt / 0.16F);
         } else if ((toast.hold -= dt) <= 0.0F) {
            toast.leaving = true;
         }
      }
   }

   private static float liftTarget(Minecraft mc, float s) {
      return mc.gui.screen() instanceof ChatScreen ? 14.0F * mc.getWindow().getGuiScale() + 6.0F * s : 10.0F * s;
   }

   private static boolean hidden(Minecraft mc) {
      return mc.gui.hud.isHidden() && mc.gui.screen() == null
         ? true
         : mc.gui.overlay() != null
            || mc.gui.screen() instanceof LevelLoadingScreen
            || mc.gui.screen() instanceof GenericMessageScreen
            || mc.gui.screen() instanceof ProgressScreen;
   }

   private static void draw(float s) {
      HudModule hud = ModuleRegistry.get(HudModule.class);
      HudModule.ToastStyle style = hud != null ? hud.toastStyle() : HudModule.ToastStyle.NOTIFY;
      Surface surface = HudTheme.get().toasts;
      if (style == HudModule.ToastStyle.NOTIFY) {
         drawNotifications(s, surface, hud == null || hud.shadow());
         return;
      }
      ToggleToasts.Look k = look(style, s, HudChrome.needsTextGuard(surface));
      float cursor = ImGui.getIO().getDisplaySizeY() - lift;

      for (int i = TOASTS.size() - 1; i >= 0; i--) {
         ToggleToasts.Toast toast = TOASTS.get(i);
         toast.open = ClickGuiRenderer.easeOut(Math.max(0.0F, Math.min(1.0F, toast.shown)));
         toast.bottom = Math.round(cursor);
         toast.top = Math.round(cursor - k.h() * toast.open);
         cursor -= (k.h() + 6.0F * s) * toast.open;
      }

      ImDrawList dl = ImGui.getForegroundDrawList();
      boolean shadow = hud == null || hud.shadow();
      float r = HudTheme.get().rounding.get() * s;
      float left = Math.round(10.0F * s);

      for (ToggleToasts.Toast toast : TOASTS) {
         float cardH = toast.bottom - toast.top;
         if (!(cardH < 1.0F)) {
            String name = toast.module.name();
            float nameW = HudChrome.width(k.name(), k.namePx(), name);
            float w = measure(style, k, nameW);
            float x = Math.round(left - (w + left) * (1.0F - toast.open));
            float a = toast.open;
            HudChrome.card(dl, surface, x, toast.top, w, cardH, r, a, shadow, s);
            boolean clip = cardH < k.h() - 0.5F;
            if (clip) {
               dl.pushClipRect(x, toast.top, x + w, toast.bottom, true);
            }

            float y = Math.round(toast.top - (k.h() - cardH) * 0.5F);
            switch (style) {
               case CARD:
                  drawCard(dl, k, toast, x, y, a);
                  break;
               case CHIP:
                  drawChip(dl, k, toast, x, y, w, a);
                  break;
               default:
                  drawBar(dl, k, toast, x, y, nameW, a);
            }

            if (clip) {
               dl.popClipRect();
            }
         }
      }
   }

   /**
    * Notification cards in the bottom-right corner: they slide in from the edge, show an icon tile with a
    * tick or cross, the module name and its new state, and a thin bar that drains until the card leaves.
    */
   private static void drawNotifications(float s, Surface surface, boolean shadow) {
      ImGuiManager gui = ImGuiManager.getInstance();
      ImFont nameFont = gui.hudTextFont(), labelFont = gui.hudLabelFont();
      int namePx = HudChrome.px(16.0F, s), labelPx = HudChrome.px(13.0F, s);
      boolean guard = HudChrome.needsTextGuard(surface);
      float h = Math.round(46.0F * s), margin = Math.round(10.0F * s), r = Math.max(6.0F, HudTheme.get().rounding.get()) * s;
      float screenW = ImGui.getIO().getDisplaySizeX();
      float cursor = ImGui.getIO().getDisplaySizeY() - lift;
      int accent = WorldText.hudAccent();
      ImDrawList dl = ImGui.getForegroundDrawList();
      for (int i = TOASTS.size() - 1; i >= 0; i--) {
         ToggleToasts.Toast toast = TOASTS.get(i);
         float open = ClickGuiRenderer.easeOut(Math.max(0.0F, Math.min(1.0F, toast.shown)));
         float top = Math.round(cursor - h * Math.min(1.0F, open * 1.4F));
         cursor -= (h + 6.0F * s) * open;
         String name = toast.module.name();
         String state = toast.enabled ? "Enabled" : "Disabled";
         float textW = Math.max(HudChrome.width(nameFont, namePx, name), HudChrome.width(labelFont, labelPx, state));
         float icon = Math.round(30.0F * s);
         float w = (float) Math.ceil(Math.max(170.0F * s, 8.0F * s + icon + 10.0F * s + textW + 16.0F * s));
         float x = Math.round(screenW - margin - w + (w + margin * 2) * (1.0F - open));
         float a = Math.min(1.0F, open * 1.2F);
         HudChrome.card(dl, surface, x, top, w, h, r, a, shadow, s);
         int tint = toast.enabled ? accent : HudChrome.TEXT_LABEL;
         // Icon tile with a tick (on) or a cross (off).
         float ix = x + 8.0F * s, iy = top + (h - icon) * 0.5F;
         dl.addRectFilled(ix, iy, ix + icon, iy + icon, HudChrome.scaleAlpha(tint, a * 0.22F), 7.0F * s);
         dl.addRect(ix, iy, ix + icon, iy + icon, HudChrome.scaleAlpha(tint, a * 0.55F), 7.0F * s, 0, 1.0F);
         float cx = ix + icon * 0.5F, cy = iy + icon * 0.5F, k = 5.0F * s, th = Math.max(1.5F, 2.0F * s);
         if (toast.enabled) {
            dl.addLine(cx - k, cy, cx - k * 0.3F, cy + k * 0.7F, HudChrome.scaleAlpha(tint, a), th);
            dl.addLine(cx - k * 0.3F, cy + k * 0.7F, cx + k, cy - k * 0.7F, HudChrome.scaleAlpha(tint, a), th);
         } else {
            dl.addLine(cx - k * 0.75F, cy - k * 0.75F, cx + k * 0.75F, cy + k * 0.75F, HudChrome.scaleAlpha(tint, a), th);
            dl.addLine(cx + k * 0.75F, cy - k * 0.75F, cx - k * 0.75F, cy + k * 0.75F, HudChrome.scaleAlpha(tint, a), th);
         }
         float tx = Math.round(ix + icon + 10.0F * s);
         float nameTop = Math.round(top + (h - namePx - 3.0F * s - labelPx) * 0.5F);
         HudChrome.text(dl, nameFont, namePx, tx, nameTop, HudChrome.scaleAlpha(HudChrome.TEXT_VALUE, a), name, guard);
         HudChrome.text(dl, labelFont, labelPx, tx, Math.round(nameTop + namePx + 3.0F * s), HudChrome.scaleAlpha(tint, a), state, guard);
         // Time-left bar along the bottom.
         float left = toast.leaving ? 0.0F : toast.shown < 1.0F ? 1.0F : Math.max(0.0F, toast.hold / 2.0F);
         float barY = top + h - Math.max(1.0F, 2.0F * s);
         dl.pushClipRect(x, top, x + w, top + h, true);
         dl.addRectFilled(x + r * 0.5F, barY, x + r * 0.5F + (w - r) * left, top + h, HudChrome.scaleAlpha(tint, a * 0.8F));
         dl.popClipRect();
      }
   }

   private static ToggleToasts.Look look(HudModule.ToastStyle style, float s, boolean guard) {
      ImGuiManager gui = ImGuiManager.getInstance();
      ImFont name = gui.hudTextFont();
      ImFont label = gui.hudLabelFont();
      int namePx = HudChrome.px(17.0F, s);
      int labelPx = HudChrome.px(14.0F, s);
      float h = style == HudModule.ToastStyle.CARD ? Math.round(8.0F * s * 2.0F + namePx + 2.0F * s + labelPx) : Math.round(28.0F * s);
      float stateW = Math.max(HudChrome.width(label, labelPx, state(style, true)), HudChrome.width(label, labelPx, state(style, false)));
      if (style == HudModule.ToastStyle.CHIP) {
         stateW = (float)Math.ceil(stateW + 6.0F * s * 2.0F);
      }

      return new ToggleToasts.Look(name, namePx, label, labelPx, s, h, stateW, WorldText.hudAccent(), guard);
   }

   private static String state(HudModule.ToastStyle style, boolean enabled) {
      return switch (style) {
         case CARD, NOTIFY -> enabled ? "Enabled" : "Disabled";
         case CHIP -> enabled ? "ON" : "OFF";
         case BAR -> enabled ? "on" : "off";
      };
   }

   private static float measure(HudModule.ToastStyle style, ToggleToasts.Look k, float nameW) {
      float s = k.s();

      float w = switch (style) {
         case CARD, NOTIFY -> 12.0F * s * 2.0F + 14.0F * s + Math.max(nameW, k.stateW());
         case CHIP -> 12.0F * s + nameW + 10.0F * s + k.stateW() + chipInset(k);
         case BAR -> 12.0F * s * 2.0F + nameW + 10.0F * s * 2.0F + 1.0F + k.stateW();
      };
      return (float)Math.ceil(w);
   }

   private static float chipInset(ToggleToasts.Look k) {
      return (float)Math.floor((k.h() - Math.round(16.0F * k.s())) * 0.5F);
   }

   private static void drawBar(ImDrawList dl, ToggleToasts.Look k, ToggleToasts.Toast toast, float x, float y, float nameW, float a) {
      float s = k.s();
      float nameTop = y + (float)Math.floor((k.h() - k.namePx()) * 0.5F);
      float labelTop = nameTop + HudChrome.baselineOffset(k.name(), k.namePx(), k.label(), k.labelPx());
      float cx = Math.round(x + 12.0F * s);
      HudChrome.text(dl, k.name(), k.namePx(), cx, nameTop, HudChrome.scaleAlpha(HudChrome.TEXT_VALUE, a), toast.module.name(), k.guard());
      cx += nameW + 10.0F * s;
      HudChrome.divider(dl, cx, y + k.h() * 0.5F, 12.0F * s, a);
      cx += 1.0F + 10.0F * s;
      int color = HudChrome.scaleAlpha(toast.enabled ? k.accent() : HudChrome.TEXT_LABEL, a);
      HudChrome.text(dl, k.label(), k.labelPx(), Math.round(cx), labelTop, color, state(HudModule.ToastStyle.BAR, toast.enabled), k.guard());
   }

   private static void drawCard(ImDrawList dl, ToggleToasts.Look k, ToggleToasts.Toast toast, float x, float y, float a) {
      float s = k.s();
      dl.addCircleFilled(x + 15.0F * s, y + k.h() * 0.5F, 3.0F * s, HudChrome.scaleAlpha(toast.enabled ? k.accent() : HudChrome.TEXT_LABEL, a), 0);
      float tx = Math.round(x + 26.0F * s);
      float nameTop = Math.round(y + 8.0F * s);
      HudChrome.text(dl, k.name(), k.namePx(), tx, nameTop, HudChrome.scaleAlpha(HudChrome.TEXT_VALUE, a), toast.module.name(), k.guard());
      HudChrome.text(
         dl,
         k.label(),
         k.labelPx(),
         tx,
         Math.round(nameTop + k.namePx() + 2.0F * s),
         HudChrome.scaleAlpha(HudChrome.TEXT_LABEL, a),
         state(HudModule.ToastStyle.CARD, toast.enabled),
         k.guard()
      );
   }

   private static void drawChip(ImDrawList dl, ToggleToasts.Look k, ToggleToasts.Toast toast, float x, float y, float w, float a) {
      float s = k.s();
      float nameTop = y + (float)Math.floor((k.h() - k.namePx()) * 0.5F);
      HudChrome.text(
         dl, k.name(), k.namePx(), Math.round(x + 12.0F * s), nameTop, HudChrome.scaleAlpha(HudChrome.TEXT_VALUE, a), toast.module.name(), k.guard()
      );
      float inset = chipInset(k);
      float capH = Math.round(16.0F * s);
      float right = x + w - inset;
      float l = right - k.stateW();
      float top = y + inset;
      float r = Math.min(HudTheme.get().rounding.get(), 4.0F) * s;
      if (toast.enabled) {
         dl.addRectFilled(l, top, right, top + capH, HudChrome.scaleAlpha(k.accent(), a), r);
      } else {
         dl.addRect(l, top, right, top + capH, HudChrome.scaleAlpha(HudChrome.TEXT_LABEL, a * 0.55F), r, 0, 1.0F);
      }

      String word = state(HudModule.ToastStyle.CHIP, toast.enabled);
      float wordX = Math.round(l + (k.stateW() - HudChrome.width(k.label(), k.labelPx(), word)) * 0.5F);
      float wordY = Math.round(top + capH * 0.5F - capMiddle(k.label(), k.labelPx()));
      HudChrome.text(
         dl, k.label(), k.labelPx(), wordX, wordY, HudChrome.scaleAlpha(toast.enabled ? -1 : HudChrome.TEXT_LABEL, a), word, !toast.enabled && k.guard()
      );
   }

   private static float capMiddle(ImFont font, int px) {
      float top = px * 0.2F;
      if (font != null && font.getFontSize() > 0.0F) {
         ImFontGlyph glyph = font.findGlyphNoFallback(72);
         if (glyph.isValidPtr()) {
            top = glyph.getY0() * px / font.getFontSize();
         }
      }

      return (top + HudChrome.ascent(font, px)) * 0.5F;
   }

   @Environment(EnvType.CLIENT)
   private record Look(ImFont name, int namePx, ImFont label, int labelPx, float s, float h, float stateW, int accent, boolean guard) {
   }

   @Environment(EnvType.CLIENT)
   private static final class Toast {
      final ClientModule module;
      boolean enabled;
      boolean leaving;
      float shown;
      float hold;
      float open;
      float top;
      float bottom;

      Toast(ClientModule module) {
         this.module = module;
      }
   }

   @Environment(EnvType.CLIENT)
   private record Toggle(ClientModule module, boolean enabled) {
   }
}
