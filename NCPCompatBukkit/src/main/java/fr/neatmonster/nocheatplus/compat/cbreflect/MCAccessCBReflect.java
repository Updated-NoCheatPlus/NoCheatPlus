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
package fr.neatmonster.nocheatplus.compat.cbreflect;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.compat.AlmostBoolean;
import fr.neatmonster.nocheatplus.compat.bukkit.BlockCacheBukkit;
import fr.neatmonster.nocheatplus.compat.bukkit.MCAccessBukkit;
import fr.neatmonster.nocheatplus.compat.cbreflect.reflect.ReflectHelper;
import fr.neatmonster.nocheatplus.compat.cbreflect.reflect.ReflectHelper.ReflectFailureException;
import fr.neatmonster.nocheatplus.compat.versions.GenericVersion;
import fr.neatmonster.nocheatplus.compat.versions.ServerVersion;
import fr.neatmonster.nocheatplus.logging.Streams;
import fr.neatmonster.nocheatplus.utilities.location.LocUtil;
import fr.neatmonster.nocheatplus.utilities.map.BlockCache;

public class MCAccessCBReflect extends MCAccessBukkit {

    protected final ReflectHelper helper;

    /** Generally supported Minecraft version (know for sure). */
    protected final boolean knownSupportedVersion;
    /** We know for sure that dealFallDamage will fire a damage event. */
    protected final boolean dealFallDamageFiresAnEvent;  

    public MCAccessCBReflect() throws ReflectFailureException {
        // TODO: Add unavailable stuff to features / missing (TBD).
        helper = new ReflectHelper();
        // Version Envelope tests (1.4.5-R1.0 ... 1.8.x is considered to be ok).
        final String mcVersion = ServerVersion.getMinecraftVersion();
        if (mcVersion == GenericVersion.UNKNOWN_VERSION) {
            NCPAPIProvider.getNoCheatPlusAPI().getLogManager().warning(Streams.INIT, "The Minecraft version could not be detected, Compat-CB-Reflect might or might not work.");
            this.knownSupportedVersion = false;
        }
        else if (GenericVersion.compareVersions(mcVersion, "1.5") < 0) {
            NCPAPIProvider.getNoCheatPlusAPI().getLogManager().warning(Streams.INIT, "The Minecraft version seems to be older than what Compat-CB-Reflect can support.");
            this.knownSupportedVersion = false;
        }
        else if (GenericVersion.compareVersions(mcVersion, "1.12.2") > 0) {
            this.knownSupportedVersion = false;
            NCPAPIProvider.getNoCheatPlusAPI().getLogManager().warning(Streams.INIT, "The Minecraft version seems to be more recent than the one Compat-CB-Reflect has been built with - this might work, but there could be incompatibilities.");
        } else {
            this.knownSupportedVersion = true;
        }
        // Fall damage / event. TODO: Tests between 1.8 and 1.7.2. How about spigot vs. CB?
        if (mcVersion == GenericVersion.UNKNOWN_VERSION || GenericVersion.compareVersions(mcVersion, "1.8") < 0) {
            dealFallDamageFiresAnEvent = false;
        } else {
            // Assume higher versions to fire an event.
            dealFallDamageFiresAnEvent = true;
        }
    }

    @Override
    public String getMCVersion() {
        return "1.5-1.12.2";
    }

    @Override
    public String getServerVersionTag() {
        return "CB-Reflect";
    }

    @Override
    public BlockCache getBlockCache(World world) {
        try {
            return new BlockCacheCBReflect(helper, world);
        }
        catch (ReflectFailureException ex) {
            return new BlockCacheBukkit(world);
        }
    }

    @Override
    public boolean shouldBeZombie(Player player) {
        try {
            Object handle = helper.getHandle(player);
            return !helper.nmsPlayer_dead(handle) && helper.nmsPlayer_getHealth(handle) <= 0.0; 
        }
        catch (ReflectFailureException ex) {
            // Fall back to Bukkit.
            return super.shouldBeZombie(player);
        }
    }

    @Override
    public void setDead(Player player, int deathTicks) {
        try {
            Object handle = helper.getHandle(player);
            helper.nmsPlayer_dead(handle, true);
            helper.nmsPlayer_deathTicks(handle, deathTicks);
        }
        catch (ReflectFailureException ex) {
            super.setDead(player, deathTicks);
        }
    }

    @Override
    public AlmostBoolean dealFallDamageFiresAnEvent() {
        if (!dealFallDamageFiresAnEvent) {
            return AlmostBoolean.NO;
        }
        return AlmostBoolean.match(this.helper.canDealFallDamage());
    }

    @Override
    public void dealFallDamage(final Player player, final double damage) {
        try {
            helper.dealFallDamage(player, damage);
        }
        catch (ReflectFailureException ex) {
            // TODO: Fire an event ?
            super.dealFallDamage(player, damage);
        }
    }

    @Override
    public int getInvulnerableTicks(final Player player) {
        try {
            return helper.getInvulnerableTicks(player);
        }
        catch (ReflectFailureException ex) {
            return super.getInvulnerableTicks(player);
        }
    }

    @Override
    public void setInvulnerableTicks(final Player player, final int ticks) {
        try {
            helper.setInvulnerableTicks(player, ticks);
        }
        catch (ReflectFailureException ex) {
            super.setInvulnerableTicks(player, ticks);
        }
    }

    @Override
    public AlmostBoolean isBlockSolid(final Material id) {
        try {
            return helper.isBlockSolid(id);
        }
        catch (ReflectFailureException ex) {
            return super.isBlockSolid(id);
        }
    }

    @Override
    public AlmostBoolean isBlockLiquid(final Material id) {
        try {
            return helper.isBlockLiquid(id);
        }
        catch (ReflectFailureException ex) {
            return super.isBlockLiquid(id);
        }
    }

    @Override
    public double getHeight(Entity entity) {
        if (bukkitHasGetHeightAndGetWidth) {
            return super.getHeight(entity);
        }
        try {
            return helper.getHeight(entity);
        }
        catch (ReflectFailureException ex) {
            return super.getHeight(entity);
        }
    }

    @Override
    public double getWidth(Entity entity) {
        if (bukkitHasGetHeightAndGetWidth) {
            return super.getWidth(entity);
        }
        try {
            return helper.getWidth(entity);
        }
        catch (ReflectFailureException ex) {
            return super.getWidth(entity);
        }
    }

    @Override
    public AlmostBoolean isIllegalBounds(final Player player) {
        if (player.isDead()) {
            return AlmostBoolean.NO;
        }
        try {
            final double[] bounds = helper.getBoundsTemp(player);
            if (LocUtil.isBadCoordinate(bounds)) {
                return AlmostBoolean.YES;
            }
            if (!player.isSleeping()) {
                final double dY = Math.abs(bounds[4] - bounds[1]);
                if (dY > 1.8) {
                    return AlmostBoolean.YES; // dY > 1.65D || 
                }
                // TODO: Get height/length from ReflectEntity.
                if (dY < 0.1D && getHeight(player) >= 0.1) {
                    return AlmostBoolean.YES;
                }
            }
        }
        catch (ReflectFailureException e) {
            // Ignore.
        }
        catch (NullPointerException ne) {
            // Ignore.
        }
        return AlmostBoolean.MAYBE;
    }

    // ---- Missing (probably ok with Bukkit only) ----

    // (getCommandMap already uses reflection, but could be more speedy.).
    // getJumpAmplifier(final Player player)
    // getFasterMovementAmplifier(final Player player)
    // isComplexPart(final Entity entity) // Fails for very old builds, likely irrelevant.
    // hasGravity(Material)

}
