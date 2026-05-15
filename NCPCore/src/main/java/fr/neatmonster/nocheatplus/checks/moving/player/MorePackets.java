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

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.actions.ParameterName;
import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.ViolationData;
import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.net.NetStatic;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.CheckUtils;
import fr.neatmonster.nocheatplus.utilities.StringUtil;
import fr.neatmonster.nocheatplus.utilities.location.PlayerLocation;

/**
 * The MorePackets check will try to identify players that send more than the usual
 * amount of move-packets to the server to be able to move faster than normal, without getting caught by the other
 * checks (flying/running).
 */
public class MorePackets extends Check {

    private final List<String> tags = new ArrayList<String>();

    /**
     * Instantiates a new more packets check.
     */
    public MorePackets() {
        super(CheckType.MOVING_MOREPACKETS);
    }

    /**
     * Check for speeding by sending too many packets. We assume 22 packets per
     * second to be legitimate, while 20 would be ideal. See
     * PlayerData.morePacketsFreq for the monitored amount of time and the
     * resolution. See NetStatic for the actual check code.
     * 
     * @param player
     * @param from
     * @param to
     * @param allowSetSetBack
     *            If to allow setting the set back location.
     * @param data
     * @param cc
     * @return
     */
    public Location check(final Player player, final PlayerLocation from, final PlayerLocation to, 
            final boolean allowSetSetBack, final MovingData data, final MovingConfig cc,
            final IPlayerData pData) {
        // Take time once, first:
        final long time = System.currentTimeMillis();
        final boolean debug = pData.isDebugActive(type);

        //    	if (from.isSamePos(to)) {
        //    		// Ignore moves with "just look" for now.
        //    		// TODO: Extra ActionFrequency for "just look" + use to burn, maybe also check individually.
        //    		return null;
        //    	}

        // Ensure we have a set back location.
        if (allowSetSetBack && !data.hasMorePacketsSetBack()){
            // TODO: Check if other set back is appropriate or if to set/reset on other events.
            if (data.hasSetBack()) {
                data.setMorePacketsSetBackFromSurvivalfly();
            }
            else {
                data.setMorePacketsSetBack(from);
            }
        }

        // Check for a violation of the set limits.
        tags.clear();
        // Diagnostic info: packet-rate tags are separate from SurvivalFly movement-model tags.
        final double violation = NetStatic.morePacketsCheck(data.morePacketsFreq, time, 1f, cc.morePacketsEPSMax, cc.morePacketsEPSIdeal, data.morePacketsBurstFreq, cc.morePacketsBurstPackets, cc.morePacketsBurstDirect, cc.morePacketsBurstEPM, tags);

        // Process violation result.
        if (violation > 0.0) {
            // Increment violation level.
            data.morePacketsVL = violation; // TODO: Accumulate somehow [e.g. always += 1, decrease with continuous moving without violation]?
            // Diagnostic info: expose packet-rate violations as their own subcheck under MOVING_MOREPACKETS.
            tags.add(0, "subcheck_morepackets_frequency");

            // Violation handling.
            final ViolationData vd = new ViolationData(this, player, data.morePacketsVL, violation, cc.morePacketsActions);
            if (debug || vd.needsParameters()) {
                vd.setParameter(ParameterName.PACKETS, Integer.toString(new Double(violation).intValue()));
                vd.setParameter(ParameterName.TAGS, StringUtil.join(tags, "+"));
            }
            if (CheckUtils.shouldLogDebugToConsole()) {
                logConsoleDetails(violation, player, from, to, allowSetSetBack, data, cc, pData, time);
            }
            if (executeActions(vd).willCancel()) {
                // Set to cancel the move.
                /*
                 * TODO: Harmonize with MovingUtil.getApplicableSetBackLocation
                 * (somehow include the desired set back type / loc / context).
                 */
                return data.hasMorePacketsSetBack() ? data.getMorePacketsSetBack() : data.getSetBack(to); // TODO
            }
        } 
        else if (allowSetSetBack && data.getMorePacketsSetBackAge() > cc.morePacketsSetBackAge) {
            // Update the set back location. (CHANGED to only update, if not a violation.)
            // (Might update whenever newTo == null)
            data.setMorePacketsSetBack(from);
            if (debug) {
                debug(player, "Update set back (morepackets) to from.");
            }
        }

        // No set back.
        return null;

    }

    private void logConsoleDetails(final double violation, final Player player,
                                   final PlayerLocation from, final PlayerLocation to,
                                   final boolean allowSetSetBack,
                                   final MovingData data, final MovingConfig cc,
                                   final IPlayerData pData, final long time) {
        try {
            // Diagnostic info: console-only packet-rate context for distinguishing packet spam from movement model issues.
            final StringBuilder builder = new StringBuilder(620);
            builder.append("[NCP][MorePackets][detail] player=").append(player.getName())
                    .append(" bedrock=").append(pData.isBedrockPlayer() || player.getName().startsWith(".")
                            || player.getUniqueId().toString().startsWith("00000000-0000-0000-0009-"))
                    .append(" bedrockData=").append(pData.isBedrockPlayer())
                    .append(" uuid=").append(player.getUniqueId())
                    .append(" client=").append(pData.getClientVersion())
                    .append(" time=").append(time)
                    .append(" subcheck=MOREPACKETS_FREQUENCY")
                    .append(" summary=packet_rate{violation=").append(StringUtil.fdec3.format(violation))
                    .append(",epsMax=").append(cc.morePacketsEPSMax)
                    .append(",burst=").append(cc.morePacketsBurstPackets)
                    .append(",setback=").append(data.hasMorePacketsSetBack())
                    .append('}')
                    .append(" violation=").append(StringUtil.fdec3.format(violation))
                    .append(" totalVL=").append(StringUtil.fdec3.format(data.morePacketsVL))
                    .append(" moveCount=").append(data.getPlayerMoveCount())
                    .append(" allowSetBack=").append(allowSetSetBack)
                    .append(" morePacketsSetBack=").append(data.hasMorePacketsSetBack())
                    .append(" morePacketsSetBackAge=").append(data.getMorePacketsSetBackAge())
                    .append(" from=").append(formatLocation(from))
                    .append(" to=").append(formatLocation(to))
                    .append(" move=").append(formatMove(from, to))
                    .append(" fromEnv=").append(formatEnvironment(from))
                    .append(" toEnv=").append(formatEnvironment(to))
                    .append(" playerState=sprint:").append(player.isSprinting())
                    .append(",sneak:").append(player.isSneaking())
                    .append(",fly:").append(player.isFlying())
                    .append(",allowFlight:").append(player.getAllowFlight())
                    .append(",vehicle:").append(player.isInsideVehicle())
                    .append(",velocity=").append(StringUtil.fdec3.format(player.getVelocity().getX()))
                    .append('/').append(StringUtil.fdec3.format(player.getVelocity().getY()))
                    .append('/').append(StringUtil.fdec3.format(player.getVelocity().getZ()))
                    .append(" config=epsIdeal:").append(cc.morePacketsEPSIdeal)
                    .append(",epsMax:").append(cc.morePacketsEPSMax)
                    .append(",burstPackets:").append(cc.morePacketsBurstPackets)
                    .append(",burstDirect:").append(cc.morePacketsBurstDirect)
                    .append(",burstEPM:").append(cc.morePacketsBurstEPM)
                    .append(" tags=").append(StringUtil.join(tags, "+"));
            player.getServer().getLogger().info(builder.toString());
        }
        catch (Throwable t) {
            player.getServer().getLogger().info("[NCP][MorePackets][detail] diagnostic logging failed for player="
                    + player.getName() + " reason=" + t.getClass().getSimpleName());
        }
    }

    private String formatMove(final PlayerLocation from, final PlayerLocation to) {
        final double x = to.getX() - from.getX();
        final double y = to.getY() - from.getY();
        final double z = to.getZ() - from.getZ();
        final double h = Math.sqrt(x * x + z * z);
        return "x:" + StringUtil.fdec6.format(x)
                + ",y:" + StringUtil.fdec6.format(y)
                + ",z:" + StringUtil.fdec6.format(z)
                + ",h:" + StringUtil.fdec6.format(h);
    }

    private String formatLocation(final PlayerLocation location) {
        return StringUtil.fdec3.format(location.getX()) + ","
                + StringUtil.fdec3.format(location.getY()) + ","
                + StringUtil.fdec3.format(location.getZ());
    }

    private String formatEnvironment(final PlayerLocation location) {
        return "{block:" + location.getBlockType()
                + ",below:" + location.getBlockTypeBelow()
                + ",above:" + location.getBlockTypeAbove()
                + ",ground:" + location.isOnGround()
                + ",reset:" + location.isResetCond()
                + ",water:" + location.isInWater()
                + ",lava:" + location.isInLava()
                + ",waterlogged:" + location.isInWaterLogged()
                + ",climb:" + location.isOnClimbable()
                + ",web:" + location.isInWeb()
                + ",passable:" + location.isPassable()
                + "}";
    }

}
