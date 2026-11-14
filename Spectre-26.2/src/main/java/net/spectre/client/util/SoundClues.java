package net.spectre.client.util;

import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.sounds.SoundSource;

/**
 * Sorts the sounds a server sends into groups and rates how strongly each one points at a player.
 * Shared by Sound ESP (what to draw) and Echo Finder (how much a sound counts as evidence).
 */
@Environment(EnvType.CLIENT)
public final class SoundClues {
   public enum Group {
      STORAGE("Storage"), REDSTONE("Redstone"), BUILDING("Building"), WORKSTATION("Workstations"), PLAYER("Players"), MOB("Mobs"), OTHER("Other");

      public final String label;

      Group(String label) {
         this.label = label;
      }
   }

   private static final String[] STORAGE = {"chest", "barrel", "shulker_box", "decorated_pot", "vault"};
   private static final String[] REDSTONE = {
      "piston", "dispenser", "dropper", "lever", "button", "pressure_plate", "tripwire", "comparator", "note_block", "copper_bulb", "crafter", "door",
      "fence_gate", "bell"
   };
   private static final String[] WORKSTATION = {
      "anvil", "furnace", "smoker", "brewing_stand", "enchantment_table", "smithing_table", "grindstone", "composter", "beacon", "respawn_anchor",
      "stonecutter", "loom", "cartography_table", "lodestone", "end_portal_frame"
   };

   private SoundClues() {
   }

   /** The group a sound falls in, from its channel and its id path (for example {@code block.chest.open}). */
   public static Group group(SoundSource source, String path) {
      if (path.startsWith("block.")) {
         if (containsAny(path, STORAGE)) {
            return Group.STORAGE;
         }

         if (containsAny(path, REDSTONE)) {
            return Group.REDSTONE;
         }

         if (containsAny(path, WORKSTATION)) {
            return Group.WORKSTATION;
         }

         if (path.endsWith(".place") || path.endsWith(".break") || path.endsWith(".hit")) {
            return Group.BUILDING;
         }

         return Group.OTHER;
      }

      if (path.startsWith("entity.player.") || source == SoundSource.PLAYERS) {
         return Group.PLAYER;
      }

      if (source == SoundSource.HOSTILE || source == SoundSource.NEUTRAL) {
         return Group.MOB;
      }

      return Group.OTHER;
   }

   /** Evidence points for a base finder: zero for sounds the world makes with nobody around. */
   public static int weight(Group group) {
      return switch (group) {
         case STORAGE -> 4;
         case WORKSTATION -> 3;
         case REDSTONE, BUILDING, PLAYER -> 2;
         case MOB, OTHER -> 0;
      };
   }

   /** {@code block.chest.open} becomes "Chest open"; {@code entity.player.burp} becomes "Player burp". */
   public static String label(String path) {
      String text = path;
      int dot = text.indexOf('.');
      if (dot > 0 && (text.startsWith("block.") || text.startsWith("entity.") || text.startsWith("item."))) {
         text = text.substring(dot + 1);
      }

      text = text.replace('.', ' ').replace('_', ' ').trim();
      return text.isEmpty() ? path : text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
   }

   private static boolean containsAny(String path, String[] words) {
      for (String word : words) {
         if (path.contains(word)) {
            return true;
         }
      }

      return false;
   }
}
