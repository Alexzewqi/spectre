package net.spectre.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ColorSetting;
import net.spectre.client.module.setting.ModeSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.spectre.client.module.setting.Setting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class ThemeStore {
   private static final Logger LOGGER = LoggerFactory.getLogger("Spectre");
   private static final ThemeStore INSTANCE = new ThemeStore();
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final int CONFIG_VERSION = 2;
   private static final String THEME_KEY = "spectreTheme";
   public static final List<ThemeStore.Preset> PRESETS = List.of(
      new ThemeStore.Preset("Default", 0xFF56DCCB, 0xFF182230, 0xFF101823),
      new ThemeStore.Preset("Liquid Glass", 0xFF8FD3FF, 0xFF1A2230, 0xFF141A24, "Liquid Glass", 0xFFE3B8FF),
      new ThemeStore.Preset("Glass Dark", 0xFFB9C6FF, 0xFF0B0D12, 0xFF08090D, "Liquid Glass", 0xFF7FE7FF),
      new ThemeStore.Preset("Aurora", 0xFF7B5CFF, 0xFF141228, 0xFF0E0C1E, "Gradient", 0xFF2EE6C8),
      new ThemeStore.Preset("Sunset", 0xFFFF5F7E, 0xFF221218, 0xFF190C12, "Gradient", 0xFFFFB25C),
      new ThemeStore.Preset("Ocean", 0xFF2C9AFF, 0xFF0E1A2A, 0xFF0A1220, "Gradient", 0xFF34E0D0),
      new ThemeStore.Preset("Neon", 0xFFFF2E97, 0xFF170D1E, 0xFF110916, "Gradient", 0xFF3CD8FF),
      new ThemeStore.Preset("Emerald", 0xFF2FD47F, 0xFF0E1C16, 0xFF0A1510, "Gradient", 0xFFB6F35A),
      new ThemeStore.Preset("Crimson", 0xFFFF3B4E, 0xFF1E0E11, 0xFF16090C),
      new ThemeStore.Preset("Violet", 0xFFC21CE0, 0xFF1E0F26, 0xFF170B1D),
      new ThemeStore.Preset("Halloween", 0xFFFF7A1A, 0xFF1C1022, 0xFF150C1A),
      new ThemeStore.Preset("Midnight",-7643914, -15001050, -15461603),
      new ThemeStore.Preset("Rose", -770210, -14410720, -15002344),
      new ThemeStore.Preset("Mint", -15681151, -15064288, -15459560),
      new ThemeStore.Preset("Amber", -680437, -14409445, -15001324),
      new ThemeStore.Preset("Mono", -1710101, -14671840, -15263977)
   );
   public static final String STYLE_SOLID = "Solid", STYLE_GRADIENT = "Gradient", STYLE_GLASS = "Liquid Glass";
   public final ColorSetting accent = new ColorSetting("Accent", 0xFF56DCCB);
   /** Second accent: the far end of gradient themes. */
   public final ColorSetting accent2 = new ColorSetting("Second accent", 0xFF7B6CFF);
   public final ModeSetting guiStyle = new ModeSetting("GUI style", STYLE_SOLID, STYLE_SOLID, STYLE_GRADIENT, STYLE_GLASS);
   public final NumberSetting rounding = new NumberSetting("Corner radius", 10.0F, 0.0F, 16.0F).step(1.0F);
   public final NumberSetting backgroundDim = new NumberSetting("Background dim", 0.25F, 0.0F, 0.8F).step(0.05F);
   public final BooleanSetting menuBlur = new BooleanSetting("Menu blur", true);
   public final NumberSetting blurStrength = new NumberSetting("Blur strength", 7.0F, 1.0F, 10.0F).step(1.0F);
   public final NumberSetting uiScale = new NumberSetting("UI scale", 1.0F, 0.75F, 1.5F).step(0.05F);
   public final NumberSetting animationSpeed = new NumberSetting("Animation speed", 1.0F, 0.0F, 2.0F).step(0.1F);
   public final BooleanSetting animateLines = new BooleanSetting("Animate patterns", true);
   public final BooleanSetting customMainMenu = new BooleanSetting("Custom menus", true);
   public final BooleanSetting customVanillaUi = new BooleanSetting("Custom vanilla UI", true);
   public final BooleanSetting donutNamePrivacy = new BooleanSetting("Scramble my name on Donut", true);
   public final BooleanSetting uiSounds = new BooleanSetting("UI sounds", true);
   public final BooleanSetting hoverSounds = new BooleanSetting("Hover sounds", false);
   /** Uses the system's Segoe UI for the GUI when it is installed; the bundled Geist otherwise. */
   public final BooleanSetting systemFont = new BooleanSetting("Arial font", true);
   public final Surface panels = new Surface("panels", "Panels", false, 0xFF182230, 0.92F, 0.45F);
   public final Surface cards = new Surface("cards", "Cards", true, 0xFF101823, 0.97F, 0.85F);
   public final Surface tabBar = new Surface("tabbar", "Tab bar", false, 0xFF101823, 0.97F, 0.5F);
   private final List<Surface> surfaces = List.of(this.panels, this.cards, this.tabBar);
   private final List<Setting> shared;
   private final List<Setting> settings;
   private final JsonObject defaults;
   private boolean loaded;
   private boolean readOnly;

   private ThemeStore() {
      List<Setting> all = new ArrayList<>(
         List.of(this.accent, this.accent2, this.guiStyle, this.rounding, this.backgroundDim, this.menuBlur, this.blurStrength, this.uiScale, this.animationSpeed, this.animateLines)
      );

      for (Surface surface : this.surfaces) {
         all.addAll(surface.settings());
      }

      this.shared = List.copyOf(all);
      all.add(this.customMainMenu);
      all.add(this.customVanillaUi);
      all.add(this.donutNamePrivacy);
      all.add(this.uiSounds);
      all.add(this.hoverSounds);
      all.add(this.systemFont);
      this.settings = List.copyOf(all);
      this.defaults = snapshot(this.settings, true);
      this.customMainMenu.onChange(() -> {
         Minecraft mc = Minecraft.getInstance();
         if (mc != null && mc.getWindow() != null) {
            mc.updateTitle();
         }
      });
   }

   public static ThemeStore get() {
      INSTANCE.ensureLoaded();
      return INSTANCE;
   }

   public List<Surface> surfaces() {
      return this.surfaces;
   }

   public void applyPreset(ThemeStore.Preset preset) {
      this.accent.setArgb(preset.accent());
      this.accent2.setArgb(preset.accent2());
      this.guiStyle.set(preset.style());
      this.panels.color.setArgb(preset.panel());
      this.cards.color.setArgb(preset.card());
      this.tabBar.color.setArgb(preset.card());
      // Keep the HUD in the same family as the GUI.
      HudTheme hud = HudTheme.get();
      String hudStyle = switch (preset.style()) {
         case STYLE_GLASS -> "Liquid Glass";
         case STYLE_GRADIENT -> "Gradient";
         default -> null;
      };
      HudTheme.PRESETS.stream().filter(p -> p.name().equals(hudStyle == null ? "Classic" : hudStyle.equals("Gradient") ? "Aurora" : preset.name().contains("Dark") ? "Glass Dark" : "Liquid Glass"))
         .findFirst().ifPresent(hud::applyPreset);
      if (STYLE_GRADIENT.equals(preset.style())) {
         hud.gradientFrom.setArgb(preset.accent());
         hud.gradientTo.setArgb(preset.accent2());
         hud.save();
      }
   }

   public boolean matchesPreset(ThemeStore.Preset preset) {
      return this.accent.rgb() == (preset.accent() & 16777215)
         && this.guiStyle.is(preset.style())
         && this.panels.color.rgb() == (preset.panel() & 16777215)
         && this.cards.color.rgb() == (preset.card() & 16777215);
   }

   public boolean glass() {
      return this.guiStyle.is(STYLE_GLASS);
   }

   public boolean gradient() {
      return this.guiStyle.is(STYLE_GRADIENT);
   }

   public void resetToDefaults() {
      apply(this.shared, this.defaults, true);
   }

   public static Path backgroundsDir() {
      return FabricLoader.getInstance().getConfigDir().resolve("spectre").resolve("backgrounds");
   }

   public String exportString() {
      JsonObject root = new JsonObject();
      root.add("spectreTheme", snapshot(this.shared, false));
      return root.toString();
   }

   public boolean importString(String text) {
      if (text != null && !text.isBlank()) {
         try {
            JsonElement parsed = JsonParser.parseString(text.trim());
            if (parsed.isJsonObject() && parsed.getAsJsonObject().has("spectreTheme")) {
               JsonElement values = parsed.getAsJsonObject().get("spectreTheme");
               if (!values.isJsonObject()) {
                  return false;
               } else {
                  apply(this.shared, values.getAsJsonObject(), false);
                  return true;
               }
            } else {
               return false;
            }
         } catch (RuntimeException var4) {
            return false;
         }
      } else {
         return false;
      }
   }

   public JsonObject profileSnapshot() {
      return snapshot(this.shared, true);
   }

   public void applyProfile(JsonObject values) {
      for (Setting setting : this.shared) {
         JsonElement value = values.get(setting.id());
         if (value == null && !setting.isPrivate()) {
            value = this.defaults.get(setting.id());
         }

         if (value != null) {
            setting.deserialize(value);
         }
      }
   }

   public JsonObject shareable(JsonObject values) {
      JsonObject kept = new JsonObject();

      for (Setting setting : this.shared) {
         JsonElement value = values.get(setting.id());
         if (value != null && !setting.isPrivate() && !Setting.sameValue(value, this.defaults.get(setting.id()))) {
            kept.add(setting.id(), value.deepCopy());
         }
      }

      return kept;
   }

   public synchronized void load() {
      if (!this.loaded) {
         this.loaded = true;
         Path path = this.configPath();
         if (Files.isRegularFile(path)) {
            try {
               try (Reader reader = Files.newBufferedReader(path)) {
                  JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                  int version = root.has("version") ? root.get("version").getAsInt() : 0;
                  if (version <= 2) {
                     if (root.has("values") && root.get("values").isJsonObject()) {
                        apply(this.settings, root.getAsJsonObject("values"), true);
                     }

                     return;
                  }

                  this.readOnly = true;
                  LOGGER.warn("[Spectre] Theme version {} is newer than supported {}; skipping load", version, 2);
               }
            } catch (RuntimeException | IOException var7) {
               LOGGER.warn("[Spectre] Failed to load theme; preserving the file", var7);
               this.readOnly = true;
            }
         }
      }
   }

   public synchronized void save() {
      if (!this.readOnly && this.loaded) {
         JsonObject root = new JsonObject();
         root.addProperty("version", 2);
         root.add("values", snapshot(this.settings, true));
         Path path = this.configPath();

         try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");

            try (Writer writer = Files.newBufferedWriter(tmp)) {
               GSON.toJson(root, writer);
            }

            try {
               Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException var8) {
               Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
         } catch (IOException var10) {
            LOGGER.warn("[Spectre] Failed to save theme", var10);
         }
      }
   }

   private static JsonObject snapshot(List<Setting> from, boolean withPrivate) {
      JsonObject values = new JsonObject();

      for (Setting setting : from) {
         if (withPrivate || !setting.isPrivate()) {
            values.add(setting.id(), setting.serialize());
         }
      }

      return values;
   }

   private static void apply(List<Setting> from, JsonObject values, boolean withPrivate) {
      for (Setting setting : from) {
         if ((withPrivate || !setting.isPrivate()) && values.has(setting.id())) {
            setting.deserialize(values.get(setting.id()));
         }
      }
   }

   private void ensureLoaded() {
      if (!this.loaded) {
         this.load();
      }
   }

   private Path configPath() {
      return FabricLoader.getInstance().getConfigDir().resolve("spectre").resolve("theme.json");
   }

   @Environment(EnvType.CLIENT)
   /** A GUI theme: colours plus a surface style; gradient and glass themes also restyle the HUD to match. */
   public record Preset(String name, int accent, int panel, int card, String style, int accent2) {
      public Preset(String name, int accent, int panel, int card) {
         this(name, accent, panel, card, STYLE_SOLID, accent);
      }
   }
}
