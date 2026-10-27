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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.checks.net.transaction.TransactionChannel;
import fr.neatmonster.nocheatplus.components.data.IData;
import fr.neatmonster.nocheatplus.components.data.IDataOnJoin;
import fr.neatmonster.nocheatplus.components.data.IDataOnLeave;
import fr.neatmonster.nocheatplus.logging.StaticLog;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.collision.CollisionUtil;

/**
 * Per-player picture of the entities that player’s client currently has.
 * <p>
 * Outgoing entity packets are not applied immediately. Each change is hung on
 * the transaction sequence in flight before the packet, and again on the next
 * one. The first run starts interpolation; the second run is the proof that
 * the client has processed the packet, because a transaction is sent after it
 * and the client answers transactions in order. The clock itself lives in
 * {@link TransactionChannel} so other checks can queue confirmations on it.
 */
public final class ClientEntityTracker implements IData, IDataOnJoin, IDataOnLeave {

    private static final double PUSH_EXPAND = 0.2D;
    private static boolean warned;

    private final Object lock = new Object();
    private final Map<Integer, ClientEntity> entities = new HashMap<Integer, ClientEntity>();
    private final Map<Integer, Outgoing> outgoing = new HashMap<Integer, Outgoing>();
    private final Map<Integer, Integer> pendingAttach = new HashMap<Integer, Integer>();
    private final Set<Integer> destroyedSincePing = new HashSet<Integer>();
    private final Set<Integer> boosting = new HashSet<Integer>();
    private final Set<Integer> boostingRemoveNextTick = new HashSet<Integer>();

    private TransactionChannel channel;
    private int generation;
    private int selfId = -1;
    private int vehicleId = -1;
    private int serverVehicleId = -1;
    private int clockSent;
    private boolean active;
    private boolean sawClientTickEnd;
    private boolean hadBoostingFirework;

    @Override
    public boolean dataOnJoin(Player player, IPlayerData pData) {
        onJoin(player);
        return false;
    }

    @Override
    public boolean dataOnLeave(Player player, IPlayerData pData) {
        onLeave();
        return false;
    }

    public void onJoin(Player player) {
        synchronized (lock) {
            resetEntities();
            active = true;
            sawClientTickEnd = false;
            try {
                selfId = player.getEntityId();
            }
            catch (Throwable ignored) {
                selfId = -1;
            }
            sendClock(player);
        }
    }

    public void onLeave() {
        synchronized (lock) {
            active = false;
            resetEntities();
            selfId = -1;
            channel = null;
        }
    }

    /** Drop client entities after a respawn or world change. The server sends them again. */
    public void invalidate() {
        synchronized (lock) {
            resetEntities();
        }
    }

    public void onEndOfTick(Player player) {
        synchronized (lock) {
            if (!active) {
                active = true;
                try {
                    selfId = player.getEntityId();
                }
                catch (Throwable ignored) {
                    selfId = -1;
                }
            }
            sendClock(player);
            if (!boostingRemoveNextTick.isEmpty()) {
                boosting.removeAll(boostingRemoveNextTick);
                boostingRemoveNextTick.clear();
            }
        }
    }

    public void onClientTick(boolean endOfTick) {
        synchronized (lock) {
            if (endOfTick) {
                sawClientTickEnd = true;
            }
            else if (sawClientTickEnd) {
                return;
            }
            for (ClientEntity entity : entities.values()) {
                entity.tick();
            }
        }
    }

    public void onSpawn(Player player, int id, UUID uuid, ClientEntity.Kind kind, String typeName, int typeId,
            double x, double y, double z, boolean positionKnown, Float yaw, Float pitch) {
        synchronized (lock) {
            ensureReady(player);
            if (isSelf(id)) {
                return;
            }
            if (destroyedSincePing.contains(Integer.valueOf(id))) {
                // The destroy is still queued. A new ping lets it run before this spawn.
                sendClock(player);
            }
            final Outgoing sent = new Outgoing();
            sent.uuid = uuid;
            sent.kind = kind == null ? ClientEntity.Kind.OBJECT : kind;
            sent.typeName = typeName;
            sent.typeId = typeId;
            sent.positionKnown = positionKnown;
            if (yaw != null) {
                sent.yaw = yaw.floatValue();
            }
            if (pitch != null) {
                sent.pitch = pitch.floatValue();
            }
            if (positionKnown) {
                sent.position.setAbsolute(x, y, z);
            }
            sent.firework = ClientEntity.isFireworkName(typeName);
            Integer attached = pendingAttach.remove(Integer.valueOf(id));
            if (sent.firework && attached != null && selfId != -1 && attached.intValue() == selfId) {
                sent.boosting = true;
            }
            outgoing.put(Integer.valueOf(id), sent);
            final int token = generation;
            final int createdOn = clockSent;
            final double spawnX = sent.position.x;
            final double spawnY = sent.position.y;
            final double spawnZ = sent.position.z;
            final boolean spawnKnown = sent.positionKnown;
            final UUID spawnUuid = sent.uuid;
            final ClientEntity.Kind spawnKind = sent.kind;
            final String spawnType = sent.typeName;
            final int spawnTypeId = sent.typeId;
            final Float spawnYaw = yaw;
            final Float spawnPitch = pitch;
            addTask(createdOn, new Runnable() {
                @Override
                public void run() {
                    if (token != generation) {
                        return;
                    }
                    ClientEntity entity = new ClientEntity(id);
                    entity.spawn(spawnUuid, spawnKind, spawnType, spawnTypeId,
                            spawnX, spawnY, spawnZ, spawnKnown, spawnYaw, spawnPitch, sent.dead, sent.invisible);
                    entities.put(Integer.valueOf(id), entity);
                    if (sent.boosting) {
                        rememberBoost(id, entity);
                    }
                }
            });
            addTask(createdOn + 1, new Runnable() {
                @Override
                public void run() {
                    if (token != generation) {
                        return;
                    }
                    ClientEntity entity = entities.get(Integer.valueOf(id));
                    if (entity != null) {
                        entity.confirm(createdOn + 1);
                    }
                }
            });
        }
    }

    public void onRelativeMove(Player player, int id, long dx, long dy, long dz, boolean hasPosition, Float yaw, Float pitch) {
        synchronized (lock) {
            ensureReady(player);
            if (isSelf(id)) {
                return;
            }
            final Outgoing sent = outgoing.get(Integer.valueOf(id));
            if (sent == null) {
                return;
            }
            if (sent.hung == clockSent) {
                // Two relative moves cannot share one transaction, or the client interpolates both at once.
                sendClock(player);
            }
            if (hasPosition && sent.positionKnown) {
                sent.position.addEncoded(dx, dy, dz);
            }
            if (yaw != null) {
                sent.yaw = yaw.floatValue();
            }
            if (pitch != null) {
                sent.pitch = pitch.floatValue();
            }
            sent.hung = clockSent;
            queueRelative(id, dx, dy, dz, hasPosition, yaw, pitch);
        }
    }

    public void onAbsoluteMove(Player player, int id, double x, double y, double z,
            boolean relativeX, boolean relativeY, boolean relativeZ, Float yaw, Float pitch, boolean snap) {
        synchronized (lock) {
            ensureReady(player);
            if (isSelf(id)) {
                return;
            }
            final Outgoing sent = outgoing.get(Integer.valueOf(id));
            if (sent == null) {
                return;
            }
            if (relativeX || relativeY || relativeZ) {
                if (!sent.positionKnown) {
                    return;
                }
                if (relativeX) {
                    x = sent.position.x + x;
                }
                if (relativeY) {
                    y = sent.position.y + y;
                }
                if (relativeZ) {
                    z = sent.position.z + z;
                }
            }
            sent.position.setAbsolute(x, y, z);
            sent.positionKnown = true;
            if (yaw != null) {
                sent.yaw = yaw.floatValue();
            }
            if (pitch != null) {
                sent.pitch = pitch.floatValue();
            }
            sent.hung = clockSent;
            queueAbsolute(id, x, y, z, yaw, pitch, snap);
        }
    }

    public void onDestroy(Player player, int[] ids) {
        if (ids == null || ids.length == 0) {
            return;
        }
        synchronized (lock) {
            ensureReady(player);
            final int[] copy = ids.clone();
            for (int index = 0; index < copy.length; index++) {
                Integer key = Integer.valueOf(copy[index]);
                outgoing.remove(key);
                pendingAttach.remove(key);
                destroyedSincePing.add(key);
                if (serverVehicleId == copy[index]) {
                    serverVehicleId = -1;
                }
            }
            final int confirm = clockSent + 1;
            final int token = generation;
            addTask(confirm, new Runnable() {
                @Override
                public void run() {
                    if (token != generation) {
                        return;
                    }
                    for (int index = 0; index < copy.length; index++) {
                        int removed = copy[index];
                        removeEntity(removed);
                        if (boosting.contains(Integer.valueOf(removed))) {
                            boostingRemoveNextTick.add(Integer.valueOf(removed));
                        }
                    }
                }
            });
        }
    }

    /**
     * @param sharedFlags entity flags byte, or null when this packet did not include index 0
     * @param fireworkAttached entity the rocket is shot at, -1 when that field is present but empty,
     *        null when the packet did not carry the field
     */
    public void onMetadata(Player player, int id, Byte sharedFlags, Integer fireworkAttached) {
        synchronized (lock) {
            ensureReady(player);
            if (isSelf(id)) {
                return;
            }
            Outgoing sent = outgoing.get(Integer.valueOf(id));
            final boolean hasFlags = sharedFlags != null;
            final boolean invisible = hasFlags && (sharedFlags.byteValue() & 0x20) != 0;
            if (sent != null && hasFlags) {
                sent.invisible = invisible;
            }
            final boolean knowFirework = sent != null && sent.firework;
            if (fireworkAttached != null) {
                if (sent == null) {
                    pendingAttach.put(Integer.valueOf(id), fireworkAttached);
                }
                else if (sent.firework && selfId != -1 && fireworkAttached.intValue() == selfId) {
                    sent.boosting = true;
                }
                else if (sent.firework) {
                    sent.boosting = false;
                }
            }
            final boolean boost = fireworkAttached != null && selfId != -1 && fireworkAttached.intValue() == selfId;
            final boolean detach = fireworkAttached != null && !boost;
            final int token = generation;
            if (!hasFlags && fireworkAttached == null) {
                return;
            }
            addTask(clockSent, new Runnable() {
                @Override
                public void run() {
                    if (token != generation) {
                        return;
                    }
                    ClientEntity entity = entities.get(Integer.valueOf(id));
                    if (entity != null && hasFlags) {
                        entity.setInvisible(invisible);
                    }
                    boolean firework = knowFirework || (entity != null && entity.isFirework());
                    if (!firework || fireworkAttached == null) {
                        return;
                    }
                    if (boost) {
                        rememberBoost(id, entity);
                    }
                    else if (detach && boosting.contains(Integer.valueOf(id))) {
                        boostingRemoveNextTick.add(Integer.valueOf(id));
                        if (entity != null) {
                            entity.setBoosting(false);
                        }
                    }
                }
            });
        }
    }

    public void onStatus(Player player, int id, byte status) {
        if (status != 3) {
            return;
        }
        synchronized (lock) {
            ensureReady(player);
            if (isSelf(id)) {
                return;
            }
            Outgoing sent = outgoing.get(Integer.valueOf(id));
            if (sent != null) {
                sent.dead = true;
            }
            final int token = generation;
            addTask(clockSent, new Runnable() {
                @Override
                public void run() {
                    if (token != generation) {
                        return;
                    }
                    ClientEntity entity = entities.get(Integer.valueOf(id));
                    if (entity != null) {
                        entity.setDead(true);
                    }
                    if (boosting.contains(Integer.valueOf(id))) {
                        boostingRemoveNextTick.add(Integer.valueOf(id));
                    }
                }
            });
        }
    }

    public void onMount(Player player, int vehicle, int[] passengers) {
        synchronized (lock) {
            ensureReady(player);
            final int[] riding = passengers == null ? new int[0] : passengers.clone();
            boolean affectsPlayer = serverVehicleId == vehicle || contains(riding, selfId);
            if (affectsPlayer) {
                sendClock(player);
            }
            if (contains(riding, selfId)) {
                serverVehicleId = vehicle;
            }
            else if (serverVehicleId == vehicle) {
                serverVehicleId = -1;
            }
            final int token = generation;
            addTask(clockSent, new Runnable() {
                @Override
                public void run() {
                    if (token != generation) {
                        return;
                    }
                    applyMount(vehicle, riding);
                }
            });
        }
    }

    /**
     * 1.8 attach-entity. {@code vehicle} -1 dismounts the passenger.
     * Leash packets are not mounts and must not be passed here.
     */
    public void onLegacyAttach(Player player, int passenger, int vehicle) {
        synchronized (lock) {
            ensureReady(player);
            if (vehicle == -1) {
                ClientEntity rider = entities.get(Integer.valueOf(passenger));
                int current = rider == null ? serverVehicleId : rider.getVehicleId();
                if (passenger == selfId) {
                    current = serverVehicleId;
                }
                onMount(player, current, new int[0]);
                return;
            }
            onMount(player, vehicle, new int[] { passenger });
        }
    }

    public ClientEntity getEntity(int entityId) {
        synchronized (lock) {
            return snapshot(entityId);
        }
    }

    public List<ClientEntity> getEntities() {
        synchronized (lock) {
            List<ClientEntity> copy = new ArrayList<ClientEntity>(Math.max(entities.size(), outgoing.size()));
            HashSet<Integer> seen = new HashSet<Integer>();
            for (Integer id : entities.keySet()) {
                seen.add(id);
                ClientEntity snap = snapshot(id.intValue());
                if (snap != null) {
                    copy.add(snap);
                }
            }
            for (Integer id : outgoing.keySet()) {
                if (seen.add(id)) {
                    ClientEntity snap = snapshot(id.intValue());
                    if (snap != null) {
                        copy.add(snap);
                    }
                }
            }
            return copy;
        }
    }

    /** Vehicle the client has been told about, after the confirming transaction. -1 if none. */
    public int getVehicleId() {
        synchronized (lock) {
            return vehicleId;
        }
    }

    /** Vehicle from the latest mount packet, before the client has necessarily applied it. -1 if none. */
    public int getServerVehicleId() {
        synchronized (lock) {
            return serverVehicleId;
        }
    }

    public int getLastTransactionSent() {
        TransactionChannel clock = channel;
        return clock == null ? 0 : clock.getLastSent();
    }

    public int getLastTransactionReceived() {
        TransactionChannel clock = channel;
        return clock == null ? 0 : clock.getLastReceived();
    }

    /** Round trip of the latest answered transaction, or -1 if none has come back yet. */
    public long getTransactionPingMillis() {
        TransactionChannel clock = channel;
        return clock == null ? -1L : clock.getPingMillis();
    }

    /**
     * Firework rockets the client currently has attached to this player.
     * Destroy is kept until the next server tick, matching the rocket’s last boost tick.
     */
    public int getBoostingFireworkCount() {
        synchronized (lock) {
            return boosting.size();
        }
    }

    /**
     * True after a rocket was confirmed on this player and then removed.
     * Callers can stop trusting the right-click timer once this is set and the count is 0.
     */
    public boolean hasFinishedFireworkBoost() {
        synchronized (lock) {
            return hadBoostingFirework && boosting.isEmpty();
        }
    }

    /**
     * Entities that push this player on the client, at the position the client is ticking.
     * A mob does this from its own tick: it finds players inside its box and calls {@code player.push(mob)}.
     */
    public List<Location> collidingLocations(Player player, double x, double y, double z, double playerWidth, double playerHeight, boolean log) {
        List<PushSpot> spots = new ArrayList<PushSpot>();
        int mount;
        synchronized (lock) {
            mount = vehicleId != -1 ? vehicleId : serverVehicleId;
            for (ClientEntity entity : entities.values()) {
                if (!canPush(entity) || entity.getId() == selfId) {
                    continue;
                }
                int riding = entity.getVehicleId();
                if (mount != -1 && (entity.getId() == mount || riding == mount)) {
                    continue;
                }
                if (selfId != -1 && riding == selfId) {
                    continue;
                }
                PushSpot spot = new PushSpot();
                spot.id = entity.getId();
                spot.type = entity.getTypeName();
                spot.player = entity.getKind() == ClientEntity.Kind.PLAYER || "PLAYER".equals(typeKey(entity.getTypeName()));
                spot.boat = isBoat(typeKey(entity.getTypeName()));
                spot.x = entity.getX();
                spot.y = entity.getY();
                spot.z = entity.getZ();
                spot.serverX = entity.getServerX();
                spot.serverY = entity.getServerY();
                spot.serverZ = entity.getServerZ();
                double[] sized = box(entity);
                spot.half = sized[0] * 0.5D;
                spot.height = sized[1];
                spots.add(spot);
            }
        }
        List<Location> locations = new ArrayList<Location>();
        if (spots.isEmpty() || player == null || player.getWorld() == null) {
            return locations;
        }
        double half = (playerWidth > 0.0D ? playerWidth : 0.6D) * 0.5D;
        double height = playerHeight > 0.0D ? playerHeight : 1.8D;
        double pMinX = x - half;
        double pMaxX = x + half;
        double pMinY = y;
        double pMaxY = y + height;
        double pMinZ = z - half;
        double pMaxZ = z + half;
        Map<Integer, Entity> nearby = nearbyById(player, log ? 2.0D : 0.25D);
        for (int index = 0; index < spots.size(); index++) {
            PushSpot spot = spots.get(index);
            Entity bukkit = nearby.get(Integer.valueOf(spot.id));
            double otherHalf = spot.half;
            double otherHeight = spot.height;
            double expandX = spot.boat ? 0.2D : 0.0D;
            double expandY = spot.boat ? -0.01D : 0.0D;
            if (bukkit != null) {
                try {
                    // Team, sleep, and spectator rules apply to players. A mob pushes even when Bukkit says it is not collidable.
                    if (spot.player && !CollisionUtil.exertsPushingForce(bukkit)) {
                        continue;
                    }
                    if (bukkit.getWidth() > 0.0D) {
                        otherHalf = bukkit.getWidth() * 0.5D;
                    }
                    if (bukkit.getHeight() > 0.0D) {
                        otherHeight = bukkit.getHeight();
                    }
                }
                catch (Throwable ignored) {}
            }
            // Boat.tick inflates its box by (0.2, -0.01, 0.2) before it pushes.
            boolean hit = spot.x + otherHalf + expandX > pMinX && spot.x - otherHalf - expandX < pMaxX
                    && spot.y + otherHeight + expandY > pMinY && spot.y - expandY < pMaxY
                    && spot.z + otherHalf + expandX > pMinZ && spot.z - otherHalf - expandX < pMaxZ;
            if (hit && spot.boat && pMinY > spot.y) {
                hit = false;
            }
            double dx = spot.x - x;
            double dz = spot.z - z;
            if (!hit && dx * dx + dz * dz > 4.0D) {
                continue;
            }
            if (log) {
                Location server = bukkit == null ? null : bukkit.getLocation();
                System.out.println("push ac player=(" + x + ", " + y + ", " + z + ")"
                        + " otherId=" + spot.id
                        + " type=" + spot.type
                        + " client=(" + spot.x + ", " + spot.y + ", " + spot.z + ")"
                        + " server=(" + (server == null ? "absent" : server.getX() + ", " + server.getY() + ", " + server.getZ()) + ")"
                        + " hit=" + hit);
            }
            if (hit) {
                locations.add(new Location(player.getWorld(), spot.x, spot.y, spot.z));
            }
        }
        return locations;
    }

    /** Confirmed firework rockets attached to this player, or 0 when the packet hook is off. */
    public static int boostingFireworks(Player player) {
        if (!fr.neatmonster.nocheatplus.checks.net.transaction.TransactionChannel.isHooked() || player == null) {
            return 0;
        }
        IPlayerData data = DataManager.getPlayerDataSafe(player);
        if (data == null) {
            return 0;
        }
        ClientEntityTracker tracker = data.getGenericInstance(ClientEntityTracker.class);
        return tracker == null ? 0 : tracker.getBoostingFireworkCount();
    }

    private ClientEntity snapshot(int entityId) {
        ClientEntity live = entities.get(Integer.valueOf(entityId));
        Outgoing sent = outgoing.get(Integer.valueOf(entityId));
        if (live == null && sent == null) {
            return null;
        }
        if (live == null) {
            ClientEntity pendingSpawn = new ClientEntity(entityId);
            pendingSpawn.spawn(sent.uuid, sent.kind, sent.typeName, sent.typeId,
                    sent.position.x, sent.position.y, sent.position.z, sent.positionKnown,
                    Float.valueOf(sent.yaw), Float.valueOf(sent.pitch), sent.dead, sent.invisible);
            return pendingSpawn;
        }
        ClientEntity copy = live.copy();
        if (sent != null && sent.positionKnown) {
            copy.rememberServer(sent.position.x, sent.position.y, sent.position.z);
        }
        return copy;
    }

    private void queueRelative(final int id, final long dx, final long dy, final long dz, final boolean hasPosition,
            final Float yaw, final Float pitch) {
        final int token = generation;
        final int hung = clockSent;
        final Runnable task = new Runnable() {
            private boolean started;

            @Override
            public void run() {
                if (token != generation) {
                    return;
                }
                ClientEntity entity = entities.get(Integer.valueOf(id));
                if (entity == null) {
                    return;
                }
                if (started) {
                    entity.confirm(hung + 1);
                    return;
                }
                started = true;
                entity.beginRelative(dx, dy, dz, hasPosition, yaw, pitch);
            }
        };
        addTask(hung, task);
        addTask(hung + 1, task);
    }

    private void queueAbsolute(final int id, final double x, final double y, final double z,
            final Float yaw, final Float pitch, final boolean snap) {
        final int token = generation;
        final int hung = clockSent;
        final Runnable task = new Runnable() {
            private boolean started;

            @Override
            public void run() {
                if (token != generation) {
                    return;
                }
                ClientEntity entity = entities.get(Integer.valueOf(id));
                if (entity == null) {
                    return;
                }
                if (started) {
                    entity.confirm(hung + 1);
                    return;
                }
                started = true;
                entity.beginAbsolute(x, y, z, yaw, pitch, snap);
            }
        };
        addTask(hung, task);
        addTask(hung + 1, task);
    }

    private void applyMount(int vehicle, int[] riding) {
        ClientEntity mount = entities.get(Integer.valueOf(vehicle));
        if (mount == null) {
            if (vehicleId == vehicle) {
                vehicleId = -1;
            }
            return;
        }
        int[] previous = mount.getPassengers();
        for (int index = 0; index < previous.length; index++) {
            ClientEntity passenger = entities.get(Integer.valueOf(previous[index]));
            if (passenger != null && passenger.getVehicleId() == vehicle) {
                passenger.setVehicleId(-1);
            }
        }
        mount.setPassengers(riding);
        boolean selfRiding = false;
        for (int index = 0; index < riding.length; index++) {
            ClientEntity passenger = entities.get(Integer.valueOf(riding[index]));
            if (passenger != null) {
                passenger.setVehicleId(vehicle);
            }
            if (riding[index] == selfId) {
                selfRiding = true;
            }
        }
        if (selfRiding) {
            vehicleId = vehicle;
        }
        else if (vehicleId == vehicle) {
            vehicleId = -1;
        }
    }

    private void removeEntity(int id) {
        ClientEntity removed = entities.remove(Integer.valueOf(id));
        if (removed != null) {
            int[] riding = removed.getPassengers();
            for (int index = 0; index < riding.length; index++) {
                ClientEntity passenger = entities.get(Integer.valueOf(riding[index]));
                if (passenger != null && passenger.getVehicleId() == id) {
                    passenger.setVehicleId(-1);
                }
            }
            if (removed.getVehicleId() != -1) {
                ClientEntity mount = entities.get(Integer.valueOf(removed.getVehicleId()));
                if (mount != null) {
                    mount.setPassengers(without(mount.getPassengers(), id));
                }
            }
        }
        if (vehicleId == id) {
            vehicleId = -1;
        }
        for (ClientEntity entity : entities.values()) {
            if (entity.getVehicleId() == id) {
                entity.setVehicleId(-1);
            }
        }
    }

    private void ensureReady(Player player) {
        if (!active) {
            active = true;
            try {
                selfId = player.getEntityId();
            }
            catch (Throwable ignored) {
                selfId = -1;
            }
        }
        channel(player);
        if (clockSent == 0) {
            sendClock(player);
        }
    }

    private void sendClock(Player player) {
        if (!active) {
            return;
        }
        TransactionChannel clock = channel(player);
        if (clock == null) {
            return;
        }
        int sequence = clock.send(player);
        if (sequence > 0) {
            clockSent = sequence;
            destroyedSincePing.clear();
        }
    }

    private void addTask(int transaction, final Runnable runnable) {
        if (runnable == null) {
            return;
        }
        if (transaction <= 0) {
            try {
                runnable.run();
            }
            catch (Throwable throwable) {
                logTask(throwable);
            }
            return;
        }
        TransactionChannel clock = channel;
        if (clock == null) {
            try {
                runnable.run();
            }
            catch (Throwable throwable) {
                logTask(throwable);
            }
            return;
        }
        clock.addTask(transaction, new Runnable() {
            @Override
            public void run() {
                synchronized (lock) {
                    try {
                        runnable.run();
                    }
                    catch (Throwable throwable) {
                        logTask(throwable);
                    }
                }
            }
        });
    }

    private TransactionChannel channel(Player player) {
        if (channel != null) {
            return channel;
        }
        if (player == null) {
            return null;
        }
        IPlayerData data = DataManager.getPlayerDataSafe(player);
        if (data == null) {
            return null;
        }
        channel = data.getGenericInstance(TransactionChannel.class);
        return channel;
    }

    private void resetEntities() {
        generation++;
        entities.clear();
        outgoing.clear();
        pendingAttach.clear();
        destroyedSincePing.clear();
        boosting.clear();
        boostingRemoveNextTick.clear();
        hadBoostingFirework = false;
        vehicleId = -1;
        serverVehicleId = -1;
        clockSent = 0;
    }

    private static void logTask(Throwable throwable) {
        if (!warned) {
            warned = true;
            StaticLog.logWarning("Entity tracker transaction task failed: " + throwable.getMessage());
        }
    }

    private boolean isSelf(int id) {
        return selfId != -1 && id == selfId;
    }

    private static boolean contains(int[] values, int id) {
        if (values == null || id == -1) {
            return false;
        }
        for (int index = 0; index < values.length; index++) {
            if (values[index] == id) {
                return true;
            }
        }
        return false;
    }

    private static int[] without(int[] values, int id) {
        if (values == null || values.length == 0) {
            return new int[0];
        }
        int count = 0;
        for (int index = 0; index < values.length; index++) {
            if (values[index] != id) {
                count++;
            }
        }
        int[] kept = new int[count];
        int cursor = 0;
        for (int index = 0; index < values.length; index++) {
            if (values[index] != id) {
                kept[cursor++] = values[index];
            }
        }
        return kept;
    }

    private void rememberBoost(int id, ClientEntity entity) {
        boosting.add(Integer.valueOf(id));
        boostingRemoveNextTick.remove(Integer.valueOf(id));
        hadBoostingFirework = true;
        if (entity != null) {
            entity.setBoosting(true);
        }
    }

    private static boolean canPush(ClientEntity entity) {
        if (entity.isDead() || !entity.isConfirmed() || !entity.hasPosition() || entity.isFirework()) {
            return false;
        }
        if (entity.getKind() == ClientEntity.Kind.PAINTING) {
            return false;
        }
        String key = typeKey(entity.getTypeName());
        if (entity.getKind() == ClientEntity.Kind.PLAYER || "PLAYER".equals(key)) {
            return true;
        }
        if (neverPushesPlayer(key)) {
            return false;
        }
        // 1.19+ sends every mob through SPAWN_ENTITY, so the kind is often OBJECT. The type name is what matters.
        return entity.getKind() == ClientEntity.Kind.LIVING || isBoat(key) || key.length() > 0;
    }

    private static boolean isBoat(String key) {
        return key.indexOf("BOAT") >= 0 || key.indexOf("RAFT") >= 0;
    }

    /** Mobs whose client tick does not call {@code player.push(this)}. */
    private static boolean neverPushesPlayer(String key) {
        if (key.length() == 0) {
            return false;
        }
        if ("PARROT".equals(key) || "BAT".equals(key) || "ENDER_DRAGON".equals(key)) {
            return true;
        }
        return nonPushable(key) && !isBoat(key);
    }

    private static boolean nonPushable(String key) {
        return key.indexOf("ARMOR_STAND") >= 0
                || key.indexOf("MINECART") >= 0
                || key.indexOf("BOAT") >= 0
                || key.indexOf("FIREWORK") >= 0
                || key.indexOf("ITEM") >= 0
                || key.indexOf("PAINTING") >= 0
                || key.indexOf("EXPERIENCE") >= 0
                || key.indexOf("ARROW") >= 0
                || key.indexOf("TRIDENT") >= 0
                || key.indexOf("SNOWBALL") >= 0
                || key.indexOf("FIREBALL") >= 0
                || key.indexOf("SHULKER_BULLET") >= 0
                || key.indexOf("FISHING") >= 0
                || key.indexOf("POTION") >= 0
                || key.indexOf("PEARL") >= 0
                || key.indexOf("EGG") >= 0
                || key.indexOf("CRYSTAL") >= 0
                || key.indexOf("FALLING") >= 0
                || key.indexOf("TNT") >= 0
                || key.indexOf("AREA_EFFECT") >= 0
                || key.indexOf("EVOKER_FANG") >= 0
                || key.indexOf("LLAMA_SPIT") >= 0
                || key.indexOf("DISPLAY") >= 0
                || key.indexOf("INTERACTION") >= 0
                || key.indexOf("MARKER") >= 0
                || key.indexOf("LEASH") >= 0
                || key.indexOf("EYE_OF_ENDER") >= 0
                || key.indexOf("WIND_CHARGE") >= 0
                || key.indexOf("WITHER_SKULL") >= 0;
    }

    /** width, height. Feet are the tracked position. */
    private static double[] box(ClientEntity entity) {
        if (entity.getKind() == ClientEntity.Kind.PLAYER) {
            return new double[] { 0.6D, 1.8D };
        }
        String key = typeKey(entity.getTypeName());
        if ("PIG".equals(key) || "COW".equals(key) || "MOOSHROOM".equals(key)) {
            return new double[] { 0.9D, "PIG".equals(key) ? 0.9D : 1.4D };
        }
        if ("SHEEP".equals(key)) {
            return new double[] { 0.9D, 1.3D };
        }
        if ("CHICKEN".equals(key)) {
            return new double[] { 0.4D, 0.7D };
        }
        if ("WOLF".equals(key)) {
            return new double[] { 0.6D, 0.85D };
        }
        if (isBoat(key)) {
            return new double[] { 1.375D, 0.5625D };
        }
        if ("SLIME".equals(key) || "MAGMA_CUBE".equals(key)) {
            return new double[] { 2.04D, 2.04D };
        }
        if ("GHAST".equals(key) || "HAPPY_GHAST".equals(key)) {
            return new double[] { 4.0D, 4.0D };
        }
        if ("IRON_GOLEM".equals(key)) {
            return new double[] { 1.4D, 2.7D };
        }
        if ("RAVAGER".equals(key)) {
            return new double[] { 1.95D, 2.2D };
        }
        if ("WARDEN".equals(key)) {
            return new double[] { 0.9D, 2.9D };
        }
        if ("ENDER_DRAGON".equals(key)) {
            return new double[] { 16.0D, 8.0D };
        }
        if ("GIANT".equals(key)) {
            return new double[] { 6.0D, 12.0D };
        }
        if ("HOGLIN".equals(key) || "ZOGLIN".equals(key) || "POLAR_BEAR".equals(key)) {
            return new double[] { 1.4D, 1.4D };
        }
        if ("CAMEL".equals(key)) {
            return new double[] { 1.7D, 2.375D };
        }
        if ("SNIFFER".equals(key)) {
            return new double[] { 1.9D, 1.75D };
        }
        if ("SPIDER".equals(key) || "CAVE_SPIDER".equals(key)) {
            return new double[] { 1.4D, 0.9D };
        }
        if ("ENDERMAN".equals(key)) {
            return new double[] { 0.6D, 2.9D };
        }
        if ("WITHER".equals(key)) {
            return new double[] { 0.9D, 3.5D };
        }
        if (key.indexOf("HORSE") >= 0 || "DONKEY".equals(key) || "MULE".equals(key)) {
            return new double[] { 1.3965D, 1.6D };
        }
        return new double[] { 0.6D, 1.95D };
    }

    private static String typeKey(String typeName) {
        if (typeName == null || typeName.length() == 0) {
            return "";
        }
        int colon = typeName.lastIndexOf(':');
        String key = colon >= 0 ? typeName.substring(colon + 1) : typeName;
        return key.toUpperCase(Locale.ENGLISH);
    }

    private static double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        return value > max ? max : value;
    }

    /**
     * Bukkit grows the player box by this margin and returns entities whose box meets it.
     * A push only needs the boat's extra 0.2. Debug also names mobs within 2 blocks horizontally.
     */
    private static Map<Integer, Entity> nearbyById(Player player, double margin) {
        Map<Integer, Entity> found = new HashMap<Integer, Entity>();
        try {
            List<Entity> nearby = player.getNearbyEntities(margin, margin, margin);
            for (int index = 0; index < nearby.size(); index++) {
                Entity entity = nearby.get(index);
                if (entity != null) {
                    found.put(Integer.valueOf(entity.getEntityId()), entity);
                }
            }
        }
        catch (Throwable ignored) {}
        return found;
    }

    private static final class PushSpot {
        int id;
        String type;
        boolean player;
        boolean boat;
        double x;
        double y;
        double z;
        double serverX;
        double serverY;
        double serverZ;
        double half;
        double height;
    }

    private static final class Outgoing {
        final TrackedPosition position = new TrackedPosition();
        UUID uuid;
        ClientEntity.Kind kind = ClientEntity.Kind.OBJECT;
        String typeName;
        int typeId = -1;
        float yaw;
        float pitch;
        boolean positionKnown;
        boolean dead;
        boolean invisible;
        boolean firework;
        boolean boosting;
        int hung = Integer.MIN_VALUE;
    }

}
