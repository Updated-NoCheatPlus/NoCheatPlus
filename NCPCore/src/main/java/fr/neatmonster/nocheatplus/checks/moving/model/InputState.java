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

import org.bukkit.Input;

import fr.neatmonster.nocheatplus.compat.BridgeMisc;
import fr.neatmonster.nocheatplus.utilities.math.MathUtil;

/**
 * Carry information regarding the player's key presses (WASD, space bar, shift, sprint).
 */
public class InputState implements Cloneable {
    
    /** (A/D keys, left = 1, right = -1. A value of 0.0 means no strafe movement) */
    private float strafe;
    /** (W/S keys, forward = 1, backward = -1. A value of 0.0 means not moving backward nor forward) */
    private float forward;
    /** Space bar pressing; usually represents jumping, but can also represent flying up, or swimming up */
    private boolean isSpaceBarPressed; 
    /** The shift key, usually represents sneaking */
    private boolean isShift;
    /** The sprint key */
    private boolean isSprinting;
    /** Enum direction of the forward value */
    private ForwardDirection fdir;
    /** Enum direction of the strafe value */
    private StrafeDirection sdir;
    
    /**
     * Empty constructor to let it fail.
     */
    public InputState() {}
    
    /**
     * Composes a new InputState instance based on the given strafe and forward values.
     * Use this constructor only if you don't have access to the {@link org.bukkit.event.player.PlayerInputEvent}, or if you only need horizontal movement.
     * 
     * @param strafe Represents sideways movement.
     * @param forward Represents forward and backward movement.
     */
    public InputState(float strafe, float forward) {
        this.forward = forward;
        this.strafe = strafe;
        fdir = forward >= 0.0 ? forward == 0.0 ? ForwardDirection.NONE : ForwardDirection.FORWARD : ForwardDirection.BACKWARD;
        sdir = strafe >= 0.0 ? strafe == 0.0 ? StrafeDirection.NONE : StrafeDirection.LEFT : StrafeDirection.RIGHT;
    }
    
    /**
     * Composes a new InputState instance based on the given strafe, forward, space bar, sneaking, and sprinting states.
     * Use this constructor only if you have access to player input states (1.21.2+)
     * 
     * @param strafe Represents sideways movement.
     * @param forward Represents forward and backward movement.
     * @param isSpaceBarPressed Whether the space bar is pressed.
     * @param isShift Whether the player is sneaking.
     * @param isSprinting Whether the player is sprinting.
     */
    public InputState(float strafe, float forward, boolean isSpaceBarPressed, boolean isShift, boolean isSprinting) {
        if (!BridgeMisc.hasPlayerInputEvent()) {
            throw new UnsupportedOperationException("PlayerInputEvent is not available.");
        }
        this.strafe = strafe;
        this.forward = forward;
        this.isSpaceBarPressed = isSpaceBarPressed;
        this.isShift = isShift;
        this.isSprinting = isSprinting;
        fdir = forward >= 0.0 ? forward == 0.0 ? ForwardDirection.NONE : ForwardDirection.FORWARD : ForwardDirection.BACKWARD;
        sdir = strafe >= 0.0 ? strafe == 0.0 ? StrafeDirection.NONE : StrafeDirection.LEFT : StrafeDirection.RIGHT;
    }

    /**
     * Composes a new InputState instance based on the dispatched {@link Input}.
     *
     * @param input The given input read from the {@link org.bukkit.event.player.PlayerInputEvent}
     * @throws UnsupportedOperationException if {@link org.bukkit.event.player.PlayerInputEvent} is not available.
     */
    public InputState(Input input) {
        if (!BridgeMisc.hasPlayerInputEvent()) {
            throw new UnsupportedOperationException("Cannot read inputs.");
        }
        this.strafe = input.isLeft() ? 1.0f : input.isRight() ? -1.0f : 0.0f;
        this.forward = input.isForward() ? 1.0f : input.isBackward() ? -1.0f : 0.0f;
        this.isSpaceBarPressed = input.isJump();
        this.isShift = input.isSneak();
        this.isSprinting = input.isSprint();
        this.fdir = forward == 0.0f ? ForwardDirection.NONE : forward > 0.0 ? ForwardDirection.FORWARD : ForwardDirection.BACKWARD;
        this.sdir = strafe == 0.0f ? StrafeDirection.NONE : strafe > 0.0 ? StrafeDirection.LEFT : StrafeDirection.RIGHT;
    }
    
    /**
     * @return the strafe value
     */
    public float getStrafe() {
        return strafe;
    }
    
    /**
     * @return the forward value
     */
    public float getForward() {
        return forward;
    }
    
    /**
     * Whether the player is moving (either strafe or forward).
     * @return True if so.
     */
    public boolean hasHorizontalImpulse() {
        return sdir != StrafeDirection.NONE || fdir != ForwardDirection.NONE;
    }
    
    /**
     * Whether the space bar is pressed.
     * @return True if so.
     */
    public boolean isSpaceBarPressed() {
        return isSpaceBarPressed;
    }
    
    /**
     * Whether the player is sneaking.
     * @return True if so.
     */
    public boolean isShift() {
        return isShift;
    }
    
    /**
     * Whether the player is sprinting.
     * @return True if so.
     */
    public boolean isSprinting() {
        return isSprinting;
    }

    /**
     * @return A clone of this InputDirection
     */
    @Override
    public InputState clone() {
        InputState clonei;
        try {
            clonei = (InputState) super.clone();
        } catch (CloneNotSupportedException e) {
            clonei = new InputState(strafe, forward, isSpaceBarPressed, isShift, isSprinting);
        }
        return clonei;
    }

    /**
     * @return The input squared.
     */
    public double getHorizontalInputSquared() {
        return MathUtil.square(strafe) + MathUtil.square(forward); // Cast to a double because the client does it
    }

    /**
     * Performs an operation on the strafe and forward values using the given factors.
     *
     * @param strafeFactor  The factor used to adjust the strafe value (sideways movement).
     * @param forwardFactor The factor used to adjust the forward value (forward/backward movement).
     * @param operation     The type of operation to perform:
     *                      <ul>
     *                          <li><strong>0:</strong> Resets both strafe and forward values to zero 
     *                          and sets their directions to `NONE`.</li>
     *                          <li><strong>1:</strong> Multiplies the strafe value by {@code strafeFactor}
     *                          and the forward value by {@code forwardFactor}.</li>
     *                          <li><strong>2:</strong> Divides the strafe value by {@code strafeFactor}
     *                          and the forward value by {@code forwardFactor}.</li>
     *                      </ul>
     * @throws IllegalArgumentException if the operation is not 0, 1, or 2.
     */
    public void operationToInt(double strafeFactor, double forwardFactor, int operation) {
        switch (operation) {
            case 0:
                strafe = 0f;
                forward = 0f;
                fdir = ForwardDirection.NONE;
                sdir = StrafeDirection.NONE;
                break;
            case 1:
                strafe *= strafeFactor;
                forward *= forwardFactor;
                break;
            case 2:
                strafe /= strafeFactor;
                forward /= forwardFactor;
                break;
            default:
                throw new IllegalArgumentException("Invalid operation: " + operation + ". Expected 0, 1, or 2.");
        }
    }
    
    /**
     * @return The enum direction that corresponds to the strafe value (LEFT/RIGHT/NONE)
     */
    public StrafeDirection getStrafeDir() {
        return sdir;
    }
    
    /**
     * @return The enum direction that corresponds to the forward value (FORWARD/BACKWARD/NONE)
     */
    public ForwardDirection getForwardDir() {
        return fdir;
    }

    public enum ForwardDirection {
        NONE,
        FORWARD,
        BACKWARD
    }

    public enum StrafeDirection {
        NONE,
        LEFT,
        RIGHT
    }
}
