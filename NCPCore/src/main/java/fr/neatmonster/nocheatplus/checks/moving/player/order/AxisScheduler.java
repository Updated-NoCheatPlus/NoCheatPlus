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

/**
 * Runs one axis through a {@link MotionSchedule} without mixing it with the other axis.
 * <p>
 * The packet distance is the result of {@link MotionPhaseId#COLLIDE}. Phases after that id are the
 * previous tick's carried velocity, so they run first. Phases before it are this tick, up to and including collide.
 * Horizontal and vertical each pass their own {@code apply} and ignore ids they do not implement.
 */
public final class AxisScheduler {

    public interface Apply {
        void apply(MotionPhaseId id);
    }

    private AxisScheduler() {
    }

    /**
     * @param betweenCarryAndPre velocity and other inserts that sit after travel effects and before this tick's input
     */
    public static void carryThenPre(final MotionSchedule schedule, final Apply apply, final Runnable betweenCarryAndPre) {
        if (schedule == null || apply == null) {
            return;
        }
        for (final MotionPhaseId id : schedule.phases()) {
            if (schedule.carriesIntoNextTick(id)) {
                apply.apply(id);
            }
        }
        if (betweenCarryAndPre != null) {
            betweenCarryAndPre.run();
        }
        for (final MotionPhaseId id : schedule.phases()) {
            if (schedule.carriesIntoNextTick(id)) {
                continue;
            }
            apply.apply(id);
            if (id == MotionPhaseId.COLLIDE) {
                break;
            }
        }
    }
}
