/*
 *   This program is free software: you can redistribute it and/or modify
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
package fr.neatmonster.nocheatplus.checks.chat.analysis.engine.processors;

import fr.neatmonster.nocheatplus.checks.chat.analysis.MessageLetterCount;


public interface WordProcessor {
    
    /**
     * For debugging purposes.
     * @return
     */
    public String getProcessorName(); 
    
    /**
     * Configured weight.
     * @return
     */
    public float getWeight();
    
    /**
     * 
     * @param message
     * @return A number ranging from 0 to 1. 0 means no matching, 1 means high repetition score.
     */
    public float process(MessageLetterCount message);
    
    /**
     * Clear all held data.
     */
    public void clear();
}
