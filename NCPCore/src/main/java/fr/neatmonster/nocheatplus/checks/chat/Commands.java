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
package fr.neatmonster.nocheatplus.checks.chat;

import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.ColorUtil;
import fr.neatmonster.nocheatplus.utilities.TickTask;

/**
 * Check only for commands
 * @author asofold
 *
 */
public class Commands extends Check {

    public Commands() {
        super(CheckType.CHAT_COMMANDS);
    }
    
    /**
     * Checks a player for command spam
     * 
     * @param player
     * @param message
     * @param cc
     * @param pData
     * @param captcha
     * @return true if successful.
     */
    public boolean check(final Player player, final String message, 
                         final ChatConfig cc, final IPlayerData pData, 
                         final ICaptcha captcha) {

        final long now = System.currentTimeMillis();
        final int tick = TickTask.getTick();
        final ChatData data = pData.getGenericInstance(ChatData.class);
        final boolean captchaEnabled = !cc.captchaSkipCommands && pData.isCheckActive(CheckType.CHAT_CAPTCHA, player); 
        if (captchaEnabled){
            synchronized (data) {
                if (captcha.shouldCheckCaptcha(player, cc, data, pData)){
                    captcha.checkCaptcha(player, message, cc, data, true);
                    return true;
                }
            }
        }

        // Rest of the check is done without sync, because the data is only used by this check.
        // Weight might later be read from some prefix tree (also known / unknown).
        final float weight = 1f;
        data.commandsWeights.add(now, weight);
        if (tick < data.commandsShortTermTick) {
            // TickTask got reset.
            data.commandsShortTermTick = tick;
            data.commandsShortTermWeight = 1.0;
        }
        else if (tick - data.commandsShortTermTick < cc.commandsShortTermTicks) {
            if (!pData.getCurrentWorldData().shouldAdjustToLag(type) 
                || TickTask.getLag(50L * (tick - data.commandsShortTermTick), true) < 1.3f) {
                // Add up.
                data.commandsShortTermWeight += weight;
            }
            else {
                // Reset, too much lag.
                data.commandsShortTermTick = tick;
                data.commandsShortTermWeight = 1.0;
            }
        }
        else{
            // Reset.
            data.commandsShortTermTick = tick;
            data.commandsShortTermWeight = 1.0;
        }

        final float nw = data.commandsWeights.score(1f);
        final double violation = Math.max(nw - cc.commandsLevel, data.commandsShortTermWeight - cc.commandsShortTermLevel);

        if (violation > 0.0) {
            data.commandsVL += violation;
            // TODO: Evaluate if sync(data) is necessary or better for executeActions.
            if (captchaEnabled) {
                synchronized (data) {
                    captcha.sendNewCaptcha(player, cc, data);
                }
                return true;
            }
            else if (executeActions(player, data.commandsVL, violation, cc.commandsActions).willCancel()) {
                return true;
            }
        }
        else if (cc.chatWarningCheck && now - data.chatWarningTime > cc.chatWarningTimeout && (100f * nw / cc.commandsLevel > cc.chatWarningLevel || 100f * data.commandsShortTermWeight / cc.commandsShortTermLevel > cc.chatWarningLevel)){
            player.sendMessage(ColorUtil.replaceColors(cc.chatWarningMessage));
            data.chatWarningTime = now;
        }
        else {
            // TODO: This might need invalidation with time.
            data.commandsVL *= 0.99;
        }
        return false;
    }

}
