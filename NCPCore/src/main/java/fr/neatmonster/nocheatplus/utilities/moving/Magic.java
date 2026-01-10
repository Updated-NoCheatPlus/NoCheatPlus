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
package fr.neatmonster.nocheatplus.utilities.moving;


/**
 * A library to confine most / some of the moving magic.
 * @author asofold
 *
 */
public class Magic {
	
	//////////////////////////
	// Vanilla Physics      //
	//////////////////////////
	public static final double DEFAULT_FLYSPEED = 0.1;
    /** EntityLiving, travel */
    public static final float AIR_HORIZONTAL_INERTIA = 0.91f;
    /** EntityLiving, jumpFromGround */
    public static final float BUNNYHOP_BOOST = 0.2f;
    /** EntityLiving, noJumpDelay field */
    public static final int MAX_JUMP_DELAY = 10;
    /** EntityLiving, handleOnClimbable */
    public static final double CLIMBABLE_MAX_SPEED = 0.15f;
    /** EntityLiving, flyingSpeed (NMS field can be misleading, this is for air in general, not strictly creative-fly) */
    public static final float AIR_ACCELERATION = 0.02f;
    /** EntityLiving, travel */
    public static final float LIQUID_ACCELERATION = 0.02f;
    /** EntityLiving, travel */
    public static final float HORIZONTAL_SWIMMING_INERTIA = 0.9f;
    /** EntityLiving, getWaterSlowDown */
    public static final float WATER_HORIZONTAL_INERTIA = 0.8f;
    /** EntityLiving, travel */
    public static final float DOLPHIN_GRACE_INERTIA = 0.96f;
    /** EntityLiving, travel */
    public static final float STRIDER_OFF_GROUND_MULTIPLIER = 0.5f;
    /** LocalPlayer, aiStep */
    public static final float SNEAK_MULTIPLIER = 0.3f;
    /** MCPK */
    public static final float SPRINT_MULTIPLIER = 1.3f;
    /** LocalPlayer, aiStep */
    public static final float USING_ITEM_MULTIPLIER = 0.2f;
    /** EntityHuman, maybeBackOffFromEdge */
    public static final double SNEAK_STEP_DISTANCE = 0.05;
    /** EntityLiving, travel */
    public static final float LAVA_HORIZONTAL_INERTIA = 0.5f;
    /** Result of 0.6^3, where 0.6 is the default block friction (except for ice, blue ice and slime blocks) */
    public static final float DEFAULT_FRICTION_CUBED = 0.6f * 0.6f * 0.6f; // 0.21600002f;
    /** Result of (0.6 * {@link #AIR_HORIZONTAL_INERTIA})^3, where 0.6 is the default block friction factor (except for ice, blue ice and slime blocks). Used by legacy clients. Newer clients use {@link #DEFAULT_FRICTION_CUBED} (not multiplied by 0.91). */
    public static final float DEFAULT_FRICTION_MULTIPLIED_BY_091_CUBED = 0.16277136f;
    /** HoneyBlock */
    public static final double SLIDE_START_AT_VERTICAL_MOTION_THRESHOLD = 0.13;
    /** HoneyBlock */
    public static final float SLIDE_SPEED_THROTTLE = 0.05f;
    /** EntityLiving, aiStep */
    public static final double NEGLIGIBLE_SPEED_THRESHOLD = 0.003;
    /** EntityLiving, aiStep */
    public static final double NEGLIGIBLE_SPEED_THRESHOLD_LEGACY = 0.005;
    /** EntityLiving, jumpInLiquid */
    public static final double LIQUID_SPEED_GAIN = 0.04;
    /** EntityLiving, goDownInWater */
    public static final double LEGACY_LIQUID_GRAVITY = 0.02;
    /** EntityLiving, travel */
    public static final double WATER_VERTICAL_INERTIA = 0.8;
    /** EntityLiving, travel */
    public static final double LAVA_VERTICAL_INERTIA = 0.5;
    /** EntityLiving, travel */
    public static final double SLOW_FALL_GRAVITY = 0.01;
    /** EntityLiving, travel */
    public static final double DEFAULT_GRAVITY = 0.08;
    /** EntityLiving, travel */
    public static final double FRICTION_MEDIUM_AIR = 0.98;
    /** EntityHuman, attack */
    public static final double ATTACK_SLOWDOWN = 0.6;
    /** TridentItem/ItemTrident, releaseItem */
    public static final double RIPTIDE_ON_GROUND_MOVE = 1.2;
    
    
    
    ///////////////////////////////////////////////
    // CraftBukkit/Minecraft constants.          //
    ///////////////////////////////////////////////
    public static final float CB_DEFAULT_WALKSPEED = 0.2f;
    /** Minimum squared distance for bukkit to fire PlayerMoveEvents. PlayerConnection.java */
    public static final double CraftBukkit_minMoveSqDist = 1f / 256;
    /** Minimum distance (square root) for bukkit to fire PlayerMoveEvents. PlayerConnection.java */
    public static final double CraftBukkit_minMoveDist = 0.0625; // Math.sqrt(CraftBukkit_minMoveSqDist);
    /** Minimum looking direction change for bukkit to fire PlayerMoveEvents. PlayerConnection.java */
    public static final float CraftBukkit_minLookChange = 10f;
    /** The minimum squared distance for clients to send flying packets to the server (EntityPlayerSP/LocalPlayer.java, sendPosition()): movements smaller than this are not sent. (Thanks Mojang!) */
    public static final double Minecraft_minMoveSqDistance = 0.03;
    

    
    
    ///////////////////////////////////////////
    // NoCheatPlus Physics / constants       //
    ///////////////////////////////////////////
    // *----------Misc.----------*
    public static final double PAPER_DIST = 0.01;
    /** The margin of error tolerated by predictions */
    public static final double PREDICTION_EPSILON = 0.0001;
    /**
     * Absolute vertical distance that players can cover with a single move
     */
    public static final double EXTREME_MOVE_DIST_VERTICAL = 7.0;
    /**
     * Maximum horizontal distance that players can cover with a single move.
     */
    public static final double EXTREME_MOVE_DIST_HORIZONTAL = 15.0;
    /** Minimal xz-margin for chunk load. */
    public static final double CHUNK_LOAD_MARGIN_MIN = 3.0;
    
    // *----------Gravity----------*
    public static final double GRAVITY_MAX = 0.0834;
    /** Likely the result of minimum gravity for CraftBukkit (below this distance, movemenets won't be fired) */
    public static final double GRAVITY_MIN = 0.0624; 
    /** Odd gravity: to be used with formula lastYDist * data.lastFrictionVertical - gravity, after failing the fallingEnvelope() check (old vDistrel) */
    public static final double GRAVITY_ODD = 0.05;
    /** Assumed minimal average decrease per move, suitable for regarding 3 moves. */
    public static final float GRAVITY_VACC = (float) (GRAVITY_MIN * 0.6); // 0.03744
    /** Span of gravity between maximum and minimum. 0.021 */
    public static final double GRAVITY_SPAN = GRAVITY_MAX - GRAVITY_MIN;
    
    
    // *----------Horizontal speeds/modifiers----------*
    public static final double WALK_SPEED = 0.221D;
    /** Some kind of minimum y descend speed (note the negative sign), for an already advanced gliding/falling phase with elytra. */
    public static final double GLIDE_DESCEND_PHASE_MIN = -Magic.GRAVITY_MAX - Magic.GRAVITY_SPAN;
    /** Somewhat arbitrary, advanced glide phase, maximum descend speed gain (absolute value is negative). */
    public static final double GLIDE_DESCEND_GAIN_MAX_NEG = -GRAVITY_MAX;
    /**
     * Somewhat arbitrary, advanced glide phase, maximum descend speed gain
     * (absolute value is positive, a negative gain seen in relation to the
     * moving direction).
     */
    public static final double GLIDE_DESCEND_GAIN_MAX_POS = GRAVITY_ODD / 1.95;
    
    // *----------On ground judgement----------*
    public static final double Y_ON_GROUND_MIN = 0.000001;
    public static final double Y_ON_GROUND_MAX = 0.01;
    public static final double Y_ON_GROUND_DEFAULT = 0.00001;
    /** LEGACY NCP YONGROUND VALUES */
    // public static final double Y_ON_GROUND_MIN = 0.00001;
    // public static final double Y_ON_GROUND_MAX = 0.0626;
    // TODO: Model workarounds as lost ground, use Y_ON_GROUND_MIN?
    // public static final double Y_ON_GROUND_DEFAULT = 0.025; // Jump upwards, while placing blocks. // Old 0.016
    // public static final double Y_ON_GROUND_DEFAULT = 0.029; // Bounce off slime blocks.
    

    // *----------Falling distance / damage / Nofall----------*
    /** The lower bound of fall distance for taking fall damage. */
    public static final double FALL_DAMAGE_DIST = 3.0;
    /** The minimum damage amount that actually should get applied. */
    public static final double MINIMUM_FALL_DAMAGE = 0.5;
    /**
     * The maximum distance that can be achieved with bouncing back from slime
     * blocks.
     */
    public static final double BOUNCE_VERTICAL_MAX_DIST = 3.5;   
    /** BlockBed, bounceUp method */
    public static final double BED_BOUNCE_MULTIPLIER = 0.66;
    /** BlockBed, bounceUp method */
    public static final double BED_BOUNCE_MULTIPLIER_ENTITYLIVING = 0.8;
    
}
