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
import java.util.List;

import fr.neatmonster.nocheatplus.checks.chat.ChatConfig;
import fr.neatmonster.nocheatplus.checks.chat.analysis.engine.processors.FlatWords;
import fr.neatmonster.nocheatplus.checks.chat.analysis.engine.processors.SimilarWordsBKL;
import fr.neatmonster.nocheatplus.checks.chat.analysis.engine.processors.WordPrefixes;
import fr.neatmonster.nocheatplus.checks.chat.analysis.engine.processors.WordProcessor;
import fr.neatmonster.nocheatplus.components.data.IData;

/**
 * Engine specific player data.
 * @author asofold
 *
 */
public class EnginePlayerData implements IData{
    
    public final List<WordProcessor> processors = new ArrayList<WordProcessor>(5);

    public EnginePlayerData(ChatConfig cc) {
        EnginePlayerConfig config = cc.textEnginePlayerConfig;
        if (config.ppWordsCheck) {
            processors.add(new FlatWords("ppWords", config.ppWordsSettings));
        }
        if (config.ppPrefixesCheck) {
            processors.add(new WordPrefixes("ppPrefixes", config.ppPrefixesSettings));
        }
        if (config.ppSimilarityCheck) {
            processors.add(new SimilarWordsBKL("ppSimilarity", config.ppSimilaritySettings));
        }
    }
    
}
