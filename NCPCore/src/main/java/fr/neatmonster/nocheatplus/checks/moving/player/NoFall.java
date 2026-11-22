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
package fr.neatmonster.nocheatplus.checks.moving.player;

import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.type.PointedDripstone;
import org.bukkit.block.data.type.TurtleEgg;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.Vector;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.model.LocationData;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveData;
import fr.neatmonster.nocheatplus.compat.*;
import fr.neatmonster.nocheatplus.compat.bukkit.BridgeEnchant;
import fr.neatmonster.nocheatplus.compat.bukkit.BridgeHealth;
import fr.neatmonster.nocheatplus.compat.bukkit.BridgeMaterial;
import fr.neatmonster.nocheatplus.compat.versions.ClientVersion;
import fr.neatmonster.nocheatplus.compat.versions.ServerVersion;
import fr.neatmonster.nocheatplus.components.modifier.IAttributeAccess;
import fr.neatmonster.nocheatplus.components.registry.activation.Activation;
import fr.neatmonster.nocheatplus.components.registry.event.IGenericInstanceHandle;
import fr.neatmonster.nocheatplus.components.registry.feature.TickListener;
import fr.neatmonster.nocheatplus.logging.StaticLog;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.ReflectionUtil;
import fr.neatmonster.nocheatplus.utilities.TickTask;
import fr.neatmonster.nocheatplus.utilities.location.PlayerLocation;
import fr.neatmonster.nocheatplus.utilities.map.BlockFlags;
import fr.neatmonster.nocheatplus.utilities.map.MaterialUtil;
import fr.neatmonster.nocheatplus.utilities.math.MathUtil;



/**
 * A check to see if people cheat by tricking the server to not deal them fall damage.
 */
public class NoFall extends Check {

    /*
     * NOTE: Due to farmland/soil not converting back to dirt with the current
     * implementation: Implement packet sync with moving events. Then alter
     * packet on-ground and mc fall distance for a new default concept. As a
     * fall back either the old method, or an adaption with scheduled/later fall
     * damage dealing could be considered, detecting the actual cheat with a
     * slight delay. Packet sync will need a better tracking than the last n
     * packets, e.g. include the latest/oldest significant packet for (...) and
     * if a packet has already been related to a Bukkit event.
     */

    /** For temporary use: LocUtil.clone before passing deeply, call setWorld(null) after use. */
    private final Location useLoc = new Location(null, 0, 0, 0);
    private final Location useLoc2 = new Location(null, 0, 0, 0);
    private final boolean protocolLibPresent = new Activation().pluginExist("ProtocolLib").isAvailable();
    private static final IGenericInstanceHandle<IAttributeAccess> attributeAccess = NCPAPIProvider.getNoCheatPlusAPI().getGenericInstanceHandle(IAttributeAccess.class);
    // If the server don't deal fall damage, so do NCP
    private final static boolean fallDistChange = ServerVersion.isAtLeast("1.21.11");
    private final static boolean attributeAvailable = ServerVersion.isAtLeast("1.20.5");
    private final static boolean hayChange = ServerVersion.isAtLeast("1.9");
    private final static boolean bedChange = ServerVersion.isAtLeast("1.12");
    
    /**
     * Instantiates a new no fall check.
     */
    public NoFall() {
        super(CheckType.MOVING_NOFALL);
    }


    public static double calculateFallPower(final double fallDistance, Player player) {
        return fallDistance + (fallDistChange ? 1.0E-6D : 0D) - attributeAccess.getHandle().getSafeFallDistance(player);
    }
    
    /**
     * Calculate the raw damage in hearts from the given fall distance.
     *
     * @param fallDistance
     * @param player
     * @return
     */
    public static double getBaseDamage(final double fallDistance, Player player) {
        return calculateFallPower(fallDistance, player);
    }
    
    /**
     * Ceil or Floor the damage base on server version
     *
     * @param fallDamage
     * @return
     */
    public static double getFloorOrCeilDamage(final double fallDamage) {
        return fallDistChange ? MathUtil.floor(fallDamage) : MathUtil.ceil(fallDamage);
    }
    
    /**
     * Calculate the damage in hearts from the given fall distance.
     *
     * @param fallDistance
     * @param baseMultiply The multiplier from blocks
     * @param player
     * @return
     */
    public static double getDamage(final double fallDistance, final float baseMultiply, Player player) {
        final double baseResult = calculateFallPower(fallDistance, player) * baseMultiply * attributeAccess.getHandle().getFallDamageMultiplier(player);
        return fallDistChange ? MathUtil.floor(baseResult) : MathUtil.ceil(baseResult);
    }


    /**
     * Deal damage if appropriate. To be used for if the player is on ground
     * somehow. Contains checking for skipping conditions (getAllowFlight set +
     * configured to skip).
     * 
     * @param player
     * @param y
     * @param previousSetBackY
     *            The set back y from lift-off. If not present:
     *            Double.NEGATIVE_INFINITY.
     * @param pLoc current location
     * @param reallyOnGround if this function was fire as violation
     * @param data MovingData
     * @param cc MovingConfig
     * @param pData generic PlayerData
     */
    private void handleOnGround(final Player player, final double y, final double previousSetBackY,
                                final PlayerLocation pLoc,
                                final boolean reallyOnGround, final MovingData data, final MovingConfig cc,
                                final IPlayerData pData) {
        // Get the fall distance and modify it accordingly to the fallen on block (currently only related to pointed dripstone)
        double fallDist = getAndRunFallDistanceDependentTasks(player, y, previousSetBackY, pLoc, data);
        // Calculate damage and apply all possible modifiers.
        double maxDamage = getDamage(fallDist, applyBlockDamageMultiplier(player, pLoc, data, pData), player);
        maxDamage = applyFeatherFalling(player, maxDamage, mcAccess.getHandle().dealFallDamageFiresAnEvent().decide());
        if (maxDamage > 0.0) {
            // Check skipping conditions.
            if (cc.noFallSkipAllowFlight && player.getAllowFlight()) {
                data.clearNoFallData();
                data.noFallSkipAirCheck = true;
                // Not resetting the fall distance here, let Minecraft or the issue tracker deal with that.
            }
            else {
                if (pData.isDebugActive(type)) {
                    debug(player, "NoFall deal damage" + (reallyOnGround ? "" : "violation") + ": " + maxDamage);
                }
                // TODO: might not be necessary: if (mcPlayer.invulnerableTicks <= 0)  [no damage event for resetting]
                // TODO: Detect fake fall distance accumulation here as well.
                data.noFallSkipAirCheck = true;
                dealFallDamage(player, maxDamage);
            }
        }
        else {
            data.clearNoFallData();
            player.setFallDistance(0);
        }
    }

    /**
     * Get the applicable fall-distance for the given data and run some tasks related to fall-distance specifically. <br>
     * (I.e.: altering the block the player fell on (farmland, turtle eggs) or modify the fall-distance (stalagmites))
     * @param pLoc 
     */
    private double getAndRunFallDistanceDependentTasks(final Player player, double y, double previousSetBackY, PlayerLocation pLoc, final MovingData data) {
        // Base fall-distance
        double fallDist = getApplicableFallHeight(player, y, previousSetBackY, pLoc, data);
        Block block = player.getWorld().getBlockAt(pLoc.getBlockX(), pLoc.getBlockY(), pLoc.getBlockZ());
        final IPlayerData pData = DataManager.getPlayerData(player);
        // Falling on farmland has a chance of trampling it (wasn't always the case. On legacy versions, just walking on crops would have been enough to trample them :) )
        if (block.getType() == BridgeMaterial.FARMLAND && fallDist > 0.5 && ThreadLocalRandom.current().nextFloat() < fallDist - 0.5) {
            final BlockState newState = block.getState();
            newState.setType(Material.DIRT);
            if (canChangeBlock(player, block, newState, true, true, true)) {
                // Move up a little bit in order to not get stuck in the block
                player.setVelocity(new Vector(player.getVelocity().getX() * -1, 0.062501, player.getVelocity().getZ() * -1));
                block.setType(Material.DIRT);
                if (pData.isDebugActive(type)) {
                    debug(player, "Apply block-state change workaround for FARMLAND.");
                }
            }
            useLoc2.setWorld(null);
            return fallDist;
        }
        // 1.13+: Falling on turtle eggs has a chance of breaking them.
        if (Bridge1_13.hasIsSwimming() && block.getType() == Material.TURTLE_EGG && ThreadLocalRandom.current().nextInt(3) == 0) {
            final TurtleEgg egg = (TurtleEgg) block.getBlockData();
            final BlockState newState = block.getState();
            if (canChangeBlock(player, block, newState, true, false, false)) {
                if (egg.getEggs() - 1 > 0) {
                    egg.setEggs(egg.getEggs() - 1);
                } 
                else block.setType(Material.AIR); // What about Cave air? (i.e.: with eggs being inside a cave)
                if (pData.isDebugActive(type)) {
                    debug(player, "Apply block-state change workaround for TURTLE_EGG.");
                }
            }
            useLoc2.setWorld(null);
            return fallDist;
        }
        // TODO: Might want to clean up NoFall to only track for ground state and override as mention above 
        // (save performance, less code but packet dependent and precise ground state requirement)
        // or still maintain it
        // (lot of tracking stuffs for impulse, change blocks on land, damage recalculation -> degraded efficiency but packet independent
        if (fallDist - attributeAccess.getHandle().getSafeFallDistance(player) > 0.0 && data.noFallCurrentLocOnWindChargeHit != null) {
            final double lastImpulseY = data.noFallCurrentLocOnWindChargeHit.getY();
            data.clearWindChargeImpulse();
            fallDist = (lastImpulseY < y ? 0.0 : lastImpulseY - y);
        }
        useLoc2.setWorld(null);
        return fallDist;
    }
    

    /**
     * Artificially fire some events to see if other plugins allow to change the state of the block we want to modify.
     * 
     * @param player
     * @param block
     * @param newState The BlockState of the new block
     * @param interact If to call a PlayerInteractEvent
     * @param entityChangeBlock If to call an EntityChangeBlockEvent
     * @param fade If to call a BlockFadeEvent
     * 
     * @return True, if the state of this block can be changed (no other plugin put a veto)
     */
    @SuppressWarnings("deprecation")
    private boolean canChangeBlock(final Player player, final Block block, final BlockState newState, final boolean interact, final boolean entityChangeBlock, final boolean fade) {
        if (interact) {
            final PlayerInteractEvent interactEvent = new PlayerInteractEvent(player, Action.PHYSICAL, null, block, BlockFace.SELF);
            Bukkit.getPluginManager().callEvent(interactEvent);
            if (interactEvent.isCancelled()) {
                // Denied by some plugin.
                return false;
            }
        }
        if (entityChangeBlock) {
            if (!Bridge1_13.hasIsSwimming()) {
                // 1.6.4-1.12.2 backward compatibility
                Object o = ReflectionUtil.newInstance(ReflectionUtil.getConstructor(EntityChangeBlockEvent.class, Entity.class, Block.class, Material.class, byte.class), player, block, Material.DIRT, (byte)0);
                if (o instanceof EntityChangeBlockEvent) {
                    EntityChangeBlockEvent event = (EntityChangeBlockEvent)o;
                    Bukkit.getPluginManager().callEvent(event);
                    if (event.isCancelled()) {
                         // Denied by some plugin.
                         return false;
                    }
                }
            } 
            else {
                final EntityChangeBlockEvent blockEvent = new EntityChangeBlockEvent(player, block, newState.getBlockData());
                Bukkit.getPluginManager().callEvent(blockEvent);
                if (blockEvent.isCancelled()) {
                    // Denied by some plugin.
                    return false;
                }
            }
        }
        // Not fire on 1.8 below
        if (fade && Bridge1_9.hasGetItemInOffHand()) {
            final BlockState newFadeState = block.getState();
            newFadeState.setType(Material.DIRT);
            final BlockFadeEvent fadeEvent = new BlockFadeEvent(block, newFadeState);
            Bukkit.getPluginManager().callEvent(fadeEvent);
            if (fadeEvent.isCancelled()) {
                // Denied by some plugin.
                return false;
            }
        }
        return true;
    }


    /**
     * Reduce the fall damage according to the feather-fall enchant level (aka: fall protection) for BukkitAPI-only mode.
     *
     * @param damage The fall damage to correct.
     * @param dealFallDamageFiresAnEvent If dealFallDamageFiresAnEvent is true.
     *                                   In that case fall damage won't be modified, as feather-fall is already taken into account.
     * @return Corrected fall damage.
     */
    public static double applyFeatherFalling(Player player, double damage, boolean dealFallDamageFiresAnEvent) {
        if (dealFallDamageFiresAnEvent) {
            return damage;
        }
        // Bukkit-API only mode: 1.13 and above.
        if (BridgeEnchant.hasFeatherFalling() && damage > 0.0) {
            int level = BridgeEnchant.getFeatherFallingLevel(player);
            if (level > 0) {
                int tmp = level * 3;
                if (tmp > 20) {
                    tmp = 20;
                }
                return damage * (1.0 - tmp / 25.0);
            }
        }
        return damage;
    }
    

    /**
     * Modify the given fall-damage according to what block the player fell on.<br>
     *
     * @param player
     * @param pLoc 
     * @param data
     * @param pData 
     * @param damage The fall damage to correct.
     * @return Modified damage.
     */
    public static float applyBlockDamageMultiplier(final Player player, final PlayerLocation pLoc, final MovingData data, final IPlayerData pData) {
        if (pData.getClientVersion().isAtLeast(ClientVersion.V_1_20)) {
            if (pLoc.isSupportedBy(BlockFlags.F_STICKY)) {
                return 0.2f;
            }
            if (pLoc.isSupportedBy(BlockFlags.F_BED)) {
                return 0.5f;
            }
            if (pLoc.isSupportedBy(Material.HAY_BLOCK)) {
                return 0.2f;
            }
            final Block block = player.getWorld().getBlockAt(pLoc.getBlockX(), pLoc.getBlockY(), pLoc.getBlockZ());
            if (block.getBlockData() instanceof PointedDripstone) {
                PointedDripstone dripstone = (PointedDripstone) block.getBlockData();
                boolean isStalagmite = dripstone.getThickness().equals(PointedDripstone.Thickness.TIP) && dripstone.getVerticalDirection().equals(BlockFace.UP);
                if (isStalagmite) {
                    return 2f;
                }
            }
            return 1f;
        }
        final Block block = player.getWorld().getBlockAt(pLoc.getBlockX(), pLoc.getBlockY(), pLoc.getBlockZ());
        final Material mat = block.getType();
        if ((BlockFlags.getBlockFlags(mat) & BlockFlags.F_STICKY) != 0) {
            return 0.2f;
        }
        if (bedChange && MaterialUtil.BEDS.contains(mat)) {
            return 0.5f;
        }
        if (hayChange && mat == Material.HAY_BLOCK) {
            return 0.2f;
        }
        if (BridgeMisc.hasIsFrozen()) {
            if (block.getBlockData() instanceof PointedDripstone) {
                PointedDripstone dripstone = (PointedDripstone) block.getBlockData();
                boolean isStalagmite = dripstone.getThickness().equals(PointedDripstone.Thickness.TIP) && dripstone.getVerticalDirection().equals(BlockFace.UP);
                if (isStalagmite) {
                    return 2f;
                }
            }
        }
        return 1f;
    }


    /**
     * Estimate the applicable fall height for the given data.
     * 
     * @param player
     * @param y
     * @param previousSetBackY
     *            The set back y from lift-off. If not present:
     *            Double.NEGATIVE_INFINITY.
     * @param pLoc 
     * @param data
     * @return
     */
    private static double getApplicableFallHeight(final Player player, final double y, final double previousSetBackY, final PlayerLocation pLoc, final MovingData data) {
        final double yDistance = Math.max(data.noFallMaxY - y, data.noFallFallDistance);
        if (yDistance > 0.0 && !attributeAvailable && data.jumpAmplifier > 0.0 && previousSetBackY != Double.NEGATIVE_INFINITY) {
            // Fall height counts below previous set-back-y.
            // TODO: Likely updating the amplifier after lift-off doesn't make sense.
            // TODO: In case of velocity... skip too / calculate max exempt height?
            final double correction = data.noFallMaxY - previousSetBackY;
            if (correction > 0.0) {
                return Math.max(0.0, yDistance - correction);
            }
        }
        if (BridgeMisc.hasIsFrozen() && yDistance > 0.0) {
            final Block fallenOnBlock = player.getWorld().getBlockAt(pLoc.getBlockX(), pLoc.getBlockY(), pLoc.getBlockZ());
            if (fallenOnBlock.getBlockData() instanceof PointedDripstone) {
                PointedDripstone dripstone = (PointedDripstone) fallenOnBlock.getBlockData();
                boolean isStalagmite = dripstone.getThickness().equals(PointedDripstone.Thickness.TIP) && dripstone.getVerticalDirection().equals(BlockFace.UP);
                if (isStalagmite) {
                    // Source of the formula: PointedDripstoneBlock.java -> fallOn() -> calculateFallDamage()
                    return yDistance + (fallDistChange ? 2.5D : 2D); // Since 1.21.11 they switched to floor the dmg, so they added +0.5 in stalagmite in height so the floor doesn’t 0 out the damage. So we need to add +0.5 to the height as well.
                }
            }
        }
        return yDistance;
    }


    public static double getApplicableFallHeight(final Player player, final double y, final PlayerLocation pLoc, final MovingData data) {
        return getApplicableFallHeight(player, y, data.hasSetBack() ? data.getSetBackY() : Double.NEGATIVE_INFINITY, pLoc, data);
    }


    /**
     * Test if fall damage would be dealt accounting for the given data.
     * 
     * @param player
     * @param y
     * @param previousSetBackY
     *            The set back y from lift-off. If not present:
     *            Double.NEGATIVE_INFINITY.
     * @param data
     * @return
     */
    public boolean willDealFallDamage(final Player player, final double y, final double previousSetBackY, final PlayerLocation pLoc, final MovingData data) {
        return getDamage(getApplicableFallHeight(player, y, previousSetBackY, pLoc, data), 1f, player) > 0D;
    }


    /**
     * 
     * @param player
     * @param minY
     * @param previousSetBackY 
     * @param pLoc 
     * @param reallyOnGround
     * @param data
     */
    private void adjustFallDistance(final Player player, final double minY, double previousSetBackY, final PlayerLocation pLoc, final boolean reallyOnGround, final MovingData data) {
        boolean adjust = false;
        if (willDealFallDamage(player, minY, previousSetBackY, pLoc, data)) {
            // Don't set fall distance yet, as they are being touch the ground, and fallDistance hasn't been updated
            //player.setFallDistance(noFallFallDistance);
            data.requestTrueForGround = true;
            adjust = true;
        }
        if (!adjust) data.clearNoFallData();
        if (!protocolLibPresent && adjust) {
            StaticLog.logWarning("Without ProtocolLib, deal fall damage by altering ground state won't work!");
        }
    }


    /**
     * Deal the given fall damage through NMS or a Bukkit event
     */
    private void dealFallDamage(final Player player, final double damage) {
        if (mcAccess.getHandle().dealFallDamageFiresAnEvent().decide()) {
            // TODO: Better decideOptimistically?
            mcAccess.getHandle().dealFallDamage(player, damage);
        }
        else {
            final EntityDamageEvent event = BridgeHealth.getEntityDamageEvent(player, DamageCause.FALL, damage);
            Bukkit.getPluginManager().callEvent(event);
            if (!event.isCancelled()) {
                // For some odd reason, player#setNoDamageTicks does not actually
                // set the no damage ticks. As a workaround, wait for it to be zero and then damage the player.
                if (player.getNoDamageTicks() > 0) {
                    TickListener damagePlayer = new TickListener() {
                        @Override
                        public void onTick(int tick, long timeLast) {
                            if (player.getNoDamageTicks() > 0) {
                                return;
                            }
                            player.setLastDamageCause(event);
                            mcAccess.getHandle().dealFallDamage(player, BridgeHealth.getRawDamage(event));
                            TickTask.removeTickListener(this);
                        }
                    };
                    TickTask.addTickListener(damagePlayer);
                } 
                else {
                    player.setLastDamageCause(event);
                    mcAccess.getHandle().dealFallDamage(player, BridgeHealth.getRawDamage(event));
                }
            }
        }

        // Currently resetting is done from within the damage event handler.
        // TODO: MUST detect if event fired at all (...) and override, if necessary. Best probe once per class (with YES).
        //        data.clearNoFallData();
        player.setFallDistance(0);
    }


    /**
     * Checks a player. Expects from and to using cc.yOnGround.
     *
     * @param previousSetBackY
     *            The set back y from lift-off. If not present:
     *            Double.NEGATIVE_INFINITY.
     */
    public void check(final Player player, final PlayerLocation pFrom, final PlayerLocation pTo, 
                      final double previousSetBackY, final MovingData data, final MovingConfig cc, final IPlayerData pData) {
        final boolean debug = pData.isDebugActive(type);
        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
        final LocationData from = thisMove.from;
        final LocationData to = thisMove.to;
        final double fromY = from.getY();
        final double toY = to.getY();
        final double yDiff = toY - fromY;
        final double oldNFDist = data.noFallFallDistance;
        // Reset-cond is not touched by yOnGround.
        // TODO: Distinguish water depth vs. fall distance ?
        /*
         * TODO: Account for flags instead (F_FALLDIST_ZERO and
         * F_FALLDIST_HALF). Resetcond as trigger: if (resetFrom) { ...
         */
        // TODO: Also handle from and to independently (rather fire twice than wait for next time).
        final boolean fromReset = from.resetCond;
        final boolean toReset = to.resetCond;
        final boolean fromOnGround, toOnGround;
        // Adapt yOnGround if necessary (sf uses another setting).
        if (yDiff < 0 && cc.yOnGround < cc.noFallyOnGround) {
            // In fact this is somewhat heuristic, but it seems to work well.
            // Missing on-ground seems to happen with running down pyramids rather.
            // TODO: Should be obsolete.
            adjustYonGround(pFrom, pTo , cc.noFallyOnGround);
            fromOnGround = pFrom.isOnGround();
            toOnGround = pTo.isOnGround();
        } 
        else {
            fromOnGround = from.onGround;
            toOnGround = to.onGround;
        }

        // TODO: early returns (...) 

        final double minY = Math.min(fromY, toY);
        if (fromReset) {
            // Just reset.
            data.clearNoFallData();
            // Ensure very big/strange moves don't yield violations.
            if (toY - fromY <= -attributeAccess.getHandle().getSafeFallDistance(player)) {
                data.noFallSkipAirCheck = true;
            }
        }
        else if (fromOnGround || !toOnGround && thisMove.touchedGround) {
            // Check if to deal damage (fall back damage check).
            touchDown(player, minY, previousSetBackY, pTo, data, cc, pData); // Includes the current y-distance on descend!
            // Ensure very big/strange moves don't yield violations.
            if (toY - fromY <= -attributeAccess.getHandle().getSafeFallDistance(player)) {
                data.noFallSkipAirCheck = true;
            }
        }
        else if (toReset) {
            // Just reset.
            data.clearNoFallData();
        }
        else if (toOnGround) {
            // Check if to deal damage.
            if (yDiff < 0) {
                // In this case the player has traveled further: add the difference.
                data.noFallFallDistance -= yDiff;
            }
            touchDown(player, minY, previousSetBackY, pTo, data, cc, pData);
        }
        else {
            // Ensure fall distance is correct, or "anyway"?
        }

        // Set reference y for nofall (always).
        /*
         * TODO: Consider setting this before handleOnGround (at least for
         * resetTo). This is after dealing damage, needs to be done differently.
         */
        data.noFallMaxY = Math.max(Math.max(fromY, toY), data.noFallMaxY);

        // TODO: fall distance might be behind (!)
        // TODO: should be the data.noFallMaxY be counted in ?
        final float mcFallDistance = player.getFallDistance(); // Note: it has to be fetched here.
        // SKIP: data.noFallFallDistance = Math.max(mcFallDistance, data.noFallFallDistance);

        // Add y distance.
        if (!toReset && !toOnGround && yDiff < 0) {
            data.noFallFallDistance -= yDiff;
        }
        else if (cc.noFallAntiCriticals && (toReset || toOnGround || (fromReset || fromOnGround || thisMove.touchedGround) && yDiff >= 0)) {
            final double max = Math.max(data.noFallFallDistance, mcFallDistance);
            if (max > 0.0 && max < 0.75) { // (Ensure this does not conflict with deal-damage set to false.) 
                if (debug) {
                    debug(player, "NoFall: Reset fall distance (anticriticals): mc=" + mcFallDistance +" / nf=" + data.noFallFallDistance);
                }
                if (data.noFallFallDistance > 0) {
                    data.noFallFallDistance = 0;
                }
                if (mcFallDistance > 0f) {
                    player.setFallDistance(0f);
                }
            }
        }
        if (debug) {
            debug(player, "NoFall: mc=" + mcFallDistance +" / nf=" + data.noFallFallDistance + (oldNFDist < data.noFallFallDistance ? " (+" + (data.noFallFallDistance - oldNFDist) + ")" : "") + " | ymax=" + data.noFallMaxY);
        }
    }


    /**
     * Called during check.
     * 
     * @param player
     * @param minY
     * @param previousSetBackY
     *            The set back y from lift-off. If not present:
     *            Double.NEGATIVE_INFINITY.
     * @param data
     * @param cc
     */
    private void touchDown(final Player player, final double minY, final double previousSetBackY,
                           final PlayerLocation pLoc,
                           final MovingData data, final MovingConfig cc, IPlayerData pData) {
        if (cc.noFallDealDamage) {
            handleOnGround(player, minY, previousSetBackY, pLoc, true, data, cc, pData);
        }
        else adjustFallDistance(player, minY, previousSetBackY, pLoc, true, data);
    }


    /**
     * Set yOnGround for from and to, if needed, should be obsolete.
     */
    private void adjustYonGround(final PlayerLocation from, final PlayerLocation to, final double yOnGround) {
        if (!from.isOnGround()) {
            from.setyOnGround(yOnGround);
        }
        if (!to.isOnGround()) {
            to.setyOnGround(yOnGround);
        }
    }


    /**
     * Quit or kick: adjust fall distance if necessary.
     */
    public void onLeave(final Player player, final MovingData data, final IPlayerData pData) {
        final float fallDistance = player.getFallDistance();
        // TODO: Might also detect too high mc fall dist.
        if (data.noFallFallDistance > fallDistance) {
            final double playerY = player.getLocation(useLoc).getY();
            useLoc.setWorld(null);
            if (player.isFlying() || player.getGameMode() == GameMode.CREATIVE
                || player.getAllowFlight() && pData.getGenericInstance(MovingConfig.class).noFallSkipAllowFlight) {
                // Forestall potential issues with flying plugins.
                player.setFallDistance(0f);
                data.noFallFallDistance = 0f;
                data.noFallMaxY = playerY;
            } 
            else {
                final float yDiff = (float) (data.noFallMaxY - playerY);
                // TODO: Consider to only use one accounting method (maxY). 
                final float maxDist = Math.max(yDiff, data.noFallFallDistance);
                player.setFallDistance(maxDist);
            }
        }
    }


    /**
     * This is called if a player fails a check and gets set back, to avoid using that to avoid fall damage the player might be dealt damage here.
     * @param player
     * @param y
     * @param pLoc
     *            The current/newest location
     * @param data
     * @param pData
     */
    public void checkDamage(final Player player, final double y, final PlayerLocation pLoc, final MovingData data, final IPlayerData pData) {
        final MovingConfig cc = pData.getGenericInstance(MovingConfig.class);
        // Deal damage.
        handleOnGround(player, y, data.hasSetBack() ? data.getSetBackY() : Double.NEGATIVE_INFINITY, pLoc, false, data, cc, pData);
    }
}