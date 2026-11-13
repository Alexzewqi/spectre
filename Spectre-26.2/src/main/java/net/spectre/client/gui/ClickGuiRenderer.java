package net.spectre.client.gui;

import imgui.ImDrawList;
import imgui.ImFont;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.flag.ImGuiColorEditFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.spectre.client.config.ConfigProfiles;
import net.spectre.client.config.FriendStore;
import net.spectre.client.config.HudTheme;
import net.spectre.client.config.ModuleCodes;
import net.spectre.client.config.ModuleConfigStore;
import net.spectre.client.config.ProfileException;
import net.spectre.client.config.Surface;
import net.spectre.client.config.ThemeStore;
import net.spectre.client.imgui.ImGuiManager;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.ModuleRegistry;
import net.spectre.client.module.impl.client.HotkeysModule;
import net.spectre.client.module.impl.client.HudModule;
import net.spectre.client.module.impl.client.RadarModule;
import net.spectre.client.module.impl.client.RegionMapModule;
import net.spectre.client.module.setting.ActionSetting;
import net.spectre.client.module.setting.BlockColorListSetting;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ColorSetting;
import net.spectre.client.module.setting.ItemColorListSetting;
import net.spectre.client.module.setting.KeySetting;
import net.spectre.client.module.setting.ModeSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.spectre.client.module.setting.Setting;
import net.spectre.client.module.setting.StringSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public final class ClickGuiRenderer {
   private static final float PANEL_W = 212.0F;
   private static final float PANEL_GAP = 14.0F;
   private static final float PANEL_TOP = 28.0F;
   private static final float HEADER_H = 42.0F;
   private static final float ROW_H = 30.0F;
   private static final float ROW_GAP = 2.0F;
   private static final float ROW_INSET = 6.0F;
   private static final float CARD_W = 460.0F;
   private static final float CARD_MAX_H = 760.0F;
   private static final float CARD_PAD = 24.0F;
   private static final float SETTING_ROW_H = 46.0F;
   private static final float TAB_BAR_H = 44.0F;
   private static final float TAB_BAR_BOTTOM = 26.0F;
   private static final float INSPECTOR_W = 360.0F;
   private static final float SMALL_TOG_W = 16.0F;
   private static final float SMALL_TOG_H = 16.0F;
   public static final int ERROR_TEXT = -10856974;
   private static final int[] AVATAR_COLORS = new int[]{-624588, -10893516, -16080897, -2207057, -10536961, -3690448, -2730408, -16724737};
   private static final int PANEL_FLAGS = 123;
   private static final int CARD_FLAGS = 8511;
   public static final int CHILD_FLAGS = 24;
   private static final String PANEL_ID_PREFIX = "##panel_";
   private static final String SEARCH_PANEL_ID = "##panel_search";
   private static final int STYLE_VAR_COUNT = 11;
   private static final int DYNAMIC_COLOR_COUNT = 10;
   private static final int[] THEME_COLORS = new int[]{
      0,
      -461582,
      1,
      -4542808,
      3,
      0,
      4,
      -97901279,
      5,
      352321535,
      6,
      0,
      7,
      -13226454,
      8,
      -12240588,
      9,
      -12240588,
      21,
      -13226454,
      22,
      -12240588,
      24,
      -13226454,
      25,
      -12240588,
      27,
      268435455,
      14,
      0,
      15,
      822083583,
      16,
      1358954495,
      17,
      1895825407,
      30,
      0,
      31,
      0,
      32,
      0,
      53,
      0
   };
   private ClickGuiRenderer.Tab tab = ClickGuiRenderer.Tab.MODULES;
   private final long guiOpenedAt = System.nanoTime();
   private long viewChangedAt = this.guiOpenedAt;
   private float tabIndicatorX = -1.0F;
   private float tabIndicatorW;
   private ClientModule settingsModule;
   private boolean cardJustOpened;
   private boolean dividerPending;
   private boolean panelGuard;
   private boolean tabBarGuard;
   private boolean cardGuard;
   private boolean cardTextLift;
   private final HashMap<String, float[]> scroll = new HashMap<>();
   private static float scrollDragFrom;
   private static final ImBoolean SCROLLBAR_HOVERED = new ImBoolean();
   private static final ImBoolean SCROLLBAR_HELD = new ImBoolean();
   private ClientModule listeningModule = null;
   private boolean listeningFirstFrame = false;
   private KeySetting listeningKeySetting = null;
   private boolean listeningKeyFirstFrame = false;
   private final ImBoolean colorPickerOpen = new ImBoolean(false);
   private ClientModule activeColorModule = null;
   private ColorSetting activeColorSetting = null;
   private final ImString globalSearch = new ImString(128);
   private boolean focusSearch;
   private float searchBodyH;
   private final Map<String, float[]> panelSpots = new HashMap<>();
   private ClickGuiRenderer.CategoryPanel[] panelCache = new ClickGuiRenderer.CategoryPanel[0];
   private int panelCacheRevision = -1;
   private final Map<String, float[]> panelFold = new HashMap<>();
   private final Map<String, Motion.Rows> rowMotion = new HashMap<>();
   private final Int2ObjectOpenHashMap<String> keyLabels = new Int2ObjectOpenHashMap();
   private int cachedRegistryRevision = -1;
   private String cachedGlobalSearch;
   private List<ClientModule> cachedGlobalResults = List.of();
   private final ImString friendInput = new ImString(32);
   private boolean focusFriendInput;
   private String friendMessage;
   private long friendMessageAt;
   private final ImString profileInput = new ImString(64);
   private boolean focusProfileInput;
   private String configMessage;
   private boolean configMessageError;
   private long configMessageAt;
   private String moduleCodeFor;
   private String moduleCodeMessage;
   private boolean moduleCodeError;
   private long moduleCodeAt;
   private final ProfileList profileList = new ProfileList(this::setConfigMessage);
   private String pendingConfirm;
   private long pendingConfirmAt;
   private String themeMessage;
   private boolean themeMessageError;
   private long themeMessageAt;
   private Surface themeSurface;
   private boolean inspectMode;
   private final List<float[]> panelRects = new ArrayList<>();
   private float[] cardRect;
   private float[] tabBarRect;
   private float[] inspectorRect;
   private boolean focusInspector;
   private long previewChangedAt;
   private String hudElementId;
   private boolean focusHudInspector;
   private String hudMessage;
   private boolean hudMessageError;
   private long hudMessageAt;
   private final BlockListEditor blockLists = new BlockListEditor();
   private ItemColorListSetting activeItemColorPalette = null;
   private boolean itemColorWindowOpen = false;
   private final ImString itemSearchBuf = new ImString(256);
   private ItemColorListSetting.ItemEntry activeItemColorEntry = null;
   private boolean editingItemDefaultColor = false;
   private final HashMap<ItemColorListSetting.ItemEntry, Float> itemEntryTogAnim = new HashMap<>();
   private final ImBoolean itemColorPickerOpen = new ImBoolean(false);

   // Single-window layout: a navigation sidebar, a top bar with greeting, clock and
   // search, and a two-column board of module cards that expand in place. The
   // existing setting editors are reused so every module keeps its specialized
   // controls, persistence and key bindings. All chrome colours are tinted from the
   // theme accent, so the "Violet" preset gives the purple look.
   private ModuleCategory workspaceCategory = ModuleCategory.COMBAT;
   public boolean builderWorkspaceOpen(){return workspaceCategory==ModuleCategory.BASE_BUILDER && tab == Tab.MODULES;}
   private ClientModule selectedModule;
   private boolean compactRows;
   private float workspaceX, workspaceY, workspaceW, workspaceH;
   private float sideW;
   private float contentX, contentY, contentW, contentH;
   private static String clientVersion;

   /** Near-black mixed toward the accent; t=0 is the darkest base, higher is lighter and more saturated. */
   private static int tone(float t, float alpha) {
      // Halloween keeps orange for accents but builds surfaces from a deep purple.
      // Surfaces start from a cool, near-neutral black and take only a hint of the accent, so panels
      // read as clean dark glass instead of a muddy tinted fill.
      int toward = spooky ? 0xFF8A2E9C : SpectreTheme.lerpColor(0xFF2A2420, GuiPalette.accent(), 0.45F);
      return SpectreWidgets.c(SpectreWidgets.withAlpha(SpectreTheme.lerpColor(0xFF0D0A09, toward, t), alpha));
   }

   /** True while the Halloween preset is active; refreshed once per frame. */
   private static boolean spooky;

   /**
    * Paints one GUI pane in the theme's style. {@code depth} is the tone of the solid fill (frame ~0.09,
    * panes ~0.15). Every style gets a 1px inner top highlight and a hairline border so panes read as
    * raised surfaces rather than flat boxes.
    */
   static void pane(ImDrawList dl, float x, float y, float w, float h, float r, float depth, float alpha) {
      ThemeStore theme = ThemeStore.get();
      if (theme.glass() && !spooky) {
         // Frosted: a translucent dark body lit from above, a bright rim and a soft drifting sheen.
         dl.addRectFilled(x, y, x + w, y + h, tone(depth * 0.6F, 0.62F * alpha), r);
         dl.addRectFilled(x, y, x + w, y + h, SpectreWidgets.c(SpectreWidgets.withAlpha(0xFFFFFFFF, 0.045F * alpha)), r);
         HudChrome.gradientVertical(dl, x, y, w, Math.min(h, SpectreWidgets.s(70F)), r,
            SpectreWidgets.c(SpectreWidgets.withAlpha(0xFFFFFFFF, 0.10F * alpha)), SpectreWidgets.c(SpectreWidgets.withAlpha(0xFFFFFFFF, 0F)));
         dl.addRect(x, y, x + w, y + h, SpectreWidgets.c(SpectreWidgets.withAlpha(0xFFFFFFFF, 0.10F * alpha)), r, 0, 1F);
         dl.pushClipRect(x, y, x + w, y + Math.min(h * 0.5F, SpectreWidgets.s(24F)), true);
         dl.addRect(x + 0.5F, y + 0.5F, x + w - 0.5F, y + h - 0.5F, SpectreWidgets.c(SpectreWidgets.withAlpha(0xFFFFFFFF, 0.28F * alpha)), r, 0, 1F);
         dl.popClipRect();
         return;
      }
      dl.addRectFilled(x, y, x + w, y + h, tone(depth, alpha), r);
      if (theme.gradient() && !spooky) {
         int a = GuiPalette.abgr(theme.accent.argbWithAlpha(255)), b = GuiPalette.abgr(theme.accent2.argbWithAlpha(255));
         HudChrome.gradient(dl, x, y, w, h, r, SpectreWidgets.c(SpectreWidgets.withAlpha(a, 0.13F)), SpectreWidgets.c(SpectreWidgets.withAlpha(b, 0.13F)), alpha);
      }
      // Inner top light and hairline edge.
      HudChrome.gradientVertical(dl, x, y, w, Math.min(h, SpectreWidgets.s(40F)), r,
         SpectreWidgets.c(SpectreWidgets.withAlpha(0xFFFFFFFF, 0.035F * alpha)), SpectreWidgets.c(SpectreWidgets.withAlpha(0xFFFFFFFF, 0F)));
      dl.addRect(x, y, x + w, y + h, SpectreWidgets.c(SpectreWidgets.withAlpha(0xFFFFFFFF, 0.06F * alpha)), r, 0, 1F);
   }

   /** The accent colour at {@code t} along accent → second accent (flat accent unless the theme is a gradient). */
   static int accentAt(float t) {
      ThemeStore theme = ThemeStore.get();
      if (!theme.gradient() && !theme.glass()) return GuiPalette.accent();
      return SpectreTheme.lerpColor(GuiPalette.accent(), GuiPalette.abgr(theme.accent2.argbWithAlpha(255)), t);
   }

   /** Ink for glyphs drawn on an accent fill: near-black on bright accents (white, mint, amber), white otherwise. */
   static int onAccentInk() {
      int c = accentAt(0.5F);
      float lum = 0.2126F * (c & 0xFF) + 0.7152F * (c >> 8 & 0xFF) + 0.0722F * (c >> 16 & 0xFF);
      return lum > 165F ? 0xFF15121A : 0xFFFFFFFF;
   }

   // Text colours stay unconverted: SpectreWidgets.text applies the style alpha itself.
   private static int mutedText(float alpha) {
      return SpectreWidgets.withAlpha(SpectreTheme.lerpColor(0xFFB4ABB6, GuiPalette.accent(), 0.18F), alpha);
   }

   private static int brightText(float alpha) {
      return SpectreWidgets.withAlpha(0xFFF4F0F5, alpha);
   }

   private static String clientVersion() {
      if (clientVersion == null) {
         clientVersion = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("spectre")
            .map(c -> "v" + c.getMetadata().getVersion().getFriendlyString().split("\\+")[0])
            .orElse("");
      }
      return clientVersion;
   }

   private static String playerName() {
      Minecraft mc = Minecraft.getInstance();
      return mc.getUser() == null ? "Player" : mc.getUser().getName();
   }

   /** Opening motion: scale with a slight overshoot, and a light sweep along the top edge. */
   private float openPop = 1F, openSweep = 1F;

   private static float backOut(float t) {
      t = Math.max(0F, Math.min(1F, t));
      float c = 1.4F, u = t - 1F;
      return 1F + (c + 1F) * u * u * u + c * u * u;
   }

   /**
    * Scene behind the window: an edge vignette, an accent haze rising from the bottom of the screen, and
    * slow floating motes that twinkle. Positions come from the mote index and the clock, so nothing is stored.
    */
   private static void drawBackdrop(ImDrawList bg, float sw, float sh, float open) {
      int black = SpectreWidgets.c(SpectreWidgets.withAlpha(0xFF000000, 0.55F * open)), clear = SpectreWidgets.c(SpectreWidgets.withAlpha(0xFF000000, 0F));
      float edgeX = sw * 0.22F, edgeY = sh * 0.26F;
      bg.addRectFilledMultiColor(0, 0, sw, edgeY, black, black, clear, clear);
      bg.addRectFilledMultiColor(0, sh - edgeY, sw, sh, clear, clear, black, black);
      bg.addRectFilledMultiColor(0, 0, edgeX, sh, black, clear, clear, black);
      bg.addRectFilledMultiColor(sw - edgeX, 0, sw, sh, clear, black, black, clear);
      if (spooky) return;
      int hazeA = SpectreWidgets.c(SpectreWidgets.withAlpha(accentAt(0F), 0.16F * open)), hazeB = SpectreWidgets.c(SpectreWidgets.withAlpha(accentAt(1F), 0.16F * open));
      int hazeClearA = SpectreWidgets.c(SpectreWidgets.withAlpha(accentAt(0F), 0F)), hazeClearB = SpectreWidgets.c(SpectreWidgets.withAlpha(accentAt(1F), 0F));
      bg.addRectFilledMultiColor(0, sh * 0.62F, sw, sh, hazeClearA, hazeClearB, hazeB, hazeA);
      double t = System.nanoTime() / 1.0E9;
      for (int i = 0; i < 70; i++) {
         // Cheap hash per mote for its lane, speed, size and phase.
         float h1 = frac(i * 0.61803F), h2 = frac(i * 0.75487F + 0.31F), h3 = frac(i * 0.56984F + 0.17F);
         float speed = 0.012F + 0.03F * h2;
         float life = frac((float) (t * speed) + h3);
         float px = sw * h1 + (float) Math.sin(t * (0.3 + h2) + i) * SpectreWidgets.s(18F);
         float py = sh * (1.05F - life * 1.1F);
         float fade = Math.min(1F, Math.min(life * 5F, (1F - life) * 4F));
         float twinkle = 0.55F + 0.45F * (float) Math.sin(t * (1.5 + h3 * 2.5) + i * 1.7);
         float size = SpectreWidgets.s(0.8F + 1.8F * h3);
         int col = accentAt(h2);
         float a = 0.5F * fade * twinkle * open;
         bg.addCircleFilled(px, py, size * 3F, SpectreWidgets.c(SpectreWidgets.withAlpha(col, 0.08F * a)), 12);
         bg.addCircleFilled(px, py, size, SpectreWidgets.c(SpectreWidgets.withAlpha(SpectreTheme.lerpColor(col, 0xFFFFFFFF, 0.5F), a)), 10);
      }
   }

   private static float frac(float v) {
      return v - (float) Math.floor(v);
   }

   /** A travelling glint on one horizontal edge of the window: clear → bright → clear, clamped to the straight part. */
   private static void edgeGlint(ImDrawList bg, float x0, float x1, float y, float head, float len, int color, float alpha) {
      float a = Math.max(x0, head - len), m = Math.max(x0, Math.min(x1, head - len * 0.5F)), b = Math.min(x1, head);
      if (b <= x0 || a >= x1) return;
      int bright = SpectreWidgets.c(SpectreWidgets.withAlpha(color, alpha)), clear = SpectreWidgets.c(SpectreWidgets.withAlpha(color, 0F));
      float th = SpectreWidgets.s(1.2F);
      if (m > a) bg.addRectFilledMultiColor(a, y - th * 0.5F, m, y + th * 0.5F, clear, bright, bright, clear);
      if (b > m) bg.addRectFilledMultiColor(m, y - th * 0.5F, b, y + th * 0.5F, bright, clear, clear, bright);
   }

   private void layoutFrame(float open) {
      float sw = ImGui.getIO().getDisplaySizeX(), sh = ImGui.getIO().getDisplaySizeY();
      float grow = 0.9F + 0.1F * openPop;
      workspaceW = Math.min(sw - 32F, SpectreWidgets.s(900F)) * grow;
      workspaceH = Math.min(sh - 32F - SpectreWidgets.s(40F), SpectreWidgets.s(580F)) * grow;
      workspaceX = (sw - workspaceW) / 2;
      workspaceY = (sh - workspaceH) / 2 + SpectreWidgets.s(14F) + SpectreWidgets.s(18F) * (1 - open);
      sideW = 0;
      float gap = SpectreWidgets.s(14F);
      contentX = workspaceX + gap;
      contentW = workspaceW - gap * 2;
      contentY = workspaceY + headerH();
      contentH = workspaceY + workspaceH - footerH() - contentY;
   }

   private static float headerH() {
      return SpectreWidgets.s(70F);
   }

   private static float footerH() {
      return SpectreWidgets.s(46F);
   }

   private void renderFrame(float open, float dt) {
      spooky = HalloweenDecor.active();
      layoutFrame(open);
      boolean hud = tab == Tab.HUD;
      if (!hud) {
         ImDrawList bg = ImGui.getBackgroundDrawList();
         float r = SpectreWidgets.s(18F);
         drawShadow(workspaceX, workspaceY, workspaceW, workspaceH, r, 8);
         // The logo sits on a round tab rising out of the top edge.
         bg.addCircleFilled(workspaceX + workspaceW * 0.5F, workspaceY + SpectreWidgets.s(20F), SpectreWidgets.s(46F), tone(0.06F, 0.96F * open), 48);
         pane(bg, workspaceX, workspaceY, workspaceW, workspaceH, r, 0.06F, 0.96F * open);
         bg.addRect(workspaceX, workspaceY, workspaceX + workspaceW, workspaceY + workspaceH, spooky
            ? SpectreWidgets.c(SpectreWidgets.withAlpha(GuiPalette.accent(), 0.45F * open)) : SpectreWidgets.c(SpectreWidgets.withAlpha(0xFFFFFFFF, 0.08F * open)), r, 0, 1);
         if (!spooky) {
            // Accent-tinted top rim, plus two glints that drift along the top and bottom edges in opposite directions.
            HudChrome.gradientVertical(bg, workspaceX, workspaceY, workspaceW, SpectreWidgets.s(90F), r,
               SpectreWidgets.c(SpectreWidgets.withAlpha(accentAt(0.5F), 0.07F * open)), SpectreWidgets.c(SpectreWidgets.withAlpha(accentAt(0.5F), 0F)));
            double gt = System.nanoTime() / 1.0E9;
            float span = workspaceW - r * 2, len = workspaceW * 0.32F, p = frac((float) (gt * 0.09));
            edgeGlint(bg, workspaceX + r, workspaceX + workspaceW - r, workspaceY + 0.5F, workspaceX + r + (span + len) * p, len, accentAt(p), 0.85F * open);
            float q = 1F - frac((float) (gt * 0.09) + 0.5F);
            edgeGlint(bg, workspaceX + r, workspaceX + workspaceW - r, workspaceY + workspaceH - 0.5F, workspaceX + r + (span + len) * q, len, accentAt(q), 0.5F * open);
         }
         if (openSweep < 1F) {
            // Accent glow fading out around the frame, and a bright streak running along its top edge.
            float fade = 1F - openSweep;
            for (int i = 1; i <= 4; i++) {
               float g = SpectreWidgets.s(2F) * i;
               bg.addRect(workspaceX - g, workspaceY - g, workspaceX + workspaceW + g, workspaceY + workspaceH + g,
                  SpectreWidgets.c(SpectreWidgets.withAlpha(GuiPalette.accent(), 0.22F * fade / i)), r + g, 0, SpectreWidgets.s(2F));
            }
            float head = workspaceX + r + (workspaceW - r * 2) * easeOut(openSweep), tail = Math.max(workspaceX + r, head - workspaceW * 0.35F);
            int bright = SpectreWidgets.c(SpectreWidgets.withAlpha(GuiPalette.accentHover(), fade)), clear = SpectreWidgets.c(SpectreWidgets.withAlpha(GuiPalette.accent(), 0F));
            bg.addRectFilledMultiColor(tail, workspaceY - 0.5F, head, workspaceY + SpectreWidgets.s(1.5F), clear, bright, bright, clear);
         }
      }
      if (!hud) renderTopBar(open);
      renderFooter(open);
      if (spooky && !hud) {
         // Faint and thin, on top of the panels; bats fly above the frame so they never cover controls.
         ImDrawList fg = ImGui.getForegroundDrawList();
         float web = SpectreWidgets.s(54F);
         HalloweenDecor.cobweb(fg, workspaceX + workspaceW - SpectreWidgets.s(2F), workspaceY + SpectreWidgets.s(2F), web, -1, 1, open);
         HalloweenDecor.cobweb(fg, workspaceX + SpectreWidgets.s(2F), workspaceY + workspaceH - SpectreWidgets.s(2F), web * 0.8F, 1, -1, open);
         HalloweenDecor.bats(fg, workspaceX, workspaceY - SpectreWidgets.s(70F), workspaceW, SpectreWidgets.s(64F), open);
      }
   }

   /** Slim bar along the bottom: search on the left, the client pages as text links on the right. */
   private void renderFooter(float open) {
      float h = footerH(), x = contentX, y = workspaceY + workspaceH - h, w = contentW;
      if (tab == Tab.HUD) {
         // The HUD editor needs the screen clear, so the bar moves to the bottom-left corner.
         w = Math.min(w, SpectreWidgets.s(560F));
         x = SpectreWidgets.s(16F);
         y = ImGui.getIO().getDisplaySizeY() - h - SpectreWidgets.s(8F);
      }
      ImGui.setNextWindowPos(x, y, 1);
      ImGui.setNextWindowSize(w, h, 1);
      ImGui.pushStyleVar(0, Math.max(0.01F, open));
      ImGui.pushStyleVar(2, 0F, 0F);
      ImGui.begin("##corz_footer", 8511 | 128);
      ImDrawList dl = ImGui.getWindowDrawList();
      ImFont small = ImGuiManager.getInstance().smallFont();
      if (tab == Tab.HUD) pane(dl, x, y, w, h - SpectreWidgets.s(8F), SpectreWidgets.s(12F), 0.15F, 1);

      float searchW = Math.min(SpectreWidgets.s(240F), w * 0.38F), searchH = SpectreWidgets.s(28F);
      float sx = x + SpectreWidgets.s(tab == Tab.HUD ? 8F : 0F), sy = y + (h - SpectreWidgets.s(8F) - searchH) * 0.5F;
      boolean searchFocus = ImGui.isMouseHoveringRect(sx, sy, sx + searchW, sy + searchH) || !globalSearch.get().isEmpty();
      float sf = SpectreWidgets.animate(ImGui.getID("##search_focus"), searchFocus, 14F);
      dl.addRectFilled(sx, sy, sx + searchW, sy + searchH, tone(0.13F, 1F), searchH * 0.5F);
      if (sf > 0.01F) dl.addRect(sx, sy, sx + searchW, sy + searchH, SpectreWidgets.c(SpectreWidgets.withAlpha(accentAt(0F), 0.8F * sf)), searchH * 0.5F, 0, 1);
      SpectreWidgets.magnifier(dl, sx + SpectreWidgets.s(15F), sy + searchH * 0.5F, SpectreWidgets.s(5F), 0xFFB4ABB6);
      ImGui.setCursorScreenPos(sx + SpectreWidgets.s(26F), sy);
      ImGui.pushStyleColor(7, 0);
      ImGui.pushStyleColor(8, 0);
      ImGui.pushStyleColor(9, 0);
      ImGui.pushStyleVar(11, SpectreWidgets.s(4F), (searchH - ImGui.getTextLineHeight()) * 0.5F);
      ImGui.setNextItemWidth(searchW - SpectreWidgets.s(38F));
      if (focusSearch) { ImGui.setKeyboardFocusHere(); focusSearch = false; }
      if (ImGui.inputTextWithHint("##corz_search", "Search", globalSearch)) {
         if (tab != Tab.MODULES) switchTab(Tab.MODULES);
         scroll.remove("corz_grid");
      }
      ImGui.popStyleVar();
      ImGui.popStyleColor(3);

      // Page links, right-aligned; the open page is drawn in the accent colour.
      Tab[] pages = {Tab.FRIENDS, Tab.CONFIG, Tab.THEME, Tab.HUD};
      float right = x + w - SpectreWidgets.s(tab == Tab.HUD ? 12F : 2F), linkH = SpectreWidgets.s(28F), ly = sy;
      for (Tab page : pages) {
         String label = pageTitle(page);
         float lw = SpectreWidgets.textWidth(small, label) + SpectreWidgets.s(18F);
         right -= lw;
         ImGui.setCursorScreenPos(right, ly);
         if (ImGui.invisibleButton("##page_" + page.name(), lw, linkH)) {
            if (tab != page) GuiSounds.click();
            switchTab(tab == page ? Tab.MODULES : page);
         }
         boolean chosen = tab == page;
         float glow = SpectreWidgets.animate(ImGui.getItemID(), chosen || ImGui.isItemHovered(), 18F);
         if (chosen) dl.addRectFilled(right, ly, right + lw, ly + linkH, SpectreWidgets.c(SpectreWidgets.withAlpha(accentAt(0F), 0.16F)), linkH * 0.5F);
         SpectreWidgets.text(dl, small, right + SpectreWidgets.s(9F), ly + (linkH - SpectreWidgets.lineHeight(small)) * 0.5F,
            chosen ? accentAt(0F) : SpectreTheme.lerpColor(mutedText(1), brightText(1), glow), label);
         right -= SpectreWidgets.s(2F);
      }
      ImGui.end();
      ImGui.popStyleVar(2);
   }

   private static void railDivider(ImDrawList dl, float x, float y, float w) {
      dl.addLine(x + SpectreWidgets.s(14F), y, x + w - SpectreWidgets.s(14F), y, tone(0.30F, 1), 1);
   }

   private static String pageTitle(Tab page) {
      return switch (page) {
         case HUD -> "HUD Editor";
         case THEME -> "Theme";
         case CONFIG -> "Profiles";
         case FRIENDS -> "Friends";
         default -> "Modules";
      };
   }

   /** Short tab label; the two long category names are trimmed so every tab fits on one row. */
   private static String tabLabel(ModuleCategory category) {
      if (category == ModuleCategory.CLIENT) return "Client";
      if (category == ModuleCategory.BASE_BUILDER) return "Builder";
      return category.displayName();
   }

   /** Header: the logo in the middle with the categories as text tabs on either side of it. */
   private void renderTopBar(float open) {
      float h = headerH(), x = workspaceX, y = workspaceY, w = workspaceW;
      ImGui.setNextWindowPos(x, y - SpectreWidgets.s(30F), 1);
      ImGui.setNextWindowSize(w, h + SpectreWidgets.s(30F), 1);
      ImGui.pushStyleVar(0, Math.max(0.01F, open));
      ImGui.pushStyleVar(2, 0F, 0F);
      ImGui.begin("##corz_topbar", 8511 | 128);
      ImDrawList dl = ImGui.getWindowDrawList();
      float cx = x + w * 0.5F, logoY = y + SpectreWidgets.s(18F), lr = SpectreWidgets.s(15F);
      if (spooky) {
         HalloweenDecor.pumpkin(dl, cx, logoY, lr, 1);
      } else {
         dl.pathClear();
         dl.pathArcTo(cx, logoY, lr, (float) (Math.PI * 0.28), (float) (Math.PI * 1.72), 32);
         dl.pathStroke(SpectreWidgets.c(brightText(1)), 0, SpectreWidgets.s(7F));
      }
      if (ImGui.isMouseHoveringRect(cx - lr * 1.4F, logoY - lr * 1.4F, cx + lr * 1.4F, logoY + lr * 1.4F)) showTooltip("Spectre " + clientVersion());

      boolean searching = !normalizeSearch(globalSearch.get()).isEmpty();
      List<ModuleCategory> categories = navCategories();
      int leftCount = (categories.size() + 1) / 2;
      ImFont font = ImGuiManager.getInstance().categoryFont();
      float edge = SpectreWidgets.s(22F), hole = SpectreWidgets.s(58F), tabH = SpectreWidgets.s(34F), tabY = y + (h - tabH) * 0.5F + SpectreWidgets.s(2F);
      for (int i = 0; i < categories.size(); i++) {
         ModuleCategory category = categories.get(i);
         boolean leftSide = i < leftCount;
         int slots = leftSide ? leftCount : categories.size() - leftCount, slot = leftSide ? i : i - leftCount;
         float from = leftSide ? x + edge : cx + hole, to = leftSide ? cx - hole : x + w - edge;
         float centre = from + (to - from) * (slot + 0.5F) / slots;
         String label = tabLabel(category);
         float tw = SpectreWidgets.textWidth(font, label), bw = tw + SpectreWidgets.s(20F);
         ImGui.setCursorScreenPos(centre - bw * 0.5F, tabY);
         boolean chosen = tab == Tab.MODULES && !searching && category == workspaceCategory;
         if (ImGui.invisibleButton("##tab_" + category.name(), bw, tabH)) {
            if (!chosen) GuiSounds.click();
            switchTab(Tab.MODULES);
            workspaceCategory = category;
            selectedModule = null;
            detailOpen = false;
            globalSearch.set("");
            scroll.remove("corz_grid");
         }
         boolean hovered = ImGui.isItemHovered();
         if (hovered) GuiSounds.hover(ImGui.getItemID());
         float glow = SpectreWidgets.animate(ImGui.getItemID(), chosen || hovered, 18F);
         float on = SpectreWidgets.animate(ImGui.getID("##tab_on_" + category.name()), chosen, 16F);
         SpectreWidgets.text(dl, font, centre - tw * 0.5F, tabY + (tabH - SpectreWidgets.lineHeight(font)) * 0.5F,
            SpectreTheme.lerpColor(mutedText(1), brightText(1), glow), label);
         if (on > 0.01F) {
            float uw = tw * 0.5F * on, uy = tabY + tabH - SpectreWidgets.s(3F);
            dl.addRectFilled(centre - uw, uy, centre + uw, uy + SpectreWidgets.s(2.5F), SpectreWidgets.c(SpectreWidgets.withAlpha(accentAt(0F), on)), SpectreWidgets.s(2F));
         }
      }
      ImGui.end();
      ImGui.popStyleVar(2);
   }

   /** Modules page: list column, character preview stage, and the selected module's settings. */
   private void renderModuleBoard(float dt, float alpha) {
      if (BuilderPanel.takeOpenRequest()) workspaceCategory = ModuleCategory.BASE_BUILDER;
      boolean searching = !normalizeSearch(globalSearch.get()).isEmpty();
      if (workspaceCategory == null) workspaceCategory = navCategories().getFirst();
      if (!searching && workspaceCategory == ModuleCategory.BASE_BUILDER) {
         ImGui.setNextWindowPos(contentX, contentY, 1);
         ImGui.setNextWindowSize(contentW, contentH, 1);
         ImGui.pushStyleVar(0, Math.max(0.01F, alpha));
         ImGui.pushStyleVar(2, 0F, 0F);
         ImGui.begin("##corz_body", 8511 | 128);
         pane(ImGui.getWindowDrawList(), contentX, contentY, contentW, contentH, SpectreWidgets.s(12F), 0.15F, 1);
         BuilderPanel.renderContents();
         ImGui.end();
         ImGui.popStyleVar(2);
         return;
      }

      List<ClientModule> modules;
      if (searching) {
         modules = searchModules(globalSearch.get());
      } else {
         // Alphabetical, so a module is always in the same place whatever order it was registered in.
         modules = new ArrayList<>(modulesFor(workspaceCategory));
         modules.sort(Comparator.comparing(ClientModule::name, String.CASE_INSENSITIVE_ORDER));
      }
      if (selectedModule != null && !modules.contains(selectedModule)) {
         selectedModule = null;
         detailOpen = false;
      }
      // Card grid fills the page; a module's settings slide in on the right while it is open.
      float gap = SpectreWidgets.s(10F);
      float detail = SpectreWidgets.animate(DETAIL_KEY, detailOpen && selectedModule != null, 14F);
      if (detail > 0.01F && selectedModule != null) detailShown = selectedModule;
      float panelFull = Math.max(SpectreWidgets.s(300F), contentW * 0.46F);
      float panelW = panelFull * easeOut(detail);
      float gridW = contentW - (detail > 0.01F ? panelW + gap * detail : 0);
      renderModuleGrid(modules, contentX, contentY, gridW, contentH, alpha, dt);
      if (detail > 0.01F && detailShown != null) {
         if (detailShown != panelModule) {
            panelModule = detailShown;
            panelChangedAt = System.nanoTime();
         }
         float swap = 0.35F + 0.65F * easeOut(elapsed(panelChangedAt, 240F));
         renderModulePanel(detailShown, contentX + contentW - panelW, panelW, alpha * detail * swap, dt);
      } else {
         detailShown = null;
      }
   }

   private static final int DETAIL_KEY = "corz_detail".hashCode();
   private String gridKey;
   private long gridChangedAt;
   /** The module whose settings were last shown; a change fades the panel content in again. */
   private ClientModule panelModule;
   private long panelChangedAt;
   /** Whether the settings panel is open for {@link #selectedModule}. */
   private boolean detailOpen;
   /** The module whose panel is on screen (kept while it slides closed). */
   private ClientModule detailShown;

   /**
    * Cards in three columns, each as tall as its text needs: the name with its switch, then the full
    * description. Click the switch to toggle; click anywhere else on a card for its settings and keybind.
    */
   private void renderModuleGrid(List<ClientModule> modules, float x, float y, float w, float h, float alpha, float dt) {
      ImGui.setNextWindowPos(x, y, 1);
      ImGui.setNextWindowSize(w, h, 1);
      // Switching category or search fades the new set of cards in once, together.
      String key = workspaceCategory.name() + "|" + normalizeSearch(globalSearch.get());
      if (!key.equals(gridKey)) {
         gridKey = key;
         gridChangedAt = System.nanoTime();
      }
      float enter = 0.25F + 0.75F * easeOut(elapsed(gridChangedAt, 160F));
      ImGui.pushStyleVar(0, Math.max(0.01F, alpha * enter));
      ImGui.pushStyleVar(2, 0F, 0F);
      ImGui.begin("##corz_grid_window", 8511 | 128);
      if (ImGui.beginChild("##corz_grid", w, h, false, 24)) {
         if (modules.isEmpty()) {
            ImGui.setCursorPos(SpectreWidgets.s(14F), SpectreWidgets.s(10F));
            ImGui.textDisabled("No modules found");
         }
         float gap = SpectreWidgets.s(10F), pad = SpectreWidgets.s(16F), r = SpectreWidgets.s(13F);
         int cols = w >= SpectreWidgets.s(620F) ? 3 : w >= SpectreWidgets.s(380F) ? 2 : 1;
         float cardW = (w - gap * (cols - 1)) / cols, headH = SpectreWidgets.s(46F);
         float swW = SpectreWidgets.s(40F), swH = SpectreWidgets.s(21F), wrapW = cardW - pad * 2;
         float[] columnY = new float[cols];
         ImFont nameFont = ImGuiManager.getInstance().categoryFont();
         ImFont small = ImGuiManager.getInstance().smallFont();
         int descColor = SpectreWidgets.c(mutedText(1));
         for (ClientModule module : modules) {
            String description = module.description();
            boolean described = description != null && !description.isBlank();
            float descH = 0;
            if (described) {
               boolean pushed = pushFont(small);
               descH = ImGui.calcTextSize(description, false, wrapW).y;
               if (pushed) ImGui.popFont();
            }
            float cardH = described ? headH + descH + SpectreWidgets.s(14F) : headH + SpectreWidgets.s(4F);
            // Each card goes into whichever column is shortest so far.
            int col = 0;
            for (int c = 1; c < cols; c++) if (columnY[c] < columnY[col] - 0.5F) col = c;
            float lx = col * (cardW + gap), ly = columnY[col];
            columnY[col] += cardH + gap;

            ImGui.pushID(module.id());
            ImGui.setCursorPos(lx, ly);
            boolean clicked = ImGui.invisibleButton("##card", cardW, cardH);
            boolean rightClicked = ImGui.isItemClicked(1);
            boolean hovered = ImGui.isItemHovered();
            float cx = ImGui.getItemRectMinX(), cy = ImGui.getItemRectMinY();
            ImDrawList dl = ImGui.getWindowDrawList();
            float hover = SpectreWidgets.animate(ImGui.getItemID(), hovered, 16F);
            float onT = SpectreWidgets.animate(ImGui.getID("##on"), module.isEnabled(), 12F);
            boolean chosen = detailOpen && module == selectedModule;
            float swX = cx + cardW - swW - pad, swY = cy + (headH - swH) * 0.5F + SpectreWidgets.s(3F);
            boolean overSwitch = ImGui.isMouseHoveringRect(swX - SpectreWidgets.s(8F), cy, cx + cardW, cy + headH);

            if (clicked) {
               if (overSwitch) {
                  if (module.isAction()) module.runAction(); else module.toggle();
                  GuiSounds.toggle(module.isAction() || module.isEnabled());
               } else {
                  boolean close = detailOpen && selectedModule == module;
                  selectedModule = module;
                  detailOpen = !close;
                  GuiSounds.click();
               }
            } else if (rightClicked) {
               selectedModule = module;
               detailOpen = true;
               GuiSounds.click();
            }
            if (hovered) GuiSounds.hover(ImGui.getItemID());

            dl.addRectFilled(cx, cy, cx + cardW, cy + cardH, tone(0.125F + 0.03F * hover, 1F), r);
            if (chosen) dl.addRect(cx, cy, cx + cardW, cy + cardH, SpectreWidgets.c(SpectreWidgets.withAlpha(accentAt(0F), 0.85F)), r, 0, SpectreWidgets.s(1.5F));
            String name = SpectreWidgets.ellipsize(nameFont, module.name(), swX - cx - pad - SpectreWidgets.s(10F));
            SpectreWidgets.text(dl, nameFont, cx + pad, cy + (headH - SpectreWidgets.lineHeight(nameFont)) * 0.5F + SpectreWidgets.s(3F), brightText(1), name);
            if (module.isAction()) {
               float pr = swH * 0.55F, pcx = swX + swW - pr, pcy = swY + swH * 0.5F;
               dl.addCircleFilled(pcx, pcy, pr + SpectreWidgets.s(1F), SpectreWidgets.c(overSwitch && hovered ? accentAt(0F) : GuiPalette.surfaceHover()), 20);
               dl.addTriangleFilled(pcx - pr * 0.35F, pcy - pr * 0.5F, pcx - pr * 0.35F, pcy + pr * 0.5F, pcx + pr * 0.55F, pcy, SpectreWidgets.c(-1));
            } else {
               SpectreWidgets.drawSwitch(dl, swX, swY, swW, swH, onT);
            }
            if (described) {
               ImGui.setCursorPos(lx + pad, ly + headH);
               boolean pushed = pushFont(small);
               ImGui.pushStyleColor(0, descColor);
               ImGui.pushTextWrapPos(lx + pad + wrapW);
               ImGui.textUnformatted(description);
               ImGui.popTextWrapPos();
               ImGui.popStyleColor();
               if (pushed) ImGui.popFont();
            }
            if (hovered && overSwitch) showTooltip(module.isAction() ? "Run" : module.isEnabled() ? "Turn off" : "Turn on");
            ImGui.popID();
         }
         float bottom = 0;
         for (float column : columnY) bottom = Math.max(bottom, column);
         ImGui.setCursorPos(0, bottom);
         ImGui.dummy(1, SpectreWidgets.s(4F));
         applySmoothScroll("corz_grid", dt);
      }
      ImGui.endChild();
      ImGui.end();
      ImGui.popStyleVar(2);
   }

   private void renderModuleList(List<ClientModule> modules, String title, float x, float w, float alpha, float dt) {
      float y = contentY, h = contentH;
      ImGui.setNextWindowPos(x, y, 1);
      ImGui.setNextWindowSize(w, h, 1);
      ImGui.pushStyleVar(0, Math.max(0.01F, alpha));
      ImGui.pushStyleVar(2, 0F, 0F);
      ImGui.begin("##corz_list_window", 8511 | 128);
      ImDrawList dl = ImGui.getWindowDrawList();
      pane(dl, x, y, w, h, SpectreWidgets.s(12F), 0.15F, 1);
      ImFont small = ImGuiManager.getInstance().smallFont();
      SpectreWidgets.text(dl, small, x + SpectreWidgets.s(14F), y + SpectreWidgets.s(12F), mutedText(1), title.toUpperCase(Locale.ROOT));
      long on = modules.stream().filter(ClientModule::isEnabled).count();
      String count = on + " / " + modules.size();
      SpectreWidgets.text(dl, small, x + w - SpectreWidgets.s(14F) - SpectreWidgets.textWidth(small, count), y + SpectreWidgets.s(12F), on > 0 ? GuiPalette.accent() : mutedText(1), count);
      float top = SpectreWidgets.s(34F);
      ImGui.setCursorPos(0, top);
      if (ImGui.beginChild("##corz_list", w, h - top - SpectreWidgets.s(6F), false, 24)) {
         if (modules.isEmpty()) {
            ImGui.setCursorPos(SpectreWidgets.s(14F), SpectreWidgets.s(6F));
            ImGui.textDisabled("No modules found");
         }
         float rowH = SpectreWidgets.s(30F);
         for (ClientModule module : modules) {
            ImGui.pushID(module.id());
            float localY = ImGui.getCursorPosY();
            ImGui.setCursorPos(SpectreWidgets.s(6F), localY);
            boolean clicked = ImGui.invisibleButton("##row", w - SpectreWidgets.s(12F), rowH);
            boolean hovered = ImGui.isItemHovered();
            float rx = ImGui.getItemRectMinX(), ry = ImGui.getItemRectMinY(), rw = w - SpectreWidgets.s(12F);
            // The switch at the row's end toggles (or runs) the module; anywhere else opens its settings.
            float swW = SpectreWidgets.s(28F), swH = SpectreWidgets.s(15F);
            float swX = rx + rw - swW - SpectreWidgets.s(8F), swY = ry + (rowH - swH) * 0.5F;
            boolean overSwitch = ImGui.isMouseHoveringRect(swX - SpectreWidgets.s(4F), ry, rx + rw, ry + rowH);
            if (clicked) {
               if (overSwitch) {
                  if (module.isAction()) module.runAction(); else module.toggle();
                  GuiSounds.toggle(module.isAction() || module.isEnabled());
               } else {
                  if (selectedModule != module) GuiSounds.click();
                  selectedModule = module;
               }
            }
            if (hovered) GuiSounds.hover(ImGui.getItemID());
            boolean chosen = module == selectedModule;
            float glow = SpectreWidgets.animate(ImGui.getItemID(), chosen || hovered, 18F);
            ImDrawList rowDl = ImGui.getWindowDrawList();
            if (chosen) {
               // Selected row: an accent pill fading across (accent → second accent in gradient themes).
               HudChrome.gradient(rowDl, rx, ry, rw, rowH, SpectreWidgets.s(8F), SpectreWidgets.c(SpectreWidgets.withAlpha(accentAt(0F), 0.24F)),
                  SpectreWidgets.c(SpectreWidgets.withAlpha(accentAt(1F), 0.06F)), 1F);
               rowDl.addRect(rx, ry, rx + rw, ry + rowH, SpectreWidgets.c(SpectreWidgets.withAlpha(accentAt(0F), 0.25F)), SpectreWidgets.s(8F), 0, 1F);
               rowDl.addRectFilled(rx, ry + SpectreWidgets.s(7F), rx + SpectreWidgets.s(3F), ry + rowH - SpectreWidgets.s(7F), SpectreWidgets.c(accentAt(0F)), SpectreWidgets.s(2F));
            } else if (glow > 0.01F) {
               rowDl.addRectFilled(rx, ry, rx + rw, ry + rowH, SpectreWidgets.c(SpectreWidgets.withAlpha(0xFFFFFFFF, 0.05F * glow)), SpectreWidgets.s(8F));
            }
            float dotX = rx + SpectreWidgets.s(16F), dotY = ry + rowH * 0.5F;
            float onT = SpectreWidgets.animate(ImGui.getID("##on"), module.isEnabled(), 12F);
            rowDl.addCircle(dotX, dotY, SpectreWidgets.s(3.5F), SpectreWidgets.c(mutedText(0.7F)), 12, SpectreWidgets.s(1.2F));
            if (onT > 0.01F) {
               rowDl.addCircleFilled(dotX, dotY, SpectreWidgets.s(6F), SpectreWidgets.c(SpectreWidgets.withAlpha(GuiPalette.accent(), 0.25F * onT)), 16);
               rowDl.addCircleFilled(dotX, dotY, SpectreWidgets.s(3.5F), SpectreWidgets.c(SpectreWidgets.withAlpha(GuiPalette.accent(), onT)), 12);
            }
            ImFont font = ImGui.getFont();
            String name = SpectreWidgets.ellipsize(font, module.name(), rw - SpectreWidgets.s(34F) - swW - SpectreWidgets.s(10F));
            SpectreWidgets.text(rowDl, font, rx + SpectreWidgets.s(28F), ry + (rowH - ImGui.getTextLineHeight()) * 0.5F,
               chosen ? brightText(1) : module.isEnabled() ? brightText(0.9F) : mutedText(1), name);
            if (module.isAction()) {
               // One-shot tools get a small play chip instead of a switch.
               float pr = swH * 0.5F, pcx = swX + swW - pr, pcy = swY + pr;
               rowDl.addCircleFilled(pcx, pcy, pr + SpectreWidgets.s(1F), SpectreWidgets.c(overSwitch && hovered ? GuiPalette.accent() : GuiPalette.surfaceHover()), 16);
               rowDl.addTriangleFilled(pcx - pr * 0.35F, pcy - pr * 0.5F, pcx - pr * 0.35F, pcy + pr * 0.5F, pcx + pr * 0.55F, pcy, SpectreWidgets.c(-1));
            } else {
               SpectreWidgets.drawSwitch(rowDl, swX, swY, swW, swH, onT);
            }
            if (hovered && module.description() != null && !module.description().isBlank()) showTooltip(overSwitch ? (module.isAction() ? "Run" : module.isEnabled() ? "Turn off" : "Turn on") : module.description());
            ImGui.setCursorPos(0, localY + rowH + SpectreWidgets.s(2F));
            ImGui.popID();
         }
         ImGui.dummy(1, SpectreWidgets.s(4F));
         applySmoothScroll("corz_list", dt);
      }
      ImGui.endChild();
      ImGui.end();
      ImGui.popStyleVar(2);
   }

   /** Big character preview: the Spectre ghost holding the selected module's prop on a glowing pedestal. */
   private void renderPreviewStage(List<ClientModule> modules, float x, float w, float alpha) {
      float y = contentY, h = contentH;
      ImDrawList dl = ImGui.getBackgroundDrawList();
      ImGui.setNextWindowPos(x, y, 1);
      ImGui.setNextWindowSize(w, h, 1);
      ImGui.pushStyleVar(0, Math.max(0.01F, alpha));
      ImGui.pushStyleVar(2, 0F, 0F);
      ImGui.begin("##corz_stage", 8511 | 128);
      dl = ImGui.getWindowDrawList();
      dl.addRectFilled(x, y, x + w, y + h, tone(0.12F, 1), SpectreWidgets.s(12F));
      ClientModule module = selectedModule;
      ImFont small = ImGuiManager.getInstance().smallFont();
      if (module != null) {
         boolean on = module.isEnabled();
         float cx = x + w * 0.5F, base = y + h * 0.78F;
         // Soft spotlight from below.
         for (int i = 6; i >= 1; i--) {
            dl.addCircleFilled(cx, base - h * 0.22F, w * 0.08F * i, SpectreWidgets.c(SpectreWidgets.withAlpha(GuiPalette.accent(), (on ? 0.022F : 0.010F))), 48);
         }
         ellipseRing(dl, cx, base, w * 0.36F, w * 0.075F, SpectreWidgets.c(SpectreWidgets.withAlpha(GuiPalette.accent(), on ? 0.55F : 0.25F)), SpectreWidgets.s(1.5F));
         ellipseRing(dl, cx, base, w * 0.28F, w * 0.055F, SpectreWidgets.c(SpectreWidgets.withAlpha(GuiPalette.accent(), on ? 0.35F : 0.15F)), SpectreWidgets.s(1F));
         float size = Math.min(w * 0.95F, h * 0.62F);
         WorkspaceMascot.drawModule(dl, cx, base - size * 0.5F, size, on ? 1F : 0.75F, module.id());
         String code = moduleCode(module, modules);
         SpectreWidgets.text(dl, small, x + SpectreWidgets.s(16F), y + SpectreWidgets.s(14F), mutedText(1), spaced(code));
         String status = module.isAction() ? "READY" : on ? "ACTIVE" : "OFFLINE";
         SpectreWidgets.text(dl, small, x + w - SpectreWidgets.s(16F) - SpectreWidgets.textWidth(small, spaced(status)), y + SpectreWidgets.s(14F),
            on || module.isAction() ? GuiPalette.accent() : mutedText(1), spaced(status));
      }
      ImGui.end();
      ImGui.popStyleVar(2);
   }

   private static void ellipseRing(ImDrawList dl, float cx, float cy, float rx, float ry, int color, float thickness) {
      int segments = 56;
      float px = cx + rx, py = cy;
      for (int i = 1; i <= segments; i++) {
         double a = Math.PI * 2 * i / segments;
         float nx = cx + (float) Math.cos(a) * rx, ny = cy + (float) Math.sin(a) * ry;
         dl.addLine(px, py, nx, ny, color, thickness);
         px = nx;
         py = ny;
      }
   }

   /** A short code like "BR-03" from the category initials and the module's place in the list. */
   private static String moduleCode(ClientModule module, List<ClientModule> modules) {
      String[] words = module.category().displayName().trim().split("\\s+");
      StringBuilder code = new StringBuilder();
      if (words.length > 1) for (String word : words) code.append(Character.toUpperCase(word.charAt(0)));
      else code.append(words[0], 0, Math.min(3, words[0].length()));
      int index = Math.max(0, modules.indexOf(module)) + 1;
      return code.toString().toUpperCase(Locale.ROOT) + "-" + (index < 10 ? "0" : "") + index;
   }

   private static String spaced(String text) {
      StringBuilder out = new StringBuilder();
      for (int i = 0; i < text.length(); i++) {
         if (i > 0) out.append(' ');
         out.append(text.charAt(i));
      }
      return out.toString();
   }

   /** Settings panel: category label, large module title with toggle, description, then the settings. */
   private void renderModulePanel(ClientModule module, float x, float w, float alpha, float dt) {
      float y = contentY, h = contentH;
      ImGui.setNextWindowPos(x, y, 1);
      ImGui.setNextWindowSize(w, h, 1);
      ImGui.pushStyleVar(0, Math.max(0.01F, alpha));
      ImGui.pushStyleVar(2, 0F, 0F);
      ImGui.begin("##corz_panel", 8511 | 128);
      ImDrawList dl = ImGui.getWindowDrawList();
      float r = SpectreWidgets.s(12F);
      float on = module == null ? 0 : SpectreWidgets.animate(ImGui.getID("##panel_on_" + module.id()), module.isEnabled(), 12F);
      pane(dl, x, y, w, h, r, 0.15F, 1);
      if (module == null) {
         ImGui.setCursorPos(SpectreWidgets.s(18F), SpectreWidgets.s(18F));
         ImGui.textDisabled("Pick a module on the left.");
         ImGui.end();
         ImGui.popStyleVar(2);
         return;
      }
      ImGui.pushID(module.id());
      ImFont small = ImGuiManager.getInstance().smallFont();
      ImFont titleFont = ImGuiManager.getInstance().titleFont();
      float pad = SpectreWidgets.s(18F);
      // Header card: drawn on a lower channel once its height (title + description) is known.
      dl.channelsSplit(2);
      dl.channelsSetCurrent(1);
      float icon = SpectreWidgets.s(40F), iconX = x + pad, iconY = y + SpectreWidgets.s(20F);
      float textX = iconX + icon + SpectreWidgets.s(14F);
      SpectreWidgets.text(dl, small, textX, iconY - SpectreWidgets.s(1F), SpectreWidgets.withAlpha(accentAt(0F), 0.85F), spaced(module.category().displayName().toUpperCase(Locale.ROOT)));
      float titleY = iconY + SpectreWidgets.lineHeight(small) + SpectreWidgets.s(1F);
      int titleColor = brightText(1);
      String title = SpectreWidgets.ellipsize(titleFont, module.name(), x + w - textX - pad - SpectreWidgets.s(130F));
      SpectreWidgets.text(dl, titleFont, textX, titleY, titleColor, title);
      float lineH = SpectreWidgets.lineHeight(titleFont);
      String description = module.description();
      // Status pill after the title.
      if (!module.isAction()) {
         String status = module.isEnabled() ? "ON" : "OFF";
         float pw = SpectreWidgets.textWidth(small, status) + SpectreWidgets.s(14F), ph = SpectreWidgets.lineHeight(small) + SpectreWidgets.s(4F);
         float px = textX + SpectreWidgets.textWidth(titleFont, title) + SpectreWidgets.s(10F), py = titleY + (lineH - ph) * 0.5F;
         int pill = SpectreTheme.lerpColor(SpectreWidgets.withAlpha(0xFFFFFFFF, 0.08F), SpectreWidgets.withAlpha(accentAt(0F), 0.30F), on);
         dl.addRectFilled(px, py, px + pw, py + ph, SpectreWidgets.c(pill), ph * 0.5F);
         SpectreWidgets.text(dl, small, px + SpectreWidgets.s(7F), py + SpectreWidgets.s(2F), SpectreTheme.lerpColor(mutedText(1), brightText(1), on), status);
      }
      // Close button in the corner; the panel slides away and the grid widens again.
      float closeS = SpectreWidgets.s(24F), closeX = x + w - SpectreWidgets.s(14F) - closeS, closeY = y + SpectreWidgets.s(14F);
      ImGui.setCursorScreenPos(closeX, closeY);
      if (ImGui.invisibleButton("##panel_close", closeS, closeS)) {
         detailOpen = false;
         GuiSounds.click();
      }
      float closeHover = SpectreWidgets.animate(ImGui.getItemID(), ImGui.isItemHovered(), 16F);
      dl.addRectFilled(closeX, closeY, closeX + closeS, closeY + closeS, SpectreWidgets.c(SpectreWidgets.withAlpha(0xFFFFFFFF, 0.05F + 0.10F * closeHover)), SpectreWidgets.s(7F));
      float cc = closeS * 0.5F, cr = SpectreWidgets.s(4F);
      int xc = SpectreWidgets.c(SpectreTheme.lerpColor(mutedText(1), brightText(1), closeHover));
      dl.addLine(closeX + cc - cr, closeY + cc - cr, closeX + cc + cr, closeY + cc + cr, xc, SpectreWidgets.s(1.6F));
      dl.addLine(closeX + cc + cr, closeY + cc - cr, closeX + cc - cr, closeY + cc + cr, xc, SpectreWidgets.s(1.6F));
      // Enable switch, or a Run button for one-shot actions, left of the close button.
      float swW = SpectreWidgets.s(42F), swH = SpectreWidgets.s(22F), right = closeS + SpectreWidgets.s(10F);
      if (module.isAction()) {
         float runW = SpectreWidgets.s(64F), runH = SpectreWidgets.s(28F);
         ImGui.setCursorPos(w - pad - right - runW, titleY - y + (lineH - runH) * 0.5F);
         if (SpectreWidgets.chipButton("##panel_run", "Run", runW, runH, true)) module.runAction();
      } else {
         ImGui.setCursorPos(w - pad - right - swW, titleY - y + (lineH - swH) * 0.5F);
         float sx = ImGui.getCursorScreenPosX(), sy = ImGui.getCursorScreenPosY();
         if (ImGui.invisibleButton("##panel_toggle", swW, swH)) {
            module.toggle();
            GuiSounds.toggle(module.isEnabled());
         }
         SpectreWidgets.drawSwitch(dl, sx, sy, swW, swH, SpectreWidgets.animate(ImGui.getItemID(), module.isEnabled(), 14F));
      }
      float textY = Math.max(titleY + lineH, iconY + icon) + SpectreWidgets.s(8F);
      if (description != null && !description.isBlank()) {
         textY = mutedParagraph(description, pad, textY - y, w - pad) + y + SpectreWidgets.s(2F);
      }
      float divY = textY + SpectreWidgets.s(12F);
      dl.channelsSetCurrent(0);
      {
         float hx = x + SpectreWidgets.s(8F), hy = y + SpectreWidgets.s(8F), hw = w - SpectreWidgets.s(16F), hh = divY - hy - SpectreWidgets.s(2F), hr = SpectreWidgets.s(10F);
         dl.addRectFilled(hx, hy, hx + hw, hy + hh, SpectreWidgets.c(SpectreWidgets.withAlpha(0xFFFFFFFF, 0.03F)), hr);
         // Enabled modules light the header with the accent (gradient) wash.
         HudChrome.gradient(dl, hx, hy, hw, hh, hr, SpectreWidgets.c(SpectreWidgets.withAlpha(accentAt(0F), 0.04F + 0.18F * on)),
            SpectreWidgets.c(SpectreWidgets.withAlpha(accentAt(1F), 0.0F)), 1F);
         dl.addRect(hx, hy, hx + hw, hy + hh, SpectreWidgets.c(SpectreWidgets.withAlpha(0xFFFFFFFF, 0.06F + 0.06F * on)), hr, 0, 1F);
         // Icon tile with the category glyph.
         HudChrome.gradient(dl, iconX, iconY, icon, icon, SpectreWidgets.s(10F), SpectreWidgets.c(SpectreWidgets.withAlpha(accentAt(0F), 0.35F + 0.4F * on)),
            SpectreWidgets.c(SpectreWidgets.withAlpha(accentAt(1F), 0.20F + 0.4F * on)), 1F);
         dl.addRect(iconX, iconY, iconX + icon, iconY + icon, SpectreWidgets.c(SpectreWidgets.withAlpha(0xFFFFFFFF, 0.18F)), SpectreWidgets.s(10F), 0, 1F);
         drawCategoryIcon(dl, module.category(), iconX + icon * 0.5F, iconY + icon * 0.5F, SpectreWidgets.s(9F), 0xFFFFFFFF);
      }
      dl.channelsMerge();

      ImGui.setCursorPos(0, divY - y + SpectreWidgets.s(2F));
      if (ImGui.beginChild("##panel_body", w, Math.max(1, y + h - divY - SpectreWidgets.s(8F)), false, 24)) {
         float contentWidth = w - pad * 2;
         dividerPending = false;
         compactRows = true;
         try {
            ImGui.setCursorPos(0, SpectreWidgets.s(4F));
            renderKeybindRow(module, pad, contentWidth);
            boolean any = false;
            // Runs of on/off settings collapse into a grid of toggle buttons instead of one row each.
            List<BooleanSetting> toggles = new ArrayList<>();
            for (Setting setting : module.settings()) {
               if (setting.isVisible() && (!module.isAction() || setting != module.showInHud())) {
                  any = true;
                  if (setting instanceof BooleanSetting bs) {
                     toggles.add(bs);
                     continue;
                  }
                  renderToggleGrid(toggles, pad, contentWidth);
                  ImGui.pushID(setting.id());
                  renderSetting(module, setting, pad, contentWidth);
                  ImGui.popID();
               }
            }
            renderToggleGrid(toggles, pad, contentWidth);
            if (!any) mutedParagraph("This module has no settings.", pad, ImGui.getCursorPosY() + SpectreWidgets.s(6F), w - pad);
            if (isHudModule(module)) renderHudActions(pad, contentWidth);
            sectionLabel("Config", pad);
            renderModuleConfigActions(module, pad, contentWidth);
            if (module.hasModifiedSettings()) {
               ImGui.setCursorPos(pad, ImGui.getCursorPosY() + SpectreWidgets.s(6F));
               String key = "reset:" + module.id();
               if (SpectreWidgets.chipButton("##panel_reset", isConfirming(key) ? "Confirm reset" : "Reset all settings", contentWidth, SpectreWidgets.s(30F), isConfirming(key)) && confirmClick(key)) {
                  module.resetSettings();
               }
            }
         } finally {
            compactRows = false;
         }
         ImGui.dummy(1, SpectreWidgets.s(16F));
         applySmoothScroll("panel_" + module.id(), dt);
      }
      ImGui.endChild();
      ImGui.popID();
      ImGui.end();
      ImGui.popStyleVar(2);
   }

   public void render() {
      float dt = animationDelta(Math.max(ImGui.getIO().getDeltaTime(), 0.001F));
      ThemeStore theme = ThemeStore.get();
      this.panelGuard = HudChrome.needsTextGuard(theme.panels);
      this.tabBarGuard = HudChrome.needsTextGuard(theme.tabBar);
      this.cardGuard = HudChrome.needsTextGuard(theme.cards);
      this.cardTextLift = theme.cards.isImage() && !theme.cards.isBanner();
      applyTheme();
      boolean moduleFontPushed = pushFont(ImGuiManager.getInstance().moduleFont());

      try {
         float sw = ImGui.getIO().getDisplaySizeX();
         float sh = ImGui.getIO().getDisplaySizeY();
         float intro = elapsed(this.guiOpenedAt, 460.0F);
         float open = easeOut(Math.min(1F, intro * 1.6F));
         this.openPop = backOut(intro);
         this.openSweep = intro;
         float view = easeOut(elapsed(this.viewChangedAt, 260.0F));
         float alpha = Math.max(0.01F, open * view);
         ImGui.getBackgroundDrawList().addRectFilled(0.0F, 0.0F, sw, sh, SpectreWidgets.withAlpha(-16777216, theme.backgroundDim.get() * open));
         if (this.tab != Tab.HUD) drawBackdrop(ImGui.getBackgroundDrawList(), sw, sh, open);
         Path picked = ThemeImages.get().takePendingImport();
         if (picked != null) {
            this.importHeaderImage(picked);
         }

         this.handleShortcuts();
         this.renderFrame(open, dt);
         switch (this.tab) {
            case MODULES:
               this.renderModuleBoard(dt, alpha);
               break;
            case HUD:
               this.renderHudStudio(dt, alpha, view);
               break;
            case FRIENDS:
               this.renderFriendsCard(dt, alpha, view);
               break;
            case THEME:
               this.renderThemeStudio(dt, alpha, view);
               break;
            case CONFIG:
               this.renderConfigCard(dt, alpha, view);
         }


         this.renderColorPickerWindow();
         this.blockLists.renderWindows();
         this.renderItemColorPaletteWindow();
         if (this.tab != Tab.MODULES) this.closeCardOnOutsideClick();
      } finally {
         if (moduleFontPushed) {
            ImGui.popFont();
         }

         removeTheme();
      }
   }

   private void handleShortcuts() {
      // Ctrl+K (556) or Ctrl+F (551) focuses the top-bar search from any page.
      if (this.settingsModule == null && ImGui.getIO().getKeyCtrl() && (ImGui.isKeyPressed(556) || ImGui.isKeyPressed(551))) {
         this.focusSearch = true;
      }
   }

   private void switchTab(ClickGuiRenderer.Tab next) {
      if (next != this.tab) {
         this.closeSettings();
         this.tab = next;
         this.viewChangedAt = System.nanoTime();
         this.pendingConfirm = null;
         this.profileList.disarm();
         if (next == ClickGuiRenderer.Tab.CONFIG) {
            ConfigProfiles.getInstance().refresh();
         }
      }
   }

   private void renderModulePanels(float dt, float alpha) {
      ClickGuiRenderer.CategoryPanel[] panels = this.visiblePanels();
      boolean placed = this.panelSpots.containsKey("##panel_search");

      for (ClickGuiRenderer.CategoryPanel panel : panels) {
         placed &= this.panelSpots.containsKey(panel.id());
      }

      if (!placed) {
         this.placeNewPanels(panels);
      }

      for (ClickGuiRenderer.CategoryPanel panel : panels) {
         this.renderCategoryPanel(panel, dt, alpha);
      }

      this.renderSearchPanel(dt, alpha);
   }

   private ClickGuiRenderer.CategoryPanel[] visiblePanels() {
      int revision = ModuleRegistry.revision();
      if (revision != this.panelCacheRevision) {
         List<ClickGuiRenderer.CategoryPanel> panels = new ArrayList<>();

         for (ModuleCategory category : ModuleCategory.values()) {
            if (!this.modulesFor(category).isEmpty()) {
               panels.add(new ClickGuiRenderer.CategoryPanel(category, "##panel_" + category.name(), "##rows_" + category.name()));
            }
         }

         this.panelCache = panels.toArray(ClickGuiRenderer.CategoryPanel[]::new);
         this.panelCacheRevision = revision;
      }

      return this.panelCache;
   }

   private void renderCategoryPanel(ClickGuiRenderer.CategoryPanel panel, float dt, float alpha) {
      List<ClientModule> modules = this.modulesFor(panel.category());
      float listH = Math.min(rowListHeight(modules.size()), maxListHeight());
      float open = this.unfolded(panel, dt);
      if (open < 1.0F) {
         ImGui.setNextWindowSize(SpectreWidgets.s(212.0F), SpectreWidgets.s(42.0F) + listH * open, 1);
      }

      if (this.beginPanel(panel.id(), alpha)) {
         String title = panel.category().displayName();
         this.drawPanelChrome(title, panel.category());
         this.recordPanelRect();
         this.renderFoldControl(panel.category(), title, open);
         if (open > 0.0F) {
            this.renderRowList(panel.rowsId(), modules, listH, false, dt);
         }
      }

      endPanel();
   }

   private float unfolded(ClickGuiRenderer.CategoryPanel panel, float dt) {
      float target = PanelStateStore.getInstance().isCollapsed(panel.category()) ? 0.0F : 1.0F;
      float[] open = this.panelFold.get(panel.id());
      if (open == null) {
         this.panelFold.put(panel.id(), open = new float[]{target});
      }

      open[0] = smooth(open[0], target, dt, 14.0F);
      if (Math.abs(open[0] - target) < 0.002F) {
         open[0] = target;
      }

      return open[0];
   }

   private void renderFoldControl(ModuleCategory category, String title, float open) {
      float wx = ImGui.getWindowPosX();
      float wy = ImGui.getWindowPosY();
      float ww = ImGui.getWindowWidth();
      float headerH = SpectreWidgets.s(42.0F);
      float hit = SpectreWidgets.s(22.0F);
      ImFont font = ImGuiManager.getInstance().categoryFont();
      float cx = Math.min(wx + SpectreWidgets.s(14.0F) + SpectreWidgets.textWidth(font, title) + SpectreWidgets.s(12.0F), wx + ww - SpectreWidgets.s(40.0F));
      float cy = wy + headerH * 0.5F;
      boolean overHeader = !this.inspectMode && ImGui.isWindowHovered() && ImGui.isMouseHoveringRect(wx, wy, wx + ww, wy + headerH);
      ImGui.setCursorScreenPos(cx - hit * 0.5F, cy - hit * 0.5F);
      boolean clicked = ImGui.invisibleButton("##fold", hit, hit) && !this.inspectMode;
      float hover = SpectreWidgets.animate(ImGui.getItemID(), overHeader, 18.0F);
      ImGui.setCursorPos(0.0F, headerH);
      if (clicked || overHeader && ImGui.isMouseClicked(1)) {
         PanelStateStore store = PanelStateStore.getInstance();
         store.setCollapsed(category, !store.isCollapsed(category));
      }

      SpectreWidgets.chevronRotated(
         ImGui.getWindowDrawList(), cx, cy, SpectreWidgets.s(4.0F), SpectreTheme.lerpColor(-4542808, -461582, hover), open * (float) (Math.PI / 2)
      );
   }

   private void renderSearchPanel(float dt, float alpha) {
      if (this.beginPanel("##panel_search", alpha)) {
         this.drawPanelChrome("Search", null);
         this.recordPanelRect();
         float inset = SpectreWidgets.s(6.0F) + SpectreWidgets.s(2.0F);
         ImGui.setCursorPos(inset, SpectreWidgets.s(42.0F) - SpectreWidgets.s(4.0F));
         ImGui.pushStyleVar(11, SpectreWidgets.s(10.0F), SpectreWidgets.s(7.0F));
         ImGui.setNextItemWidth(SpectreWidgets.s(212.0F) - inset * 2.0F);
         if (this.focusSearch) {
            ImGui.setKeyboardFocusHere();
            this.focusSearch = false;
         }

         ImGui.inputTextWithHint("##globalsearch", "Search modules...", this.globalSearch);
         ImGui.popStyleVar();
         ImGui.dummy(0.0F, SpectreWidgets.s(8.0F));
         String query = normalizeSearch(this.globalSearch.get());
         List<ClientModule> results = query.isEmpty() ? List.of() : this.searchModules(query);
         float target = query.isEmpty() ? 0.0F : (results.isEmpty() ? SpectreWidgets.s(34.0F) : Math.min(rowListHeight(results.size()), maxListHeight()));
         this.searchBodyH = smooth(this.searchBodyH, target, dt, 14.0F);
         if (Math.abs(this.searchBodyH - target) < 0.5F) {
            this.searchBodyH = target;
         }

         if (this.searchBodyH >= 1.0F) {
            if (results.isEmpty()) {
               ImGui.beginChild("##search_empty", SpectreWidgets.s(212.0F), this.searchBodyH, false, 24);
               String empty = "No results";
               ImFont font = ImGui.getFont();
               SpectreWidgets.text(
                  ImGui.getWindowDrawList(),
                  font,
                  ImGui.getWindowPosX() + (SpectreWidgets.s(212.0F) - SpectreWidgets.textWidth(font, empty)) * 0.5F,
                  ImGui.getWindowPosY() + SpectreWidgets.s(4.0F),
                  -4542808,
                  empty,
                  this.panelGuard
               );
               ImGui.endChild();
            } else {
               this.renderRowList("##search_rows", results, this.searchBodyH, true, dt);
            }
         }
      }

      endPanel();
   }

   private boolean beginPanel(String id, float alpha) {
      float[] spot = this.panelSpots.get(id);
      ImGui.setNextWindowPos(spot[0], spot[1], 4);
      ImGui.setNextWindowSizeConstraints(SpectreWidgets.s(212.0F), 0.0F, SpectreWidgets.s(212.0F), Float.MAX_VALUE);
      ImGui.pushStyleVar(0, alpha);
      ImGui.pushStyleVar(2, 0.0F, 0.0F);
      ImGui.pushStyleVar(14, 0.0F, 0.0F);
      ImGui.pushStyleVar(3, panelRounding());
      boolean open = ImGui.begin(id, 123);
      spot[0] = ImGui.getWindowPosX();
      spot[1] = ImGui.getWindowPosY();
      return open;
   }

   private static void endPanel() {
      ImGui.end();
      ImGui.popStyleVar(4);
   }

   private void drawPanelChrome(String title, ModuleCategory category) {
      float wx = ImGui.getWindowPosX();
      float wy = ImGui.getWindowPosY();
      float ww = ImGui.getWindowWidth();
      float wh = ImGui.getWindowHeight();
      drawShadow(wx, wy, ww, wh, panelRounding(), 4);
      ImDrawList dl = ImGui.getWindowDrawList();
      SurfaceBackground.draw(dl, ThemeStore.get().panels, wx, wy, ww, wh, panelRounding());
      ImFont font = ImGuiManager.getInstance().categoryFont();
      float headerH = SpectreWidgets.s(42.0F);
      SpectreWidgets.text(dl, font, wx + SpectreWidgets.s(14.0F), wy + (headerH - SpectreWidgets.lineHeight(font)) * 0.5F, -461582, title, this.panelGuard);
      float cx = wx + ww - SpectreWidgets.s(20.0F);
      float cy = wy + headerH * 0.5F;
      float r = SpectreWidgets.s(7.0F);
      if (category == null) {
         SpectreWidgets.magnifier(dl, cx, cy, r, -4542808);
      } else {
         drawCategoryIcon(dl, category, cx, cy, r, -4542808);
      }

      ImGui.dummy(ww, headerH);
   }

   private static void drawCategoryIcon(ImDrawList dl, ModuleCategory category, float cx, float cy, float r, int color) {
      switch (category.icon()) {
         case CROSSHAIR:
            SpectreWidgets.crosshair(dl, cx, cy, r, color);
            break;
         case CHEVRON:
            SpectreWidgets.chevron(dl, cx, cy, r, color, true);
            break;
         case GRID:
            SpectreWidgets.grid(dl, cx, cy, r, color);
            break;
         case RING:
            SpectreWidgets.ring(dl, cx, cy, r, color);
            break;
         case EYE:
            SpectreWidgets.eye(dl, cx, cy, r, color);
            break;
         case MONITOR:
            SpectreWidgets.monitor(dl, cx, cy, r, color);
            break;
         case HOUSE:
            SpectreWidgets.house(dl, cx, cy, r, color);
            break;
         case PAPYRUS:
            SpectreWidgets.papyrus(dl, cx, cy, r, color);
            break;
         case ANKH:
            SpectreWidgets.ankh(dl, cx, cy, r, color);
            break;
         case GHOST:
            WorkspaceMascot.drawBrand(dl,cx,cy,r*2,(color>>>24)/255f);
      }
   }

   public static void drawShadow(float x, float y, float w, float h, float rounding, int layers) {
      ImDrawList bg = ImGui.getBackgroundDrawList();

      for (int i = 1; i <= layers; i++) {
         float e = SpectreWidgets.s(2.5F) * i;
         bg.addRectFilled(x - e, y - e + SpectreWidgets.s(4.0F), x + w + e, y + h + e + SpectreWidgets.s(4.0F), SpectreWidgets.c(335544320), rounding + e);
      }
   }

   private static float rowListHeight(int rows) {
      return rows * SpectreWidgets.s(32.0F) - SpectreWidgets.s(2.0F) + SpectreWidgets.s(6.0F);
   }

   private static float maxListHeight() {
      return Math.max(SpectreWidgets.s(120.0F), ImGui.getIO().getDisplaySizeY() * 0.62F - SpectreWidgets.s(42.0F));
   }

   private void renderRowList(String id, List<ClientModule> modules, float height, boolean showCategory, float dt) {
      if (ImGui.beginChild(id, SpectreWidgets.s(212.0F), Math.max(1.0F, height), false, 24)) {
         Motion.Rows motion = this.rowMotion.computeIfAbsent(id, key -> new Motion.Rows());
         motion.bind(modules);

         for (int i = 0; i < modules.size(); i++) {
            this.renderModuleRow(modules.get(i), showCategory, motion, i, dt);
         }

         ImGui.dummy(1.0F, SpectreWidgets.s(6.0F) - SpectreWidgets.s(2.0F));
         this.applySmoothScroll(id, dt);
      }

      ImGui.endChild();
   }

   private void renderModuleRow(ClientModule module, boolean showCategory, Motion.Rows motion, int row, float dt) {
      ImGui.pushID(module.id());
      float inset = SpectreWidgets.s(6.0F);
      float rowH = SpectreWidgets.s(30.0F);
      float localY = ImGui.getCursorPosY();
      ImGui.setCursorPosX(inset);
      float x0 = ImGui.getCursorScreenPosX();
      float y0 = ImGui.getCursorScreenPosY();
      float w = ImGui.getWindowWidth() - inset * 2.0F;
      if (ImGui.invisibleButton("##row", w, rowH) && !this.inspectMode) {
         if (module.isAction()) {
            module.runAction();
         } else {
            module.toggle();
         }
      }

      boolean hovered = ImGui.isItemHovered();
      if (!this.inspectMode && ImGui.isItemClicked(1)) {
         this.openSettings(module);
      }

      if (!this.inspectMode && ImGui.isItemHovered(65536)) {
         String description = module.description();
         showTooltip(description != null && !description.isBlank() ? description + "\nRight-click for settings" : "Right-click for settings");
      }

      float hover = motion.hover(row, hovered, dt, 18.0F);
      float on = motion.on(row, module.isEnabled(), dt, 14.0F);
      ImDrawList dl = ImGui.getWindowDrawList();
      float r = SpectreWidgets.s(6.0F);
      if (hover > 0.01F) {
         dl.addRectFilled(x0, y0, x0 + w, y0 + rowH, SpectreWidgets.c(SpectreWidgets.withAlpha(318767103, hover)), r);
      }

      if (on > 0.01F) {
         dl.addRectFilled(
            x0,
            y0,
            x0 + w,
            y0 + rowH,
            SpectreWidgets.c(SpectreWidgets.withAlpha(SpectreTheme.lerpColor(GuiPalette.accent(), GuiPalette.accentHover(), hover * 0.3F), on)),
            r
         );
      }

      ImFont font = ImGui.getFont();
      ImFont small = ImGuiManager.getInstance().smallFont();
      String trailing = showCategory ? module.category().displayName() : (module.keybind() == -1 ? null : this.keyLabel(module.keybind()));
      float tw = trailing == null ? 0.0F : SpectreWidgets.textWidth(small, trailing);
      int textColor = SpectreTheme.lerpColor(SpectreTheme.lerpColor(-2042416, -461582, hover), -1, on);
      String name = showCategory ? SpectreWidgets.ellipsize(font, module.name(), w - SpectreWidgets.s(28.0F) - tw) : module.name();
      SpectreWidgets.text(dl, font, x0 + SpectreWidgets.s(10.0F), y0 + (rowH - SpectreWidgets.lineHeight(font)) * 0.5F, textColor, name, this.panelGuard);
      if (trailing != null) {
         int trailColor = SpectreTheme.lerpColor(-4542808, -419430401, on);
         SpectreWidgets.text(
            dl, small, x0 + w - SpectreWidgets.s(10.0F) - tw, y0 + (rowH - SpectreWidgets.lineHeight(small)) * 0.5F, trailColor, trailing, this.panelGuard
         );
      } else if (module.isAction()) {
         SpectreWidgets.chevronRight(
            dl, x0 + w - SpectreWidgets.s(14.0F), y0 + rowH * 0.5F, SpectreWidgets.s(4.0F), SpectreTheme.lerpColor(-4542808, -461582, hover)
         );
      }

      ImGui.setCursorPos(0.0F, localY + rowH + SpectreWidgets.s(2.0F));
      ImGui.popID();
   }

   public static void showTooltip(String text) {
      ImGui.pushStyleVar(2, SpectreWidgets.s(10.0F), SpectreWidgets.s(8.0F));
      ImGui.beginTooltip();
      ImGui.pushTextWrapPos(SpectreWidgets.s(280.0F));
      ImGui.textUnformatted(text);
      ImGui.popTextWrapPos();
      ImGui.endTooltip();
      ImGui.popStyleVar();
   }

   private void applySmoothScroll(String key, float dt) {
      smoothScroll(this.scroll.computeIfAbsent(key, k -> new float[2]), dt);
   }

   public static void smoothScroll(float[] state, float dt) {
      float max = ImGui.getScrollMaxY();
      float wx = ImGui.getWindowPosX();
      float wy = ImGui.getWindowPosY();
      float ww = ImGui.getWindowWidth();
      float wh = ImGui.getWindowHeight();
      float barH = Math.max(SpectreWidgets.s(24.0F), wh * wh / (wh + max));
      float travel = Math.max(1.0F, wh - barH);
      boolean held = false;
      boolean hot = false;
      if (max > 0.5F) {
         int id = ImGui.getID("##scrollbar");
         imgui.internal.ImGui.keepAliveID(id);
         imgui.internal.ImGui.buttonBehavior(wx + ww - SpectreWidgets.s(8.0F), wy, wx + ww, wy + wh, id, SCROLLBAR_HOVERED, SCROLLBAR_HELD);
         held = SCROLLBAR_HELD.get();
         hot = held || SCROLLBAR_HOVERED.get();
         if (held && ImGui.isMouseClicked(0)) {
            float barY = wy + travel * (state[0] / max);
            float mouseY = ImGui.getMousePosY();
            state[1] = mouseY >= barY && mouseY <= barY + barH ? state[0] : (mouseY - wy - barH * 0.5F) / travel * max;
            scrollDragFrom = state[1];
         } else if (held) {
            state[1] = scrollDragFrom + ImGui.getMouseDragDeltaY(0, 0.0F) * max / travel;
         }
      }

      if (!held && ImGui.isWindowHovered()) {
         float wheel = ImGui.getIO().getMouseWheel();
         if (wheel != 0.0F) {
            state[1] -= wheel * SpectreWidgets.s(56.0F);
         }
      }

      state[1] = Math.max(0.0F, Math.min(max, state[1]));
      state[0] = held ? state[1] : smooth(state[0], state[1], dt, 16.0F);
      if (Math.abs(state[0] - state[1]) < 0.3F) {
         state[0] = state[1];
      }

      ImGui.setScrollY(state[0]);
      if (max > 0.5F) {
         float barY = wy + travel * (state[0] / max);
         ImGui.getWindowDrawList()
            .addRectFilled(
               wx + ww - (hot ? SpectreWidgets.s(6.0F) : SpectreWidgets.s(4.0F)),
               barY,
               wx + ww - SpectreWidgets.s(1.5F),
               barY + barH,
               SpectreWidgets.c(hot ? 1728053247 : 872415231),
               SpectreWidgets.s(2.0F)
            );
      }
   }

   private void placeNewPanels(ClickGuiRenderer.CategoryPanel[] panels) {
      List<String> ids = new ArrayList<>();
      List<Float> heights = new ArrayList<>();

      for (ClickGuiRenderer.CategoryPanel panel : panels) {
         ids.add(panel.id());
         heights.add(SpectreWidgets.s(42.0F) + Math.min(rowListHeight(this.modulesFor(panel.category()).size()), maxListHeight()));
      }

      ids.add("##panel_search");
      heights.add(SpectreWidgets.s(42.0F) + ImGui.getFontSize() + SpectreWidgets.s(18.0F) + maxListHeight());
      Map<String, float[]> saved = savedPanelSpots();
      List<float[]> taken = new ArrayList<>();

      for (int i = 0; i < ids.size(); i++) {
         float[] spot = this.panelSpots.get(ids.get(i));
         if (spot == null && saved.containsKey(ids.get(i))) {
            spot = saved.get(ids.get(i));
            this.panelSpots.put(ids.get(i), spot);
         }

         if (spot != null) {
            taken.add(new float[]{spot[0], spot[1], SpectreWidgets.s(212.0F), heights.get(i)});
         }
      }

      boolean firstRun = taken.isEmpty();

      for (int i = 0; i < ids.size(); i++) {
         if (!this.panelSpots.containsKey(ids.get(i))) {
            float h = heights.get(i);
            float[] spotx = firstRun ? panelPosFor(i, ids.size()) : null;
            if (spotx == null || coveredArea(spotx[0], spotx[1], h, taken) > 0.0F) {
               spotx = freeSpot(h, taken);
            }

            this.panelSpots.put(ids.get(i), spotx);
            taken.add(new float[]{spotx[0], spotx[1], SpectreWidgets.s(212.0F), h});
         }
      }
   }

   private static float[] panelPosFor(int index, int panelCount) {
      float sw = ImGui.getIO().getDisplaySizeX();
      float sh = ImGui.getIO().getDisplaySizeY();
      float w = SpectreWidgets.s(212.0F);
      float gap = SpectreWidgets.s(14.0F);
      int columns = Math.max(1, (int)((sw - SpectreWidgets.s(40.0F) + gap) / (w + gap)));
      int inRow = Math.min(columns, panelCount);
      float rowW = inRow * (w + gap) - gap;
      float startX = Math.max(SpectreWidgets.s(20.0F), (sw - rowW) * 0.5F);
      return new float[]{startX + index % columns * (w + gap), SpectreWidgets.s(28.0F) + index / columns * sh * 0.45F};
   }

   private static float[] freeSpot(float h, List<float[]> taken) {
      float sw = ImGui.getIO().getDisplaySizeX();
      float sh = ImGui.getIO().getDisplaySizeY();
      float w = SpectreWidgets.s(212.0F);
      float gap = SpectreWidgets.s(14.0F);
      float margin = SpectreWidgets.s(20.0F);
      float bottom = sh - SpectreWidgets.s(70.0F) - gap;
      float highest = Float.MAX_VALUE;

      for (float[] rect : taken) {
         highest = Math.min(highest, rect[1]);
      }

      float[] rightMost = null;

      for (float[] rect : taken) {
         boolean topRow = rect[1] < highest + SpectreWidgets.s(42.0F);
         if (topRow && (rightMost == null || rect[0] + rect[2] > rightMost[0] + rightMost[2])) {
            rightMost = rect;
         }
      }

      float[] beside = rightMost == null ? new float[]{margin, SpectreWidgets.s(28.0F)} : new float[]{rightMost[0] + rightMost[2] + gap, rightMost[1]};
      List<float[]> scan = new ArrayList<>();
      scan.add(new float[]{margin, SpectreWidgets.s(28.0F)});

      for (float[] rectx : taken) {
         scan.add(new float[]{rectx[0] + rectx[2] + gap, rectx[1]});
         scan.add(new float[]{rectx[0], rectx[1] + rectx[3] + gap});
      }

      scan.sort(Comparator.<float[]>comparingDouble(spotx -> spotx[1]).thenComparingDouble(spotx -> spotx[0]));
      scan.addFirst(beside);

      for (float[] spot : scan) {
         boolean onScreen = spot[0] >= 0.0F && spot[1] >= 0.0F && spot[0] + w <= sw - margin && spot[1] + h <= bottom;
         if (onScreen && coveredArea(spot[0], spot[1], h, taken) == 0.0F) {
            return spot;
         }
      }

      float[] best = null;
      float bestArea = Float.MAX_VALUE;

      for (float[] spotx : scan) {
         float x = Math.max(0.0F, Math.min(spotx[0], sw - w - margin));
         float y = Math.max(0.0F, Math.min(spotx[1], sh - h - margin));
         float area = coveredArea(x, y, h, taken);
         if (area < bestArea) {
            best = new float[]{x, y};
            bestArea = area;
         }
      }

      return best;
   }

   private static float coveredArea(float x, float y, float h, List<float[]> taken) {
      float w = SpectreWidgets.s(212.0F);
      float m = SpectreWidgets.s(14.0F) * 0.5F;
      float area = 0.0F;

      for (float[] r : taken) {
         float overlapW = Math.min(x + w, r[0] + r[2]) + m - Math.max(x, r[0]);
         float overlapH = Math.min(y + h, r[1] + r[3]) + m - Math.max(y, r[1]);
         if (overlapW > 0.0F && overlapH > 0.0F) {
            area += overlapW * overlapH;
         }
      }

      return area;
   }

   private static Map<String, float[]> savedPanelSpots() {
      Map<String, float[]> spots = new HashMap<>();
      String ini = ImGui.getIO().getIniFilename();
      if (ini != null && Files.isRegularFile(Path.of(ini))) {
         String section = "[Window][";

         try {
            String window = null;

            for (String line : Files.readAllLines(Path.of(ini))) {
               if (line.startsWith("[")) {
                  boolean panel = line.startsWith(section + "##panel_") && line.endsWith("]");
                  window = panel ? line.substring(section.length(), line.length() - 1) : null;
               } else if (window != null && line.startsWith("Pos=")) {
                  String[] xy = line.substring(4).split(",");
                  spots.put(window, new float[]{Float.parseFloat(xy[0]), Float.parseFloat(xy[1])});
               }
            }
         } catch (RuntimeException | IOException var7) {
            spots.clear();
         }

         return spots;
      } else {
         return spots;
      }
   }

   private List<ClientModule> modulesFor(ModuleCategory category) {
      return ModuleRegistry.inCategory(category);
   }

   /** Sidebar order. Categories an addon registers later follow the built-in ones. */
   private static final List<ModuleCategory> NAV_ORDER = List.of(ModuleCategory.COMBAT, ModuleCategory.MOVEMENT, ModuleCategory.RENDER, ModuleCategory.VISUALS,
      ModuleCategory.BASE_RAID, ModuleCategory.DONUT, ModuleCategory.MISC, ModuleCategory.CLIENT, ModuleCategory.BASE_BUILDER);

   /** Every category with modules (the Builder always shows), each as its own sidebar row. */
   private List<ModuleCategory> navCategories() {
      List<ModuleCategory> out = new ArrayList<>();
      for (ModuleCategory category : NAV_ORDER) {
         if (category == ModuleCategory.BASE_BUILDER || !modulesFor(category).isEmpty()) out.add(category);
      }
      for (ModuleCategory category : ModuleCategory.values()) {
         if (!NAV_ORDER.contains(category) && !modulesFor(category).isEmpty()) out.add(category);
      }
      return out;
   }

   private List<ClientModule> searchModules(String query) {
      query = normalizeSearch(query);
      int revision = ModuleRegistry.revision();
      if (revision != this.cachedRegistryRevision) {
         this.cachedRegistryRevision = revision;
         this.cachedGlobalSearch = null;
         this.cachedGlobalResults = List.of();
      }

      if (query.equals(this.cachedGlobalSearch)) {
         return this.cachedGlobalResults;
      } else {
         List<ClientModule> matches = new ArrayList<>();

         for (ClientModule module : ModuleRegistry.all()) {
            if (module.name().toLowerCase(Locale.ROOT).contains(query)) {
               matches.add(module);
            }
         }

         this.cachedGlobalSearch = query;
         this.cachedGlobalResults = List.copyOf(matches);
         return this.cachedGlobalResults;
      }
   }

   private static String normalizeSearch(String value) {
      return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
   }

   private void renderTabBar(float dt, float open) {
      ClickGuiRenderer.Tab[] tabs = ClickGuiRenderer.Tab.values();
      ImFont font = ImGui.getFont();
      float h = SpectreWidgets.s(44.0F);
      float inner = SpectreWidgets.s(4.0F);
      float padX = SpectreWidgets.s(20.0F);
      float[] widths = new float[tabs.length];
      float total = inner * 2.0F;

      for (int i = 0; i < tabs.length; i++) {
         widths[i] = SpectreWidgets.textWidth(font, tabs[i].label) + padX * 2.0F;
         total += widths[i];
      }

      float sw = ImGui.getIO().getDisplaySizeX();
      float sh = ImGui.getIO().getDisplaySizeY();
      float x = (sw - total) * 0.5F;
      float y = sh - h - SpectreWidgets.s(26.0F) + SpectreWidgets.s(16.0F) * (1.0F - open);
      ImGui.setNextWindowPos(x, y, 1);
      ImGui.setNextWindowSize(total, h, 1);
      ImGui.pushStyleVar(0, Math.max(0.01F, open));
      ImGui.pushStyleVar(2, 0.0F, 0.0F);
      ImGui.pushStyleVar(3, h * 0.5F);
      ImGui.pushStyleColor(2, GuiPalette.tabBarBg());
      if (ImGui.begin("##tabbar", 319)) {
         this.tabBarRect = new float[]{x, y, total, h};
         drawShadow(x, y, total, h, h * 0.5F, 4);
         ImDrawList dl = ImGui.getWindowDrawList();
         SurfaceBackground.draw(dl, ThemeStore.get().tabBar, x, y, total, h, h * 0.5F);
         dl.addRect(x, y, x + total, y + h, SpectreWidgets.c(318767103), h * 0.5F, 0, 1.0F);
         float targetX = inner;

         for (int i = 0; i < this.tab.ordinal(); i++) {
            targetX += widths[i];
         }

         float targetW = widths[this.tab.ordinal()];
         if (this.tabIndicatorX < 0.0F) {
            this.tabIndicatorX = targetX;
            this.tabIndicatorW = targetW;
         }

         this.tabIndicatorX = smooth(this.tabIndicatorX, targetX, dt, 18.0F);
         this.tabIndicatorW = smooth(this.tabIndicatorW, targetW, dt, 18.0F);
         float pillH = h - inner * 2.0F;
         dl.addRectFilled(
            x + this.tabIndicatorX,
            y + inner,
            x + this.tabIndicatorX + this.tabIndicatorW,
            y + inner + pillH,
            SpectreWidgets.c(GuiPalette.accent()),
            pillH * 0.5F
         );
         float tx = inner;

         for (int i = 0; i < tabs.length; i++) {
            ImGui.setCursorPos(tx, inner);
            if (ImGui.invisibleButton("##tab_" + tabs[i].name(), widths[i], pillH)) {
               this.switchTab(tabs[i]);
            }

            float hover = SpectreWidgets.animate(ImGui.getItemID(), ImGui.isItemHovered(), 18.0F);
            float centre = x + tx + widths[i] * 0.5F;
            float cover = Math.max(0.0F, 1.0F - Math.abs(centre - (x + this.tabIndicatorX + this.tabIndicatorW * 0.5F)) / Math.max(1.0F, widths[i] * 0.5F));
            int color = SpectreTheme.lerpColor(SpectreTheme.lerpColor(-4542808, -461582, hover), -1, cover);
            float tw = SpectreWidgets.textWidth(font, tabs[i].label);
            SpectreWidgets.text(
               dl, font, x + tx + (widths[i] - tw) * 0.5F, y + (h - SpectreWidgets.lineHeight(font)) * 0.5F, color, tabs[i].label, this.tabBarGuard
            );
            tx += widths[i];
         }
      }

      ImGui.end();
      ImGui.popStyleColor();
      ImGui.popStyleVar(3);
   }

   private float[] beginCard(String id, float alpha, float view) {
      return this.beginCard(id, alpha, view, ImGui.getIO().getDisplaySizeX() * 0.5F);
   }

   private float[] beginCard(String id, float alpha, float view, float centreX) {
      float sw = ImGui.getIO().getDisplaySizeX();
      float sh = ImGui.getIO().getDisplaySizeY();
      float w;
      float h;
      float x;
      float y;
      if (this.contentW > 0.0F) {
         // Inside the main frame: fill the content column beside the sidebar.
         w = Math.min(this.contentW, SpectreWidgets.s(620.0F));
         h = this.contentH;
         x = this.contentX + (this.contentW - w) * 0.5F;
         y = this.contentY + SpectreWidgets.s(16.0F) * (1.0F - view);
      } else {
         float reserved = SpectreWidgets.s(86.0F);
         w = Math.min(sw - SpectreWidgets.s(32.0F), SpectreWidgets.s(460.0F));
         h = Math.min(sh - reserved - SpectreWidgets.s(32.0F), SpectreWidgets.s(760.0F));
         x = Math.max(SpectreWidgets.s(16.0F), centreX - w * 0.5F);
         y = Math.max(SpectreWidgets.s(16.0F), (sh - reserved - h) * 0.5F + SpectreWidgets.s(8.0F)) + SpectreWidgets.s(24.0F) * (1.0F - view);
      }
      ImGui.setNextWindowPos(x, y, 1);
      ImGui.setNextWindowSize(w, h, 1);
      ImGui.pushStyleVar(0, alpha);
      ImGui.pushStyleVar(2, 0.0F, 0.0F);
      ImGui.pushStyleVar(14, 0.0F, 0.0F);
      ImGui.pushStyleVar(3, cardRounding());
      ImGui.pushStyleColor(2, GuiPalette.cardBg());
      if (this.cardTextLift) {
         ImGui.pushStyleColor(0, -1);
         ImGui.pushStyleColor(1, -2042416);
      }

      ImGui.begin(id, 8511);
      drawShadow(x, y, w, h, cardRounding(), 6);
      ImDrawList dl = ImGui.getWindowDrawList();
      Surface cards = ThemeStore.get().cards;
      SurfaceBackground.draw(dl, cards, x, y, w, h, cardRounding());
      dl.addRect(x, y, x + w, y + h, SpectreWidgets.c(318767103), cardRounding(), 0, 1.0F);
      float top = SurfaceBackground.hasImageBanner(cards)
         ? Math.max(SpectreWidgets.s(22.0F), SurfaceBackground.bannerHeight(cards) - SpectreWidgets.s(44.0F))
         : SpectreWidgets.s(22.0F);
      return new float[]{w, h, top, x, y};
   }

   private static float panelRounding() {
      return SpectreWidgets.s(GuiPalette.rounding());
   }

   public static float cardRounding() {
      return SpectreWidgets.s(Math.min(28.0F, GuiPalette.rounding() * 2.2F));
   }

   private void endCard() {
      ImGui.end();
      ImGui.popStyleColor(this.cardTextLift ? 3 : 1);
      ImGui.popStyleVar(4);
   }

   public static void drawCardTitle(String title, float x, float centreY) {
      drawCardTitle(title, x, centreY, false);
   }

   private static void drawCardTitle(String title, float x, float centreY, boolean guard) {
      ImFont font = ImGuiManager.getInstance().titleFont();
      SpectreWidgets.text(
         ImGui.getWindowDrawList(),
         font,
         ImGui.getWindowPosX() + x,
         ImGui.getWindowPosY() + centreY - SpectreWidgets.lineHeight(font) * 0.5F,
         -461582,
         title,
         guard
      );
   }

   public static float mutedParagraph(String text, float x, float y, float wrapX) {
      ImGui.setCursorPos(x, y);
      ImGui.pushTextWrapPos(wrapX);
      ImGui.pushStyleColor(0, ImGui.getStyleColorVec4(1));
      ImGui.textUnformatted(text);
      ImGui.popStyleColor();
      ImGui.popTextWrapPos();
      return ImGui.getCursorPosY();
   }

   public static void cardDivider(float pad, float w, float localY) {
      float wx = ImGui.getWindowPosX();
      float wy = ImGui.getWindowPosY();
      ImGui.getWindowDrawList().addLine(wx + pad, wy + localY, wx + w - pad, wy + localY, SpectreWidgets.c(268435455), 1.0F);
   }

   private void renderModuleCard(ClientModule module, float dt, float alpha, float view) {
      this.renderModuleCard(module, dt, alpha, view, false);
   }

   private void renderModuleCard(ClientModule module, float dt, float alpha, float view, boolean preview) {
      this.renderModuleCard(module, dt, alpha, view, preview, !preview);
   }

   private void renderModuleCard(ClientModule module, float dt, float alpha, float view, boolean preview, boolean showBack) {
      float sw = ImGui.getIO().getDisplaySizeX();
      float sh = ImGui.getIO().getDisplaySizeY();
      float[] size;
      if (preview) {
         size = this.beginCard("##preview_card", alpha, view, (sw - SpectreWidgets.s(408.0F)) * 0.5F);
         this.cardRect = new float[]{size[3], size[4], size[0], size[1]};
         ImGui.pushStyleVar(1, 1.0F);
         ImGui.beginDisabled();
      } else {
         ImGui.getBackgroundDrawList().addRectFilled(0.0F, 0.0F, sw, sh, SpectreWidgets.withAlpha(1426063360, view));
         size = this.beginCard("##module_card", alpha, view);
      }

      float w = size[0];
      float h = size[1];
      float pad = SpectreWidgets.s(24.0F);
      float top = size[2];
      float btn = SpectreWidgets.s(32.0F);
      ImDrawList dl = ImGui.getWindowDrawList();
      float wx = ImGui.getWindowPosX();
      float wy = ImGui.getWindowPosY();
      float titleX = pad;
      if (!preview && showBack) {
         ImGui.setCursorPos(pad - SpectreWidgets.s(4.0F), top);
         if (SpectreWidgets.iconButton("##back", btn)) {
            this.closeSettings();
         }

         SpectreWidgets.chevronLeft(
            dl, wx + pad - SpectreWidgets.s(4.0F) + btn * 0.5F - SpectreWidgets.s(1.0F), wy + top + btn * 0.5F, SpectreWidgets.s(5.0F), -461582
         );
         titleX = pad + btn + SpectreWidgets.s(8.0F);
      }

      drawCardTitle(module.name(), titleX, top + btn * 0.5F, this.cardGuard);
      if (module.isAction()) {
         float openW = SpectreWidgets.s(72.0F);
         float openH = SpectreWidgets.s(30.0F);
         ImGui.setCursorPos(w - pad - openW, top + (btn - openH) * 0.5F);
         if (SpectreWidgets.chipButton("##open", "Open", openW, openH, true)) {
            module.runAction();
         }
      } else {
         float swW = SpectreWidgets.s(46.0F);
         float swH = SpectreWidgets.s(26.0F);
         ImGui.setCursorPos(w - pad - swW, top + (btn - swH) * 0.5F);
         float swX = ImGui.getCursorScreenPosX();
         float swY = ImGui.getCursorScreenPosY();
         if (ImGui.invisibleButton("##enabled", swW, swH)) {
            module.toggle();
         }

         SpectreWidgets.drawSwitch(dl, swX, swY, swW, swH, SpectreWidgets.animate(ImGui.getItemID(), module.isEnabled(), 14.0F));
      }

      float y = top + btn + SpectreWidgets.s(12.0F);
      String description = module.description();
      if (description != null && !description.isBlank()) {
         y = mutedParagraph(description, pad, y, w - pad) + SpectreWidgets.s(4.0F);
      }

      y += SpectreWidgets.s(12.0F);
      cardDivider(pad, w, y);
      ImGui.setCursorPos(0.0F, y + 1.0F);
      if (ImGui.beginChild("##body", w, Math.max(1.0F, h - y - 1.0F - SpectreWidgets.s(10.0F)), false, 24)) {
         float contentW = w - pad * 2.0F;
         float labelY = ImGui.getCursorPosY() + SpectreWidgets.s(16.0F);
         this.sectionLabel("Settings", pad);
         if (!preview && module.hasModifiedSettings()) {
            this.renderResetAllChip(module, pad, contentW, labelY);
         }

         boolean any = false;

         for (Setting setting : module.settings()) {
            if (setting.isVisible() && (!module.isAction() || setting != module.showInHud())) {
               any = true;
               ImGui.pushID(setting.id());
               this.renderSetting(module, setting, pad, contentW);
               ImGui.popID();
            }
         }

         if (!any) {
            mutedParagraph("This module has no settings.", pad, ImGui.getCursorPosY() + SpectreWidgets.s(8.0F), w - pad);
         }

         this.sectionLabel("Keybind", pad);
         ImGui.pushID("##keybind_row");
         this.renderKeybindRow(module, pad, contentW);
         ImGui.popID();
         if (isHudModule(module)) {
            this.renderHudActions(pad, contentW);
         }

         this.sectionLabel("Config", pad);
         this.renderModuleConfigActions(module, pad, contentW);
         ImGui.dummy(1.0F, SpectreWidgets.s(18.0F));
         this.applySmoothScroll("card_" + module.id(), dt);
      }

      ImGui.endChild();
      if (preview) {
         ImGui.endDisabled();
         ImGui.popStyleVar();
      }

      this.endCard();
   }

   private void renderResetAllChip(ClientModule module, float pad, float contentW, float labelY) {
      float afterY = ImGui.getCursorPosY();
      String key = "reset:" + module.id();
      String label = this.isConfirming(key) ? "Confirm reset" : "Reset all";
      ImFont small = ImGuiManager.getInstance().smallFont();
      boolean pushed = pushFont(small);
      float chipW = SpectreWidgets.textWidth(small, label) + SpectreWidgets.s(18.0F);
      float chipH = SpectreWidgets.lineHeight(small) + SpectreWidgets.s(8.0F);
      ImGui.setCursorPos(pad + contentW - chipW, labelY + (ImGui.getTextLineHeight() - chipH) * 0.5F);
      if (SpectreWidgets.chipButton("##reset_all", label, chipW, chipH, this.isConfirming(key)) && this.confirmClick(key)) {
         module.resetSettings();
      }

      if (ImGui.isItemHovered(65536)) {
         showTooltip("Restore every setting to its default");
      }

      if (pushed) {
         ImGui.popFont();
      }

      ImGui.setCursorPosY(afterY);
   }

   private boolean confirmClick(String key) {
      if (this.isConfirming(key)) {
         this.pendingConfirm = null;
         return true;
      } else {
         this.pendingConfirm = key;
         this.pendingConfirmAt = System.nanoTime();
         return false;
      }
   }

   private boolean isConfirming(String key) {
      return key.equals(this.pendingConfirm) && System.nanoTime() - this.pendingConfirmAt < 3000000000L;
   }

   private void sectionLabel(String text, float pad) {
      // Small spaced caps with an accent tick and a hairline that fades out to the right.
      ImGui.setCursorPos(pad, ImGui.getCursorPosY() + SpectreWidgets.s(16.0F));
      ImFont small = ImGuiManager.getInstance().smallFont();
      ImDrawList dl = ImGui.getWindowDrawList();
      float sx = ImGui.getCursorScreenPosX(), sy = ImGui.getCursorScreenPosY(), lh = SpectreWidgets.lineHeight(small);
      dl.addRectFilled(sx, sy + lh * 0.2F, sx + SpectreWidgets.s(3.0F), sy + lh * 0.85F, SpectreWidgets.c(accentAt(0F)), SpectreWidgets.s(1.5F));
      String label = spaced(text.toUpperCase(Locale.ROOT));
      float tx = sx + SpectreWidgets.s(9.0F);
      SpectreWidgets.text(dl, small, tx, sy, mutedText(1), label);
      float lx = tx + SpectreWidgets.textWidth(small, label) + SpectreWidgets.s(10.0F), lineEnd = ImGui.getWindowPosX() + ImGui.getWindowWidth() - pad;
      if (lineEnd > lx) {
         int a = SpectreWidgets.c(SpectreWidgets.withAlpha(0xFFFFFFFF, 0.10F)), b = SpectreWidgets.c(SpectreWidgets.withAlpha(0xFFFFFFFF, 0F));
         dl.addRectFilledMultiColor(lx, sy + lh * 0.55F, lineEnd, sy + lh * 0.55F + 1.0F, a, b, b, a);
      }
      ImGui.dummy(1, lh);

      ImGui.setCursorPosY(ImGui.getCursorPosY() + SpectreWidgets.s(4.0F));
      this.dividerPending = false;
   }

   private float rowStart(float pad, float contentW) {
      float localY = ImGui.getCursorPosY();
      if (this.dividerPending && !this.compactRows) {
         float wx = ImGui.getWindowPosX();
         float sy = ImGui.getCursorScreenPosY();
         ImGui.getWindowDrawList().addLine(wx + pad, sy, wx + pad + contentW, sy, SpectreWidgets.c(268435455), 1.0F);
      }

      this.dividerPending = true;
      return localY;
   }

   public static void rowLabel(String label, float pad, float startY, float rowH) {
      ImGui.setCursorPos(pad, startY + (rowH - ImGui.getTextLineHeight()) * 0.5F);
      ImGui.textUnformatted(label);
   }

   private void openSettings(ClientModule module) {
      this.workspaceCategory = module.category();
      this.selectedModule = module;
      this.settingsModule = module;
      this.viewChangedAt = System.nanoTime();
      this.cardJustOpened = true;
   }

   private void closeSettings() {
      if (this.settingsModule != null) {
         this.viewChangedAt = System.nanoTime();
      }

      this.settingsModule = null;
      this.listeningModule = null;
      this.listeningKeySetting = null;
      this.activeColorModule = null;
      this.activeColorSetting = null;
      this.colorPickerOpen.set(false);
      this.blockLists.close();
      this.itemColorWindowOpen = false;
   }

   private void closeCardOnOutsideClick() {
      if (this.cardJustOpened) {
         this.cardJustOpened = false;
      } else if (this.settingsModule != null && this.listeningModule == null && this.listeningKeySetting == null) {
         if (ImGui.isMouseClicked(0) && !ImGui.isWindowHovered(4) && !ImGui.isAnyItemActive()) {
            this.closeSettings();
         }
      }
   }

   private void renderFriendsCard(float dt, float alpha, float view) {
      float[] size = this.beginCard("##friends_card", alpha, view);
      float w = size[0];
      float h = size[1];
      float pad = SpectreWidgets.s(24.0F);
      float top = size[2];
      float btn = SpectreWidgets.s(32.0F);
      FriendStore store = FriendStore.getInstance();
      List<String> friends = store.all();
      drawCardTitle("Friends", pad, top + btn * 0.5F, this.cardGuard);
      String count = friends.size() == 1 ? "1 player" : friends.size() + " players";
      ImFont small = ImGuiManager.getInstance().smallFont();
      SpectreWidgets.text(
         ImGui.getWindowDrawList(),
         small,
         ImGui.getWindowPosX() + w - pad - SpectreWidgets.textWidth(small, count),
         ImGui.getWindowPosY() + top + (btn - SpectreWidgets.lineHeight(small)) * 0.5F,
         -4542808,
         count,
         this.cardGuard
      );
      float y = mutedParagraph("Add players by their exact username.", pad, top + btn + SpectreWidgets.s(12.0F), w - pad) + SpectreWidgets.s(14.0F);
      float addW = SpectreWidgets.s(84.0F);
      float inputH = ImGui.getTextLineHeight() + SpectreWidgets.s(16.0F);
      ImGui.setCursorPos(pad, y);
      ImGui.pushStyleVar(11, SpectreWidgets.s(12.0F), SpectreWidgets.s(8.0F));
      ImGui.setNextItemWidth(w - pad * 2.0F - addW - SpectreWidgets.s(8.0F));
      if (this.focusFriendInput) {
         ImGui.setKeyboardFocusHere();
         this.focusFriendInput = false;
      }

      ImGui.inputTextWithHint("##friend_input", "Username", this.friendInput, 16);
      boolean submitted = SpectreWidgets.submittedWithEnter();
      ImGui.popStyleVar();
      ImGui.setCursorPos(w - pad - addW, y);
      if (SpectreWidgets.chipButton("##friend_add", "Add", addW, inputH, true)) {
         submitted = true;
      }

      if (submitted) {
         this.tryAddFriend(store);
      }

      y += inputH + SpectreWidgets.s(8.0F);
      if (this.friendMessage != null && elapsed(this.friendMessageAt, 2500.0F) < 1.0F) {
         ImGui.setCursorPos(pad, y);
         boolean pushed = pushFont(small);
         ImGui.pushStyleColor(0, -10856974);
         ImGui.textUnformatted(this.friendMessage);
         ImGui.popStyleColor();
         if (pushed) {
            ImGui.popFont();
         }
      }

      y += SpectreWidgets.lineHeight(small) + SpectreWidgets.s(10.0F);
      cardDivider(pad, w, y);
      ImGui.setCursorPos(0.0F, y + 1.0F);
      if (ImGui.beginChild("##friend_list", w, Math.max(1.0F, h - y - 1.0F - SpectreWidgets.s(10.0F)), false, 24)) {
         if (friends.isEmpty()) {
            String empty = "No friends yet";
            ImGui.setCursorPos((w - ImGui.calcTextSize(empty).x) * 0.5F, SpectreWidgets.s(40.0F));
            ImGui.textDisabled(empty);
         } else {
            ImGui.dummy(1.0F, SpectreWidgets.s(8.0F));

            for (String name : friends) {
               this.renderFriendRow(store, name, pad, w - pad * 2.0F);
            }
         }

         ImGui.dummy(1.0F, SpectreWidgets.s(12.0F));
         this.applySmoothScroll("friends", dt);
      }

      ImGui.endChild();
      this.endCard();
   }

   private void renderFriendRow(FriendStore store, String name, float pad, float contentW) {
      ImGui.pushID("friend_" + name);
      float rowH = SpectreWidgets.s(48.0F);
      float localY = ImGui.getCursorPosY();
      ImGui.setCursorPos(pad, localY);
      float x0 = ImGui.getCursorScreenPosX();
      float y0 = ImGui.getCursorScreenPosY();
      ImGui.setNextItemAllowOverlap();
      ImGui.invisibleButton("##row", contentW, rowH);
      boolean rowHovered = ImGui.isMouseHoveringRect(x0, y0, x0 + contentW, y0 + rowH);
      float hover = SpectreWidgets.animate(ImGui.getItemID(), rowHovered, 18.0F);
      ImDrawList dl = ImGui.getWindowDrawList();
      if (hover > 0.01F) {
         dl.addRectFilled(
            x0 - SpectreWidgets.s(8.0F),
            y0,
            x0 + contentW + SpectreWidgets.s(8.0F),
            y0 + rowH,
            SpectreWidgets.c(SpectreWidgets.withAlpha(218103807, hover)),
            SpectreWidgets.s(10.0F)
         );
      }

      float avatarR = SpectreWidgets.s(15.0F);
      float acx = x0 + avatarR;
      float acy = y0 + rowH * 0.5F;
      int avatarColor = AVATAR_COLORS[Math.floorMod(name.toLowerCase(Locale.ROOT).hashCode(), AVATAR_COLORS.length)];
      dl.addCircleFilled(acx, acy, avatarR, SpectreWidgets.c(avatarColor), 32);
      ImFont category = ImGuiManager.getInstance().categoryFont();
      String initial = name.substring(0, 1).toUpperCase(Locale.ROOT);
      SpectreWidgets.text(dl, category, acx - SpectreWidgets.textWidth(category, initial) * 0.5F, acy - SpectreWidgets.lineHeight(category) * 0.5F, -1, initial);
      ImFont font = ImGui.getFont();
      SpectreWidgets.text(dl, font, x0 + avatarR * 2.0F + SpectreWidgets.s(12.0F), y0 + (rowH - SpectreWidgets.lineHeight(font)) * 0.5F, -461582, name);
      float removeSize = SpectreWidgets.s(28.0F);
      ImGui.setCursorPos(pad + contentW - removeSize, localY + (rowH - removeSize) * 0.5F);
      float rx = ImGui.getCursorScreenPosX();
      float ry = ImGui.getCursorScreenPosY();
      if (ImGui.invisibleButton("##remove", removeSize, removeSize)) {
         store.remove(name);
      }

      float removeHover = SpectreWidgets.animate(ImGui.getItemID(), ImGui.isItemHovered(), 18.0F);
      float rcx = rx + removeSize * 0.5F;
      float rcy = ry + removeSize * 0.5F;
      if (removeHover > 0.01F) {
         dl.addCircleFilled(rcx, rcy, removeSize * 0.5F, SpectreWidgets.c(SpectreWidgets.withAlpha(-10856974, 0.18F * removeHover)), 32);
      }

      SpectreWidgets.cross(
         dl, rcx, rcy, SpectreWidgets.s(4.5F), SpectreTheme.lerpColor(SpectreWidgets.withAlpha(-4542808, 0.25F + 0.75F * hover), -10856974, removeHover)
      );
      ImGui.setCursorPos(0.0F, localY + rowH + SpectreWidgets.s(4.0F));
      ImGui.popID();
   }

   private void tryAddFriend(FriendStore store) {
      String name = this.friendInput.get().trim();
      if (!FriendStore.isValidName(name)) {
         this.friendMessage = "Use 1-16 letters, digits or _ (Bedrock names start with .)";
      } else if (store.isFriend(name)) {
         this.friendMessage = name + " is already on your list";
      } else {
         store.add(name);
         this.friendInput.set("");
         this.friendMessage = null;
      }

      this.friendMessageAt = System.nanoTime();
      this.focusFriendInput = true;
   }

   private void renderConfigCard(float dt, float alpha, float view) {
      float[] size = this.beginCard("##config_card", alpha, view);
      float w = size[0];
      float h = size[1];
      float pad = SpectreWidgets.s(24.0F);
      float top = size[2];
      float btn = SpectreWidgets.s(32.0F);
      float contentW = w - pad * 2.0F;
      ModuleConfigStore store = ModuleConfigStore.getInstance();
      ConfigProfiles profiles = ConfigProfiles.getInstance();
      ImFont small = ImGuiManager.getInstance().smallFont();
      drawCardTitle("Config", pad, top + btn * 0.5F, this.cardGuard);
      String status = store.isReadOnly() ? "Read-only" : (store.hasUnsavedChanges() ? "Saving..." : "Saved");
      int statusColor = store.isReadOnly() ? -10856974 : -4542808;
      SpectreWidgets.text(
         ImGui.getWindowDrawList(),
         small,
         ImGui.getWindowPosX() + w - pad - SpectreWidgets.textWidth(small, status),
         ImGui.getWindowPosY() + top + (btn - SpectreWidgets.lineHeight(small)) * 0.5F,
         statusColor,
         status,
         this.cardGuard
      );
      if (store.isReadOnly()
         && ImGui.isMouseHoveringRect(
            ImGui.getWindowPosX() + w - pad - SpectreWidgets.s(80.0F),
            ImGui.getWindowPosY() + top,
            ImGui.getWindowPosX() + w - pad,
            ImGui.getWindowPosY() + top + btn
         )) {
         showTooltip("modules.json could not be read. Fix or delete it.");
      }

      float y = mutedParagraph("Changes save automatically. Profiles can be shared with a code.", pad, top + btn + SpectreWidgets.s(12.0F), w - pad)
         + SpectreWidgets.s(14.0F);
      float gap = SpectreWidgets.s(8.0F);
      float chipH = ImGui.getTextLineHeight() + SpectreWidgets.s(16.0F);
      float chipW = (contentW - gap * 2.0F) / 3.0F;
      ImGui.setCursorPos(pad, y);
      if (SpectreWidgets.chipButton("##cfg_save", "Save all", chipW, chipH, true)) {
         this.saveAllConfigs(store);
      }

      if (ImGui.isItemHovered(65536)) {
         showTooltip("Save everything now");
      }

      ImGui.setCursorPos(pad + chipW + gap, y);
      if (SpectreWidgets.chipButton("##cfg_folder", "Open folder", chipW, chipH, false)) {
         this.openProfilesFolder(profiles);
      }

      ImGui.setCursorPos(pad + (chipW + gap) * 2.0F, y);
      boolean resetArmed = this.isConfirming("reset-all-modules");
      if (SpectreWidgets.chipButton("##cfg_reset", resetArmed ? "Confirm" : "Reset all", chipW, chipH, resetArmed) && this.confirmClick("reset-all-modules")) {
         for (ClientModule module : ModuleRegistry.all()) {
            module.resetSettings();
         }

         this.setConfigMessage("Every module setting is back to default", false);
      }

      if (ImGui.isItemHovered(65536)) {
         showTooltip("Reset all module settings. Keybinds stay.");
      }

      y += chipH + SpectreWidgets.s(18.0F);
      cardDivider(pad, w, y);
      y += SpectreWidgets.s(14.0F);
      float createW = SpectreWidgets.s(84.0F);
      ImGui.setCursorPos(pad, y);
      ImGui.pushStyleVar(11, SpectreWidgets.s(12.0F), SpectreWidgets.s(8.0F));
      ImGui.setNextItemWidth(contentW - createW - gap);
      if (this.focusProfileInput) {
         ImGui.setKeyboardFocusHere();
         this.focusProfileInput = false;
      }

      ImGui.inputTextWithHint("##profile_input", "New profile name", this.profileInput);
      boolean submitted = SpectreWidgets.submittedWithEnter();
      ImGui.popStyleVar();
      ImGui.setCursorPos(w - pad - createW, y);
      if (SpectreWidgets.chipButton("##profile_create", "Create", createW, chipH, true)) {
         submitted = true;
      }

      if (ImGui.isItemHovered(65536)) {
         showTooltip("Save your current modules as a new profile");
      }

      if (submitted) {
         this.tryCreateProfile(profiles);
      }

      y += chipH + SpectreWidgets.s(8.0F);
      float halfW = (contentW - gap) * 0.5F;
      ImGui.setCursorPos(pad, y);
      if (SpectreWidgets.chipButton("##profile_import", "Import file", halfW, chipH, false)) {
         this.importProfileFile();
      }

      if (ImGui.isItemHovered(65536)) {
         showTooltip("Import a profile .json file. You can also drop it here.");
      }

      ImGui.setCursorPos(pad + halfW + gap, y);
      if (SpectreWidgets.chipButton("##profile_paste", "Paste code", halfW, chipH, false)) {
         this.pasteProfileCode();
      }

      if (ImGui.isItemHovered(65536)) {
         showTooltip("Load a share code from your clipboard. Your current settings go to Auto backup.");
      }

      y += chipH + SpectreWidgets.s(8.0F);
      if (this.configMessage != null && elapsed(this.configMessageAt, 2500.0F) < 1.0F) {
         ImGui.setCursorPos(pad, y);
         boolean pushed = pushFont(small);
         ImGui.pushStyleColor(0, this.configMessageError ? -10856974 : -4542808);
         ImGui.textUnformatted(this.configMessage);
         ImGui.popStyleColor();
         if (pushed) {
            ImGui.popFont();
         }
      }

      y += SpectreWidgets.lineHeight(small) + SpectreWidgets.s(10.0F);
      cardDivider(pad, w, y);
      List<ConfigProfiles.Profile> list = profiles.list();
      ImGui.setCursorPos(0.0F, y + 1.0F);
      if (ImGui.beginChild("##profile_list", w, Math.max(1.0F, h - y - 1.0F - SpectreWidgets.s(10.0F)), false, 24)) {
         if (list.isEmpty()) {
            String empty = "No profiles yet";
            ImGui.setCursorPos((w - ImGui.calcTextSize(empty).x) * 0.5F, SpectreWidgets.s(40.0F));
            ImGui.textDisabled(empty);
         } else {
            ImGui.dummy(1.0F, SpectreWidgets.s(8.0F));
            this.profileList.render(profiles, list, store.activeProfile(), pad, contentW);
         }

         ImGui.dummy(1.0F, SpectreWidgets.s(12.0F));
         float target = this.profileList.takeScrollTarget();
         if (!Float.isNaN(target)) {
            this.scroll.computeIfAbsent("profiles", k -> new float[2])[1] = target;
         }

         this.applySmoothScroll("profiles", dt);
      }

      ImGui.endChild();
      this.endCard();
   }

   private void tryCreateProfile(ConfigProfiles profiles) {
      try {
         String name = profiles.create(this.profileInput.get());
         this.profileInput.set("");
         this.profileList.open(name);
         this.setConfigMessage("Created " + name + " from the current modules", false);
      } catch (ProfileException var3) {
         this.setConfigMessage(var3.getMessage(), true);
      }

      this.focusProfileInput = true;
   }

   private void importProfileFile() {
      if (FileDialogs.busy()) {
         this.setConfigMessage("A file window is already open", true);
      } else {
         FileDialogs.openProfile("Import an Spectre profile", this::importProfile);
      }
   }

   private void importProfile(Path path) {
      try {
         String name = ConfigProfiles.getInstance().importFile(path);
         this.profileList.open(name);
         this.setConfigMessage("Imported " + name + " - press Load to use it", false);
      } catch (ProfileException var3) {
         this.setConfigMessage(var3.getMessage(), true);
      }
   }

   private void pasteProfileCode() {
      ConfigProfiles profiles = ConfigProfiles.getInstance();

      try {
         String name = profiles.importCode(Minecraft.getInstance().keyboardHandler.getClipboard());
         ConfigProfiles.Profile imported = profiles.find(name);
         profiles.backup(imported == null ? Set.of(ConfigProfiles.Part.MODULES) : imported.parts());
         profiles.load(name);
         this.profileList.open(name);
         this.setConfigMessage("Loaded " + name + " - your previous settings are in Auto backup", false);
      } catch (ProfileException var4) {
         this.setConfigMessage(var4.getMessage(), true);
      }
   }

   private void saveAllConfigs(ModuleConfigStore store) {
      store.save();
      ThemeStore.get().save();
      HudTheme.get().save();
      HudLayoutStore.getInstance().save();
      FriendStore.getInstance().save();
      if (store.isReadOnly()) {
         this.setConfigMessage("modules.json is unreadable, so it was left untouched", true);
      } else {
         this.setConfigMessage("Everything saved", false);
      }
   }

   private void openProfilesFolder(ConfigProfiles profiles) {
      try {
         Path dir = profiles.directory();
         Files.createDirectories(dir);
         Util.getPlatform().openPath(dir);
      } catch (RuntimeException | IOException var3) {
         this.setConfigMessage("Could not open the profiles folder", true);
      }
   }

   private void setConfigMessage(String message, boolean error) {
      this.configMessage = message;
      this.configMessageError = error;
      this.configMessageAt = System.nanoTime();
   }

   private void renderThemeStudio(float dt, float alpha, float view) {
      ThemeStore theme = ThemeStore.get();
      this.renderAppearancePreview(theme);
      this.renderInspector(theme, dt, alpha);
   }

   private float appearanceInspectorWidth() {
      return Math.min(SpectreWidgets.s(360), contentW * 0.5F);
   }

   /** A single non-interactive sample, contained in the workspace, never live modules. */
   private void renderAppearancePreview(ThemeStore theme) {
      float w = contentW - appearanceInspectorWidth() - SpectreWidgets.s(10), h = contentH;
      float pad = SpectreWidgets.s(20);
      ImGui.setNextWindowPos(contentX, contentY, 1);
      ImGui.setNextWindowSize(w, h, 1);
      ImGui.begin("##appearance_preview", 8511 | ImGuiWindowFlags.NoBackground | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoSavedSettings);
      ImDrawList dl = ImGui.getWindowDrawList();
      float x = ImGui.getWindowPosX(), y = ImGui.getWindowPosY();
      dl.addRectFilled(x, y, x + w, y + h, GuiPalette.abgr(0xFF101823), 12);
      ImGui.setCursorPos(pad, pad);
      ImGui.textUnformatted("Appearance preview");
      ImGui.setCursorPos(pad, pad + SpectreWidgets.s(30));
      ImGui.pushTextWrapPos(w - pad);
      ImGui.textDisabled("One sample of the selected surface. Module controls are in Modules.");
      ImGui.popTextWrapPos();
      Surface sample = themeSurface == null ? theme.cards : themeSurface;
      String label = themeSurface == theme.panels ? "Panel sample" : themeSurface == theme.tabBar ? "Navigation sample" : themeSurface == theme.cards ? "Settings card sample" : "Interface sample";
      float sampleW = Math.min(w - pad * 2, SpectreWidgets.s(440));
      float sampleH = Math.min(h - SpectreWidgets.s(210), SpectreWidgets.s(themeSurface == theme.tabBar ? 92 : 250));
      sampleH = Math.max(64, sampleH);
      float sx = x + (w - sampleW) / 2, sy = y + (h - sampleH) / 2;
      HudChrome.card(dl, sample, sx, sy, sampleW, sampleH, GuiPalette.rounding(), 1, true, SpectreWidgets.s(1));
      float inset = SpectreWidgets.s(20);
      SpectreWidgets.text(dl, ImGui.getFont(), sx + inset, sy + inset, -461582, label);
      if (themeSurface == theme.tabBar) {
         dl.addRectFilled(sx + inset, sy + sampleH - 5, sx + sampleW * .45F, sy + sampleH - 3, GuiPalette.accent(), 2);
      } else {
         float rowY = sy + SpectreWidgets.s(70), rowW = sampleW - inset * 2;
         dl.addRectFilled(sx + inset, rowY, sx + inset + rowW, rowY + SpectreWidgets.s(34), SpectreWidgets.withAlpha(GuiPalette.accent(), .14F), 6);
         SpectreWidgets.text(dl, ImGui.getFont(), sx + inset + 10, rowY + 9, -461582, "Selected item");
         if (sampleH > SpectreWidgets.s(170)) {
            float trackY = sy + sampleH - SpectreWidgets.s(45);
            dl.addRectFilled(sx + inset, trackY, sx + sampleW - inset, trackY + 3, 0x553F4B59, 2);
            dl.addRectFilled(sx + inset, trackY, sx + sampleW * .65F, trackY + 3, GuiPalette.accent(), 2);
            dl.addCircleFilled(sx + sampleW * .65F, trackY + 1.5F, 5, -1, 20);
         }
      }
      ImGui.setCursorPos(pad, h - SpectreWidgets.s(54));
      if (ImGui.button("Open Modules", Math.min(w - pad * 2, SpectreWidgets.s(160)), SpectreWidgets.s(32))) switchTab(Tab.MODULES);
      ImGui.end();
   }

   private void renderInspector(ThemeStore theme, float dt, float alpha) {
      float w = appearanceInspectorWidth();
      float h = contentH;
      ImGui.setNextWindowPos(contentX + contentW - w, contentY, 1);
      ImGui.setNextWindowSize(w, h, 1);
      if (this.focusInspector) {
         ImGui.setNextWindowFocus();
         this.focusInspector = false;
      }

      ImGui.pushStyleVar(0, alpha);
      ImGui.pushStyleVar(2, 0.0F, 0.0F);
      ImGui.pushStyleVar(14, 0.0F, 0.0F);
      ImGui.pushStyleVar(3, cardRounding());
      ImGui.pushStyleColor(2, GuiPalette.abgr(0xFF101823));
      int flags = 59 | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoSavedSettings;
      if (ImGui.begin("##theme_inspector", flags)) {
         float x = ImGui.getWindowPosX();
         float y = ImGui.getWindowPosY();
         this.inspectorRect = new float[]{x, y, w, h};
         drawShadow(x, y, w, h, cardRounding(), 6);
         ImGui.getWindowDrawList().addRect(x, y, x + w, y + h, SpectreWidgets.c(318767103), cardRounding(), 0, 1.0F);
         float pad = SpectreWidgets.s(20.0F);
         float top = SpectreWidgets.s(20.0F);
         drawCardTitle("Appearance", pad, top + SpectreWidgets.s(14.0F));
         float ty = top + SpectreWidgets.s(34.0F);
         if (this.themeMessage != null && System.nanoTime() - this.themeMessageAt < 3000000000L) {
            ImGui.setCursorPos(pad, ty);
            ImGui.pushTextWrapPos(w - pad);
            ImGui.pushStyleColor(0, this.themeMessageError ? -10856974 : -461582);
            ImGui.textUnformatted(this.themeMessage);
            ImGui.popStyleColor();
            ImGui.popTextWrapPos();
            ty = ImGui.getCursorPosY();
         } else {
            ty = mutedParagraph("Edit colors and layout. The sample updates live.", pad, ty, w - pad);
         }

         ty = this.renderSurfaceTabs(theme, pad, ty + SpectreWidgets.s(12.0F), w - pad * 2.0F) + SpectreWidgets.s(14.0F);
         cardDivider(pad, w, ty);
         ImGui.setCursorPos(0.0F, ty + 1.0F);
         if (ImGui.beginChild("##inspector_body", w, Math.max(1.0F, h - ty - 1.0F - SpectreWidgets.s(10.0F)), false, 24)) {
            float contentW = w - pad * 2.0F;
            if (this.activeColorSetting != null && this.activeColorModule == null && this.colorPickerOpen.get()) {
               ImGui.setCursorPos(pad, SpectreWidgets.s(12));
               ImGui.textUnformatted("Color - " + this.activeColorSetting.name());
               ImGui.setCursorPosX(pad);
               ImGui.setNextItemWidth(contentW);
               int pickerFlags = ImGuiColorEditFlags.NoSidePreview | ImGuiColorEditFlags.NoLabel | ImGuiColorEditFlags.NoOptions
                  | ImGuiColorEditFlags.AlphaBar | ImGuiColorEditFlags.DisplayRGB | ImGuiColorEditFlags.InputRGB
                  | ImGuiColorEditFlags.Uint8 | ImGuiColorEditFlags.PickerHueBar;
               if (ImGui.colorPicker4("##appearance_picker", this.activeColorSetting.value(), pickerFlags)) this.activeColorSetting.notifyChanged();
               ImGui.setCursorPosX(pad);
               if (ImGui.button("Done", contentW, SpectreWidgets.s(30))) {
                  this.activeColorSetting.notifyChanged();
                  this.activeColorSetting = null;
                  this.colorPickerOpen.set(false);
               }
            } else if (this.themeSurface == null) {
               this.renderGlobalOptions(theme, pad, contentW);
            } else {
               this.renderSurfaceOptions(this.themeSurface, pad, contentW);
            }

            ImGui.dummy(1.0F, SpectreWidgets.s(18.0F));
            this.applySmoothScroll("inspector", dt);
         }

         ImGui.endChild();
      }

      ImGui.end();
      ImGui.popStyleColor();
      ImGui.popStyleVar(4);
   }

   private float renderSurfaceTabs(ThemeStore theme, float pad, float y, float contentW) {
      String[] labels = new String[]{"Global", "Panels", "Cards", "Tab bar"};
      Surface[] targets = new Surface[]{null, theme.panels, theme.cards, theme.tabBar};
      int selected = 0;

      for (int i = 1; i < targets.length; i++) {
         if (targets[i] == this.themeSurface) {
            selected = i;
         }
      }

      float h = SpectreWidgets.s(32.0F);
      ImGui.setCursorPos(pad, y);
      int clicked = SpectreWidgets.segmented("##surface", labels, selected, contentW, h);
      if (clicked >= 0) {
         this.selectSurface(targets[clicked]);
      }

      return y + h;
   }

   private void renderGlobalOptions(ThemeStore theme, float pad, float contentW) {
      this.sectionLabel("Presets", pad);
      this.renderPresetRow(theme, pad, contentW);
      this.sectionLabel("Style", pad);
      this.settingRows(pad, contentW, theme.guiStyle);
      this.sectionLabel("Colors", pad);
      if (theme.gradient() || theme.glass()) {
         this.settingRows(pad, contentW, theme.accent, theme.accent2);
      } else {
         this.settingRows(pad, contentW, theme.accent);
      }
      this.sectionLabel("Layout", pad);
      this.settingRows(pad, contentW, theme.rounding, theme.backgroundDim, theme.menuBlur);
      if (theme.menuBlur.get()) this.settingRows(pad, contentW, theme.blurStrength);
      this.sectionLabel("Interface", pad);
      this.settingRows(pad, contentW, theme.uiScale, theme.systemFont, theme.animationSpeed, theme.animateLines, theme.uiSounds, theme.hoverSounds, theme.customMainMenu, theme.customVanillaUi, theme.donutNamePrivacy);
      this.sectionLabel("Share", pad);
      this.renderThemeActions(theme, pad, contentW);
   }

   private void renderSurfaceOptions(Surface surface, float pad, float contentW) {
      this.sectionLabel("Fill", pad);
      this.settingRows(pad, contentW, surface.color, surface.opacity);
      this.sectionLabel("Background", pad);
      this.settingRows(pad, contentW, surface.background);
      if (surface.isImage()) {
         this.renderImageGallery(surface, pad, contentW);
      }

      this.settingRows(
         pad,
         contentW,
         surface.mapping,
         surface.coverage,
         surface.headerHeight,
         surface.imageOpacity,
         surface.imageScale,
         surface.imageX,
         surface.imageY,
         surface.lineOpacity
      );
      if (surface.isScreenMapped()) {
         mutedParagraph(
            "Screen mapping pins the background to the screen, so each surface is a window onto it. Drag a panel to see another part.",
            pad,
            ImGui.getCursorPosY() + SpectreWidgets.s(12.0F),
            pad + contentW
         );
      }
   }

   private void selectSurface(Surface surface) {
      if (surface != this.themeSurface) {
         this.colorPickerOpen.set(false);
         this.activeColorSetting = null;
         this.activeColorModule = null;
         ThemeStore theme = ThemeStore.get();
         if (surface == theme.cards != (this.themeSurface == theme.cards)) {
            this.previewChangedAt = System.nanoTime();
         }

         this.themeSurface = surface;
         this.focusInspector = true;
      }
   }

   private void highlightSurfaces(ThemeStore theme) {
      float mx = ImGui.getIO().getMousePosX();
      float my = ImGui.getIO().getMousePosY();
      boolean blocked = contains(this.inspectorRect, mx, my) || ImGui.isPopupOpen("", 3072);
      Surface hovered = null;
      if (!blocked) {
         for (float[] rect : this.panelRects) {
            if (contains(rect, mx, my)) {
               hovered = theme.panels;
            }
         }

         if (contains(this.cardRect, mx, my)) {
            hovered = theme.cards;
         }

         if (contains(this.tabBarRect, mx, my)) {
            hovered = theme.tabBar;
         }
      }

      ImDrawList fg = ImGui.getForegroundDrawList();
      if (this.themeSurface != null) {
         this.outlineSurface(fg, theme, this.themeSurface, SpectreWidgets.withAlpha(GuiPalette.accent(), 0.8F));
      }

      if (hovered != null) {
         if (hovered != this.themeSurface) {
            this.outlineSurface(fg, theme, hovered, -2130706433);
         }

         boolean tabBar = hovered == theme.tabBar;
         String hint = hovered.label + (tabBar ? "  ·  right-click to edit" : "  ·  click to edit");
         ImFont small = ImGuiManager.getInstance().smallFont();
         float chipW = SpectreWidgets.textWidth(small, hint) + SpectreWidgets.s(18.0F);
         float chipH = SpectreWidgets.lineHeight(small) + SpectreWidgets.s(10.0F);
         float cx = Math.min(mx + SpectreWidgets.s(14.0F), ImGui.getIO().getDisplaySizeX() - chipW - SpectreWidgets.s(4.0F));
         float cy = Math.min(my + SpectreWidgets.s(18.0F), ImGui.getIO().getDisplaySizeY() - chipH - SpectreWidgets.s(4.0F));
         fg.addRectFilled(cx, cy, cx + chipW, cy + chipH, GuiPalette.accent(), chipH * 0.5F);
         SpectreWidgets.text(fg, small, cx + SpectreWidgets.s(9.0F), cy + SpectreWidgets.s(5.0F), -1, hint);
         boolean clicked = tabBar ? ImGui.isMouseClicked(1) : ImGui.isMouseClicked(0) || ImGui.isMouseClicked(1);
         if (clicked) {
            this.selectSurface(hovered);
         }
      }
   }

   private void outlineSurface(ImDrawList fg, ThemeStore theme, Surface surface, int color) {
      List<float[]> rects;
      float rounding;
      if (surface == theme.panels) {
         rects = this.panelRects;
         rounding = panelRounding();
      } else if (surface == theme.cards) {
         rects = this.cardRect == null ? List.of() : List.of(this.cardRect);
         rounding = cardRounding();
      } else {
         rects = this.tabBarRect == null ? List.of() : List.of(this.tabBarRect);
         rounding = this.tabBarRect == null ? 0.0F : this.tabBarRect[3] * 0.5F;
      }

      float e = SpectreWidgets.s(3.0F);

      for (float[] r : rects) {
         fg.addRect(r[0] - e, r[1] - e, r[0] + r[2] + e, r[1] + r[3] + e, color, rounding + e, 0, SpectreWidgets.s(1.5F));
      }
   }

   private void recordPanelRect() {
      if (this.inspectMode) {
         this.panelRects.add(new float[]{ImGui.getWindowPosX(), ImGui.getWindowPosY(), ImGui.getWindowWidth(), ImGui.getWindowHeight()});
      }
   }

   private static boolean contains(float[] rect, float x, float y) {
      return rect != null && x >= rect[0] && x <= rect[0] + rect[2] && y >= rect[1] && y <= rect[1] + rect[3];
   }

   private static ClientModule previewModule() {
      ClientModule fallback = null;

      for (ClientModule module : ModuleRegistry.all()) {
         if (fallback == null) {
            fallback = module;
         }

         if (module.settings().size() >= 3) {
            return module;
         }
      }

      return fallback;
   }

   private void settingRows(float pad, float contentW, Setting... settings) {
      for (Setting setting : settings) {
         if (setting.isVisible()) {
            ImGui.pushID(setting.id());
            this.renderSetting(null, setting, pad, contentW);
            ImGui.popID();
         }
      }
   }

   /** Premade HUD looks as clickable swatches that preview their own style. */
   private void renderHudPresets(float pad, float contentW) {
      HudTheme theme = HudTheme.get();
      int columns = contentW >= SpectreWidgets.s(300F) ? 3 : 2;
      float gap = SpectreWidgets.s(6F), tileH = SpectreWidgets.s(38F), r = SpectreWidgets.s(9F);
      float tileW = (contentW - gap * (columns - 1)) / columns;
      float top = ImGui.getCursorPosY() + SpectreWidgets.s(2F);
      ImDrawList dl = ImGui.getWindowDrawList();
      ImFont font = ImGui.getFont();
      for (int i = 0; i < HudTheme.PRESETS.size(); i++) {
         HudTheme.Preset preset = HudTheme.PRESETS.get(i);
         ImGui.setCursorPos(pad + (i % columns) * (tileW + gap), top + (i / columns) * (tileH + gap));
         float x = ImGui.getCursorScreenPosX(), y = ImGui.getCursorScreenPosY();
         if (ImGui.invisibleButton("##hudpreset" + i, tileW, tileH)) {
            theme.applyPreset(preset);
            this.showHudMessage(preset.name() + " applied", false);
         }
         float hover = SpectreWidgets.animate(ImGui.getItemID(), ImGui.isItemHovered(), 18F);
         // Checkered dark backdrop so translucent glass swatches read as glass.
         dl.addRectFilled(x, y, x + tileW, y + tileH, SpectreWidgets.c(0xFF15131A), r);
         dl.addCircleFilled(x + tileW * 0.25F, y + tileH * 0.9F, tileH * 0.5F, SpectreWidgets.c(SpectreWidgets.withAlpha(GuiPalette.accent(), 0.55F)), 24);
         switch (preset.style()) {
            case HudTheme.STYLE_GRADIENT -> HudChrome.gradient(dl, x, y, tileW, tileH, r, GuiPalette.abgr(preset.from()), GuiPalette.abgr(preset.to()), 1F);
            case HudTheme.STYLE_GLASS -> {
               dl.addRectFilled(x, y, x + tileW, y + tileH, SpectreWidgets.c(GuiPalette.abgr(preset.color() & 0xFFFFFF | Math.round(preset.opacity() * 255) << 24)), r);
               dl.addRectFilled(x, y, x + tileW, y + tileH, SpectreWidgets.c(Math.round(preset.frost() * 255) << 24 | 0xFFFFFF), r);
               HudChrome.gradientVertical(dl, x, y, tileW, tileH * 0.55F, r, 0x55FFFFFF, 0x00FFFFFF);
               dl.addRect(x, y, x + tileW, y + tileH, SpectreWidgets.c(0x66FFFFFF), r, 0, 1F);
            }
            default -> dl.addRectFilled(x, y, x + tileW, y + tileH, SpectreWidgets.c(GuiPalette.abgr(preset.color() | 0xFF000000)), r);
         }
         boolean selected = theme.matchesPreset(preset);
         if (selected || hover > 0.01F) {
            dl.addRect(x - 1, y - 1, x + tileW + 1, y + tileH + 1, SpectreWidgets.c(SpectreWidgets.withAlpha(selected ? GuiPalette.accent() : -1, selected ? 1F : 0.4F * hover)), r + 1, 0, SpectreWidgets.s(1.5F));
         }
         String name = SpectreWidgets.ellipsize(font, preset.name(), tileW - SpectreWidgets.s(12F));
         SpectreWidgets.text(dl, font, x + SpectreWidgets.s(9F) + 1, y + (tileH - SpectreWidgets.lineHeight(font)) * 0.5F + 1, 0x99000000, name);
         SpectreWidgets.text(dl, font, x + SpectreWidgets.s(9F), y + (tileH - SpectreWidgets.lineHeight(font)) * 0.5F, -1, name);
      }
      int rows = (HudTheme.PRESETS.size() + columns - 1) / columns;
      ImGui.setCursorPos(0, top + rows * (tileH + gap));
   }

   private void renderPresetRow(ThemeStore theme, float pad, float contentW) {
      // A grid of small window previews: each tile shows the theme's panel, accent (or gradient) and,
      // for Liquid Glass, the frosted rim, with its name underneath.
      float startY = ImGui.getCursorPosY() + SpectreWidgets.s(4.0F);
      float gap = SpectreWidgets.s(8.0F);
      int columns = Math.max(2, (int) ((contentW + gap) / (SpectreWidgets.s(96.0F) + gap)));
      float tileW = (contentW - gap * (columns - 1)) / columns;
      float tileH = SpectreWidgets.s(58.0F);
      float r = SpectreWidgets.s(9.0F);
      ImDrawList dl = ImGui.getWindowDrawList();
      ImFont small = ImGuiManager.getInstance().smallFont();

      int slot = 0;
      for (int i = 0; i < ThemeStore.PRESETS.size(); i++) {
         ThemeStore.Preset preset = ThemeStore.PRESETS.get(i);
         // The Halloween swatch is only offered in October.
         if (preset.name().equals("Halloween") && !HalloweenDecor.inSeason()) {
            continue;
         }
         ImGui.setCursorPos(pad + slot % columns * (tileW + gap), startY + slot / columns * (tileH + gap));
         slot++;
         float tx = ImGui.getCursorScreenPosX(), ty = ImGui.getCursorScreenPosY();
         if (ImGui.invisibleButton("##preset" + i, tileW, tileH)) {
            theme.applyPreset(preset);
            GuiSounds.toggle(true);
         }

         boolean hovered = ImGui.isItemHovered();
         float hover = SpectreWidgets.animate(ImGui.getItemID(), hovered, 18.0F);
         boolean selected = theme.matchesPreset(preset);
         int accentA = GuiPalette.abgr(preset.accent() | 0xFF000000), accentB = GuiPalette.abgr(preset.accent2() | 0xFF000000);
         int panel = GuiPalette.abgr(preset.panel() | 0xFF000000);
         boolean glass = ThemeStore.STYLE_GLASS.equals(preset.style()), grad = ThemeStore.STYLE_GRADIENT.equals(preset.style());
         float lift = SpectreWidgets.s(2.0F) * hover;
         ty -= lift;

         dl.addRectFilled(tx, ty, tx + tileW, ty + tileH, SpectreWidgets.c(SpectreTheme.lerpColor(0xFF0B070C, panel, 0.85F)), r);
         if (glass) {
            HudChrome.gradient(dl, tx, ty, tileW, tileH, r, SpectreWidgets.c(SpectreWidgets.withAlpha(accentA, 0.30F)), SpectreWidgets.c(SpectreWidgets.withAlpha(accentB, 0.18F)), 1F);
            dl.addRectFilled(tx, ty, tx + tileW, ty + tileH, SpectreWidgets.c(SpectreWidgets.withAlpha(0xFFFFFFFF, 0.08F)), r);
            HudChrome.gradientVertical(dl, tx, ty, tileW, tileH * 0.5F, r, SpectreWidgets.c(SpectreWidgets.withAlpha(0xFFFFFFFF, 0.22F)), SpectreWidgets.c(SpectreWidgets.withAlpha(0xFFFFFFFF, 0F)));
         } else if (grad) {
            HudChrome.gradient(dl, tx, ty, tileW, tileH, r, SpectreWidgets.c(SpectreWidgets.withAlpha(accentA, 0.28F)), SpectreWidgets.c(SpectreWidgets.withAlpha(accentB, 0.28F)), 1F);
         }
         // Mini window: a sidebar strip, a selected pill and two content lines.
         float m = SpectreWidgets.s(7.0F), pillH = SpectreWidgets.s(6.0F);
         dl.addRectFilled(tx + m, ty + m, tx + m + tileW * 0.22F, ty + tileH - SpectreWidgets.s(20.0F), SpectreWidgets.c(SpectreWidgets.withAlpha(0xFFFFFFFF, 0.06F)), SpectreWidgets.s(4.0F));
         float px = tx + m + tileW * 0.22F + SpectreWidgets.s(5.0F), pw = tileW - (px - tx) - m;
         if (grad || glass) {
            HudChrome.gradient(dl, px, ty + m, pw, pillH, pillH * 0.5F, SpectreWidgets.c(accentA), SpectreWidgets.c(accentB), 1F);
         } else {
            dl.addRectFilled(px, ty + m, px + pw, ty + m + pillH, SpectreWidgets.c(accentA), pillH * 0.5F);
         }
         dl.addRectFilled(px, ty + m + pillH + SpectreWidgets.s(5.0F), px + pw * 0.8F, ty + m + pillH + SpectreWidgets.s(8.0F), SpectreWidgets.c(SpectreWidgets.withAlpha(0xFFFFFFFF, 0.18F)), SpectreWidgets.s(2.0F));
         dl.addRectFilled(px, ty + m + pillH + SpectreWidgets.s(11.0F), px + pw * 0.55F, ty + m + pillH + SpectreWidgets.s(14.0F), SpectreWidgets.c(SpectreWidgets.withAlpha(0xFFFFFFFF, 0.12F)), SpectreWidgets.s(2.0F));
         String name = SpectreWidgets.ellipsize(small, preset.name(), tileW - m * 2);
         SpectreWidgets.text(dl, small, tx + m, ty + tileH - SpectreWidgets.lineHeight(small) - SpectreWidgets.s(5.0F), selected ? brightText(1) : mutedText(1), name);
         if (selected) {
            dl.addRect(tx, ty, tx + tileW, ty + tileH, SpectreWidgets.c(accentA), r, 0, SpectreWidgets.s(2.0F));
         } else {
            dl.addRect(tx, ty, tx + tileW, ty + tileH, SpectreWidgets.c(SpectreWidgets.withAlpha(0xFFFFFFFF, (glass ? 0.22F : 0.08F) + 0.25F * hover)), r, 0, 1F);
         }
         if (hovered) {
            showTooltip(preset.name() + "  ·  " + preset.style());
         }
      }

      int rows = (slot + columns - 1) / columns;
      ImGui.setCursorPos(0.0F, startY + rows * (tileH + gap) + SpectreWidgets.s(2.0F));
   }

   private void renderImageGallery(Surface surface, float pad, float contentW) {
      List<String> files = ThemeImages.get().files();
      int columns = 3;
      float gap = SpectreWidgets.s(8.0F);
      float r = SpectreWidgets.s(8.0F);
      float tileW = (contentW - gap * (columns - 1)) / columns;
      float tileH = tileW * 0.6F;
      float startY = ImGui.getCursorPosY() + SpectreWidgets.s(4.0F);
      ImDrawList dl = ImGui.getWindowDrawList();
      ImFont small = ImGuiManager.getInstance().smallFont();
      String deleted = null;
      int count = files.size() + 1;

      for (int i = 0; i < count; i++) {
         ImGui.setCursorPos(pad + i % columns * (tileW + gap), startY + i / columns * (tileH + gap));
         float sx = ImGui.getCursorScreenPosX();
         float sy = ImGui.getCursorScreenPosY();
         String name = i == 0 ? null : files.get(i - 1);
         String deleteKey = name == null ? null : "image-delete:" + name;
         boolean armed = deleteKey != null && this.isConfirming(deleteKey);
         float del = SpectreWidgets.s(20.0F);
         float dx = sx + tileW - del - SpectreWidgets.s(5.0F);
         float dy = sy + SpectreWidgets.s(5.0F);
         boolean overTile = ImGui.isWindowHovered(128) && ImGui.isMouseHoveringRect(sx, sy, sx + tileW, sy + tileH);
         boolean deleteShown = deleteKey != null && (armed || overTile);
         boolean deleteHovered = false;
         float deleteHover = 0.0F;
         if (deleteShown) {
            ImGui.setCursorScreenPos(dx, dy);
            if (ImGui.invisibleButton("##tiledel" + i, del, del) && this.confirmClick(deleteKey)) {
               deleted = name;
            }

            deleteHovered = ImGui.isItemHovered();
            deleteHover = SpectreWidgets.animate(ImGui.getItemID(), deleteHovered, 18.0F);
            armed = this.isConfirming(deleteKey);
            if (deleteHovered) {
               showTooltip(armed ? "Click again to delete" : "Delete image");
            }

            ImGui.setCursorScreenPos(sx, sy);
         }

         boolean clicked = ImGui.invisibleButton("##tile" + i, tileW, tileH);
         boolean hovered = ImGui.isItemHovered() || deleteHovered;
         float hover = SpectreWidgets.animate(ImGui.getItemID(), hovered, 18.0F);
         if (i == 0) {
            dl.addRectFilled(sx, sy, sx + tileW, sy + tileH, SpectreWidgets.c(SpectreTheme.lerpColor(GuiPalette.surface(), GuiPalette.surfaceHover(), hover)), r);
            int glyph = SpectreTheme.lerpColor(-4542808, -461582, hover);
            float cx = sx + tileW * 0.5F;
            float cy = sy + tileH * 0.5F - SpectreWidgets.s(8.0F);
            dl.addLine(cx - SpectreWidgets.s(7.0F), cy, cx + SpectreWidgets.s(7.0F), cy, SpectreWidgets.c(glyph), SpectreWidgets.s(1.8F));
            dl.addLine(cx, cy - SpectreWidgets.s(7.0F), cx, cy + SpectreWidgets.s(7.0F), SpectreWidgets.c(glyph), SpectreWidgets.s(1.8F));
            String label = "Add image";
            SpectreWidgets.text(dl, small, cx - SpectreWidgets.textWidth(small, label) * 0.5F, cy + SpectreWidgets.s(12.0F), glyph, label);
            if (clicked) {
               ThemeImages.get().browseAsync();
            }
         } else {
            ThemeImages.Texture texture = ThemeImages.get().preview(name);
            boolean broken = texture == null && ThemeImages.get().isBroken(name);
            dl.addRectFilled(sx, sy, sx + tileW, sy + tileH, SpectreWidgets.c(GuiPalette.surface()), r);
            if (texture != null) {
               float[] uv = SurfaceBackground.coverUv(texture.width(), texture.height(), tileW, tileH, 0.5F);
               dl.addImageRounded(texture.id(), sx, sy, sx + tileW, sy + tileH, uv[0], uv[1], uv[2], uv[3], SpectreWidgets.c(-1), r, 240);
            } else if (broken && !armed) {
               float cx = sx + tileW * 0.5F;
               float cy = sy + tileH * 0.5F - SpectreWidgets.s(8.0F);
               SpectreWidgets.cross(dl, cx, cy, SpectreWidgets.s(5.0F), -4542808);
               String label = "Can't load";
               SpectreWidgets.text(dl, small, cx - SpectreWidgets.textWidth(small, label) * 0.5F, cy + SpectreWidgets.s(10.0F), -4542808, label);
            }

            if (ThemeImages.isGif(name)) {
               String badge = "GIF";
               float badgePad = SpectreWidgets.s(4.0F);
               float badgeH = SpectreWidgets.lineHeight(small) + SpectreWidgets.s(2.0F);
               float badgeW = SpectreWidgets.textWidth(small, badge) + badgePad * 2.0F;
               float bx = sx + SpectreWidgets.s(5.0F);
               float by = sy + tileH - badgeH - SpectreWidgets.s(5.0F);
               dl.addRectFilled(bx, by, bx + badgeW, by + badgeH, SpectreWidgets.c(SpectreWidgets.withAlpha(-16777216, 0.6F)), SpectreWidgets.s(4.0F));
               SpectreWidgets.text(dl, small, bx + badgePad, by + SpectreWidgets.s(1.0F), -1, badge);
            }

            if (armed) {
               String ask = "Delete?";
               dl.addRectFilled(sx, sy, sx + tileW, sy + tileH, SpectreWidgets.c(SpectreWidgets.withAlpha(-16777216, 0.6F)), r);
               SpectreWidgets.text(
                  dl, small, sx + (tileW - SpectreWidgets.textWidth(small, ask)) * 0.5F, sy + (tileH - SpectreWidgets.lineHeight(small)) * 0.5F, -1, ask
               );
            }

            if (name.equals(surface.image.get())) {
               dl.addRect(
                  sx - SpectreWidgets.s(1.0F),
                  sy - SpectreWidgets.s(1.0F),
                  sx + tileW + SpectreWidgets.s(1.0F),
                  sy + tileH + SpectreWidgets.s(1.0F),
                  SpectreWidgets.c(GuiPalette.accent()),
                  r + SpectreWidgets.s(1.0F),
                  0,
                  SpectreWidgets.s(2.0F)
               );
            } else if (hover > 0.01F) {
               dl.addRect(sx, sy, sx + tileW, sy + tileH, SpectreWidgets.c(SpectreWidgets.withAlpha(-1, 0.4F * hover)), r, 0, 1.0F);
            }

            if (deleteShown) {
               float fade = armed ? 1.0F : hover;
               float dcx = dx + del * 0.5F;
               float dcy = dy + del * 0.5F;
               int disc = armed ? -10856974 : SpectreTheme.lerpColor(SpectreWidgets.withAlpha(-16777216, 0.55F), GuiPalette.accent(), deleteHover);
               dl.addCircleFilled(dcx, dcy, del * 0.5F, SpectreWidgets.c(SpectreWidgets.withAlpha(disc, fade)), 32);
               SpectreWidgets.cross(dl, dcx, dcy, SpectreWidgets.s(3.5F), SpectreWidgets.withAlpha(-1, fade));
            }

            if (clicked) {
               if (armed) {
                  this.pendingConfirm = null;
               } else {
                  surface.image.set(name);
               }
            }

            if (!deleteHovered && ImGui.isItemHovered(65536)) {
               showTooltip(broken ? name + "\nCouldn't load this image" : name);
            }
         }
      }

      if (deleted != null) {
         this.deleteImage(deleted);
      }

      int rows = (count + columns - 1) / columns;
      float hintY = startY + rows * (tileH + gap) - gap + SpectreWidgets.s(10.0F);
      String hint = "Drop an image on the screen or ";
      String link = "open the folder";
      float hintW = SpectreWidgets.textWidth(small, hint);
      float linkW = SpectreWidgets.textWidth(small, link);
      float lineH = SpectreWidgets.lineHeight(small);
      float wx = ImGui.getWindowPosX();
      float wy = ImGui.getWindowPosY() - ImGui.getScrollY();
      SpectreWidgets.text(dl, small, wx + pad, wy + hintY, -4542808, hint);
      ImGui.setCursorPos(pad + hintW, hintY);
      if (ImGui.invisibleButton("##openfolder", linkW, lineH)) {
         ThemeImages.get().openFolder();
      }

      float linkHover = SpectreWidgets.animate(ImGui.getItemID(), ImGui.isItemHovered(), 18.0F);
      SpectreWidgets.text(dl, small, wx + pad + hintW, wy + hintY, SpectreTheme.lerpColor(GuiPalette.accent(), GuiPalette.accentHover(), linkHover), link);
      ImGui.setCursorPos(0.0F, hintY + lineH + SpectreWidgets.s(8.0F));
   }

   private void renderThemeActions(ThemeStore theme, float pad, float contentW) {
      float gap = SpectreWidgets.s(8.0F);
      float h = SpectreWidgets.s(34.0F);
      float buttonW = (contentW - gap * 2.0F) / 3.0F;
      float y = ImGui.getCursorPosY() + SpectreWidgets.s(6.0F);
      ImGui.setCursorPos(pad, y);
      if (SpectreWidgets.chipButton("##copytheme", "Copy theme", buttonW, h, false)) {
         Minecraft.getInstance().keyboardHandler.setClipboard(theme.exportString());
         this.showThemeMessage("Theme copied to the clipboard", false);
      }

      ImGui.setCursorPos(pad + buttonW + gap, y);
      if (SpectreWidgets.chipButton("##pastetheme", "Paste theme", buttonW, h, false)) {
         boolean applied = theme.importString(Minecraft.getInstance().keyboardHandler.getClipboard());
         this.showThemeMessage(applied ? "Theme applied from the clipboard" : "The clipboard doesn't contain an Spectre theme", !applied);
      }

      ImGui.setCursorPos(pad + (buttonW + gap) * 2.0F, y);
      if (SpectreWidgets.chipButton("##resettheme", "Reset", buttonW, h, false)) {
         theme.resetToDefaults();
         this.showThemeMessage("Theme reset to the defaults", false);
      }

      ImGui.setCursorPos(0.0F, y + h);
   }

   private void showThemeMessage(String message, boolean error) {
      this.themeMessage = message;
      this.themeMessageError = error;
      this.themeMessageAt = System.nanoTime();
   }

   public void onFilesDropped(List<Path> paths) {
      if (!paths.isEmpty()) {
         if (this.tab == ClickGuiRenderer.Tab.CONFIG) {
            for (Path path : paths) {
               if (path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")) {
                  this.importProfile(path);
                  return;
               }
            }
         }

         for (Path pathx : paths) {
            if (ThemeImages.isImage(pathx)) {
               if (this.tab == ClickGuiRenderer.Tab.HUD) {
                  this.importHudHeaderImage(pathx);
               } else {
                  this.switchTab(ClickGuiRenderer.Tab.THEME);
                  this.importHeaderImage(pathx);
               }

               return;
            }
         }

         if (this.tab == ClickGuiRenderer.Tab.HUD) {
            this.showHudMessage("Only PNG, JPG and GIF images can be used", true);
         } else {
            this.switchTab(ClickGuiRenderer.Tab.THEME);
            this.showThemeMessage("Only PNG, JPG and GIF images can be used", true);
         }
      }
   }

   private void importHudHeaderImage(Path path) {
      String name = ThemeImages.get().importFile(path);
      if (name == null) {
         this.showHudMessage("Only PNG, JPG and GIF images can be used", true);
      } else {
         String targetId = this.hudElementId != null ? this.hudElementId : "info_bar";
         Surface target = HudTheme.get().forElementId(targetId);
         if (target != null) {
            target.image.set(name);
            target.background.set("Image");
            this.selectHudElement(targetId);
            this.showHudMessage(name + " is now the " + target.label.toLowerCase(Locale.ROOT) + " background", false);
         }
      }
   }

   private void importHeaderImage(Path path) {
      String name = ThemeImages.get().importFile(path);
      if (name == null) {
         this.showThemeMessage("Only PNG, JPG and GIF images can be used", true);
      } else {
         ThemeStore theme = ThemeStore.get();
         Surface target = this.themeSurface != null ? this.themeSurface : theme.cards;
         target.image.set(name);
         target.background.set("Image");
         this.showThemeMessage(name + " is now the " + target.label.toLowerCase(Locale.ROOT) + " background", false);
      }
   }

   private void deleteImage(String name) {
      boolean deleted = ThemeImages.get().delete(name);
      String message = deleted ? "Deleted " + name : "Couldn't delete " + name;
      if (this.tab == ClickGuiRenderer.Tab.HUD) {
         this.showHudMessage(message, !deleted);
      } else {
         this.showThemeMessage(message, !deleted);
      }
   }

   private static void drawCheck(float x, float y, float progress, int accent) {
      ImDrawList dl = ImGui.getWindowDrawList();
      dl.addRectFilled(x, y, x + 16.0F, y + 16.0F, GuiPalette.surface(), 4.0F);
      dl.addRect(x + 0.5F, y + 0.5F, x + 16.0F - 0.5F, y + 16.0F - 0.5F, SpectreTheme.lerpColor(452984831, accent, progress), 4.0F);
      if (progress > 0.01F) {
         dl.addLine(x + 4.0F, y + 8.0F, x + 7.0F, y + 11.0F, accent, 2.0F * progress);
         dl.addLine(x + 7.0F, y + 11.0F, x + 12.0F, y + 5.0F, accent, 2.0F * progress);
      }
   }

   static float smooth(float from, float to, float dt, float speed) {
      return from + (to - from) * (1.0F - (float)Math.exp(-speed * Math.max(0.0F, dt)));
   }

   public static float animationDelta(float dt) {
      float speed = GuiPalette.animationSpeed();
      return speed <= 0.0F ? 1.0F : dt * speed;
   }

   public static float elapsed(long since, float durationMs) {
      float speed = GuiPalette.animationSpeed();
      return speed <= 0.0F ? 1.0F : Math.min(1.0F, (float)(System.nanoTime() - since) * speed / (durationMs * 1000000.0F));
   }

   public static float easeOut(float t) {
      float inv = 1.0F - t;
      return 1.0F - inv * inv * inv;
   }

   private void renderKeybindRow(ClientModule module, float pad, float contentW) {
      float rowH = SpectreWidgets.s(46.0F);
      float startY = this.rowStart(pad, contentW);
      boolean isListening = this.listeningModule == module;
      if (isListening) {
         if (this.listeningFirstFrame) {
            this.listeningFirstFrame = false;
         } else {
            int pressed = scanPressedKey();
            if (pressed == 256) {
               module.setKeybind(-1);
               this.listeningModule = null;
               isListening = false;
            } else if (pressed != -1) {
               module.setKeybind(pressed);
               this.listeningModule = null;
               isListening = false;
            }
         }
      }

      rowLabel("Keybind", pad, startY, rowH);
      float chipH = SpectreWidgets.s(30.0F);
      float keyW = SpectreWidgets.s(96.0F);
      float modeW = SpectreWidgets.s(76.0F);
      float gap = SpectreWidgets.s(8.0F);
      float chipY = startY + (rowH - chipH) * 0.5F;
      if (!module.isAction()) {
         ImGui.setCursorPos(pad + contentW - keyW - gap - modeW, chipY);
         if (SpectreWidgets.chipButton("##bindmode", module.isHoldMode() ? "Hold" : "Toggle", modeW, chipH, false)) {
            module.setHoldMode(!module.isHoldMode());
         }
      }

      String label = isListening ? "Press a key..." : (module.keybind() == -1 ? "None" : keyName(module.keybind()));
      ImGui.setCursorPos(pad + contentW - keyW, chipY);
      if (SpectreWidgets.chipButton("##bind", label, keyW, chipH, isListening)) {
         if (isListening) {
            this.listeningModule = null;
         } else {
            this.listeningModule = module;
            this.listeningFirstFrame = true;
            this.listeningKeySetting = null;
            this.listeningKeyFirstFrame = false;
         }
      }

      ImGui.setCursorPos(0.0F, startY + rowH);
   }

   public boolean handleEscape() {
      if (this.listeningKeySetting != null) {
         this.listeningKeySetting.setValue(-1);
         this.listeningKeySetting = null;
         this.listeningKeyFirstFrame = false;
         return true;
      } else if (this.listeningModule != null) {
         this.listeningModule.setKeybind(-1);
         this.listeningModule = null;
         this.listeningFirstFrame = false;
         return true;
      } else if (this.blockLists.closePicker()) {
         return true;
      } else if (this.settingsModule != null) {
         this.closeSettings();
         return true;
      } else {
         return false;
      }
   }

   public static int scanPressedKey() {
      long handle = Minecraft.getInstance().getWindow().handle();

      for (int key = 32; key <= 348; key++) {
         if (GLFW.glfwGetKey(handle, key) == 1) {
            return key;
         }
      }

      for (int button = 0; button <= 7; button++) {
         if (GLFW.glfwGetMouseButton(handle, button) == 1) {
            return 1000 + button;
         }
      }

      return -1;
   }

   private String keyLabel(int key) {
      String label = (String)this.keyLabels.get(key);
      if (label == null) {
         label = keyName(key);
         this.keyLabels.put(key, label);
      }

      return label;
   }

   public static String keyName(int key) {
      if (key >= 1000) {
         int button = key - 1000;

         return switch (button) {
            case 0 -> "LMB";
            case 1 -> "RMB";
            case 2 -> "MMB";
            default -> "MB" + (button + 1);
         };
      } else {
         if (key >= 39 && key <= 96 || key >= 161 && key <= 162) {
            String name = GLFW.glfwGetKeyName(key, -1);
            if (name != null && !name.isBlank()) {
               return name.toUpperCase(Locale.ROOT);
            }
         }
         return switch (key) {
            case 32 -> "SPACE";
            case 256 -> "ESC";
            case 257 -> "ENTER";
            case 258 -> "TAB";
            case 259 -> "BKSP";
            case 260 -> "INS";
            case 261 -> "DEL";
            case 262 -> "RIGHT";
            case 263 -> "LEFT";
            case 264 -> "DOWN";
            case 265 -> "UP";
            case 266 -> "PGUP";
            case 267 -> "PGDN";
            case 268 -> "HOME";
            case 269 -> "END";
            case 290 -> "F1";
            case 291 -> "F2";
            case 292 -> "F3";
            case 293 -> "F4";
            case 294 -> "F5";
            case 295 -> "F6";
            case 296 -> "F7";
            case 297 -> "F8";
            case 298 -> "F9";
            case 299 -> "F10";
            case 300 -> "F11";
            case 301 -> "F12";
            case 335 -> "NUM ENTER";
            case 340 -> "L SHIFT";
            case 341 -> "L CTRL";
            case 342 -> "L ALT";
            case 344 -> "R SHIFT";
            case 345 -> "R CTRL";
            case 346 -> "R ALT";
            default -> "K" + key;
         };
      }
   }

   private void renderModuleConfigActions(ClientModule module, float pad, float contentW) {
      float gap = SpectreWidgets.s(8.0F);
      float h = SpectreWidgets.s(36.0F);
      float buttonW = (contentW - gap) * 0.5F;
      float y = ImGui.getCursorPosY() + SpectreWidgets.s(6.0F);
      ImGui.setCursorPos(pad, y);
      if (SpectreWidgets.chipButton("##copy_config", "Copy Config", buttonW, h, true)) {
         this.copyModuleConfig(module);
      }

      if (ImGui.isItemHovered(65536)) {
         showTooltip("Copy this module's settings as a short code");
      }

      String key = "paste:" + module.id();
      boolean confirming = this.isConfirming(key);
      ImGui.setCursorPos(pad + buttonW + gap, y);
      if (SpectreWidgets.chipButton("##paste_config", confirming ? "Confirm paste" : "Paste Config", buttonW, h, confirming) && this.confirmClick(key)) {
         this.pasteModuleConfig(module);
      }

      if (ImGui.isItemHovered(65536)) {
         showTooltip("Replace this module's settings with a code from your clipboard");
      }

      y += h + SpectreWidgets.s(8.0F);
      ImFont small = ImGuiManager.getInstance().smallFont();
      if (module.id().equals(this.moduleCodeFor) && this.moduleCodeMessage != null && elapsed(this.moduleCodeAt, 2500.0F) < 1.0F) {
         ImGui.setCursorPos(pad, y);
         boolean pushed = pushFont(small);
         ImGui.pushStyleColor(0, this.moduleCodeError ? -10856974 : -4542808);
         ImGui.textUnformatted(this.moduleCodeMessage);
         ImGui.popStyleColor();
         if (pushed) {
            ImGui.popFont();
         }
      }

      ImGui.setCursorPos(0.0F, y + SpectreWidgets.lineHeight(small));
   }

   private void copyModuleConfig(ClientModule module) {
      String code = ModuleCodes.copy(module);
      Minecraft.getInstance().keyboardHandler.setClipboard(code);
      this.setModuleCodeMessage(module, "Copied, " + code.length() + " characters", false);
   }

   private void pasteModuleConfig(ClientModule module) {
      try {
         ModuleCodes.paste(module, Minecraft.getInstance().keyboardHandler.getClipboard());
         this.setModuleCodeMessage(module, "Config pasted", false);
      } catch (ProfileException var3) {
         this.setModuleCodeMessage(module, var3.getMessage(), true);
      }
   }

   private void setModuleCodeMessage(ClientModule module, String message, boolean error) {
      this.moduleCodeFor = module.id();
      this.moduleCodeMessage = message;
      this.moduleCodeError = error;
      this.moduleCodeAt = System.nanoTime();
   }

   private void renderHudActions(float pad, float contentW) {
      float gap = SpectreWidgets.s(8.0F);
      float h = SpectreWidgets.s(36.0F);
      float buttonW = (contentW - gap) * 0.5F;
      float y = ImGui.getCursorPosY() + SpectreWidgets.s(14.0F);
      ImGui.setCursorPos(pad, y);
      if (SpectreWidgets.chipButton("##editlayout", "Edit Layout", buttonW, h, true)) {
         Minecraft.getInstance().gui.setScreen(new HudEditorScreen());
      }

      ImGui.setCursorPos(pad + buttonW + gap, y);
      if (SpectreWidgets.chipButton("##resetlayout", "Reset Layout", buttonW, h, false)) {
         HudLayoutStore.getInstance().resetToDefaults(ImGui.getIO().getDisplaySizeX(), ImGui.getIO().getDisplaySizeY());
      }

      ImGui.setCursorPos(0.0F, y + h);
   }

   private static boolean isHudModule(ClientModule module) {
      return module.category() == ModuleCategory.CLIENT && "HUD".equals(module.name());
   }

   private static ClientModule hudModule() {
      for (ClientModule module : ModuleRegistry.all()) {
         if (isHudModule(module)) {
            return module;
         }
      }

      return null;
   }

   private void renderHudStudio(float dt, float alpha, float view) {
      this.renderHudInspector(dt, alpha);
      this.highlightHudElements();
      float sw = ImGui.getIO().getDisplaySizeX();
      float sh = ImGui.getIO().getDisplaySizeY();
      HudRenderer.getInstance().studioInput(sw, sh, hudStudioBlocked());
   }

   private static boolean hudStudioBlocked() {
      return ImGui.isWindowHovered(4) || ImGui.isAnyItemActive() || ImGui.isPopupOpen("", 3072);
   }

   private void renderHudInspector(float dt, float alpha) {
      float sw = ImGui.getIO().getDisplaySizeX();
      float sh = ImGui.getIO().getDisplaySizeY();
      float w = SpectreWidgets.s(360.0F);
      float h = Math.min(sh - SpectreWidgets.s(86.0F) - SpectreWidgets.s(40.0F), SpectreWidgets.s(760.0F));
      ImGui.setNextWindowPos(sw - w - SpectreWidgets.s(24.0F), SpectreWidgets.s(24.0F), 4);
      ImGui.setNextWindowSize(w, h, 1);
      if (this.focusHudInspector) {
         ImGui.setNextWindowFocus();
         this.focusHudInspector = false;
      }

      ImGui.pushStyleVar(0, alpha);
      ImGui.pushStyleVar(2, 0.0F, 0.0F);
      ImGui.pushStyleVar(14, 0.0F, 0.0F);
      ImGui.pushStyleVar(3, cardRounding());
      ImGui.pushStyleColor(2, GuiPalette.abgr(ThemeStore.get().cards.color.argbWithAlpha(250)));
      int flags = 59;
      if (ImGui.begin("##hud_inspector", flags)) {
         // A position saved at a smaller scale can leave the panel hanging off the screen; pull it back in.
         if (ImGui.getWindowPosX() + w > sw || ImGui.getWindowPosY() + h > sh) {
            ImGui.setWindowPos(Math.max(0F, Math.min(ImGui.getWindowPosX(), sw - w - SpectreWidgets.s(8F))), Math.max(0F, Math.min(ImGui.getWindowPosY(), sh - h - SpectreWidgets.s(8F))));
         }
         float x = ImGui.getWindowPosX();
         float y = ImGui.getWindowPosY();
         drawShadow(x, y, w, h, cardRounding(), 6);
         ImGui.getWindowDrawList().addRect(x, y, x + w, y + h, SpectreWidgets.c(318767103), cardRounding(), 0, 1.0F);
         float pad = SpectreWidgets.s(20.0F);
         float top = SpectreWidgets.s(20.0F);
         drawCardTitle("HUD", pad, top + SpectreWidgets.s(14.0F));
         float ty = top + SpectreWidgets.s(34.0F);
         if (this.hudMessage != null && System.nanoTime() - this.hudMessageAt < 3000000000L) {
            ImGui.setCursorPos(pad, ty);
            ImGui.pushTextWrapPos(w - pad);
            ImGui.pushStyleColor(0, this.hudMessageError ? -10856974 : -461582);
            ImGui.textUnformatted(this.hudMessage);
            ImGui.popStyleColor();
            ImGui.popTextWrapPos();
            ty = ImGui.getCursorPosY();
         } else {
            ty = mutedParagraph("Click an element on screen to edit it.", pad, ty, w - pad);
         }

         ty = this.renderHudElementTabs(pad, ty + SpectreWidgets.s(12.0F), w - pad * 2.0F, dt) + SpectreWidgets.s(14.0F);
         cardDivider(pad, w, ty);
         ImGui.setCursorPos(0.0F, ty + 1.0F);
         if (ImGui.beginChild("##hud_inspector_body", w, Math.max(1.0F, h - ty - 1.0F - SpectreWidgets.s(10.0F)), false, 24)) {
            float contentW = w - pad * 2.0F;
            this.dividerPending = false;
            if (this.hudElementId == null) {
               this.renderHudGeneralOptions(pad, contentW);
            } else {
               this.renderHudElementOptions(this.hudElementId, pad, contentW);
            }

            ImGui.dummy(1.0F, SpectreWidgets.s(18.0F));
            this.applySmoothScroll("hud_inspector", dt);
         }

         ImGui.endChild();
      }

      ImGui.end();
      ImGui.popStyleColor();
      ImGui.popStyleVar(4);
   }

   private float renderHudElementTabs(float pad, float y, float contentW, float dt) {
      @Environment(EnvType.CLIENT)
      record Item(String id, String label) {
      }

      List<Item> items = List.of(
         new Item(null, "General"),
         new Item("info_bar", HudTheme.get().infoBar.label),
         new Item("coordinates", HudTheme.get().coordinates.label),
         new Item("module_list", HudTheme.get().moduleList.label),
         new Item("potion_tracker", HudTheme.get().potions.label),
         new Item("region_map", HudTheme.get().regionMap.label),
         new Item("anti_vanish", HudTheme.get().antiVanish.label),
         new Item("staff_list", HudTheme.get().staffList.label),
         new Item("spotify", HudTheme.get().spotify.label),
         new Item("hotkeys", HudTheme.get().hotkeys.label),
         new Item("radar", HudRenderer.editorLabel("radar")),
         new Item("target_card", HudTheme.get().targetCard.label),
         new Item("armor_status", HudTheme.get().armorStatus.label),
         new Item("item_cooldowns", HudTheme.get().itemCooldowns.label),
         new Item("toggle_toasts", HudTheme.get().toasts.label)
      );
      ImFont small = ImGuiManager.getInstance().smallFont();
      boolean pushed = pushFont(small);
      float chipH = SpectreWidgets.s(24.0F);
      float gap = SpectreWidgets.s(6.0F);
      float rowGap = SpectreWidgets.s(6.0F);
      float curX = pad;
      float curY = y;

      try {
         for (Item item : items) {
            boolean selected = Objects.equals(item.id(), this.hudElementId);
            float chipW = SpectreWidgets.textWidth(small, item.label()) + SpectreWidgets.s(18.0F);
            if (curX + chipW > pad + contentW && curX > pad) {
               curX = pad;
               curY += chipH + rowGap;
            }

            ImGui.setCursorPos(curX, curY);
            if (SpectreWidgets.chipButton("##hudel_" + (item.id() == null ? "general" : item.id()), item.label(), chipW, chipH, selected)) {
               this.selectHudElement(item.id());
            }

            curX += chipW + gap;
         }
      } finally {
         if (pushed) {
            ImGui.popFont();
         }
      }

      return curY + chipH;
   }

   private void renderHudGeneralOptions(float pad, float contentW) {
      ClientModule hud = hudModule();
      if (hud != null) {
         this.sectionLabel("Settings", pad);
         this.settingRows(pad, contentW, hud.settings().toArray(new Setting[0]));
      }

      this.sectionLabel("Style presets", pad);
      this.renderHudPresets(pad, contentW);
      HudTheme hudTheme = HudTheme.get();
      this.settingRows(pad, contentW, hudTheme.style, hudTheme.gradientFrom, hudTheme.gradientTo, hudTheme.glassTint);
      this.sectionLabel("Layout", pad);
      this.settingRows(pad, contentW, HudTheme.get().rounding);
      float y = ImGui.getCursorPosY() + SpectreWidgets.s(6.0F);
      float h = SpectreWidgets.s(34.0F);
      ImGui.setCursorPos(pad, y);
      if (SpectreWidgets.chipButton("##resethudlayout", "Reset positions & sizes", contentW, h, false)) {
         HudLayoutStore.getInstance().resetToDefaults(ImGui.getIO().getDisplaySizeX(), ImGui.getIO().getDisplaySizeY());
         this.showHudMessage("HUD layout reset", false);
      }

      ImGui.setCursorPos(0.0F, y + h);
      this.sectionLabel("Share", pad);
      this.renderHudThemeActions(pad, contentW);
   }

   private void renderHudElementOptions(String id, float pad, float contentW) {
      if (hudModule() instanceof HudModule hud) {
         List<Setting> own = hud.elementSettings(id);
         if (!own.isEmpty()) {
            this.sectionLabel(hudOptionsTitle(id), pad);
            this.settingRows(pad, contentW, own.toArray(new Setting[0]));
         }
      }

      if ("region_map".equals(id)) {
         ClientModule regionMap = ModuleRegistry.get(RegionMapModule.class);
         if (regionMap != null) {
            this.renderInlineModuleToggle(regionMap, pad, contentW);
            this.sectionLabel("Settings", pad);
            this.settingRows(pad, contentW, regionMap.settings().toArray(new Setting[0]));
         }
      }

      ClientModule owner = (ClientModule)("radar".equals(id)
         ? ModuleRegistry.get(RadarModule.class)
         : ("hotkeys".equals(id) ? ModuleRegistry.get(HotkeysModule.class) : null));
      if (owner != null) {
         this.renderInlineModuleToggle(owner, pad, contentW);
         if (!owner.settings().isEmpty()) {
            this.sectionLabel("Settings", pad);
            this.settingRows(pad, contentW, owner.settings().toArray(new Setting[0]));
         }
      }

      Surface surface = HudTheme.get().forElementId(id);
      if (surface != null) {
         this.renderSurfaceOptions(surface, pad, contentW);
      }
   }

   private static String hudOptionsTitle(String id) {
      return switch (id) {
         case "toggle_toasts" -> "Style";
         case "target_card", "armor_status", "item_cooldowns" -> "Options";
         default -> "Show";
      };
   }

   private void renderInlineModuleToggle(ClientModule module, float pad, float contentW) {
      float rowH = SpectreWidgets.s(46.0F);
      float startY = this.rowStart(pad, contentW);
      rowLabel("Active", pad, startY, rowH);
      float swW = SpectreWidgets.s(40.0F);
      float swH = SpectreWidgets.s(22.0F);
      ImGui.setCursorPos(pad + contentW - swW, startY + (rowH - swH) * 0.5F);
      float sx = ImGui.getCursorScreenPosX();
      float sy = ImGui.getCursorScreenPosY();
      if (ImGui.invisibleButton("##active", swW, swH)) {
         module.toggle();
      }

      SpectreWidgets.drawSwitch(ImGui.getWindowDrawList(), sx, sy, swW, swH, SpectreWidgets.animate(ImGui.getItemID(), module.isEnabled(), 16.0F));
      ImGui.setCursorPos(0.0F, startY + rowH);
   }

   private void renderHudThemeActions(float pad, float contentW) {
      HudTheme theme = HudTheme.get();
      float gap = SpectreWidgets.s(8.0F);
      float h = SpectreWidgets.s(34.0F);
      float buttonW = (contentW - gap * 2.0F) / 3.0F;
      float y = ImGui.getCursorPosY() + SpectreWidgets.s(6.0F);
      ImGui.setCursorPos(pad, y);
      if (SpectreWidgets.chipButton("##copyhudtheme", "Copy theme", buttonW, h, false)) {
         Minecraft.getInstance().keyboardHandler.setClipboard(theme.exportString());
         this.showHudMessage("HUD theme copied to the clipboard", false);
      }

      ImGui.setCursorPos(pad + buttonW + gap, y);
      if (SpectreWidgets.chipButton("##pastehudtheme", "Paste theme", buttonW, h, false)) {
         boolean applied = theme.importString(Minecraft.getInstance().keyboardHandler.getClipboard());
         this.showHudMessage(applied ? "HUD theme applied from the clipboard" : "The clipboard doesn't contain an Spectre HUD theme", !applied);
      }

      ImGui.setCursorPos(pad + (buttonW + gap) * 2.0F, y);
      if (SpectreWidgets.chipButton("##resethudtheme", "Reset", buttonW, h, false)) {
         theme.resetToDefaults();
         this.showHudMessage("HUD theme reset to the defaults", false);
      }

      ImGui.setCursorPos(0.0F, y + h);
   }

   private void showHudMessage(String message, boolean error) {
      this.hudMessage = message;
      this.hudMessageError = error;
      this.hudMessageAt = System.nanoTime();
   }

   private void selectHudElement(String id) {
      if (!Objects.equals(id, this.hudElementId)) {
         this.hudElementId = id;
         this.focusHudInspector = true;
      }
   }

   private void highlightHudElements() {
      float mx = ImGui.getIO().getMousePosX();
      float my = ImGui.getIO().getMousePosY();
      boolean dragging = HudRenderer.getInstance().isDragging();
      HudLayoutStore.HudElementState hovered = null;
      if (!dragging && !hudStudioBlocked()) {
         for (HudLayoutStore.HudElementState state : HudLayoutStore.getInstance().orderedElements()) {
            if (state.positioned && contains(new float[]{state.x, state.y, state.width, state.height}, mx, my)) {
               hovered = state;
            }
         }
      }

      ImDrawList fg = ImGui.getForegroundDrawList();
      if (this.hudElementId != null) {
         HudLayoutStore.HudElementState selected = findHudElementState(this.hudElementId);
         if (selected != null) {
            this.outlineHudElement(fg, selected, SpectreWidgets.withAlpha(GuiPalette.accent(), 0.8F));
         }
      }

      if (hovered != null) {
         if (!hovered.id.equals(this.hudElementId)) {
            this.outlineHudElement(fg, hovered, -2130706433);
         }

         Surface surface = HudTheme.get().forElementId(hovered.id);
         String label = surface != null ? surface.label : HudRenderer.editorLabel(hovered.id);
         String hint = label + "  ·  click to edit, drag to move, scroll to resize";
         ImFont small = ImGuiManager.getInstance().smallFont();
         float chipW = SpectreWidgets.textWidth(small, hint) + SpectreWidgets.s(18.0F);
         float chipH = SpectreWidgets.lineHeight(small) + SpectreWidgets.s(10.0F);
         float cx = Math.min(mx + SpectreWidgets.s(14.0F), ImGui.getIO().getDisplaySizeX() - chipW - SpectreWidgets.s(4.0F));
         float cy = Math.min(my + SpectreWidgets.s(18.0F), ImGui.getIO().getDisplaySizeY() - chipH - SpectreWidgets.s(4.0F));
         fg.addRectFilled(cx, cy, cx + chipW, cy + chipH, GuiPalette.accent(), chipH * 0.5F);
         SpectreWidgets.text(fg, small, cx + SpectreWidgets.s(9.0F), cy + SpectreWidgets.s(5.0F), -1, hint);
         if (ImGui.isMouseClicked(0) || ImGui.isMouseClicked(1)) {
            this.selectHudElement(hovered.id);
         }
      }
   }

   private void outlineHudElement(ImDrawList fg, HudLayoutStore.HudElementState state, int color) {
      float e = SpectreWidgets.s(3.0F);
      float rounding = HudRenderer.hudRounding() * HudRenderer.scaleOf(state);
      fg.addRect(state.x - e, state.y - e, state.x + state.width + e, state.y + state.height + e, color, rounding + e, 0, SpectreWidgets.s(1.5F));
   }

   private static HudLayoutStore.HudElementState findHudElementState(String id) {
      for (HudLayoutStore.HudElementState state : HudLayoutStore.getInstance().orderedElements()) {
         if (state.id.equals(id)) {
            return state;
         }
      }

      return null;
   }

   private void openColorPicker(ClientModule module, ColorSetting setting) {
      this.activeColorModule = module;
      this.activeColorSetting = setting;
      this.colorPickerOpen.set(true);
   }

   private void renderColorPickerWindow() {
      if (this.tab == Tab.THEME && this.activeColorModule == null) return;
      if (this.activeColorSetting != null) {
         if (!this.colorPickerOpen.get()) {
            this.activeColorSetting = null;
            this.activeColorModule = null;
         } else {
            ImGui.setNextWindowSize(270.0F, 330.0F, 4);
            ImGui.setNextWindowPos(260.0F, 90.0F, 4);
            int flags = 352;
            String owner = this.activeColorModule != null ? this.activeColorModule.name() + " / " : "";
            if (ImGui.begin("Color - " + owner + this.activeColorSetting.name() + "##color_picker", this.colorPickerOpen, flags)) {
               int pickerFlags = 135593984;
               if (ImGui.colorPicker4("##picker", this.activeColorSetting.value(), pickerFlags)) {
                  this.activeColorSetting.notifyChanged();
               }

               ImGui.setNextItemWidth(240.0F);
               if (ImGui.colorEdit4("RGBA", this.activeColorSetting.value(), pickerFlags)) {
                  this.activeColorSetting.notifyChanged();
               }

               if (ImGui.button("Close", 80.0F, 22.0F)) {
                  this.activeColorSetting.notifyChanged();
                  this.colorPickerOpen.set(false);
               }
            }

            ImGui.end();
            if (!this.colorPickerOpen.get()) {
               this.activeColorSetting = null;
               this.activeColorModule = null;
            }
         }
      }
   }

   private void renderItemColorPaletteWindow() {
      if (this.itemColorWindowOpen && this.activeItemColorPalette != null) {
         float dt = Math.max(ImGui.getIO().getDeltaTime(), 0.001F);
         ImGui.setNextWindowSize(340.0F, 500.0F, 4);
         ImGui.setNextWindowPos(340.0F, 100.0F, 4);
         int wFlags = 288;
         boolean open = ImGui.begin("Item Colors##icpalette", wFlags);
         if (!open) {
            ImGui.end();
            if (this.activeItemColorPalette != null) {
               this.activeItemColorPalette.notifyChanged();
            }

            this.itemColorWindowOpen = false;
         } else {
            ImGui.text("Default:");
            ImGui.sameLine();
            int defFlags = 131648;
            if (ImGui.colorButton("##icdefcol", this.activeItemColorPalette.defaultColor(), defFlags, 42.0F, 16.0F)) {
               this.editingItemDefaultColor = true;
               this.activeItemColorEntry = null;
               this.itemColorPickerOpen.set(true);
            }

            ImGui.separator();
            ImGui.text("Search:");
            ImGui.sameLine();
            ImGui.setNextItemWidth(200.0F);
            ImGui.inputText("##icsearch", this.itemSearchBuf);
            String search = this.itemSearchBuf.get().toLowerCase().trim();
            ImGui.separator();
            ImGui.beginChild("##iclist", 0.0F, 360.0F, false, 0);

            for (ItemColorListSetting.ItemEntry entry : this.activeItemColorPalette.entries()) {
               boolean matches = search.isEmpty() || entry.displayName().toLowerCase().contains(search) || entry.id().toLowerCase().contains(search);
               if (matches) {
                  float rowY = ImGui.getCursorPosY();
                  float entryH = 22.0F;
                  float availW = ImGui.getContentRegionAvailX();
                  ImGui.setCursorPos(4.0F, rowY + (entryH - 16.0F) * 0.5F);
                  ImGui.pushID(entry.id() + "_ictog");
                  this.renderItemEntryToggle(this.activeItemColorPalette, entry, dt);
                  ImGui.popID();
                  float textH2 = ImGui.getTextLineHeight();
                  ImGui.setCursorPos(24.0F, rowY + (entryH - textH2) * 0.5F);
                  ImGui.textUnformatted(entry.displayName());
                  float swatchX = availW - 46.0F;
                  if (swatchX < 96.0F) {
                     swatchX = 96.0F;
                  }

                  ImGui.setCursorPos(swatchX, rowY + (entryH - 16.0F) * 0.5F);
                  int csFlags = 131648;
                  ImGui.pushID(entry.id() + "_iccol");
                  if (ImGui.colorButton("##ic", entry.color(), csFlags, 42.0F, 16.0F)) {
                     this.activeItemColorEntry = entry;
                     this.editingItemDefaultColor = false;
                     this.itemColorPickerOpen.set(true);
                  }

                  ImGui.popID();
                  ImGui.setCursorPosY(rowY + entryH + 2.0F);
               }
            }

            ImGui.endChild();
            ImGui.separator();
            if (ImGui.button("Close##icclose", 80.0F, 22.0F)) {
               this.activeItemColorPalette.notifyChanged();
               this.itemColorWindowOpen = false;
               this.activeItemColorPalette = null;
            }

            ImGui.end();
            this.renderItemColorPicker();
         }
      }
   }

   private void renderItemColorPicker() {
      if (!this.itemColorPickerOpen.get()) {
         this.activeItemColorEntry = null;
         this.editingItemDefaultColor = false;
      } else if (this.activeItemColorEntry == null && !this.editingItemDefaultColor) {
         this.itemColorPickerOpen.set(false);
      } else {
         float[] target;
         String label;
         if (this.editingItemDefaultColor && this.activeItemColorPalette != null) {
            target = this.activeItemColorPalette.defaultColor();
            label = "Default Color";
         } else {
            target = this.activeItemColorEntry.color();
            label = this.activeItemColorEntry.displayName();
         }

         ImGui.setNextWindowSize(270.0F, 330.0F, 4);
         ImGui.setNextWindowPos(700.0F, 100.0F, 4);
         int wFlags = 352;
         if (ImGui.begin("Item Color - " + label, this.itemColorPickerOpen, wFlags)) {
            int pickerFlags = 135593984;
            ImGui.colorPicker4("##icpicker", target, pickerFlags);
            ImGui.setNextItemWidth(240.0F);
            if (ImGui.colorEdit4("RGBA##icce", target, pickerFlags) && this.activeItemColorPalette != null) {
               this.activeItemColorPalette.notifyChanged();
            }

            if (ImGui.button("Close##iccp", 80.0F, 22.0F)) {
               if (this.activeItemColorPalette != null) {
                  this.activeItemColorPalette.notifyChanged();
               }

               this.itemColorPickerOpen.set(false);
               this.activeItemColorEntry = null;
               this.editingItemDefaultColor = false;
            }
         }

         ImGui.end();
         if (!this.itemColorPickerOpen.get()) {
            this.activeItemColorEntry = null;
            this.editingItemDefaultColor = false;
         }
      }
   }

   private void renderItemEntryToggle(ItemColorListSetting palette, ItemColorListSetting.ItemEntry entry, float dt) {
      float tw = 16.0F;
      float th = 16.0F;
      float sx = ImGui.getCursorScreenPosX();
      float sy = ImGui.getCursorScreenPosY();
      if (ImGui.invisibleButton("##ietog", 16.0F, 16.0F)) {
         entry.enabled().set(!entry.enabled().get());
         palette.notifyChanged();
      }

      float target = entry.enabled().get() ? 1.0F : 0.0F;
      float current = this.itemEntryTogAnim.getOrDefault(entry, target);
      current = smooth(current, target, dt, 14.0F);
      this.itemEntryTogAnim.put(entry, current);
      drawCheck(sx, sy, current, GuiPalette.accent());
   }

   /** Draws the pending on/off settings as a grid of toggle buttons, then empties the list. Right-click resets one. */
   private void renderToggleGrid(List<BooleanSetting> toggles, float pad, float contentW) {
      if (toggles.isEmpty()) return;
      int columns = contentW >= SpectreWidgets.s(440F) ? 3 : 2;
      float gap = SpectreWidgets.s(6F), chipH = SpectreWidgets.s(28F);
      float chipW = (contentW - gap * (columns - 1)) / columns;
      float top = ImGui.getCursorPosY() + SpectreWidgets.s(4F);
      ImFont font = ImGui.getFont();
      for (int i = 0; i < toggles.size(); i++) {
         BooleanSetting bs = toggles.get(i);
         float localX = pad + (i % columns) * (chipW + gap), localY = top + (i / columns) * (chipH + gap);
         ImGui.setCursorPos(localX, localY);
         ImGui.pushID(bs.id());
         float sx = ImGui.getCursorScreenPosX(), sy = ImGui.getCursorScreenPosY();
         if (SpectreWidgets.chipButton("##tg", SpectreWidgets.ellipsize(font, bs.name(), chipW - SpectreWidgets.s(18F)), chipW, chipH, bs.get())) bs.set(!bs.get());
         if (ImGui.isItemHovered()) {
            showTooltip(bs.name() + (bs.isDefault() ? "" : "\nModified - right-click to reset"));
            if (ImGui.isMouseClicked(1)) bs.resetToDefault();
         }
         if (!bs.isDefault()) ImGui.getWindowDrawList().addCircleFilled(sx + SpectreWidgets.s(7F), sy + chipH * 0.5F, SpectreWidgets.s(2.5F), SpectreWidgets.c(bs.get() ? -1 : GuiPalette.accent()), 10);
         ImGui.popID();
      }
      int rows = (toggles.size() + columns - 1) / columns;
      ImGui.setCursorPos(0, top + rows * (chipH + gap) + SpectreWidgets.s(2F));
      toggles.clear();
      this.dividerPending = false;
   }

   private void renderSetting(ClientModule module, Setting setting, float pad, float contentW) {
      float rowH = SpectreWidgets.s(this.compactRows ? 32.0F : 46.0F);
      float startY = this.rowStart(pad, contentW);
      ImDrawList dl = ImGui.getWindowDrawList();
      if (setting instanceof NumberSetting ns && this.compactRows) {
         // One row: "Name  value" on the left, slider on the right.
         rowH = SpectreWidgets.s(30.0F);
         float labelW = contentW * 0.45F;
         String label = setting.name() + "  " + ns.formatted();
         ImGui.setCursorPos(pad, startY + (rowH - ImGui.getTextLineHeight()) * 0.5F);
         ImGui.textUnformatted(SpectreWidgets.ellipsize(ImGui.getFont(), label, labelW - SpectreWidgets.s(8.0F)));
         if (ImGui.isItemHovered()) showTooltip(label);
         ImGui.setCursorPos(pad + labelW - SpectreWidgets.s(7.0F), startY + (rowH - SpectreWidgets.s(18.0F)) * 0.5F);
         if (SpectreWidgets.sliderFloat("##v", ns.value(), ns.min(), ns.max(), contentW - labelW + SpectreWidgets.s(14.0F), ns::snap)) {
            ns.notifyChanged();
         }
      } else if (setting instanceof BooleanSetting bs) {
         ImGui.setCursorPos(pad, startY);
         float sx = ImGui.getCursorScreenPosX();
         float sy = ImGui.getCursorScreenPosY();
         if (ImGui.invisibleButton("##row", contentW, rowH)) {
            bs.set(!bs.get());
         }

         float hover = SpectreWidgets.animate(ImGui.getItemID(), ImGui.isItemHovered(), 18.0F);
         if (hover > 0.01F) {
            dl.addRectFilled(
               sx - SpectreWidgets.s(10.0F),
               sy + SpectreWidgets.s(3.0F),
               sx + contentW + SpectreWidgets.s(10.0F),
               sy + rowH - SpectreWidgets.s(3.0F),
               SpectreWidgets.c(SpectreWidgets.withAlpha(184549375, hover)),
               SpectreWidgets.s(10.0F)
            );
         }

         rowLabel(setting.name(), pad, startY, rowH);
         float swW = SpectreWidgets.s(40.0F);
         float swH = SpectreWidgets.s(22.0F);
         SpectreWidgets.drawSwitch(dl, sx + contentW - swW, sy + (rowH - swH) * 0.5F, swW, swH, SpectreWidgets.animate(ImGui.getID("##switch"), bs.get(), 16.0F));
      } else if (setting instanceof NumberSetting ns) {
         rowH = SpectreWidgets.s(64.0F);
         ImGui.setCursorPos(pad, startY + SpectreWidgets.s(10.0F));
         ImGui.textUnformatted(setting.name());
         ImFont small = ImGuiManager.getInstance().smallFont();
         String value = ns.formatted();
         float tw = SpectreWidgets.textWidth(small, value);
         float pillW = tw + SpectreWidgets.s(16.0F);
         float pillH = SpectreWidgets.lineHeight(small) + SpectreWidgets.s(8.0F);
         float wx = ImGui.getWindowPosX();
         float wy = ImGui.getWindowPosY() - ImGui.getScrollY();
         float px = wx + pad + contentW - pillW;
         float py = wy + startY + SpectreWidgets.s(8.0F);
         dl.addRectFilled(px, py, px + pillW, py + pillH, SpectreWidgets.c(GuiPalette.surface()), pillH * 0.5F);
         SpectreWidgets.text(dl, small, px + SpectreWidgets.s(8.0F), py + SpectreWidgets.s(4.0F), -461582, value);
         ImGui.setCursorPos(pad - SpectreWidgets.s(7.0F), startY + SpectreWidgets.s(36.0F));
         if (SpectreWidgets.sliderFloat("##v", ns.value(), ns.min(), ns.max(), contentW + SpectreWidgets.s(14.0F), ns::snap)) {
            ns.notifyChanged();
         }
      } else if (setting instanceof ModeSetting ms) {
         rowLabel(setting.name(), pad, startY, rowH);
         float widest = 0.0F;

         for (String mode : ms.modes()) {
            widest = Math.max(widest, ImGui.calcTextSize(mode).x);
         }

         float dropW = Math.min(contentW * 0.6F, Math.max(SpectreWidgets.s(120.0F), widest + SpectreWidgets.s(44.0F)));
         float dropH = SpectreWidgets.s(30.0F);
         ImGui.setCursorPos(pad + contentW - dropW, startY + (rowH - dropH) * 0.5F);
         int picked = SpectreWidgets.dropdown("##mode", ms.get(), ms.modes(), dropW, dropH);
         if (picked >= 0) {
            ms.set(ms.modes().get(picked));
         }
      } else if (setting instanceof ColorSetting cs) {
         rowLabel(setting.name(), pad, startY, rowH);
         float swW = SpectreWidgets.s(44.0F);
         float swH = SpectreWidgets.s(24.0F);
         ImGui.setCursorPos(pad + contentW - swW, startY + (rowH - swH) * 0.5F);
         float sxx = ImGui.getCursorScreenPosX();
         float syx = ImGui.getCursorScreenPosY();
         if (ImGui.invisibleButton("##color", swW, swH)) {
            this.openColorPicker(module, cs);
         }

         boolean hovered = ImGui.isItemHovered();
         float[] val = cs.value();
         int r = (int)(val[0] * 255.0F) & 0xFF;
         int g = (int)(val[1] * 255.0F) & 0xFF;
         int b = (int)(val[2] * 255.0F) & 0xFF;
         int a = val.length > 3 ? (int)(val[3] * 255.0F) & 0xFF : 255;
         dl.addRectFilled(sxx, syx, sxx + swW, syx + swH, SpectreWidgets.c(GuiPalette.surface()), SpectreWidgets.s(7.0F));
         dl.addRectFilled(sxx, syx, sxx + swW, syx + swH, SpectreWidgets.c(a << 24 | b << 16 | g << 8 | r), SpectreWidgets.s(7.0F));
         dl.addRect(sxx, syx, sxx + swW, syx + swH, SpectreWidgets.c(hovered ? 1728053247 : 654311423), SpectreWidgets.s(7.0F), 0, 1.0F);
         if (hovered) {
            showTooltip(String.format("#%02X%02X%02X", r, g, b));
         }
      } else if (setting instanceof KeySetting ks) {
         boolean isListening = this.listeningKeySetting == ks;
         if (isListening) {
            if (this.listeningKeyFirstFrame) {
               this.listeningKeyFirstFrame = false;
            } else {
               int pressed = scanPressedKey();
               if (pressed == 256) {
                  ks.setValue(-1);
                  this.listeningKeySetting = null;
                  isListening = false;
               } else if (pressed != -1) {
                  ks.setValue(pressed);
                  this.listeningKeySetting = null;
                  isListening = false;
               }
            }
         }

         rowLabel(setting.name(), pad, startY, rowH);
         float keyW = SpectreWidgets.s(96.0F);
         float chipH = SpectreWidgets.s(30.0F);
         String keyLabel = isListening ? "Press a key..." : (ks.value() == -1 ? "None" : keyName(ks.value()));
         ImGui.setCursorPos(pad + contentW - keyW, startY + (rowH - chipH) * 0.5F);
         if (SpectreWidgets.chipButton("##keyval", keyLabel, keyW, chipH, isListening)) {
            if (isListening) {
               this.listeningKeySetting = null;
            } else {
               this.listeningKeySetting = ks;
               this.listeningKeyFirstFrame = true;
               this.listeningModule = null;
               this.listeningFirstFrame = false;
            }
         }
      } else if (setting instanceof StringSetting ss) {
         ImGui.setCursorPos(pad, startY + SpectreWidgets.s(10.0F));
         ImGui.textUnformatted(setting.name());
         ImGui.setCursorPos(pad, startY + SpectreWidgets.s(34.0F));
         ImGui.pushStyleVar(11, SpectreWidgets.s(12.0F), SpectreWidgets.s(8.0F));
         ImGui.setNextItemWidth(contentW);
         if (ImGui.inputText("##str", ss.getImString())) {
            ss.notifyChanged();
         }

         ImGui.popStyleVar();
         rowH = SpectreWidgets.s(34.0F) + ImGui.getTextLineHeight() + SpectreWidgets.s(16.0F) + SpectreWidgets.s(12.0F);
      } else if (setting instanceof ActionSetting actionSetting) {
         float h = SpectreWidgets.s(36.0F);
         ImGui.setCursorPos(pad, startY + (rowH + SpectreWidgets.s(6.0F) - h) * 0.5F);
         if (SpectreWidgets.chipButton("##action", setting.name(), contentW, h, false)) {
            actionSetting.run();
         }

         rowH += SpectreWidgets.s(6.0F);
      } else if (setting instanceof BlockColorListSetting bcls) {
         rowH = this.blockLists.render(module, bcls, pad, contentW, startY);
      } else if (setting instanceof ItemColorListSetting icls) {
         long enabled = icls.entries().stream().filter(en -> en.enabled().get()).count();
         if (listChip(setting.name(), "Edit (" + enabled + ")", pad, contentW, startY, rowH)) {
            this.activeItemColorPalette = icls;
            this.itemColorWindowOpen = true;
            this.activeItemColorEntry = null;
            this.editingItemDefaultColor = false;
         }
      }

      if (!(setting instanceof ActionSetting)) {
         this.renderResetAffordance(setting, pad, contentW, startY, rowH);
      }

      ImGui.setCursorPos(0.0F, startY + rowH);
   }

   private void renderResetAffordance(Setting setting, float pad, float contentW, float startY, float rowH) {
      if (this.tab != ClickGuiRenderer.Tab.THEME) {
         boolean modified = !setting.isDefault();
         float wx = ImGui.getWindowPosX();
         float wy = ImGui.getWindowPosY() - ImGui.getScrollY();
         boolean topLabel = setting instanceof NumberSetting || setting instanceof StringSetting || setting instanceof BlockColorListSetting;
         float labelCy = topLabel ? startY + SpectreWidgets.s(10.0F) + ImGui.getTextLineHeight() * 0.5F : startY + rowH * 0.5F;
         if (modified) {
            float dx = wx + pad - SpectreWidgets.s(11.0F);
            float dy = wy + labelCy;
            float r = SpectreWidgets.s(3.0F);
            ImGui.getWindowDrawList().addCircleFilled(dx, dy, r, SpectreWidgets.c(GuiPalette.accent()), 12);
            if (ImGui.isMouseHoveringRect(dx - r * 2.0F, dy - r * 2.0F, dx + r * 2.0F, dy + r * 2.0F)) {
               showTooltip("Modified - right-click the row to reset");
            }
         }

         float x0 = wx + pad - SpectreWidgets.s(10.0F);
         float y0 = wy + startY;
         if (ImGui.isWindowHovered() && ImGui.isMouseClicked(1) && ImGui.isMouseHoveringRect(x0, y0, x0 + contentW + SpectreWidgets.s(20.0F), y0 + rowH)) {
            ImGui.openPopup("##setting_ctx");
         }

         ImGui.pushStyleVar(2, SpectreWidgets.s(6.0F), SpectreWidgets.s(6.0F));
         if (ImGui.beginPopup("##setting_ctx")) {
            ImGui.textDisabled(setting.name());
            ImGui.separator();
            if (modified) {
               if (ImGui.selectable("Reset to default")) {
                  setting.resetToDefault();
               }
            } else {
               ImGui.textDisabled("Already at default");
            }

            ImGui.endPopup();
         }

         ImGui.popStyleVar();
      }
   }

   private static boolean listChip(String label, String chip, float pad, float contentW, float startY, float rowH) {
      rowLabel(label, pad, startY, rowH);
      float chipW = Math.max(SpectreWidgets.s(96.0F), ImGui.calcTextSize(chip).x + SpectreWidgets.s(28.0F));
      float chipH = SpectreWidgets.s(30.0F);
      ImGui.setCursorPos(pad + contentW - chipW, startY + (rowH - chipH) * 0.5F);
      return SpectreWidgets.chipButton("##listedit", chip, chipW, chipH, false);
   }

   public static void applyTheme() {
      ImGui.pushStyleVar(3, SpectreWidgets.s(10.0F));
      ImGui.pushStyleVar(12, SpectreWidgets.s(8.0F));
      ImGui.pushStyleVar(9, SpectreWidgets.s(10.0F));
      ImGui.pushStyleVar(21, SpectreWidgets.s(6.0F));
      ImGui.pushStyleVar(2, SpectreWidgets.s(10.0F), SpectreWidgets.s(10.0F));
      ImGui.pushStyleVar(11, SpectreWidgets.s(10.0F), SpectreWidgets.s(6.0F));
      ImGui.pushStyleVar(14, SpectreWidgets.s(8.0F), SpectreWidgets.s(6.0F));
      ImGui.pushStyleVar(4, 0.0F);
      ImGui.pushStyleVar(10, 1.0F);
      ImGui.pushStyleVar(18, SpectreWidgets.s(6.0F));
      ImGui.pushStyleVar(19, SpectreWidgets.s(3.0F));

      for (int i = 0; i < THEME_COLORS.length; i += 2) {
         ImGui.pushStyleColor(THEME_COLORS[i], GuiPalette.neutral(THEME_COLORS[i + 1]));
      }

      int accent = GuiPalette.accent();
      int card = GuiPalette.cardBg();
      ImGui.pushStyleColor(2, GuiPalette.panelBg());
      ImGui.pushStyleColor(10, card);
      ImGui.pushStyleColor(11, card);
      ImGui.pushStyleColor(12, card);
      ImGui.pushStyleColor(18, accent);
      ImGui.pushStyleColor(19, accent);
      ImGui.pushStyleColor(20, accent);
      ImGui.pushStyleColor(23, accent);
      ImGui.pushStyleColor(26, accent);
      ImGui.pushStyleColor(51, SpectreWidgets.withAlpha(accent, 0.4F));
   }

   public static void removeTheme() {
      ImGui.popStyleColor(THEME_COLORS.length / 2 + 10);
      ImGui.popStyleVar(11);
   }

   public static boolean pushFont(ImFont font) {
      if (font == null) {
         return false;
      } else {
         ImGui.pushFont(font);
         return true;
      }
   }

   @Environment(EnvType.CLIENT)
   private record CategoryPanel(ModuleCategory category, String id, String rowsId) {
   }

   @Environment(EnvType.CLIENT)
   private static enum Tab {
      MODULES("Modules"),
      HUD("HUD"),
      FRIENDS("Friends"),
      THEME("Theme"),
      CONFIG("Config");

      final String label;

      private Tab(String label) {
         this.label = label;
      }
   }
}
