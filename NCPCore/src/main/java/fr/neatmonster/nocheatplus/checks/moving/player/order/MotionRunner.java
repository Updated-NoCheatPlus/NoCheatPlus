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

import java.util.EnumMap;
import java.util.Map;

/**
 * Runs one {@link MotionSchedule}. The phase map is shared by both predictors.
 * Register every {@link MotionPhaseId}; a schedule simply omits the ids it does not run.
 */
public final class MotionRunner {

    private final EnumMap<MotionPhaseId, MotionPhase> phases;

    public MotionRunner(final Map<MotionPhaseId, MotionPhase> phases) {
        final EnumMap<MotionPhaseId, MotionPhase> copy = new EnumMap<MotionPhaseId, MotionPhase>(MotionPhaseId.class);
        if (phases != null) {
            copy.putAll(phases);
        }
        for (final MotionPhaseId id : MotionPhaseId.values()) {
            if (!copy.containsKey(id)) {
                throw new IllegalArgumentException("Missing motion phase: " + id);
            }
        }
        this.phases = copy;
    }

    /**
     * Apply the schedule. After {@link MotionPhaseId#COLLIDE}, {@link MotionState#sampled} is set and
     * {@link MotionState#collidedX} / Y / Z are the distance to compare. Later phases carry motion into the next tick.
     */
    public void run(final MotionSchedule schedule, final MotionState state) {
        state.sampled = false;
        for (final MotionPhaseId id : schedule.phases()) {
            state.phase = id;
            phases.get(id).apply(state, schedule);
            if (id == MotionPhaseId.COLLIDE) {
                state.sampled = true;
            }
        }
    }
}
