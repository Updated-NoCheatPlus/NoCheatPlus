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

import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.reflect.StructureModifier;

import fr.neatmonster.nocheatplus.compat.registry.IBukkitAccess;

import java.util.Set;

public class BridgeBukkitAPIModern implements IBukkitAccess{
    public boolean matchSounds(PacketContainer packetContainer, Set<String> effectNames) {
        final StructureModifier<Sound> sounds = packetContainer.getSoundEffects();
        final Sound sound = sounds.read(0);
        return effectNames.contains(sound.toString());
    }
    
    public Inventory getTopInventory(Player p) {
        return p.getOpenInventory().getTopInventory();
    }
    
    public Inventory getTopInventory(InventoryClickEvent event) {
        return getInventoryView(event).getTopInventory();
    }
    
    public Inventory getBottomInventory(InventoryClickEvent event) {
        return getInventoryView(event).getBottomInventory();
    }
    
    public InventoryView getInventoryView(InventoryClickEvent event) {
        return event.getView();
    }
    
    public String getInventoryTitle(InventoryClickEvent event) {
        return getInventoryView(event).getTitle();
    }
    
    public boolean hasInventoryOpenOwnExcluded(final Player player) {
        final InventoryView view = player.getOpenInventory();
        return view != null && view.getType() != InventoryType.CRAFTING && view.getType() != InventoryType.CREATIVE; // Exclude the CRAFTING and CREATIVE inv type.
    }
    
    public AttributeInstance getSpeedAttributeInstance(final Player player) {
        return player.getAttribute(Attribute.MOVEMENT_SPEED);
    }
    
    public AttributeInstance getGravityAttributeInstance(final Player player) {
        return player.getAttribute(Attribute.GRAVITY);
    }
    
    public AttributeInstance getSafeFallAttributeInstance(final Player player) {
        return player.getAttribute(Attribute.SAFE_FALL_DISTANCE);
    }
    
    public AttributeInstance getFallMultAttributeInstance(final Player player) {
        return player.getAttribute(Attribute.FALL_DAMAGE_MULTIPLIER);
    }
    
    public AttributeInstance getBreakSpeedAttributeInstance(final Player player) {
        return player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
    }
    
    public AttributeInstance getJumpPowerAttributeInstance(final Player player) {
        return player.getAttribute(Attribute.JUMP_STRENGTH);
    }
    
    public AttributeInstance getBlockInteractionRangeAttributeInstance(final Player player) {
        return player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
    }
    
    public AttributeInstance getEntityInteractionRangeAttributeInstance(final Player player) {
        return player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
    }
    
    public AttributeInstance getStepHeightAttributeInstance(final Player player) {
        return player.getAttribute(Attribute.STEP_HEIGHT);
    }
    
    public AttributeInstance getScaleAttributeInstance(final Player player) {
        return player.getAttribute(Attribute.SCALE);
    }
}
