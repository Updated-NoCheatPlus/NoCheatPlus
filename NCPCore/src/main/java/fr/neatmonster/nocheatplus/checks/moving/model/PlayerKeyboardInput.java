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
import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.utilities.math.MathUtil;

/**
 * Carry information regarding the player's key presses (WASD, space bar, shift, sprint).
 * To not be conflated with the status of the player (isSprinting, isSneaking, etc).<br>
 */
public class PlayerKeyboardInput implements Cloneable {
    
    /** (A/D keys, left = 1, right = -1. A value of 0.0 means no strafe movement) */
    private float strafe;
    /** (W/S keys, forward = 1, backward = -1. A value of 0.0 means not moving backward nor forward) */
    private float forward;
    /** The last strafe value */
    private float lastStrafe;
    /** The last forward value */
    private float lastForward;
    /** Space bar pressing; usually represents jumping, but can also represent flying up, or swimming up */
    private boolean isSpaceBarPressed; 
    /** The last space bar pressing */
    private boolean wasSpaceBarPressed;
    /** The last sprint toggle */
    private boolean wasSprintingKeyPressed;
    /** The last shift toggle */
    private boolean wasShiftPressed;
    /** The shift key toggle, usually represents sneaking */
    private boolean isShiftPressed;
    /** The sprint key toggle */
    private boolean isSprintingKeyPressed;
    /** Enum direction of the forward value */
    private ForwardDirection fdir;
    /** Enum direction of the strafe value */
    private StrafeDirection sdir;
    
    /**
     * Default constructor for serialization/deserialization purposes.
     */
    public PlayerKeyboardInput() {}
    
    /**
     * Composes a new PlayerKeyboardInput instance based on the given strafe, forward only.<br>
     * To be used for in an input loop for horizontal movement only.<br>
     *
     * @param strafe Represents sideways movement.
     * @param forward Represents forward and backward movement.
     */
    public PlayerKeyboardInput(float strafe, float forward) {
        this.strafe = strafe;
        this.forward = forward;
        fdir = forward >= 0.0 ? forward == 0.0 ? ForwardDirection.NONE : ForwardDirection.FORWARD : ForwardDirection.BACKWARD;
        sdir = strafe >= 0.0 ? strafe == 0.0 ? StrafeDirection.NONE : StrafeDirection.LEFT : StrafeDirection.RIGHT;
    }

    /**
     * Sets the input state values in MovingData.
     * 
     * @param input The given input from {@link Player#getCurrentInput()}
     */
    public void set(Input input) {
        lastStrafe = this.strafe;
        lastForward = this.forward;
        wasSprintingKeyPressed = this.isSprintingKeyPressed;
        wasShiftPressed = this.isShiftPressed;
        wasSpaceBarPressed = this.isSpaceBarPressed;
        // do others store here
        this.forward = Boolean.compare(input.isForward(), input.isBackward());
        this.strafe = Boolean.compare(input.isLeft(), input.isRight());
        this.isSpaceBarPressed = input.isJump();
        this.isShiftPressed = input.isSneak();
        this.isSprintingKeyPressed = input.isSprint();
        fdir = forward >= 0.0 ? forward == 0.0 ? ForwardDirection.NONE : ForwardDirection.FORWARD : ForwardDirection.BACKWARD;
        sdir = strafe >= 0.0 ? strafe == 0.0 ? StrafeDirection.NONE : StrafeDirection.LEFT : StrafeDirection.RIGHT;
    }
    
    /**
     * Sets the input state values in MovingData.
     * 
     * @param strafe Strafe value as a float
     * @param forward Forward value as a float
     * @param isSpaceBarPressed Space bar status
     * @param isShiftPressed Shift key status
     * @param isSprintingKeyPressed Sprinting key status.
     */
    public void set(float strafe, float forward, boolean isSpaceBarPressed, boolean isShiftPressed, boolean isSprintingKeyPressed) {
        lastStrafe = this.strafe;
        lastForward = this.forward;
        wasSprintingKeyPressed = this.isSprintingKeyPressed;
        wasShiftPressed = this.isShiftPressed;
        wasSpaceBarPressed = this.isSpaceBarPressed;
        // do others store here
        this.forward = forward;
        this.strafe = strafe;
        this.isSpaceBarPressed = isSpaceBarPressed;
        this.isShiftPressed = isShiftPressed;
        this.isSprintingKeyPressed = isSprintingKeyPressed;
        fdir = forward >= 0.0 ? forward == 0.0 ? ForwardDirection.NONE : ForwardDirection.FORWARD : ForwardDirection.BACKWARD;
        sdir = strafe >= 0.0 ? strafe == 0.0 ? StrafeDirection.NONE : StrafeDirection.LEFT : StrafeDirection.RIGHT;
    }
    
    /**
     * @return the strafe value
     */
    public float getStrafe() {
        return strafe;
    }
    
    /**
     * @return the last strafe value
     */
    public float getLastStrafe() {
        return lastStrafe;
    }
    
    /**
     * @return the forward value
     */
    public float getForward() {
        return forward;
    }
    
    /**
     * @return the last forward value
     */
    public float getLastForward() {
        return lastForward;
    }
    
    /**
     * Whether the player is moving (either strafe or forward).
     * @return True if so.
     */
    public boolean hasHorizontalImpulse() {
        return sdir != StrafeDirection.NONE || fdir != ForwardDirection.NONE;
    }
    
    /**
     * @return The input squared.
     */
    public double getHorizontalInputSquared() {
        return MathUtil.square(strafe) + MathUtil.square(forward); // Cast to a double because the client does it
    }
    
    /**
     * Whether the space bar is pressed.
     * @return True if so.
     */
    public boolean isSpaceBarPressed() {
        return isSpaceBarPressed;
    }
    
    /**
     * Whether the space bar was pressed in the last tick.
     * @return True if so.
     */
    public boolean wasSpaceBarPressed() {
        return wasSpaceBarPressed;
    }
    
    /**
     * Whether the player is sneaking.
     * @return True if so.
     */
    public boolean isShift() {
        return isShiftPressed;
    }
    
    /**
     * Whether the player was sneaking in the last tick.
     * @return True if so.
     */
    public boolean wasShifting() {
        return wasShiftPressed;
    }
    
    /**
     * Whether the player is sprinting.
     * @return True if so.
     */
    public boolean isSprintingKeyPressed() {
        return isSprintingKeyPressed;
    }
    
    /**
     * Whether the player was sprinting in the last tick.
     * @return True if so.
     */
    public boolean wasSprintingKeyPressed() {
        return wasSprintingKeyPressed;
    }

    /** 
     * Creates and returns a copy of this {@code PlayerKeyboardInput} instance.
     * <p>
     * All field values (including directions and key states) are copied to the new
     * instance. 
     *
     * @return a new {@code PlayerKeyboardInput} object containing identical values to this instance
     */
    @Override
    public PlayerKeyboardInput clone() {
        try {
            PlayerKeyboardInput clone = (PlayerKeyboardInput) super.clone();
            return clone;
        } catch (CloneNotSupportedException e) {
            PlayerKeyboardInput clone = new PlayerKeyboardInput(this.strafe, this.forward);
            clone.lastStrafe = this.lastStrafe;
            clone.lastForward = this.lastForward;
            clone.isSpaceBarPressed = this.isSpaceBarPressed;
            clone.wasSpaceBarPressed = this.wasSpaceBarPressed;
            clone.wasSprintingKeyPressed = this.wasSprintingKeyPressed;
            clone.wasShiftPressed = this.wasShiftPressed;
            clone.isShiftPressed = this.isShiftPressed;
            clone.isSprintingKeyPressed = this.isSprintingKeyPressed;
            clone.fdir = this.fdir;
            clone.sdir = this.sdir;
            return clone;
        }
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
