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

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.chat.analysis.MessageLetterCount;
import fr.neatmonster.nocheatplus.checks.chat.analysis.WordLetterCount;
import fr.neatmonster.nocheatplus.checks.chat.analysis.engine.LetterEngine;
import fr.neatmonster.nocheatplus.checks.combined.CombinedData;
import fr.neatmonster.nocheatplus.components.NoCheatPlusAPI;
import fr.neatmonster.nocheatplus.components.registry.feature.INotifyReload;
import fr.neatmonster.nocheatplus.config.ConfigFile;
import fr.neatmonster.nocheatplus.config.ConfigManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.ColorUtil;
import fr.neatmonster.nocheatplus.utilities.StringUtil;

/**
 * Some alternative more or less advanced analysis methods.
 * @author asofold
 *
 */
public class Text extends Check implements INotifyReload {

    private LetterEngine engine = null;

    /** Not really cancelled but above threshold for actions. */
    private String lastCancelledMessage = "";
    private long lastCancelledTime = 0;

    private String lastGlobalMessage = "";
    private long lastGlobalTime = 0;

    public Text() {
        super(CheckType.CHAT_TEXT);
        init();
    }

    /**
     * Start analysis.
     * @param player
     *          The player who issued the message.
     * @param message
     *          The message to check.
     * @param captcha 
     *          Used for starting captcha on failure, if configured so.
     * @param alreadyCancelled 
     * @return
     */
    public boolean check(final Player player, final String message, 
                         final ChatConfig cc, final IPlayerData pData,
                         final ICaptcha captcha, boolean isMainThread, final boolean alreadyCancelled) {
        final ChatData data = pData.getGenericInstance(ChatData.class);
        synchronized (data) {
            return unsafeCheck(player, message, captcha, cc, data, pData, isMainThread, alreadyCancelled);
        }
    }

    private void init() {
        // Set some things from the global config.
        final ConfigFile config = ConfigManager.getConfigFile();
        final NoCheatPlusAPI api = NCPAPIProvider.getNoCheatPlusAPI();
        if (engine != null) {
            engine.clear();
            api.removeComponent(engine);
        }
        engine = new LetterEngine(config);
        api.addComponent(engine);
    }

    @Override
    public void onReload() {
        synchronized(engine) {
            engine.clear();
        }
        init();
    }

    /**
     * Check without further synchronization.
     * @param player
     * @param message
     * @param captcha
     * @param cc
     * @param data
     * @param isMainThread 
     * @param alreadyCancelled 
     * @return
     */
    private boolean unsafeCheck(final Player player, final String message, final ICaptcha captcha,
                                final ChatConfig cc, final ChatData data, final IPlayerData pData,
                                boolean isMainThread, final boolean alreadyCancelled) {

        // Test captcha.
        if (captcha.shouldCheckCaptcha(player, cc, data, pData)) {
            captcha.checkCaptcha(player, message, cc, data, isMainThread);
            return true;
        } 
        else if (alreadyCancelled) {
            // Skip checking.
            return true;
        }

        final long time = System.currentTimeMillis();
        final String lcMessage = message.trim().toLowerCase();
        boolean cancel = false;
        final boolean debug = pData.isDebugActive(type);
        final List<String> debugParts;
        if (debug) {
            debugParts = new LinkedList<String>();
            debugParts.add("Message (length=" + message.length()+"): ");
        }
        else debugParts = null;

        // Update the frequency interval weights.
        data.chatFrequency.update(time);

        // Score for this message (violation score).
        float score = 0;
        final MessageLetterCount letterCounts = new MessageLetterCount(message);
        final int msgLen = message.length();



        // (Following: random/made up criteria.)
        // TODO: Create tests for all methods with wordlists, fake chat (refactor for that).
        // Full message processing. ------------
        // Upper case.
        if (letterCounts.fullCount.upperCase > msgLen / 3) {
            // TODO: Regard chunks of 48 or so letters of the message for this?
            final float wUpperCase = 0.6f * letterCounts.fullCount.getUpperCaseRatio();
            score += wUpperCase  * cc.textMessageUpperCase;
        }

        // Letters vs. word length.
        if (msgLen > 4) {
            final float fullRep = letterCounts.fullCount.getLetterCountRatio();
            // Long messages: very small and very big are bad !
            // 128 is a quick attempt to make one long message possible on 1.11.
            final float wRepetition = (float) Math.min(msgLen, 128) / 15.0f * Math.abs(0.5f - fullRep);
            score += wRepetition * cc.textMessageLetterCount;

            // Number of words vs. length of message
            final float fnWords = (float) letterCounts.words.length / (float) msgLen;
            if (fnWords > 0.75f) { // TODO: balance or configure or remove ?
                score += fnWords  * cc.textMessagePartition;
            }
        }

        final CombinedData cData = pData.getGenericInstance(CombinedData.class);
        final long timeout = 8000; // TODO: maybe set dynamically in data.
        // Repetition of last message.
        if (cc.textMsgRepeatSelf != 0f && time - data.chatLastTime < timeout) {
            if (StringUtil.isSimilar(lcMessage, data.chatLastMessage, 0.8f)) {
                final float timeWeight = (float) (timeout - (time - data.chatLastTime)) / (float) timeout; 
                score += cc.textMsgRepeatSelf * timeWeight;
            }
        }
        // Repetition of last global message.
        if (cc.textMsgRepeatGlobal != 0f && time - lastGlobalTime < timeout) {
            if (StringUtil.isSimilar(lcMessage, lastGlobalMessage, 0.8f)) {
                final float timeWeight = (float) (timeout - (time - lastGlobalTime)) / (float) timeout; 
                score += cc.textMsgRepeatGlobal * timeWeight;
            }
        }
        // Repetition of last cancelled message.
        if (cc.textMsgRepeatCancel != 0f && time - lastCancelledTime < timeout) {
            if (StringUtil.isSimilar(lcMessage, lastCancelledMessage, 0.8f)) {
                final float timeWeight = (float) (timeout - (time - lastCancelledTime)) / (float) timeout; 
                score += cc.textMsgRepeatCancel * timeWeight;
            }
        }
        // Chat quickly after join.
        if (cc.textMsgAfterJoin != 0f && time - cData.lastJoinTime < timeout) {
            final float timeWeight = (float) (timeout - (time - cData.lastJoinTime)) / (float) timeout;
            score += cc.textMsgAfterJoin * timeWeight; 
        }
        // Chat without moving.
        if (cc.textMsgNoMoving != 0f && time - cData.lastMoveTime > timeout) {
            score += cc.textMsgNoMoving;
        }



        // Per word checks. -------------------
        float wWords = 0.0f;
        final float avwLen = (float) msgLen / (float) letterCounts.words.length; 
        for (final WordLetterCount word : letterCounts.words) {
            float wWord = 0.0f;
            final int wLen = word.word.length();
            // TODO: ? used letters vs. word length.

            // Length of word vs. av. word length.
            final float fLenAv = Math.abs(avwLen - (float) wLen) / avwLen;
            wWord += fLenAv * cc.textMessageLengthAv;

            // Length of word vs. message length;
            final float fLenMsg = (float) wLen / (float) msgLen;
            wWord += fLenMsg * cc.textMessageLengthMsg;

            // Not letter:
            float notLetter = word.getNotLetterRatio();
            notLetter *= notLetter;
            wWord += notLetter * cc.textMessageNoLetter;

            wWord *= wWord; // TODO: quadratic ? (configurable)
            wWords += wWord;
        }
        wWords /= (float) letterCounts.words.length;
        score += wWords;

        if (debug && score > 0f) {
            debugParts.add("Simple score: " + StringUtil.fdec3.format(score));
        }

        // Engine:
        // TODO: more fine grained sync !
        float wEngine = 0f;
        final Map<String, Float> engMap;
        synchronized (engine) {
            engMap = engine.process(letterCounts, player.getName(), cc, data);
            // TODO: more fine grained sync !s
            // TODO: different methods (add or max or add+max or something else).
            for (final Float res : engMap.values()) {
                if (cc.textEngineMaximum) {
                    wEngine = Math.max(wEngine, res.floatValue());
                }
                else wEngine += res.floatValue();
            }
        }
        score += wEngine;


        // Wrapping it up. --------------------
        // Add weight to frequency counts.
        final float normalScore = Math.max(cc.textFreqNormMin, score);
        data.chatFrequency.add(time, normalScore);
        final float accumulated = cc.textFreqNormWeight * data.chatFrequency.score(cc.textFreqNormFactor);
        final boolean normalViolation = accumulated > cc.textFreqNormLevel;
        final float shortTermScore = Math.max(cc.textFreqShortTermMin, score);
        data.chatShortTermFrequency.add(time, shortTermScore);
        // TODO: very short term (1st bucket) or do it indirectly.
        final float shortTermAccumulated = cc.textFreqShortTermWeight * data.chatShortTermFrequency.score(cc.textFreqShortTermFactor);
        final boolean shortTermViolation = shortTermAccumulated > cc.textFreqShortTermLevel;

        if (normalViolation || shortTermViolation) {
            lastCancelledMessage = lcMessage;
            lastCancelledTime = time;

            final double added;
            if (shortTermViolation) {
                added = (shortTermAccumulated - cc.textFreqShortTermLevel)/ 3.0;
            } 
            else added = (accumulated - cc.textFreqNormLevel) / 10.0; 
            data.textVL += added;

            if (captcha.shouldStartCaptcha(player, cc, data, pData)) {
                captcha.sendNewCaptcha(player, cc, data);
                cancel = true;
            }
            else {
                if (shortTermViolation) {
                    if (executeActions(player, data.textVL, added, cc.textFreqShortTermActions).willCancel()) {
                        cancel = true;
                    }
                }
                else if (normalViolation) {
                    if (executeActions(player, data.textVL, added, cc.textFreqNormActions).willCancel()) {
                        cancel = true;
                    }
                }
            }
        }
        else if (cc.chatWarningCheck && time - data.chatWarningTime > cc.chatWarningTimeout && (100f * accumulated / cc.textFreqNormLevel > cc.chatWarningLevel || 100f * shortTermAccumulated / cc.textFreqShortTermLevel > cc.chatWarningLevel)) {
            NCPAPIProvider.getNoCheatPlusAPI().sendMessageOnTick(player.getName(), ColorUtil.replaceColors(cc.chatWarningMessage));
            data.chatWarningTime = time;
        }
        else {
            data.textVL *= 0.95;
            if (cc.textAllowVLReset && normalScore < 2.0f * cc.textFreqNormWeight && shortTermScore < 2.0f * cc.textFreqShortTermWeight) {
                // Reset the VL.
                // Maybe elaborate on resetting conditions (after some timeout just divide by two or so?).
                data.textVL = 0.0;
            }
        }

        if (debug) {
            final List<String> keys = new LinkedList<String>(engMap.keySet());
            Collections.sort(keys);
            for (String key : keys) {
                Float s = engMap.get(key);
                if (s.floatValue() > 0.0f)
                    debugParts.add(key + ":" + StringUtil.fdec3.format(s));
            }
            if (wEngine > 0.0f)
                debugParts.add("Engine score (" + (cc.textEngineMaximum?"max":"sum") + "): " + StringUtil.fdec3.format(wEngine));

            debugParts.add("Final score: " +  StringUtil.fdec3.format(score));
            debugParts.add("Normal: min=" +  StringUtil.fdec3.format(cc.textFreqNormMin) +", weight=" +  StringUtil.fdec3.format(cc.textFreqNormWeight) + " => accumulated=" + StringUtil.fdec3.format(accumulated));
            debugParts.add("Short-term: min=" +  StringUtil.fdec3.format(cc.textFreqShortTermMin) +", weight=" +  StringUtil.fdec3.format(cc.textFreqShortTermWeight) + " => accumulated=" + StringUtil.fdec3.format(shortTermAccumulated));
            debugParts.add("vl: " + StringUtil.fdec3.format(data.textVL));
            debug(player, StringUtil.join(debugParts, " | "));
            debugParts.clear();
        }

        lastGlobalMessage = data.chatLastMessage = lcMessage;
        lastGlobalTime = data.chatLastTime = time;
        return cancel;
    }
}
