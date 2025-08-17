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

import fr.neatmonster.nocheatplus.checks.access.AsyncCheckData;
import fr.neatmonster.nocheatplus.utilities.ds.count.ActionFrequency;

/**
 * Player specific data for the chat checks.
 */
public class ChatData extends AsyncCheckData {  

    // Violation levels.
    public double captchaVL;
    public double commandsVL;
    public double textVL;
    public double relogVL;

    // Captcha data.
    public int captchTries;
    public String captchaGenerated;
    public boolean captchaStarted;

    /// Commands data.
    public final ActionFrequency commandsWeights = new ActionFrequency(5, 1000);
    public long commandsShortTermTick;
    public double commandsShortTermWeight;

    // Data of the text check.
    public final ActionFrequency chatFrequency = new ActionFrequency(10, 3000);
    public final ActionFrequency chatShortTermFrequency = new ActionFrequency(6, 500);

    // Misc. Data of the chat checks.     
    public String chatLastMessage;
    public long chatLastTime;
    public long chatWarningTime;
    
    // Relog check
    public int relogWarnings;
    public long relogWarningTime;


    /**
     * Clear the data of the chat checks.
     */
    public synchronized void reset() {
        captchTries = relogWarnings = 0;
        captchaVL = 0D;
        textVL = 0;
        final long now = System.currentTimeMillis();
        chatFrequency.clear(now);
        chatShortTermFrequency.clear(now);
        chatLastTime = relogWarningTime = 0L;
        captchaGenerated = chatLastMessage = "";
        chatLastTime = 0;
        chatWarningTime = 0;
        commandsShortTermTick = 0;
        commandsWeights.clear(now);
    }
}
