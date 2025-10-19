package fr.neatmonster.nocheatplus.checks.moving.envelope;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveData;
import fr.neatmonster.nocheatplus.compat.Bridge1_13;
import fr.neatmonster.nocheatplus.compat.Bridge1_9;
import fr.neatmonster.nocheatplus.compat.BridgeMisc;
import fr.neatmonster.nocheatplus.compat.versions.ClientVersion;
import fr.neatmonster.nocheatplus.components.modifier.IAttributeAccess;
import fr.neatmonster.nocheatplus.components.registry.event.IGenericInstanceHandle;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.location.PlayerLocation;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;
import fr.neatmonster.nocheatplus.utilities.math.MathUtil;
import fr.neatmonster.nocheatplus.utilities.moving.Magic;
import fr.neatmonster.nocheatplus.utilities.moving.MovingUtil;

/**
 * Various auxiliary (and hard-coded) methods for moving behaviour modeled after the client or otherwise observed on the server-side.
 */
public class PhysicsEnvelope {
    
    private static final IGenericInstanceHandle<IAttributeAccess> attributeAccess = NCPAPIProvider.getNoCheatPlusAPI().getGenericInstanceHandle(IAttributeAccess.class);
    
    /**
     * Test if the player is constricted in an area with a 1.5 blocks-high ceiling (applies to 1.14 clients and above).
     * We cannot detect if players try to jump in here: on the server side, player is seen as never leaving the ground and without any vertical motion change.
     * 
     * @param from
     * @param pData
     * @return If onGround with ground-like blocks above within a margin of 0.09
     */
    public static boolean isVerticallyConstricted(final PlayerLocation from, final PlayerLocation to, final IPlayerData pData) {
        if (pData.getClientVersion().isLowerThan(ClientVersion.V_1_14)) {
            // The AABB is contracted only for 1.14+ players.
            return false;
        }
        if (!pData.isInCrouchingPose()) {
            return false;
        }
        return from.seekCollisionAbove(0.0899, false) && from.isOnGround() && to.isOnGround() && pData.getGenericInstance(MovingData.class).playerMoves.getCurrentMove().yDistance == 0.0; // Do explicitly demand to have no vertical motion, don't rely on just the ground status.
    }

    /**
     * Test if this move is a bunnyhop <br>
     * (Aka: sprint-jump. Increases the player's speed up to roughly twice the usual base speed)
     *
     * @param forceSetOffGround Currently used to ensure that bunnyhopping isn't applied when we're brute-forcing speed with onGround = false, while the player is constricted in a low-ceiling area,
     *                          due to the fact the client does not send any vertical movement change while in this state.
     * @return True, if isJump() returned true while the player is sprinting.
     */
    public static boolean isBunnyhop(final PlayerLocation from, final PlayerLocation to, final IPlayerData pData, boolean fromOnGround, boolean toOnGround, final Player player, boolean forceSetOffGround) {
        if (!pData.isSprinting()) {
            return false;
        }
        final MovingData data = pData.getGenericInstance(MovingData.class);
        return
                    // 1:  99.9% of cases...
                    isJumpMotion(from, to, player, fromOnGround, toOnGround)
                    // 1: The odd one out. We can't know the ground status of the player, so this will have to do.
                    || isVerticallyConstricted(from, to, pData)
                    && (
                         !forceSetOffGround && pData.getClientVersion().isLowerThan(ClientVersion.V_1_21_2) // At least ensure to not apply this when we're brute-forcing speed with off-ground
                         || BridgeMisc.isSpaceBarImpulseKnown(player) && data.input.isSpaceBarPressed()
                    
                    )
               ;
    }
    
    /**
     * Test if the current motion can qualify as a jump.<br>
     * Note that: 
     * 1) This does not concern the player actual impulse. For that, see {@link BridgeMisc#isSpaceBarImpulseKnown(Player)}.
     * 2) It also does not include upward movement through liquids. While Minecraft considers players as "jumping" if they just press the space bar, we intend jumping in its strict sense (jumping through air)<br><p>
     * For a motion to be considered a legitimate jump, the following conditions must be met:
     * <ul>
     * <li>The player must not be gliding, riptiding, levitating, or in a liquid block.</li>
     * <li>The vertical motion must align with Minecraft's {@code jumpFromGround()} formula 
     *     (defined in {@code EntityLiving.java}).</li>
     * <li>The player must be in a "leaving ground" state, transitioning from ground to air. 
     *     Edge cases, such as lost ground, are accounted for here.</li>
     * </ul>
     * Additionally, invoking this method sets the `headObstruction` flag.
     * @return True, if the motion qualifies as a jump.
     */
    public static boolean isJumpMotion(final PlayerLocation from, final PlayerLocation to, final Player player, boolean fromOnGround, boolean toOnGround) {
        final IPlayerData pData = DataManager.getPlayerData(player);
        final MovingData data = pData.getGenericInstance(MovingData.class);
        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        ////////////////////////////////
        // 0: Early return conditions.
        ////////////////////////////////
        if (!Double.isInfinite(Bridge1_9.getLevitationAmplifier(player)) || Bridge1_13.isRiptiding(player) || thisMove.isGliding || from.isInLiquid()) {
            // Cannot jump for sure under these conditions
            return false;
        }
        ////////////////////////////////////
        // 1: Motion conditions.
        ////////////////////////////////////
        // Validate motion and update the headObstruction flag, if the player does actually collide with something above.
        double jumpGain = data.liftOffEnvelope.getJumpGain(data.jumpAmplifier) * attributeAccess.getHandle().getJumpGainMultiplier(player);
        Vector collisionVector = from.collide(new Vector(0.0, jumpGain, 0.0), fromOnGround || thisMove.fromLostGround, from.getBoundingBox());
        thisMove.headObstructed = jumpGain != collisionVector.getY() && thisMove.yDistance >= 0.0 && !toOnGround; // For setting the flag, we don't care about the correct speed.
        jumpGain = collisionVector.getY();
        if (!MathUtil.almostEqual(thisMove.yDistance, jumpGain, Magic.PREDICTION_EPSILON)) { // NOTE: This must be the current move, never the last one.
            // This is not a jumping motion. Abort early.
            return false;
        }
        //////////////////////////////////
        // 2: Ground conditions.
        //////////////////////////////////
        // Finally, if this was a jumping motion and the player has very little air time, validate the ground status.
        // Demand to be in a "leaving ground" state.
        return
                
                // 1: Ordinary lift off from ground.
                fromOnGround && !toOnGround
                // 1: Special cases; check for some safety pre-conditions first.
                || lastMove.toIsValid && lastMove.yDistance <= 0.0 && !from.seekCollisionAbove() // This behaviour has not hitherto been observed with head obstruction, thus we can confine this edge case by ruling head obstruction cases out. We call seekCollisionAbove() as we don't need accuracy in this case.
                && (
                            /* 
                             * 2: 1-tick-delayed-jump cases: ordinary and with lost ground
                             * By "1-tick-delayed-jump" we mean a specific case where the player jumps, but sends a packet with 0 y-dist while still leaving ground (from ground -> to air)
                             * On the next tick, a packet containing the jump motion (0.42) is sent, but the player is already fully in air (air -> air))
                             * Mostly observed when jumping up a 1-block-high slope and then jumping immediately after, on the edge of the block. 
                             * Technically, this should be considered a lost ground case, however the ground status is detected in this case, just with a delay.
                             * https://gyazo.com/dfab44980c71dc04e62b48c4ffca778e
                             */ 
                            lastMove.from.onGround && !lastMove.to.onGround && !thisMove.touchedGroundWorkaround // Explicitly demand to not be using a lost ground case here.
                            // 2: However, sometimes the ground detection is missed, making this "delayed jump" a true lost-ground case.
                            || (thisMove.touchedGroundWorkaround && (!lastMove.touchedGroundWorkaround || !thisMove.to.onGround)) // TODO: Check which position (fromLostGround or toLostGround). This definition was added prior to adding the distinguishing flags.
                            /*
                             * 2: Jumping while breaking blocks below.
                             * https://gyazo.com/8cb8f94217ee476b33637e15e65ed53c
                             * Sometimes, players can seemingly jump from the "to" position of the last move.
                             * In reality, the player is jumping from ground, but the ground is broken in the same tick, thus the "from" position of this move is already in air.
                             */
                            || !fromOnGround && !toOnGround && lastMove.to.onGround && !lastMove.from.onGround && !thisMove.touchedGroundWorkaround // Explicitly demand to not be using a lost ground case here. for further safety.
                            // && TrigUtil.isSamePosAndLook(thisMove.from, lastMove.to)
                )
                
            ;
    }

    /**
     * Test if this movement fits into NoCheatPlus' stepping envelope.<br>
     * For NoCheatPlus, "step" has a much simpler meaning: moving <b>from</b> ground <b>to</b> ground without having to jump with the correct motion (with some exceptions due to lost ground)<br>
     * Minecraft has a much more complex way of determining when players should be able to step; 
     * the logic is encapsulated in {@link fr.neatmonster.nocheatplus.utilities.location.RichEntityLocation#collide(Vector, boolean, double[])}
     * @return True if this movement is from and to ground with positive yDistance, as determined by the attribute parameter.
     */
    public static boolean isStepUpByNCPDefinition(final IPlayerData pData, boolean fromOnGround, boolean toOnGround, Player player) {
         final MovingData data = pData.getGenericInstance(MovingData.class);
         final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
         // Step-up is handled by the collide() function in Minecraft, which is called on every move, so one could technically step up even while ripdiing or gliding.
         return  
                fromOnGround && toOnGround && MathUtil.almostEqual(thisMove.yDistance, attributeAccess.getHandle().getMaxStepUp(player), Magic.PREDICTION_EPSILON)
                // 0: Wildcard couldstep
                || thisMove.couldStepUp
                // If the step-up movement doesn't fall into any of the criteria above, let the collide() function handle it instead.
            ;
    }

    /**
     * First move after set back / teleport. Originally has been found with
     * PaperSpigot for MC 1.7.10, however it also does occur on Spigot for MC
     * 1.7.10.
     *
     * @param data
     * @return
     */
    public static boolean couldBeSetBackLoop(final MovingData data) {
        // TODO: Confine to from at block level (offset 0)?
        final double setBackYDistance;
        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        if (data.hasSetBack()) {
            setBackYDistance = thisMove.to.getY() - data.getSetBackY();
        }
        // Skip being all too forgiving here.
        //        else if (thisMove.touchedGround) {
        //            setBackYDistance = 0.0;
        //        }
        else {
            return false;
        }
        return !lastMove.toIsValid && data.sfJumpPhase == 0 && thisMove.multiMoveCount > 0
                && setBackYDistance > 0.0 && setBackYDistance < Magic.PAPER_DIST 
                && thisMove.yDistance > 0.0 && thisMove.yDistance < Magic.PAPER_DIST && inAir(thisMove);
    }

    /**
     * Pre-conditions: A slime block is underneath and the player isn't really
     * sneaking (with negative motion with thisMove). This does not account for pistons pushing (slime) blocks.<br>
     * 
     * @param player
     * @param from
     * @param to
     * @param data
     * @param cc
     * @return
     */
    public static boolean canBounce(final Player player, final PlayerLocation from, final PlayerLocation to, 
                                    final MovingData data, final MovingConfig cc, final IPlayerData pData) {
        
        // Workaround/fix for bed bouncing. getBlockY() would return an int, while a bed's maxY is 0.5625, causing this method to always return false.
        // A better way to do this would to get the maxY through another method, just can't seem to find it :/
        if (pData.isShiftKeyPressed()) {
            return false;
        }
        double blockY = (to.getY() + 0.4375) % 1 == 0 ? to.getY() : to.getBlockY();
        return 
                // 0: Normal envelope (forestall NoFall).
                MovingUtil.getRealisticFallDistance(player, from.getY(), to.getY(), data, pData) > 1.0
                && (
                    // 1: Ordinary.
                    to.getY() - blockY <= Math.max(cc.yOnGround, cc.noFallyOnGround)
                    // 1: With carpet.
                    || BlockProperties.isCarpet(to.getBlockType()) && to.getY() - to.getBlockY() <= 0.9
                ) 
                // 0: Within wobble-distance.
                || to.getY() - blockY < 0.286 && to.getY() - from.getY() > -0.9
                && to.getY() - from.getY() < -Magic.GRAVITY_MIN
                && !to.isOnGround()
                // 0: Wildcard riptiding. No point in checking for distance constraints here when speed is so high.
                || Bridge1_13.isRiptiding(player)
                // 0: Wildcard micro bounces on beds
                || to.isOnGround() && !from.isOnGround() && to.getY() - from.getY() < 0.0 
                && MovingUtil.getRealisticFallDistance(player, from.getY(), to.getY(), data, pData) <= 0.5 // 0.5... Can probably be even smaller, since these are micro bounces.
                && to.isOnBouncyBlock() && !to.isOnSlimeBlock()
                ;
    }
    
    /**
     * Fully in-air move.
     * 
     * @param thisMove
     *            Not strictly the latest move in MovingData.
     * @return
     */
    public static boolean inAir(final PlayerMoveData thisMove) {
        return !thisMove.touchedGround && !thisMove.from.resetCond && !thisMove.to.resetCond;
    }
    
    /**
     * A liquid -> liquid move.
     * 
     * @param thisMove
     * @return
     */
    public static boolean inLiquid(final PlayerMoveData thisMove) {
        return thisMove.from.inLiquid && thisMove.to.inLiquid;
    }
    
    /**
     * A water -> water move.
     * 
     * @param thisMove
     * @return
     */
    public static boolean inWater(final PlayerMoveData thisMove) {
        return thisMove.from.inWater && thisMove.to.inWater;
    }
    
    /**
     * Test if either point is in reset condition (liquid, web, ladder).
     * 
     * @param thisMove
     * @return
     */
    public static boolean resetCond(final PlayerMoveData thisMove) {
        return thisMove.from.resetCond || thisMove.to.resetCond;
    }
    
    /**
     * Moving out of liquid, might move onto ground.
     * 
     * @param thisMove
     * @return
     */
    public static boolean leavingLiquid(final PlayerMoveData thisMove) {
        return thisMove.from.inLiquid && !thisMove.to.inLiquid;
    }
    
    /**
     * Moving out of water, might move onto ground.
     * 
     * @param thisMove
     * @return
     */
    public static boolean leavingWater(final PlayerMoveData thisMove) {
        return thisMove.from.inWater && !thisMove.to.inWater;
    }
    
    /**
     * Moving into water, might move onto ground.
     * 
     * @param thisMove
     * @return
     */
    public static boolean intoWater(final PlayerMoveData thisMove) {
        return !thisMove.from.inWater && thisMove.to.inWater;
    }
    
    /**
     * Moving into liquid., might move onto ground.
     * 
     * @param thisMove
     * @return
     */
    public static boolean intoLiquid(final PlayerMoveData thisMove) {
        return !thisMove.from.inLiquid && thisMove.to.inLiquid;
    }
}
