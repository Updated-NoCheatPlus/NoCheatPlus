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
package fr.neatmonster.nocheatplus.checks.chat.analysis.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.chat.ChatConfig;
import fr.neatmonster.nocheatplus.checks.chat.ChatData;
import fr.neatmonster.nocheatplus.checks.chat.analysis.MessageLetterCount;
import fr.neatmonster.nocheatplus.checks.chat.analysis.engine.processors.FlatWords;
import fr.neatmonster.nocheatplus.checks.chat.analysis.engine.processors.FlatWords.FlatWordsSettings;
import fr.neatmonster.nocheatplus.checks.chat.analysis.engine.processors.SimilarWordsBKL;
import fr.neatmonster.nocheatplus.checks.chat.analysis.engine.processors.SimilarWordsBKL.SimilarWordsBKLSettings;
import fr.neatmonster.nocheatplus.checks.chat.analysis.engine.processors.WordPrefixes;
import fr.neatmonster.nocheatplus.checks.chat.analysis.engine.processors.WordPrefixes.WordPrefixesSettings;
import fr.neatmonster.nocheatplus.checks.chat.analysis.engine.processors.WordProcessor;
import fr.neatmonster.nocheatplus.components.data.IData;
import fr.neatmonster.nocheatplus.components.registry.feature.ConsistencyChecker;
import fr.neatmonster.nocheatplus.components.registry.feature.IHaveCheckType;
import fr.neatmonster.nocheatplus.components.registry.feature.IRemoveData;
import fr.neatmonster.nocheatplus.config.ConfPaths;
import fr.neatmonster.nocheatplus.config.ConfigFile;
import fr.neatmonster.nocheatplus.logging.StaticLog;


/**
 * Process words.
 * @author asofold
 *
 */
public class LetterEngine implements IRemoveData, IHaveCheckType, ConsistencyChecker{

    /** Global processors */
    protected final List<WordProcessor> processors = new ArrayList<WordProcessor>();

    /**
     * Mapping players to data.
     */
    protected final EnginePlayerDataMap dataMap;

    public LetterEngine(ConfigFile config){
        // Add word processors.
        // NOTE: These settings should be compared to the per player settings done in the EnginePlayerConfig constructor.
        if (config.getBoolean(ConfPaths.CHAT_TEXT_GL_WORDS_CHECK, false)) {
            FlatWordsSettings settings = new FlatWordsSettings();
            settings.maxSize = 1000;
            settings.applyConfig(config, ConfPaths.CHAT_TEXT_GL_WORDS);
            processors.add(new FlatWords("glWords",settings));
        }
        if (config.getBoolean(ConfPaths.CHAT_TEXT_GL_PREFIXES_CHECK , false)) {
            WordPrefixesSettings settings = new WordPrefixesSettings();
            settings.maxAdd = 2000;
            settings.applyConfig(config, ConfPaths.CHAT_TEXT_GL_PREFIXES);
            processors.add(new WordPrefixes("glPrefixes", settings));
        }
        if (config.getBoolean(ConfPaths.CHAT_TEXT_GL_SIMILARITY_CHECK , false)) {
            SimilarWordsBKLSettings settings = new SimilarWordsBKLSettings();
            settings.maxSize = 1000;
            settings.applyConfig(config, ConfPaths.CHAT_TEXT_GL_SIMILARITY);
            processors.add(new SimilarWordsBKL("glSimilarity", settings));
        }
        // TODO: At least expiration duration configurable? (Entries expire after 10 minutes.)
        dataMap = new EnginePlayerDataMap(600000L, 100, 0.75f);
    }

    public Map<String, Float> process(final MessageLetterCount letterCount, final String playerName, final ChatConfig cc, final ChatData data) {
        final Map<String, Float> result = new HashMap<String, Float>();
        // Global processors.
        if (cc.textGlobalCheck) {
            for (final WordProcessor processor : processors) {
                try {
                    result.put(processor.getProcessorName(), processor.process(letterCount) * cc.textGlobalWeight);
                }
                catch( final Exception e) {
                    StaticLog.logSevere("chat.text: processor("+processor.getProcessorName()+") generated an exception: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                    StaticLog.logSevere(e);
                    continue;
                }
            }
        }

        // Per player processors.
        if (cc.textPlayerCheck) {
            final EnginePlayerData engineData = dataMap.get(playerName, cc); 
            for (final WordProcessor processor : engineData.processors){
                try {
                    result.put(processor.getProcessorName(), processor.process(letterCount) * cc.textPlayerWeight);
                }
                catch( final Exception e) {
                    StaticLog.logSevere("chat.text: processor("+processor.getProcessorName()+") generated an exception: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                    StaticLog.logSevere(e);
                    continue;
                }
            }
        }

        return result;
    }

    public void clear() {
        for (WordProcessor processor : processors) {
            processor.clear();
        }
        processors.clear();
        dataMap.clear();
    }

    @Override
    public IData removeData(final String playerName) {
        return dataMap.remove(playerName);
    }

    @Override
    public void removeAllData() {
        dataMap.clear();
    }

    @Override
    public final CheckType getCheckType() {
        return CheckType.CHAT_TEXT;
    }

    @Override
    public void checkConsistency(final Player[] onlinePlayers) {
        // Use consistency checking to release some memory.
        final long now = System.currentTimeMillis();
        if (now < dataMap.lastExpired) {
            dataMap.clear();
            return;
        }
        if (now - dataMap.lastExpired > dataMap.durExpire) {
            dataMap.expire(now - dataMap.durExpire);
        }
    }
}
