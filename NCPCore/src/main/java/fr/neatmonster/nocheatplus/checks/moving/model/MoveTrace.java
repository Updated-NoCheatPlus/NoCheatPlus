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
package fr.neatmonster.nocheatplus.checks.moving.model;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.Callable;

/**
 * Encapsulate past move tracking here.
 * 
 * @author asofold
 *
 */
public class MoveTrace <MD extends MoveData> {

    // TODO: Class name.
    // TODO: With splitting to MD and VehicleMoveData, a generic type parameter would be needed here. 

    /**
     * Keep track of past moves edge data. First entry always is the last fully
     * processed move, or invalid, even during processing. The currently
     * processed move always is currentMove. The list length always stays the
     * same.
     */
    private final LinkedList<MD> pastMoves = new LinkedList<MD>();

    /**
     * The move currently being processed. Will be inserted to first position
     * when done, and exchanged for the invalidated last element of the past
     * moves.
     */
    private MD currentMove;

    /**
     * Minimal and default size is 2 past moves with current move set extra.
     * 
     * @param factory
     *            Just abused to get instances of the desired type. Not intended
     *            to throw an Exception.
     * @param size
     *            Number of past moves to store extra to the current move.
     *            Higher the number, higher the memory usage.
     */
    public MoveTrace(final Callable<MD> factory, final int size) {
        try {
            // Past moves data: initialize with dummies.
            for (int i = 0; i < size; i++) { // Two past moves allow better workarounds than 1.
                pastMoves.add(factory.call());
            }
            // Current move as well.
            currentMove = factory.call();
        }
        catch (Exception dummy) {
            // Must not happen.
            throw new RuntimeException(dummy);
        }
    }

    public MD getCurrentMove() {
        return currentMove;
    }

    /**
     * Retrieve the number of stored past moves, disregarding the current move.
     * 
     * @return
     */
    public int getNumberOfPastMoves() {
        return pastMoves.size();
    }

    /**
     * Get the first stored past move (latest past move, the current move is not
     * considered).
     * 
     * @return
     */
    public MD getFirstPastMove() {
        return pastMoves.getFirst(); // 0
    }

    /**
     * Get the second stored past move (second-latest past move, the current
     * move is not considered).
     * 
     * @return
     */
    public MD getSecondPastMove() {
        return pastMoves.get(1);
    }
    

    /**
     * Get past move by index (0 is the latest past move, the current move is
     * not considered).
     * 
     * @param index
     * @return
     */
    public MD getPastMove(final int index) {
        return pastMoves.get(index);
    }

    /**
     * Invalidate the current move and all past moves.
     */
    public void invalidate() {
        final Iterator<MD> it = pastMoves.iterator();
        while (it.hasNext()) {
            // TODO: If using many elements ever, stop at the first already invalidated one.
            it.next().invalidate();
        }
        currentMove.invalidate();
    }

    /**
     * Call after processing with a valid the current move field. Insert the
     * current move as first in past moves, set the current move to the
     * invalidated last element of the past moves.
     */
    public void finishCurrentMove() {
        pastMoves.addFirst(currentMove);
        currentMove = pastMoves.removeLast();
        currentMove.invalidate();
    }

    /**
     * Return the current move if valid, or the first past move if valid, or
     * null if neither is valid.
     * 
     * @return
     */
    public MD getLatestValidMove() {
        if (currentMove.valid) {
            return currentMove;
        }
        else {
            final MD move = pastMoves.get(0);
            return move.valid ? move : null;
        }
    }

}
