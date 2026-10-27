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
package fr.neatmonster.nocheatplus.checks.net.entity;

import java.util.Locale;
import java.util.UUID;

/**
 * One entity as this player’s client knows it.
 * <p>
 * The position interpolates the way a client does: relative moves ease over
 * three client ticks, teleports stick on the next confirming transaction.
 * While a move is still in flight, the possible position is the span from
 * where the client was to where the packet is sending it.
 */
public final class ClientEntity {

    public enum Kind {
        PLAYER,
        LIVING,
        OBJECT,
        PAINTING
    }

    private static final int RELATIVE_STEPS = 3;
    private static final double SNAP_DISTANCE_SQ = 4096.0D;

    final int id;
    private final TrackedPosition tracked = new TrackedPosition();

    private UUID uuid;
    private Kind kind = Kind.OBJECT;
    private String typeName;
    private int typeId = -1;
    private boolean dead;
    private boolean spawnConfirmed;
    private boolean uncertain = true;
    private boolean positionKnown;
    private boolean invisible;
    private boolean firework;
    private boolean boosting;
    private int vehicleId = -1;
    private int[] passengers = new int[0];
    private float yaw;
    private float pitch;
    private double x;
    private double y;
    private double z;
    private double fromX;
    private double fromY;
    private double fromZ;
    private double targetX;
    private double targetY;
    private double targetZ;
    private double serverX;
    private double serverY;
    private double serverZ;
    private int steps;
    private int lastTransaction;

    ClientEntity(int id) {
        this.id = id;
    }

    ClientEntity copy() {
        ClientEntity copy = new ClientEntity(id);
        copy.uuid = uuid;
        copy.kind = kind;
        copy.typeName = typeName;
        copy.typeId = typeId;
        copy.dead = dead;
        copy.spawnConfirmed = spawnConfirmed;
        copy.uncertain = uncertain;
        copy.positionKnown = positionKnown;
        copy.invisible = invisible;
        copy.firework = firework;
        copy.boosting = boosting;
        copy.vehicleId = vehicleId;
        copy.passengers = passengers.clone();
        copy.yaw = yaw;
        copy.pitch = pitch;
        copy.x = x;
        copy.y = y;
        copy.z = z;
        copy.fromX = fromX;
        copy.fromY = fromY;
        copy.fromZ = fromZ;
        copy.targetX = targetX;
        copy.targetY = targetY;
        copy.targetZ = targetZ;
        copy.serverX = serverX;
        copy.serverY = serverY;
        copy.serverZ = serverZ;
        copy.steps = steps;
        copy.lastTransaction = lastTransaction;
        copy.tracked.copyFrom(tracked);
        return copy;
    }

    void spawn(UUID uuid, Kind kind, String typeName, int typeId, double x, double y, double z,
            boolean positionKnown, Float yaw, Float pitch, boolean dead, boolean invisible) {
        this.uuid = uuid;
        this.kind = kind == null ? Kind.OBJECT : kind;
        this.typeName = typeName;
        this.typeId = typeId;
        this.dead = dead;
        this.invisible = invisible;
        this.firework = isFireworkName(typeName);
        this.positionKnown = positionKnown;
        this.spawnConfirmed = false;
        this.uncertain = true;
        this.steps = 0;
        if (positionKnown) {
            tracked.setAbsolute(x, y, z);
            this.x = this.fromX = this.targetX = this.serverX = tracked.x;
            this.y = this.fromY = this.targetY = this.serverY = tracked.y;
            this.z = this.fromZ = this.targetZ = this.serverZ = tracked.z;
        }
        if (yaw != null) {
            this.yaw = yaw.floatValue();
        }
        if (pitch != null) {
            this.pitch = pitch.floatValue();
        }
    }

    void beginRelative(long dx, long dy, long dz, boolean hasPosition, Float yaw, Float pitch) {
        applyRotation(yaw, pitch);
        if (!hasPosition || !positionKnown) {
            return;
        }
        if (!uncertain && steps <= 0) {
            fromX = x;
            fromY = y;
            fromZ = z;
        }
        tracked.addEncoded(dx, dy, dz);
        targetX = serverX = tracked.x;
        targetY = serverY = tracked.y;
        targetZ = serverZ = tracked.z;
        steps = RELATIVE_STEPS;
        uncertain = true;
    }

    void beginAbsolute(double x, double y, double z, Float yaw, Float pitch, boolean snap) {
        if (!uncertain && steps <= 0) {
            fromX = this.x;
            fromY = this.y;
            fromZ = this.z;
        }
        double previousX = positionKnown ? tracked.x : x;
        double previousY = positionKnown ? tracked.y : y;
        double previousZ = positionKnown ? tracked.z : z;
        tracked.setAbsolute(x, y, z);
        positionKnown = true;
        targetX = serverX = tracked.x;
        targetY = serverY = tracked.y;
        targetZ = serverZ = tracked.z;
        double distanceSq = distanceSq(previousX, previousY, previousZ, tracked.x, tracked.y, tracked.z);
        steps = (snap || distanceSq > SNAP_DISTANCE_SQ) ? 0 : RELATIVE_STEPS;
        applyRotation(yaw, pitch);
        uncertain = true;
    }

    void confirm(int transaction) {
        uncertain = false;
        spawnConfirmed = true;
        lastTransaction = transaction;
        if (steps <= 0 && positionKnown) {
            x = targetX;
            y = targetY;
            z = targetZ;
        }
    }

    void tick() {
        if (!positionKnown || steps <= 0) {
            return;
        }
        x += (targetX - x) / steps;
        y += (targetY - y) / steps;
        z += (targetZ - z) / steps;
        steps--;
    }

    void rememberServer(double x, double y, double z) {
        this.serverX = x;
        this.serverY = y;
        this.serverZ = z;
    }

    private void applyRotation(Float yaw, Float pitch) {
        if (yaw != null) {
            this.yaw = yaw.floatValue();
        }
        if (pitch != null) {
            this.pitch = pitch.floatValue();
        }
    }

    private static double distanceSq(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        double dz = z1 - z2;
        return dx * dx + dy * dy + dz * dz;
    }

    public int getId() {
        return id;
    }

    public UUID getUniqueId() {
        return uuid;
    }

    public Kind getKind() {
        return kind;
    }

    public String getTypeName() {
        return typeName;
    }

    public int getTypeId() {
        return typeId;
    }

    public boolean isDead() {
        return dead;
    }

    void setDead(boolean dead) {
        this.dead = dead;
    }

    /**
     * True once the spawn packet’s confirming transaction has come back.
     * Until then the client may not have the entity yet.
     */
    public boolean isConfirmed() {
        return spawnConfirmed;
    }

    /**
     * True while the client may still be between the previous position and the
     * latest one, either because the transaction is open or interpolation has
     * steps left.
     */
    public boolean isUncertain() {
        return uncertain || steps > 0;
    }

    public boolean hasPosition() {
        return positionKnown;
    }

    public boolean isInvisible() {
        return invisible;
    }

    void setInvisible(boolean invisible) {
        this.invisible = invisible;
    }

    public boolean isFirework() {
        return firework;
    }

    public boolean isBoosting() {
        return boosting;
    }

    void setBoosting(boolean boosting) {
        this.boosting = boosting;
    }

    static boolean isFireworkName(String typeName) {
        return typeName != null && typeName.toUpperCase(Locale.ENGLISH).indexOf("FIREWORK") >= 0;
    }

    public int getVehicleId() {
        return vehicleId;
    }

    void setVehicleId(int vehicleId) {
        this.vehicleId = vehicleId;
    }

    public int[] getPassengers() {
        return passengers.clone();
    }

    void setPassengers(int[] passengers) {
        this.passengers = passengers == null ? new int[0] : passengers.clone();
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    /** Interpolated position the client is currently drawing. */
    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    /** Position the latest movement packet is taking the client towards. */
    public double getTargetX() {
        return targetX;
    }

    public double getTargetY() {
        return targetY;
    }

    public double getTargetZ() {
        return targetZ;
    }

    /** Last position encoded into a packet, before the client has necessarily applied it. */
    public double getServerX() {
        return serverX;
    }

    public double getServerY() {
        return serverY;
    }

    public double getServerZ() {
        return serverZ;
    }

    public double possibleMinX() {
        return spanMin(fromX, x, targetX);
    }

    public double possibleMaxX() {
        return spanMax(fromX, x, targetX);
    }

    public double possibleMinY() {
        return spanMin(fromY, y, targetY);
    }

    public double possibleMaxY() {
        return spanMax(fromY, y, targetY);
    }

    public double possibleMinZ() {
        return spanMin(fromZ, z, targetZ);
    }

    public double possibleMaxZ() {
        return spanMax(fromZ, z, targetZ);
    }

    private double spanMin(double from, double current, double target) {
        double min = Math.min(current, target);
        if (uncertain || steps > 0) {
            min = Math.min(min, from);
        }
        return min;
    }

    private double spanMax(double from, double current, double target) {
        double max = Math.max(current, target);
        if (uncertain || steps > 0) {
            max = Math.max(max, from);
        }
        return max;
    }

    /**
     * Squared distance from a point to the closest position the client might
     * be using for this entity. {@link Double#MAX_VALUE} when no position is known.
     */
    public double distanceSqToPossiblePosition(double px, double py, double pz) {
        if (!positionKnown) {
            return Double.MAX_VALUE;
        }
        double cx = clamp(px, possibleMinX(), possibleMaxX());
        double cy = clamp(py, possibleMinY(), possibleMaxY());
        double cz = clamp(pz, possibleMinZ(), possibleMaxZ());
        return distanceSq(px, py, pz, cx, cy, cz);
    }

    public int getLastTransaction() {
        return lastTransaction;
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : (value > max ? max : value);
    }

}
