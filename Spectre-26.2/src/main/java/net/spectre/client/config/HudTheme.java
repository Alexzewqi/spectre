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
import net.spectre.client.module.setting.ColorSetting;
import net.spectre.client.module.setting.ModeSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.spectre.client.module.setting.Setting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class HudTheme {
   private static final Logger LOGGER = LoggerFactory.getLogger("Spectre");
   private static final HudTheme INSTANCE = new HudTheme();
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final int CONFIG_VERSION = 1;
   private static final String THEME_KEY = "spectreHudTheme";
   private static final int DEFAULT_COLOR = -15329251;
   private static final float DEFAULT_OPACITY = 0.85F;
   private static final float DEFAULT_IMAGE_OPACITY = 0.4F;
   private static final float ROWS_OPACITY = 0.6F;
   public final NumberSetting rounding = new NumberSetting("Corner radius", 8.0F, 0.0F, 16.0F).step(1.0F);
   public static final String STYLE_FLAT = "Flat", STYLE_GRADIENT = "Gradient", STYLE_GLASS = "Liquid Glass";
   /** How every HUD card is filled: the surface colour, a two-colour gradient, or frosted liquid glass. */
   public final ModeSetting style = new ModeSetting("Card style", STYLE_FLAT, STYLE_FLAT, STYLE_GRADIENT, STYLE_GLASS);
   public final ColorSetting gradientFrom = new ColorSetting("Gradient start", 0xFF7B2FF7);
   public final ColorSetting gradientTo = new ColorSetting("Gradient end", 0xFF00C6FF);
   public final NumberSetting glassTint = new NumberSetting("Glass frost", 0.16F, 0.04F, 0.4F).step(0.01F);

   /** A ready-made look: card style, colours, opacity and corner radius applied to every HUD surface. */
   public record Preset(String name, String style, int color, float opacity, int from, int to, float frost, float radius) { }
   public static final List<Preset> PRESETS = List.of(
      new Preset("Classic", STYLE_FLAT, -15329251, 0.85F, 0xFF7B2FF7, 0xFF00C6FF, 0.16F, 8),
      new Preset("Liquid Glass", STYLE_GLASS, 0xFF1A1D24, 0.35F, 0xFF7B2FF7, 0xFF00C6FF, 0.16F, 14),
      new Preset("Glass Dark", STYLE_GLASS, 0xFF07080B, 0.6F, 0xFF7B2FF7, 0xFF00C6FF, 0.08F, 14),
      new Preset("Aurora", STYLE_GRADIENT, -15329251, 0.82F, 0xFF7B2FF7, 0xFF00C6FF, 0.16F, 10),
      new Preset("Sunset", STYLE_GRADIENT, -15329251, 0.85F, 0xFFFF5F6D, 0xFFFFC371, 0.16F, 10),
      new Preset("Ocean", STYLE_GRADIENT, -15329251, 0.82F, 0xFF0F2027, 0xFF2C9AB7, 0.16F, 10),
      new Preset("Neon", STYLE_GRADIENT, -15329251, 0.85F, 0xFFF72585, 0xFF4CC9F0, 0.16F, 6),
      new Preset("Mint", STYLE_GRADIENT, -15329251, 0.8F, 0xFF11998E, 0xFF38EF7D, 0.16F, 10)
   );

   public void applyPreset(Preset preset) {
      this.style.set(preset.style());
      this.gradientFrom.setArgb(preset.from());
      this.gradientTo.setArgb(preset.to());
      this.glassTint.set(preset.frost());
      this.rounding.set(preset.radius());
      for (Surface surface : this.surfaces) {
         surface.color.setArgb(preset.color());
         surface.opacity.set(preset.opacity());
         surface.background.set(Surface.BG_NONE);
      }
      this.save();
   }

   public boolean matchesPreset(Preset preset) {
      return this.style.is(preset.style()) && this.infoBar.color.rgb() == (preset.color() & 0xFFFFFF)
         && (!STYLE_GRADIENT.equals(preset.style()) || this.gradientFrom.rgb() == (preset.from() & 0xFFFFFF) && this.gradientTo.rgb() == (preset.to() & 0xFFFFFF))
         && Math.abs(this.infoBar.opacity.get() - preset.opacity()) < 0.005F;
   }
   public final Surface infoBar = surface("hud_info_bar", "Info Bar");
   public final Surface coordinates = surface("hud_coordinates", "Coordinates", 0.6F);
   public final Surface moduleList = surface("hud_module_list", "Module List", 0.6F);
   public final Surface potions = surface("hud_potions", "Potions", 0.6F);
   public final Surface regionMap = surface("hud_region_map", "Region Map");
   public final Surface antiVanish = surface("hud_anti_vanish", "Anti Vanish", 0.6F);
   public final Surface staffList = surface("hud_staff_list", "Staff List", 0.6F);
   public final Surface spotify = surface("hud_spotify", "Media");
   public final Surface hotkeys = surface("hud_hotkeys", "Hotkeys", 0.6F);
   public final Surface radar = surface("hud_radar", "Radar", 0.6F);
   public final Surface toasts = surface("hud_toasts", "Toggle Toasts");
   public final Surface targetCard = surface("hud_target_card", "Target HUD");
   public final Surface armorStatus = surface("hud_armor_status", "Armor HUD", 0.6F);
   public final Surface itemCooldowns = surface("hud_item_cooldowns", "Cooldown", 0.6F);
   private final List<Surface> surfaces = List.of(
      this.infoBar,
      this.coordinates,
      this.moduleList,
      this.potions,
      this.regionMap,
      this.antiVanish,
      this.staffList,
      this.spotify,
      this.hotkeys,
      this.radar,
      this.toasts,
      this.targetCard,
      this.armorStatus,
      this.itemCooldowns
   );
   private final List<Setting> settings;
   private final JsonObject defaults;
   private boolean loaded;
   private boolean readOnly;

   private HudTheme() {
      this.style.bindId("hud_style.card_style");
      this.gradientFrom.bindId("hud_style.gradient_start");
      this.gradientTo.bindId("hud_style.gradient_end");
      this.glassTint.bindId("hud_style.glass_frost");
      this.gradientFrom.visibleWhen(() -> this.style.is(STYLE_GRADIENT));
      this.gradientTo.visibleWhen(() -> this.style.is(STYLE_GRADIENT));
      this.glassTint.visibleWhen(() -> this.style.is(STYLE_GLASS));
      List<Setting> all = new ArrayList<>(List.of(this.rounding, this.style, this.gradientFrom, this.gradientTo, this.glassTint));

      for (Surface surface : this.surfaces) {
         all.addAll(surface.settings());
      }

      this.settings = List.copyOf(all);
      this.defaults = this.snapshot(true);
   }

   private static Surface surface(String key, String label) {
      return surface(key, label, 0.85F);
   }

   private static Surface surface(String key, String label, float opacity) {
      return new Surface(key, label, false, -15329251, opacity, 0.4F);
   }

   public static HudTheme get() {
      INSTANCE.ensureLoaded();
      return INSTANCE;
   }

   public List<Surface> surfaces() {
      return this.surfaces;
   }

   public Surface forElementId(String id) {
      if ("info_bar".equals(id)) {
         return this.infoBar;
      } else if ("coordinates".equals(id)) {
         return this.coordinates;
      } else if ("module_list".equals(id)) {
         return this.moduleList;
      } else if ("potion_tracker".equals(id)) {
         return this.potions;
      } else if ("region_map".equals(id)) {
         return this.regionMap;
      } else if ("anti_vanish".equals(id)) {
         return this.antiVanish;
      } else if ("staff_list".equals(id)) {
         return this.staffList;
      } else if ("spotify".equals(id)) {
         return this.spotify;
      } else if ("hotkeys".equals(id)) {
         return this.hotkeys;
      } else if ("radar".equals(id)) {
         return this.radar;
      } else if ("toggle_toasts".equals(id)) {
         return this.toasts;
      } else if ("target_card".equals(id)) {
         return this.targetCard;
      } else if ("armor_status".equals(id)) {
         return this.armorStatus;
      } else {
         return "item_cooldowns".equals(id) ? this.itemCooldowns : null;
      }
   }

   public void resetToDefaults() {
      this.apply(this.defaults, true);
   }

   public String exportString() {
      JsonObject root = new JsonObject();
      root.add("spectreHudTheme", this.snapshot(false));
      return root.toString();
   }

   public boolean importString(String text) {
      if (text != null && !text.isBlank()) {
         try {
            JsonElement parsed = JsonParser.parseString(text.trim());
            if (parsed.isJsonObject() && parsed.getAsJsonObject().has("spectreHudTheme")) {
               JsonElement values = parsed.getAsJsonObject().get("spectreHudTheme");
               if (!values.isJsonObject()) {
                  return false;
               } else {
                  this.apply(values.getAsJsonObject(), false);
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
      return this.snapshot(true);
   }

   public void applyProfile(JsonObject values) {
      for (Setting setting : this.settings) {
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

      for (Setting setting : this.settings) {
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
                  if (version <= 1) {
                     if (root.has("values") && root.get("values").isJsonObject()) {
                        this.apply(root.getAsJsonObject("values"), true);
                     }

                     return;
                  }

                  this.readOnly = true;
                  LOGGER.warn("[Spectre] HUD theme version {} is newer than supported {}; skipping load", version, 1);
               }
            } catch (RuntimeException | IOException var7) {
               LOGGER.warn("[Spectre] Failed to load HUD theme; preserving the file", var7);
               this.readOnly = true;
            }
         }
      }
   }

   public synchronized void save() {
      if (!this.readOnly && this.loaded) {
         JsonObject root = new JsonObject();
         root.addProperty("version", 1);
         root.add("values", this.snapshot(true));
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
            LOGGER.warn("[Spectre] Failed to save HUD theme", var10);
         }
      }
   }

   private JsonObject snapshot(boolean withPrivate) {
      JsonObject values = new JsonObject();

      for (Setting setting : this.settings) {
         if (withPrivate || !setting.isPrivate()) {
            values.add(setting.id(), setting.serialize());
         }
      }

      return values;
   }

   private void apply(JsonObject values, boolean withPrivate) {
      for (Setting setting : this.settings) {
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
      return FabricLoader.getInstance().getConfigDir().resolve("spectre").resolve("hud_theme.json");
   }
}
