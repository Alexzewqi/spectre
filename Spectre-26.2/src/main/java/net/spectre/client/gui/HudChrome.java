package net.spectre.client.gui;

import imgui.ImDrawList;
import imgui.ImFont;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import net.spectre.client.config.HudTheme;
import net.spectre.client.config.Surface;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class HudChrome {
   static final int TEXT_VALUE = GuiPalette.abgr(-657929);
   static final int TEXT_NAME = GuiPalette.abgr(-1447442);
   static final int TEXT_LABEL = GuiPalette.abgr(-6052949);
   static final int WARN = GuiPalette.abgr(-47814);
   static final int BORDER = 402653183;
   static final int DIVIDER = 536870911;
   static final int TRACK = 452984831;
   static final int CHIP_FILL = GuiPalette.abgr(-350873571);
   private static final int TEXT_GUARD = -1946157056;
   private static final Long2FloatOpenHashMap DIGIT_CELLS = new Long2FloatOpenHashMap();
   private static final String[] ASCII = new String[128];

   private HudChrome() {
   }

   static void card(ImDrawList dl, Surface surface, float x, float y, float w, float h, float r, float alpha, boolean shadow, float scale) {
      if (!(alpha <= 0.001F)) {
         if (shadow) {
            shadow(dl, x, y, w, h, r, alpha, scale);
         }

         HudTheme theme = HudTheme.get();
         if (theme.style.is(HudTheme.STYLE_GLASS)) {
            glass(dl, surface, theme, x, y, w, h, r, alpha);
            return;
         }
         if (theme.style.is(HudTheme.STYLE_GRADIENT)) {
            int a = Math.round(surface.opacity.get() * 255.0F);
            gradient(dl, x, y, w, h, r, GuiPalette.abgr(theme.gradientFrom.argbWithAlpha(a)), GuiPalette.abgr(theme.gradientTo.argbWithAlpha(a)), alpha);
            // Soft top light so the gradient reads as a surface, not a flat swatch.
            gradientVertical(dl, x, y, w, h * 0.5F, r, scaleAlpha(0x22FFFFFF, alpha), 0x00FFFFFF);
         } else {
            dl.addRectFilled(x, y, x + w, y + h, scaleAlpha(GuiPalette.fill(surface), alpha), r);
            // Faint top sheen and bottom shade give the flat fill some depth.
            gradientVertical(dl, x, y, w, Math.min(h, Math.max(r * 2, h * 0.5F)), r, scaleAlpha(0x14FFFFFF, alpha), 0x00FFFFFF);
            gradientVertical(dl, x, y + h * 0.65F, w, h * 0.35F, 0, 0x00000000, scaleAlpha(0x1E000000, alpha));
         }
         SurfaceBackground.draw(dl, surface, x, y, w, h, r, alpha);
         dl.addRect(x, y, x + w, y + h, scaleAlpha(402653183, alpha), r, 0, 1.0F);
         // Brighter rim along the top edge only.
         dl.pushClipRect(x, y, x + w, y + Math.max(r, h * 0.3F), true);
         dl.addRect(x + 0.5F, y + 0.5F, x + w - 0.5F, y + h - 0.5F, scaleAlpha(0x26FFFFFF, alpha), r, 0, 1.0F);
         dl.popClipRect();
      }
   }

   /**
    * iOS-style liquid glass: a dark frosted body, a bright specular cap fading down from the top edge,
    * a thin luminous rim that is strongest at the top, and a faint light band drifting across.
    */
   private static void glass(ImDrawList dl, Surface surface, HudTheme theme, float x, float y, float w, float h, float r, float alpha) {
      float frost = theme.glassTint.get();
      dl.addRectFilled(x, y, x + w, y + h, scaleAlpha(GuiPalette.fill(surface), alpha), r);
      dl.addRectFilled(x, y, x + w, y + h, scaleAlpha(argbA(0xFFFFFF, frost), alpha), r);
      SurfaceBackground.draw(dl, surface, x, y, w, h, r, alpha);
      // Specular cap.
      gradientVertical(dl, x, y, w, Math.min(h, Math.max(r * 2, h * 0.55F)), r, scaleAlpha(argbA(0xFFFFFF, 0.22F + frost * 0.4F), alpha), 0x00FFFFFF);
      // Drifting sheen: a narrow diagonal band sliding left to right every few seconds.
      double t = (System.currentTimeMillis() % 6000L) / 6000.0;
      float band = (float) (x - w * 0.4F + t * w * 1.8F);
      dl.pushClipRect(x, y, x + w, y + h, true);
      dl.addQuadFilled(band, y, band + w * 0.12F, y, band + w * 0.05F, y + h, band - w * 0.07F, y + h, scaleAlpha(argbA(0xFFFFFF, 0.06F), alpha));
      dl.popClipRect();
      // Inner shade along the bottom for depth.
      gradientVertical(dl, x, y + h * 0.6F, w, h * 0.4F, r, 0x00000000, scaleAlpha(argbA(0x000000, 0.18F), alpha));
      // Rim: faint all round, bright along the top edge.
      dl.addRect(x, y, x + w, y + h, scaleAlpha(argbA(0xFFFFFF, 0.22F), alpha), r, 0, 1.0F);
      dl.pushClipRect(x, y, x + w, y + Math.max(r, h * 0.35F), true);
      dl.addRect(x + 0.5F, y + 0.5F, x + w - 0.5F, y + h - 0.5F, scaleAlpha(argbA(0xFFFFFF, 0.45F), alpha), r, 0, 1.0F);
      dl.popClipRect();
   }

   /** ImGui colours are ABGR; channel order does not matter for white/black, alpha does. */
   private static int argbA(int rgb, float a) {
      return Math.round(Math.max(0, Math.min(1, a)) * 255.0F) << 24 | rgb & 0xFFFFFF;
   }

   /**
    * Left-to-right gradient with rounded corners, built from 1-2px columns whose height follows the
    * corner arcs (ImGui's multi-colour rectangle cannot be rounded).
    */
   static void gradient(ImDrawList dl, float x, float y, float w, float h, float r, int from, int to, float alpha) {
      r = Math.min(r, Math.min(w, h) * 0.5F);
      float step = Math.max(1.0F, w / 160.0F);
      for (float cx = x; cx < x + w; cx += step) {
         float x2 = Math.min(x + w, cx + step), mid = (cx + x2) * 0.5F;
         float edge = Math.min(mid - x, x + w - mid), inset = 0;
         if (edge < r) { float d = r - edge; inset = r - (float) Math.sqrt(Math.max(0, r * r - d * d)); }
         dl.addRectFilled(cx, y + inset, x2, y + h - inset, scaleAlpha(lerp(from, to, (mid - x) / w), alpha));
      }
   }

   /** Top-to-bottom gradient with the top corners rounded, used for highlights. */
   static void gradientVertical(ImDrawList dl, float x, float y, float w, float h, float r, int top, int bottom) {
      if (h <= 0) return;
      r = Math.min(r, Math.min(w, h) * 0.5F);
      float step = Math.max(1.0F, h / 40.0F);
      for (float cy = y; cy < y + h; cy += step) {
         float y2 = Math.min(y + h, cy + step), mid = (cy + y2) * 0.5F, inset = 0;
         if (mid - y < r) { float d = r - (mid - y); inset = r - (float) Math.sqrt(Math.max(0, r * r - d * d)); }
         dl.addRectFilled(x + inset, cy, x + w - inset, y2, lerp(top, bottom, (mid - y) / h));
      }
   }

   private static int lerp(int a, int b, float t) {
      t = Math.max(0, Math.min(1, t));
      int out = 0;
      for (int shift = 0; shift < 32; shift += 8) {
         int ca = a >>> shift & 0xFF, cb = b >>> shift & 0xFF;
         out |= Math.round(ca + (cb - ca) * t) << shift;
      }
      return out;
   }

   static void shadow(ImDrawList dl, float x, float y, float w, float h, float r, float alpha, float scale) {
      float s = Math.max(1.0F, scale);
      dl.addRectFilled(x - s, y - 0.5F * s, x + w + s, y + h + 1.5F * s, scaleAlpha(771751936, alpha), r + s);
      dl.addRectFilled(x - 2.0F * s, y + 0.5F * s, x + w + 2.0F * s, y + h + 3.5F * s, scaleAlpha(385875968, alpha), r + 2.0F * s);
      dl.addRectFilled(x - 3.0F * s, y + 1.5F * s, x + w + 3.0F * s, y + h + 6.0F * s, scaleAlpha(201326592, alpha), r + 3.0F * s);
      dl.addRectFilled(x - 5.0F * s, y + 2.5F * s, x + w + 5.0F * s, y + h + 9.0F * s, scaleAlpha(0x08000000, alpha), r + 5.0F * s);
   }

   static void divider(ImDrawList dl, float x, float centerY, float height, float alpha) {
      float px = Math.round(x);
      float top = Math.round(centerY - height * 0.5F);
      dl.addRectFilled(px, top, px + 1.0F, top + Math.round(height), scaleAlpha(536870911, alpha));
   }

   static boolean needsTextGuard(Surface surface) {
      return surface != null && (surface.opacity.get() < 0.55F || surface.isImage());
   }

   static int px(float base, float scale) {
      return Math.max(6, Math.round(base * scale));
   }

   static float width(ImFont font, int size, String text) {
      return font != null && !text.isEmpty() ? font.calcTextSizeA(size, Float.MAX_VALUE, 0.0F, text).x : text.length() * size * 0.55F;
   }

   static float ascent(ImFont font, int size) {
      return font != null && !(font.getFontSize() <= 0.0F) ? font.getAscent() * size / font.getFontSize() : size * 0.77F;
   }

   static float baselineOffset(ImFont big, int bigSize, ImFont small, int smallSize) {
      return Math.round(ascent(big, bigSize) - ascent(small, smallSize));
   }

   static void text(ImDrawList dl, ImFont font, int size, float x, float y, int color, String text, boolean guard) {
      if (!text.isEmpty() && (color >>> 24 & 0xFF) != 0) {
         if (guard) {
            raw(dl, font, size, x + 1.0F, y + 1.0F, scaleAlpha(-1946157056, (color >>> 24 & 0xFF) / 255.0F), text);
         }

         raw(dl, font, size, x, y, color, text);
      }
   }

   private static void raw(ImDrawList dl, ImFont font, int size, float x, float y, int color, String text) {
      if (font != null) {
         dl.addText(font, size, x, y, color, text);
      } else {
         dl.addText(x, y, color, text);
      }
   }

   private static float digitCell(ImFont font, int size) {
      if (font == null) {
         return size * 0.6F;
      } else {
         long key = (long)System.identityHashCode(font) << 16 ^ size;
         float cached = DIGIT_CELLS.get(key);
         if (!Float.isNaN(cached)) {
            return cached;
         } else {
            float cell = 0.0F;

            for (char c = '0'; c <= '9'; c++) {
               cell = Math.max(cell, advance(font, size, c));
            }

            if (DIGIT_CELLS.size() > 64) {
               DIGIT_CELLS.clear();
            }

            DIGIT_CELLS.put(key, cell);
            return cell;
         }
      }
   }

   private static float advance(ImFont font, int size, char c) {
      return font.getCharAdvance(c) * size / font.getFontSize();
   }

   static float tabularWidth(ImFont font, int size, String text) {
      if (font == null) {
         return width(null, size, text);
      } else {
         float cell = digitCell(font, size);
         float w = 0.0F;

         for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            w += Character.isDigit(c) ? cell : advance(font, size, c);
         }

         return w;
      }
   }

   static void tabular(ImDrawList dl, ImFont font, int size, float x, float y, int color, String text, boolean guard) {
      if (font == null) {
         text(dl, null, size, x, y, color, text, guard);
      } else {
         float cell = digitCell(font, size);
         float cx = x;

         for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            float adv = advance(font, size, c);
            if (Character.isDigit(c)) {
               text(dl, font, size, cx + (cell - adv) * 0.5F, y, color, glyph(c), guard);
               cx += cell;
            } else {
               if (c != ' ') {
                  text(dl, font, size, cx, y, color, glyph(c), guard);
               }

               cx += adv;
            }
         }
      }
   }

   private static String glyph(char c) {
      return c < ASCII.length ? ASCII[c] : String.valueOf(c);
   }

   static int scaleAlpha(int color, float alphaScale) {
      int alpha = color >>> 24 & 0xFF;
      int scaled = Math.round(alpha * Math.max(0.0F, Math.min(1.0F, alphaScale)));
      return color & 16777215 | scaled << 24;
   }

   static {
      DIGIT_CELLS.defaultReturnValue(Float.NaN);

      for (char c = 0; c < ASCII.length; c++) {
         ASCII[c] = String.valueOf(c);
      }
   }
}
