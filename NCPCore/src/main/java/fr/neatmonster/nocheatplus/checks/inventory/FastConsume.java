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
package fr.neatmonster.nocheatplus.checks.inventory;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import fr.neatmonster.nocheatplus.actions.ParameterName;
import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.ViolationData;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.entity.InventoryUtil;
import fr.neatmonster.nocheatplus.utilities.TickTask;

/**
 * Replaces the former InstantEat check (pre 1.5). Will be disabled from 1.8 and higher versions.<br>
 * @author asofold
 *
 */
public class FastConsume extends Check {

    public FastConsume() {
        super(CheckType.INVENTORY_FASTCONSUME);
    }
    
    /**
     * Checks a player
     * 
     * @param player
     * @param stack
     * @param time
     * @param data
     * @param pData
     * @return True, if to cancel the event. In that case, the inventory will be updated as well.
     */
    public boolean check(final Player player, final ItemStack stack, final long time, final InventoryData data, final IPlayerData pData) {
        // Consistency checks...
        if (stack == null) { // || stack.getType() != data.fastConsumeFood){
            // TODO: Strict version should prevent other material (?).
            return false;
        }
        final long ref = data.fastConsumeInteract == 0 ? 0 : Math.max(data.fastConsumeInteract, data.lastClickTime);
        if (time < ref) {
            // Time ran backwards.
            data.fastConsumeInteract = data.lastClickTime = time;
            return false;
        }
        // Check exceptions.
        final InventoryConfig cc = pData.getGenericInstance(InventoryConfig.class);
        final Material mat = stack == null ? null : stack.getType();
        if (mat != null) {
            if (cc.fastConsumeWhitelist) {
                if (!cc.fastConsumeItems.contains(mat)) {
                    return false;
                }
            }
            else if (cc.fastConsumeItems.contains(mat)) {
                return false;
            }
        }
        // Actually check.
        final long timeSpent = ref == 0 ? 0 : (time - ref); // Not interact = instant.
        final long expectedDuration = cc.fastConsumeDuration;
        boolean cancel = false;
        if (timeSpent < expectedDuration) {
            // TODO: Might have to do a specialized check for lag spikes here instead.
            final float lag = TickTask.getLag(expectedDuration, true);
            if (timeSpent * lag < expectedDuration) {
                final double difference = (expectedDuration - timeSpent * lag) / 100.0;
                data.fastConsumeVL += difference;
                final ViolationData vd = new ViolationData(this, player, data.fastConsumeVL, difference, cc.fastConsumeActions);
                vd.setParameter(ParameterName.FOOD, "" + mat);
                if (data.fastConsumeFood != mat) {
                    vd.setParameter(ParameterName.TAGS, "inconsistent(" + data.fastConsumeFood + ")");
                }
                else {
                    vd.setParameter(ParameterName.TAGS, "");
                }
                if (executeActions(vd).willCancel()){
                    cancel = true;
                }
            }
        }
        else {
            data.fastConsumeVL *= 0.6; 
        }
        // Reset interaction.
        if (cancel) {
            // Fake interaction to prevent violation loops with false positives.
            final ItemStack actualStack = InventoryUtil.getFirstConsumableItemInHand(player);
            data.fastConsumeFood = actualStack == null ? null : actualStack.getType();
            // TODO: Allows some abuse: 1. try instantly eat (cancelled) 2. consume item directly when needed.
        }
        else  {
            if (pData.isDebugActive(type)) {
                debug(player, "PlayerItemConsumeEvent, reset fastconsume: " + data.fastConsumeFood);
            }
            data.fastConsumeFood = null;
        }
        data.fastConsumeInteract = time;
        return cancel;
    }
}