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
package fr.neatmonster.nocheatplus.checks.blockplace;

import java.util.LinkedList;
import java.util.List;

import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import fr.neatmonster.nocheatplus.actions.ParameterName;
import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.ViolationData;
import fr.neatmonster.nocheatplus.components.registry.feature.TickListener;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.StringUtil;
import fr.neatmonster.nocheatplus.utilities.TickTask;
import fr.neatmonster.nocheatplus.utilities.math.TrigUtil;


/**
 * Check for common behavior from a client using the "Scaffold" cheat.
 * Each sub-check should cover areas that one sub-check may not cover.
 * If the cheat does not flag any other sub-checks, the time sub-check
 * Should enforce realistic time between block placements.
 * 
 * @author CaptainObvious0
 */
public class Scaffold extends Check {

    final static double MAX_ANGLE = Math.toRadians(90);
    public List<String> tags = new LinkedList<>();
    
   /*
    * Instantiates a new Scaffold check
    */
    public Scaffold() {
        super(CheckType.BLOCKPLACE_SCAFFOLD);
    }

    /**
     * Check the player for Scaffold cheats
     *
     * @param player the player
     * @param placedFace blockface player placed against
     * @param pData player data
     * @param data block place data
     * @param cc block place config
     * @param isCancelled is the event cancelled
     * @param yDistance players current yDistance for this move
     * @param jumpPhase players jump phase
     * @return
     */
    public boolean check(final Player player, final BlockFace placedFace, final IPlayerData pData,
                         final BlockPlaceData data, final BlockPlaceConfig cc, final boolean isCancelled,
                         final double yDistance, final int jumpPhase) {
        // NOTE: Looks like there's a legit fast-scaffolding technique called 'god-bridge'
        // Would be useful to compare the speed with a skilled player god-bridging VS scaffold cheats.
        // TODO: ^ Need testing + debug log + someone who can actually replicate such... :)

        // TODO: Not too sure about the usefulness of this check these days:
        // Angle: like reported in the comment below, this is already covered by BlockInteract.Direction. Why not simply sharpen that one instead of bloating NCP with redundant code?
        // Time: Possibly rethink it. Could be moved to FastPlace as a sub-check
        //       ...Or just remove it. We already have a FastPlace check (which is ALSO
        //       feeding Improbable with too fast block placements on longer time periods...)
        // Sprint: Would be best to have our own sprinting handling, and catch the cheat with SurvivalFly instead.
        //        (If the player sprints while placing a block: sprinting = false -> cause Sf to enforce walking speed -> the player will then trigger a speed violation)
        // ToolSwitch: matter of taste... it may catch badly implemented scaffold cheats but not really much else. 
        //             Also clashes with NCP's principle (having long-lasting checks based on deterministic protection, not detecting specific cheat implementations)  
        // PitchRotation: this is the only one that I can see being kept. Combined with yawrate, they can nerf the cheat decently.
        //                (At that point it should be moved as a subcheck into something else. Wrongturn? A new Combined.PitchRate check? Wouldn't make sense keeping it into the BlockPlace category)      
        boolean cancel = false;

        // Update sneakTime since the player may have unsneaked after the last move.
        if (player.isSneaking()) {
            data.sneakTime = data.currentTick;
        }
        data.currentTick = TickTask.getTick();

        // Angle Check - Check if the player is looking at the block (Should already be covered by BlockInteract.Direction)
        if (cc.scaffoldAngle) {
            final Vector placedVector = new Vector(placedFace.getModX(), placedFace.getModY(), placedFace.getModZ());
            double placedAngle = TrigUtil.angle(player.getLocation().getDirection(), placedVector);
            if (placedAngle > MAX_ANGLE) {
                cancel = violation("Angle", Math.min(Math.max(1, (int) (placedAngle - MAX_ANGLE) * 10), 10), player, data, pData);
            }
        }

        // Time Check - A FastPlace check but for Scaffold type block placements. If all other sub-checks fail to detect the cheat this
        // Should ensure the player cannot quickly place blocks below themselves.
        if (cc.scaffoldTime && !isCancelled && Math.abs(player.getLocation().getPitch()) > 70
            && (data.currentTick - data.sneakTime) > 3 
            && !player.hasPotionEffect(PotionEffectType.SPEED)) {
            
            // TODO: Switch to ActionAccumulator based check.
            data.placeTick.add(data.currentTick);
            if (data.placeTick.size() > 2) {
                long sum = 0;
                long lastTick = 0;
                for (int i = 0; i < data.placeTick.size(); i++) {
                    long tick = data.placeTick.get(i);

                    if (lastTick != 0) {
                        sum += (tick - lastTick);
                    }
                    lastTick = tick;

                }

                double avg = sum / data.placeTick.size();
                if (avg < cc.scaffoldTimeAvg) {
                    cancel = violation("Time", Math.min((cc.scaffoldTimeAvg - (int) avg), 5), player, data, pData);
                    if (data.placeTick.size() > 20) data.placeTick.clear(); // Clear if it gets too big to prevent players being unable to place blocks
                } 
                else {
                    data.placeTick.clear();
                }
            }
        }

        // Sprint check - Prevent players from sprinting while placing blocks below them
        long diff = data.currentTick - data.sprintTime;
        if (cc.scaffoldSprint && Math.abs(player.getLocation().getPitch()) > 70
            && diff < 8 && yDistance < 0.1 && jumpPhase < 4) { 

            cancel = violation("Sprint", 1, player, data, pData);
        }

        // Rotate Check - Check for large changes in rotation between block placements
        // Note: Yaw speed change is also monitored. (See listener...)
        if (cc.scaffoldRotate) {
            data.lastYaw = player.getLocation().getYaw();
            TickListener yawTick = new TickListener() {
                @Override
                public void onTick(int tick, long timeLast) {
                    // Needs to be run on the next tick
                    // Most likely better way to to this with TickTask but this works as well
                    if (TickTask.getTick() != data.currentTick) {
                        float diff = Math.abs(data.lastYaw - player.getLocation().getYaw());

                        if (diff > cc.scaffoldRotateDiff) {
                            data.cancelNextPlace = violation("Rotate", Math.min((int) (diff - cc.scaffoldRotateDiff) / 10, 5), player, data, pData);
                            tags.clear();
                        }
                        TickTask.removeTickListener(this);
                    }
                }
            };
            TickTask.addTickListener(yawTick);
        }

        // Tool Switch - Check if the player is quickly switching inventory slots between block placements
        if (cc.scaffoldToolSwitch) {

            data.lastSlot = player.getInventory().getHeldItemSlot();
            TickListener toolSwitchTick = new TickListener() {
                @Override
                public void onTick(int tick, long timeLast) {
                    // Needs to be run on the next tick
                    // Most likely better way to to this with TickTask but this works as well
                    if (data.currentTick != TickTask.getTick()) {
                        if (data.lastSlot != player.getInventory().getHeldItemSlot()) {
                            data.cancelNextPlace = violation("ToolSwitch", 1, player, data, pData);
                            tags.clear();
                        }
                        TickTask.removeTickListener(this);
                    }
                }
            };
            TickTask.addTickListener(toolSwitchTick);
        }
        
        tags.clear();
        return cancel;
    }

    /**
     * Create a violation for Scaffold
     *
     * @param addTags
     * @param weight
     * @param player
     * @param data
     * @param pData
     * @return
     */
    private boolean violation(final String addTags, final int weight, final Player player,
                              final BlockPlaceData data, final IPlayerData pData) {
        ViolationData vd = new ViolationData(this, player, data.scaffoldVL, weight, pData.getGenericInstance(BlockPlaceConfig.class).scaffoldActions);
        tags.add(addTags);
        if (vd.needsParameters()) vd.setParameter(ParameterName.TAGS, StringUtil.join(tags, "+"));
        data.scaffoldVL += weight;
        return executeActions(vd).willCancel();
    }
}
