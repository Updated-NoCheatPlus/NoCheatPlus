package fr.neatmonster.nocheatplus.compat.registry;

import java.util.Set;

import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

import com.comphenix.protocol.events.PacketContainer;

public interface IBukkitAccess {
    public boolean matchSounds(PacketContainer packetContainer, Set<String> effectNames);
    public Inventory getTopInventory(Player p);
    public Inventory getTopInventory(InventoryClickEvent event);
    public Inventory getBottomInventory(InventoryClickEvent event);
    public InventoryView getInventoryView(InventoryClickEvent event);
    public String getInventoryTitle(InventoryClickEvent event);
    public boolean hasInventoryOpenOwnExcluded(final Player player);
    public AttributeInstance getSpeedAttributeInstance(final Player player);
    public AttributeInstance getGravityAttributeInstance(final Player player);
    public AttributeInstance getSafeFallAttributeInstance(final Player player);
    public AttributeInstance getFallMultAttributeInstance(final Player player);
    public AttributeInstance getBreakSpeedAttributeInstance(final Player player);
    public AttributeInstance getJumpPowerAttributeInstance(final Player player);
    public AttributeInstance getBlockInteractionRangeAttributeInstance(final Player player);
    public AttributeInstance getEntityInteractionRangeAttributeInstance(final Player player);
    public AttributeInstance getStepHeightAttributeInstance(final Player player);
    public AttributeInstance getScaleAttributeInstance(final Player player);
}
