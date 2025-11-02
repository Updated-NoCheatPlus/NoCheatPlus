/*
 * This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package fr.neatmonster.nocheatplus.compat.bukkit;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.Sound;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.utility.MinecraftReflection;
import com.comphenix.protocol.utility.MinecraftVersion;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Set;

//import fr.neatmonster.nocheatplus.compat.versions.ServerVersion;

public class BridgeBukkitAPI {
    private static final boolean isServerModern = MinecraftVersion.getCurrentVersion().isAtLeast(MinecraftVersion.AQUATIC_UPDATE);
    public static String getNamedSound(PacketContainer packet) {
        String soundName = "";
        Class<?> soundEffectCls = MinecraftReflection.getSoundEffectClass();
        //if (soundEffectCls == null) {
        //    return soundName;
        //}

        // Read raw SoundEffect (no converters)
        Object soundEffect = packet.getModifier().withType(soundEffectCls).readSafely(0);
        if (soundEffect == null) {
            return soundName;
        }
        
        try {
            Field fieldResourceLocation = soundEffect.getClass().getDeclaredField("b");
            fieldResourceLocation.setAccessible(true);
            Object resourceLocation = fieldResourceLocation.get(soundEffect);

            Field soundSuffixField = resourceLocation.getClass().getDeclaredField("b");
            soundSuffixField.setAccessible(true);
            soundName = (String) soundSuffixField.get(resourceLocation);
        } catch (Exception e) {
            return soundName;
        }

        return soundName.replace('.', '_').toUpperCase(Locale.ROOT);
    }
    public static boolean matchSounds(PacketContainer packetContainer, Set<String> effectNames) {
        if (isServerModern) {
            final StructureModifier<Sound> sounds = packetContainer.getSoundEffects();
            final Sound sound = sounds.read(0);
            return effectNames.contains(sound.toString());
        }
        return effectNames.contains(getNamedSound(packetContainer));
    }
    
    public static Inventory getTopInventory(Player p) {
        return p.getOpenInventory().getTopInventory();
    }
    
    public static boolean hasInventoryOpenOwnExcluded(final Player player) {
        final InventoryView view = player.getOpenInventory();
        return view != null && view.getType() != InventoryType.CRAFTING && view.getType() != InventoryType.CREATIVE; // Exclude the CRAFTING and CREATIVE inv type.
    }
    
    public static AttributeInstance getSpeedAttributeInstance(final Player player) {
        return player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
    }
    
    public static AttributeInstance getGravityAttributeInstance(final Player player) {
        //if (isServerLowerThan1_20_5) return null;
        return player.getAttribute(Attribute.GENERIC_GRAVITY);
    }
    
    public static AttributeInstance getSafeFallAttributeInstance(final Player player) {
        //if (isServerLowerThan1_20_5) return null;
        return player.getAttribute(Attribute.GENERIC_SAFE_FALL_DISTANCE);
    }
    
    public static AttributeInstance getFallMultAttributeInstance(final Player player) {
        //if (isServerLowerThan1_20_5) return null;
        return player.getAttribute(Attribute.GENERIC_FALL_DAMAGE_MULTIPLIER);
    }
    
    public static AttributeInstance getBreakSpeedAttributeInstance(final Player player) {
        //if (isServerLowerThan1_20_5) return null;
        return player.getAttribute(Attribute.PLAYER_BLOCK_BREAK_SPEED);
    }
    
    public static AttributeInstance getJumpPowerAttributeInstance(final Player player) {
        //if (isServerLowerThan1_20_5) return null;
        return player.getAttribute(Attribute.GENERIC_JUMP_STRENGTH);
    }
    
    public static AttributeInstance getBlockInteractionRangeAttributeInstance(final Player player) {
        //if (isServerLowerThan1_20_5) return null;
        return player.getAttribute(Attribute.PLAYER_BLOCK_INTERACTION_RANGE);
    }
    
    public static AttributeInstance getEntityInteractionRangeAttributeInstance(final Player player) {
        //if (isServerLowerThan1_20_5) return null;
        return player.getAttribute(Attribute.PLAYER_BLOCK_INTERACTION_RANGE);
    }
    
    public static AttributeInstance getStepHeightAttributeInstance(final Player player) {
        //if (isServerLowerThan1_20_5) return null;
        return player.getAttribute(Attribute.GENERIC_STEP_HEIGHT);
    }
    
    public static AttributeInstance getScaleAttributeInstance(final Player player) {
        //if (isServerLowerThan1_20_5) return null;
        return player.getAttribute(Attribute.GENERIC_SCALE);
    }
}
