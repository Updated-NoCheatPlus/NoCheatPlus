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
package fr.neatmonster.nocheatplus.checks.moving.player.order;

import java.util.Collection;

/**
 * Velocity a phase is allowed to edit. Horizontal motion is one pair.
 * Vertical motion is either {@link #motionY} or {@link #motionYCandidates} when the jump or the WASD state is being looped.
 * <p>
 * {@link #collidedX}, {@link #collidedY}, and {@link #collidedZ} are the sampled move, filled by {@link MotionPhaseId#COLLIDE}.
 * Phases after that sample edit {@code motion*} only. That motion is what the next tick starts from.
 */
public final class MotionState {

    public enum AxisOp {
        SET,
        ADD,
        SUBTRACT,
        MULTIPLY,
        DIVIDE;

        public static double apply(final double current, final AxisOp op, final double value) {
            switch (op) {
                case SET:
                    return value;
                case ADD:
                    return current + value;
                case SUBTRACT:
                    return current - value;
                case MULTIPLY:
                    return current * value;
                case DIVIDE:
                    return current / value;
                default:
                    return current;
            }
        }
    }

    public double motionX;
    public double motionY;
    public double motionZ;

    /** Null when {@link #motionY} is the only vertical value. */
    public double[] motionYCandidates;

    public double collidedX;
    public double collidedY;
    public double collidedZ;

    /** Set by {@link MotionRunner} after {@link MotionPhaseId#COLLIDE} returns. */
    public boolean sampled;

    public MotionPhaseId phase;

    /** Optional. Phases call {@link #tag} and do nothing when this is null. */
    public Collection<String> tags;

    public void applyY(final AxisOp op, final double value) {
        if (motionYCandidates == null) {
            motionY = AxisOp.apply(motionY, op, value);
            return;
        }
        for (int i = 0; i < motionYCandidates.length; i++) {
            motionYCandidates[i] = AxisOp.apply(motionYCandidates[i], op, value);
        }
    }

    public void applyHorizontal(final AxisOp op, final double xValue, final double zValue) {
        motionX = AxisOp.apply(motionX, op, xValue);
        motionZ = AxisOp.apply(motionZ, op, zValue);
    }

    public void scaleHorizontal(final double factor) {
        motionX *= factor;
        motionZ *= factor;
    }

    public void tag(final String tag) {
        if (tags != null && tag != null) {
            tags.add(tag);
        }
    }
}
