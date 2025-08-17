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

import fr.neatmonster.nocheatplus.checks.chat.analysis.engine.processors.FlatWords.FlatWordsSettings;
import fr.neatmonster.nocheatplus.checks.chat.analysis.engine.processors.SimilarWordsBKL.SimilarWordsBKLSettings;
import fr.neatmonster.nocheatplus.checks.chat.analysis.engine.processors.WordPrefixes.WordPrefixesSettings;
import fr.neatmonster.nocheatplus.config.ConfPaths;
import fr.neatmonster.nocheatplus.config.ConfigFile;

/**
 * Per player config for the engine.
 * @author asofold
 *
 */
public class EnginePlayerConfig {
    
    public final boolean ppPrefixesCheck;
    public final WordPrefixesSettings ppPrefixesSettings;
    public final boolean ppWordsCheck;
    public final FlatWordsSettings ppWordsSettings;
    public final boolean ppSimilarityCheck;
    public final SimilarWordsBKLSettings ppSimilaritySettings;
    
    public EnginePlayerConfig(final ConfigFile config) {
        // NOTE: These settings should be compared to the global settings done in the LetterEngine constructor.
        ppWordsCheck = config.getBoolean(ConfPaths.CHAT_TEXT_PP_WORDS_CHECK, false);
        if (ppWordsCheck){
            ppWordsSettings = new FlatWordsSettings();
            ppWordsSettings.maxSize = 150; // Adapt to smaller size.
            ppWordsSettings.applyConfig(config, ConfPaths.CHAT_TEXT_PP_WORDS);
        }
        else ppWordsSettings = null; // spare some memory.
        ppPrefixesCheck = config.getBoolean(ConfPaths.CHAT_TEXT_PP_PREFIXES_CHECK, false);
        if (ppPrefixesCheck){
            ppPrefixesSettings = new WordPrefixesSettings();
            ppPrefixesSettings.maxAdd = 320; // Adapt to smaller size.
            ppPrefixesSettings.applyConfig(config, ConfPaths.CHAT_TEXT_PP_PREFIXES);
        }
        else ppPrefixesSettings = null;
        ppSimilarityCheck = config.getBoolean(ConfPaths.CHAT_TEXT_PP_SIMILARITY_CHECK, false);
        if (ppSimilarityCheck){
            ppSimilaritySettings = new SimilarWordsBKLSettings();
            ppSimilaritySettings.maxSize = 100; // Adapt to smaller size;
            ppSimilaritySettings.applyConfig(config, ConfPaths.CHAT_TEXT_PP_SIMILARITY);
        }
        else ppSimilaritySettings = null;
    }
}
