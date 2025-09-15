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
package fr.neatmonster.nocheatplus.utilities.collision.supportingblock;

import java.util.Objects;

import org.bukkit.util.Vector;

/**
 * Store information about the position of the supporting block,
 * and whether the entity is currently on the ground.
 * For further details see {@link SupportingBlockUtils}
 */
public class SupportingBlockData {

    private Vector blockPos;
    private boolean onGround;
    
    /**
     * Constructs a new {@link SupportingBlockData} instance.
     *
     * @param blockPos The position of the supporting block, or {@code null} if none.
     * @param onGround {@code true} if the entity is considered to be on the ground, otherwise {@code false}.
     */
    public SupportingBlockData(Vector blockPos, boolean onGround) {
        this.blockPos = blockPos;
        this.onGround = onGround;
    }
    
    /**
     * Retrieves the position of the supporting block.
     *
     * @return A {@link Vector} representing the block position, or {@code null} if none.
     */
    public Vector getBlockPos() {
        return blockPos;
    }
    
    /**
     * Sets the position of the supporting block.
     *
     * @param blockPos A {@link Vector} representing the new block position.
     */
    public void setBlockPos(Vector blockPos) {
        this.blockPos = blockPos;
    }
    
    /**
     * Checks whether the entity is currently on the ground.
     *
     * @return {@code true} if the entity is on the ground, otherwise {@code false}.
     */
    public boolean isOnGround() {
        return onGround;
    }
    
    /**
     * Sets whether the entity is considered to be on the ground.
     *
     * @param onGround {@code true} if the entity is on the ground, otherwise {@code false}.
     */
    public void setOnGround(boolean onGround) {
        this.onGround = onGround;
    }
    
    /**
     * Determines whether the entity was last on the ground without a supporting block.
     *
     * @return {@code true} if the last known state had no block but was on the ground, otherwise {@code false}.
     */
    public boolean lastOnGroundAndNoBlock() {
        return blockPos == null && onGround;
    }
    
    /* 
     * (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "SupportingBlockData{" +
                "blockPos= " + blockPos +
                ", onGround= " + onGround +
                '}';
    }
    
    /* 
     * (non-Javadoc)
     * @see java.lang.Object#equals()
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SupportingBlockData that = (SupportingBlockData) o;
        return onGround == that.onGround &&
                (Objects.equals(blockPos, that.blockPos));
    }
    
    /* 
     * (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        int result = (blockPos != null ? blockPos.hashCode() : 0);
        result = 31 * result + (onGround ? 1 : 0);
        return result;
    }
}

