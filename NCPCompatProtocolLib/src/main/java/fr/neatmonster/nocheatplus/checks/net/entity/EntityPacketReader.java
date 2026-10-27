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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.EntityType;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;

import fr.neatmonster.nocheatplus.compat.versions.ServerVersion;

/**
 * ProtocolLib field layout for the entity packets the tracker follows.
 * Relative deltas are left in their packet units (1/32 before 1.9, 1/4096 after).
 */
public final class EntityPacketReader {

    private static boolean loggedUnknownFirework;
    private static boolean loggedUnreadAbsolute;
    private static final boolean AT_LEAST_1_9 = ServerVersion.isAtLeast("1.9");
    private static final boolean AT_LEAST_1_14 = ServerVersion.isAtLeast("1.14");
    private static final boolean AT_LEAST_26_3 = ServerVersion.isAtLeast("26.3");

    public static final class Spawn {
        public int id;
        public UUID uuid;
        public String typeName;
        public int typeId = -1;
        public double x;
        public double y;
        public double z;
        public boolean positionKnown;
        public Float yaw;
        public Float pitch;
    }

    public static final class Relative {
        public int id;
        public boolean hasPosition;
        public long dx;
        public long dy;
        public long dz;
        public Float yaw;
        public Float pitch;
    }

    public static final class Absolute {
        public int id;
        public boolean positionKnown;
        public double x;
        public double y;
        public double z;
        /** When set, that axis is a delta added to the last position the client was given. */
        public boolean relativeX;
        public boolean relativeY;
        public boolean relativeZ;
        public Float yaw;
        public Float pitch;
    }

    public static final class Mount {
        public int vehicleId;
        public int[] passengers = new int[0];
    }

    public static final class Attach {
        public int passengerId;
        public int vehicleId;
        public boolean mount;
    }

    private EntityPacketReader() {}

    public static Spawn readSpawn(PacketContainer packet) {
        Integer id = readInt(packet, 0);
        if (id == null) {
            return null;
        }
        Spawn spawn = new Spawn();
        spawn.id = id.intValue();
        spawn.uuid = readUuid(packet);
        spawn.typeName = readTypeName(packet);
        spawn.typeId = readTypeId(packet);
        fillAbsolute(packet, spawn);
        return spawn;
    }

    public static Relative readRelative(PacketContainer packet, boolean hasPosition, boolean hasLook) {
        Integer id = readInt(packet, 0);
        if (id == null) {
            return null;
        }
        Relative relative = new Relative();
        relative.id = id.intValue();
        if (hasPosition) {
            long[] delta = readDelta(packet);
            if (delta != null) {
                relative.hasPosition = true;
                relative.dx = delta[0];
                relative.dy = delta[1];
                relative.dz = delta[2];
            }
        }
        if (hasLook) {
            readAngles(packet, relative);
        }
        return relative;
    }

    public static Absolute readAbsolute(PacketContainer packet) {
        Integer id = readInt(packet, 0);
        if (id == null) {
            return null;
        }
        Absolute absolute = new Absolute();
        absolute.id = id.intValue();
        fillNestedPosition(packet, absolute);
        if (!absolute.positionKnown) {
            try {
                StructureModifier<Double> doubles = packet.getDoubles();
                Double x = doubles.readSafely(0);
                Double y = doubles.readSafely(1);
                Double z = doubles.readSafely(2);
                if (x != null && y != null && z != null) {
                    absolute.positionKnown = true;
                    absolute.x = x.doubleValue();
                    absolute.y = y.doubleValue();
                    absolute.z = z.doubleValue();
                }
            }
            catch (Throwable ignored) {}
        }
        if (!absolute.positionKnown) {
            int[] legacy = legacyFixedPoint(packet);
            if (legacy != null) {
                absolute.positionKnown = true;
                absolute.x = legacy[0] / 32.0D;
                absolute.y = legacy[1] / 32.0D;
                absolute.z = legacy[2] / 32.0D;
            }
        }
        if (!absolute.positionKnown) {
            noteUnreadAbsolute(packet);
        }
        readAngles(packet, absolute);
        return absolute.positionKnown ? absolute : null;
    }

    /**
     * 1.21.2+ teleport and position-sync store the coordinates inside a position/rotation
     * holder (position vector, velocity vector, yaw, pitch), not as packet-level doubles.
     * A 1.20.6 client receives that holder already resolved to an absolute teleport.
     */
    private static void fillNestedPosition(PacketContainer packet, Absolute absolute) {
        Object handle;
        try {
            handle = packet.getHandle();
        }
        catch (Throwable ignored) {
            return;
        }
        if (handle == null) {
            return;
        }
        Object holder = positionHolder(handle);
        double[] xyz = holder == null ? null : firstVector(holder);
        if (xyz == null) {
            return;
        }
        absolute.positionKnown = true;
        absolute.x = xyz[0];
        absolute.y = xyz[1];
        absolute.z = xyz[2];
        float[] rotation = holder == null ? null : firstTwoFloats(holder);
        if (rotation != null && absolute.yaw == null) {
            absolute.yaw = Float.valueOf(rotation[0]);
            absolute.pitch = Float.valueOf(rotation[1]);
        }
        markRelativeAxes(handle, absolute);
    }

    private static void noteUnreadAbsolute(PacketContainer packet) {
        if (loggedUnreadAbsolute) {
            return;
        }
        loggedUnreadAbsolute = true;
        Object handle = null;
        try {
            handle = packet.getHandle();
        }
        catch (Throwable ignored) {}
        System.out.println("entity position unread " + (handle == null ? "null" : handle.getClass().getName()));
    }

    /** The nested holder, or the packet itself when the vector sits directly on it. */
    private static Object positionHolder(Object packet) {
        if (firstVector(packet) != null) {
            return packet;
        }
        Class<?> type = packet.getClass();
        while (type != null && type != Object.class) {
            Field[] fields = type.getDeclaredFields();
            for (int index = 0; index < fields.length; index++) {
                Field field = fields[index];
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
                    continue;
                }
                Object value = readField(field, packet);
                if (value != null && firstVector(value) != null) {
                    return value;
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    /** First vector on this object: its own three doubles, or the first nested vector field. */
    private static double[] firstVector(Object object) {
        if (object == null) {
            return null;
        }
        double[] own = instanceDoubles(object);
        if (own != null) {
            return own;
        }
        Class<?> type = object.getClass();
        while (type != null && type != Object.class) {
            Field[] fields = type.getDeclaredFields();
            for (int index = 0; index < fields.length; index++) {
                Field field = fields[index];
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
                    continue;
                }
                double[] nested = instanceDoubles(readField(field, object));
                if (nested != null) {
                    return nested;
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static double[] instanceDoubles(Object object) {
        if (object == null) {
            return null;
        }
        Field[] fields = object.getClass().getDeclaredFields();
        int count = 0;
        for (int index = 0; index < fields.length; index++) {
            Field field = fields[index];
            if (!Modifier.isStatic(field.getModifiers()) && field.getType() == double.class) {
                count++;
            }
        }
        if (count < 3) {
            return null;
        }
        double[] values = new double[3];
        int found = 0;
        for (int index = 0; index < fields.length && found < 3; index++) {
            Field field = fields[index];
            if (Modifier.isStatic(field.getModifiers()) || field.getType() != double.class) {
                continue;
            }
            Object raw = readField(field, object);
            if (!(raw instanceof Double)) {
                return null;
            }
            values[found++] = ((Double) raw).doubleValue();
        }
        return found == 3 ? values : null;
    }

    private static float[] firstTwoFloats(Object object) {
        Field[] fields = object.getClass().getDeclaredFields();
        float[] values = null;
        int found = 0;
        for (int index = 0; index < fields.length; index++) {
            Field field = fields[index];
            if (Modifier.isStatic(field.getModifiers()) || field.getType() != float.class) {
                continue;
            }
            Object raw = readField(field, object);
            if (!(raw instanceof Float)) {
                return null;
            }
            if (values == null) {
                values = new float[2];
            }
            values[found++] = ((Float) raw).floatValue();
            if (found == 2) {
                return values;
            }
        }
        return null;
    }

    /**
     * Teleport packets name the axes that are deltas. Position sync has no such set,
     * so every axis stays absolute. Names are the enum constants (X, Y, Z).
     */
    private static void markRelativeAxes(Object packet, Absolute absolute) {
        Class<?> type = packet.getClass();
        while (type != null && type != Object.class) {
            Field[] fields = type.getDeclaredFields();
            for (int index = 0; index < fields.length; index++) {
                Field field = fields[index];
                if (Modifier.isStatic(field.getModifiers()) || !Set.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                Object raw = readField(field, packet);
                if (!(raw instanceof Set)) {
                    continue;
                }
                Set<?> flags = (Set<?>) raw;
                java.util.Iterator<?> iterator = flags.iterator();
                while (iterator.hasNext()) {
                    Object flag = iterator.next();
                    if (!(flag instanceof Enum)) {
                        continue;
                    }
                    String name = ((Enum<?>) flag).name();
                    if ("X".equals(name)) {
                        absolute.relativeX = true;
                    }
                    else if ("Y".equals(name)) {
                        absolute.relativeY = true;
                    }
                    else if ("Z".equals(name)) {
                        absolute.relativeZ = true;
                    }
                }
                return;
            }
            type = type.getSuperclass();
        }
    }

    private static Object readField(Field field, Object target) {
        try {
            field.setAccessible(true);
            return field.get(target);
        }
        catch (Throwable ignored) {
            return null;
        }
    }

    public static int[] readDestroy(PacketContainer packet) {
        try {
            int[] ids = packet.getIntegerArrays().readSafely(0);
            if (ids != null) {
                return ids;
            }
        }
        catch (Throwable ignored) {}
        try {
            List<Integer> list = packet.getIntLists().readSafely(0);
            if (list != null && !list.isEmpty()) {
                int[] ids = new int[list.size()];
                for (int index = 0; index < list.size(); index++) {
                    Integer id = list.get(index);
                    ids[index] = id == null ? -1 : id.intValue();
                }
                return ids;
            }
        }
        catch (Throwable ignored) {}
        Integer one = readInt(packet, 0);
        return one == null ? new int[0] : new int[] { one.intValue() };
    }

    public static Mount readMount(PacketContainer packet) {
        Integer vehicle = readInt(packet, 0);
        if (vehicle == null) {
            return null;
        }
        Mount mount = new Mount();
        mount.vehicleId = vehicle.intValue();
        mount.passengers = readPassengerIds(packet);
        return mount;
    }

    public static Attach readAttach(PacketContainer packet) {
        // 1.8: int 0 is 0 for mount and 1 for leash, then passenger, then vehicle.
        Integer flag = readInt(packet, 0);
        Integer passenger = readInt(packet, 1);
        Integer vehicle = readInt(packet, 2);
        if (flag == null || passenger == null || vehicle == null || flag.intValue() != 0) {
            return null;
        }
        Attach attach = new Attach();
        attach.mount = true;
        attach.passengerId = passenger.intValue();
        attach.vehicleId = vehicle.intValue();
        return attach;
    }

    /**
     * Entity id a firework rocket is attached to, from the metadata index vanilla uses.
     * Null when that index is not in the packet. -1 when the optional is present but empty.
     */
    public static Integer readFireworkAttached(PacketContainer packet) {
        int index = fireworkAttachIndex();
        try {
            List<WrappedDataValue> values = packet.getDataValueCollectionModifier().readSafely(0);
            Integer attached = entityIdAt(values, index);
            if (attached != null || hasIndex(values, index)) {
                return attached;
            }
        }
        catch (Throwable ignored) {}
        try {
            List<WrappedWatchableObject> values = packet.getWatchableCollectionModifier().readSafely(0);
            if (values == null) {
                return null;
            }
            for (int cursor = 0; cursor < values.size(); cursor++) {
                WrappedWatchableObject value = values.get(cursor);
                if (value != null && value.getIndex() == index) {
                    return entityIdOf(value.getValue());
                }
            }
        }
        catch (Throwable ignored) {}
        return null;
    }

    private static int fireworkAttachIndex() {
        // Grim's offset from index 9: 1.12.2 and older use 7, through 1.16.5 use 8, newer use 9.
        if (!ServerVersion.isAtLeast("1.13")) {
            return 7;
        }
        if (!ServerVersion.isAtLeast("1.17")) {
            return 8;
        }
        return 9;
    }

    private static boolean hasIndex(List<WrappedDataValue> values, int index) {
        if (values == null) {
            return false;
        }
        for (int cursor = 0; cursor < values.size(); cursor++) {
            WrappedDataValue value = values.get(cursor);
            if (value != null && value.getIndex() == index) {
                return true;
            }
        }
        return false;
    }

    private static Integer entityIdAt(List<WrappedDataValue> values, int index) {
        if (values == null) {
            return null;
        }
        for (int cursor = 0; cursor < values.size(); cursor++) {
            WrappedDataValue value = values.get(cursor);
            if (value != null && value.getIndex() == index) {
                return entityIdOf(value.getValue());
            }
        }
        return null;
    }

    private static Integer entityIdOf(Object value) {
        if (value instanceof Integer) {
            return (Integer) value;
        }
        // 1.14+ firework "attached to" is an unsigned optional int, not Optional<Integer>.
        if (value instanceof OptionalInt) {
            OptionalInt optional = (OptionalInt) value;
            return optional.isPresent() ? Integer.valueOf(optional.getAsInt()) : Integer.valueOf(-1);
        }
        if (value instanceof Optional) {
            Optional<?> optional = (Optional<?>) value;
            if (!optional.isPresent()) {
                return Integer.valueOf(-1);
            }
            Object inner = optional.get();
            if (inner instanceof Integer) {
                return (Integer) inner;
            }
            return entityIdOf(inner);
        }
        if (value != null && value.getClass().getName().endsWith("OptionalInt")) {
            try {
                Method present = value.getClass().getMethod("isPresent");
                Object isPresent = present.invoke(value);
                if (!(isPresent instanceof Boolean) || !((Boolean) isPresent).booleanValue()) {
                    return Integer.valueOf(-1);
                }
                Method read = value.getClass().getMethod("getAsInt");
                Object id = read.invoke(value);
                if (id instanceof Integer) {
                    return (Integer) id;
                }
            }
            catch (Throwable ignored) {}
        }
        if (value != null && !loggedUnknownFirework) {
            loggedUnknownFirework = true;
            System.out.println("firework meta type " + value.getClass().getName() + " value=" + value);
        }
        return null;
    }

    public static Byte readSharedFlags(PacketContainer packet) {
        try {
            List<WrappedDataValue> values = packet.getDataValueCollectionModifier().readSafely(0);
            Byte flags = flagsAtIndexZero(values);
            if (flags != null) {
                return flags;
            }
        }
        catch (Throwable ignored) {}
        try {
            List<WrappedWatchableObject> values = packet.getWatchableCollectionModifier().readSafely(0);
            if (values == null) {
                return null;
            }
            for (int index = 0; index < values.size(); index++) {
                WrappedWatchableObject value = values.get(index);
                if (value != null && value.getIndex() == 0) {
                    Byte flags = asByte(value.getValue());
                    if (flags != null) {
                        return flags;
                    }
                }
            }
        }
        catch (Throwable ignored) {}
        return null;
    }

    private static Byte flagsAtIndexZero(List<WrappedDataValue> values) {
        if (values == null) {
            return null;
        }
        for (int index = 0; index < values.size(); index++) {
            WrappedDataValue value = values.get(index);
            if (value != null && value.getIndex() == 0) {
                Byte flags = asByte(value.getValue());
                if (flags != null) {
                    return flags;
                }
            }
        }
        return null;
    }

    private static void fillAbsolute(PacketContainer packet, Spawn spawn) {
        try {
            StructureModifier<Double> doubles = packet.getDoubles();
            Double x = doubles.readSafely(0);
            Double y = doubles.readSafely(1);
            Double z = doubles.readSafely(2);
            if (x != null && y != null && z != null) {
                spawn.positionKnown = true;
                spawn.x = x.doubleValue();
                spawn.y = y.doubleValue();
                spawn.z = z.doubleValue();
            }
        }
        catch (Throwable ignored) {}
        if (!spawn.positionKnown) {
            try {
                BlockPosition block = packet.getBlockPositionModifier().readSafely(0);
                if (block != null) {
                    spawn.positionKnown = true;
                    spawn.x = block.getX();
                    spawn.y = block.getY();
                    spawn.z = block.getZ();
                }
            }
            catch (Throwable ignored) {}
        }
        if (!spawn.positionKnown) {
            int[] legacy = legacyFixedPoint(packet);
            if (legacy != null) {
                spawn.positionKnown = true;
                spawn.x = legacy[0] / 32.0D;
                spawn.y = legacy[1] / 32.0D;
                spawn.z = legacy[2] / 32.0D;
            }
        }
        Float[] angles = readAnglePair(packet);
        if (angles != null) {
            spawn.yaw = angles[0];
            spawn.pitch = angles[1];
        }
    }

    private static int[] legacyFixedPoint(PacketContainer packet) {
        try {
            StructureModifier<Integer> integers = packet.getIntegers();
            if (integers.size() >= 5) {
                Integer x = integers.readSafely(2);
                Integer y = integers.readSafely(3);
                Integer z = integers.readSafely(4);
                if (x != null && y != null && z != null) {
                    return new int[] { x.intValue(), y.intValue(), z.intValue() };
                }
            }
            if (integers.size() >= 4) {
                Integer x = integers.readSafely(1);
                Integer y = integers.readSafely(2);
                Integer z = integers.readSafely(3);
                if (x != null && y != null && z != null) {
                    return new int[] { x.intValue(), y.intValue(), z.intValue() };
                }
            }
        }
        catch (Throwable ignored) {}
        return null;
    }

    private static long[] readDelta(PacketContainer packet) {
        if (AT_LEAST_26_3) {
            long[] modern = ModernDelta.read(packet);
            if (modern != null) {
                return modern;
            }
        }
        try {
            if (AT_LEAST_1_14) {
                StructureModifier<Short> shorts = packet.getShorts();
                if (shorts.size() >= 3) {
                    return new long[] { shorts.read(0).shortValue(), shorts.read(1).shortValue(), shorts.read(2).shortValue() };
                }
            }
            else if (AT_LEAST_1_9) {
                StructureModifier<Integer> integers = packet.getIntegers();
                if (integers.size() >= 4) {
                    return new long[] { integers.read(1).intValue(), integers.read(2).intValue(), integers.read(3).intValue() };
                }
            }
            else {
                StructureModifier<Byte> bytes = packet.getBytes();
                if (bytes.size() >= 3) {
                    return new long[] { bytes.read(0).byteValue(), bytes.read(1).byteValue(), bytes.read(2).byteValue() };
                }
            }
        }
        catch (Throwable ignored) {}
        if (!AT_LEAST_26_3) {
            return ModernDelta.read(packet);
        }
        return null;
    }

    private static void readAngles(PacketContainer packet, Relative relative) {
        Float[] angles = readAnglePair(packet);
        if (angles != null) {
            relative.yaw = angles[0];
            relative.pitch = angles[1];
        }
    }

    private static void readAngles(PacketContainer packet, Absolute absolute) {
        Float[] angles = readAnglePair(packet);
        if (angles != null) {
            absolute.yaw = angles[0];
            absolute.pitch = angles[1];
        }
    }

    private static Float[] readAnglePair(PacketContainer packet) {
        try {
            StructureModifier<Float> floats = packet.getFloat();
            if (floats.size() >= 2) {
                Float yaw = floats.readSafely(0);
                Float pitch = floats.readSafely(1);
                if (yaw != null && pitch != null) {
                    return new Float[] { yaw, pitch };
                }
            }
        }
        catch (Throwable ignored) {}
        try {
            StructureModifier<Byte> bytes = packet.getBytes();
            int size = bytes.size();
            if (size >= 2) {
                Byte yaw = bytes.readSafely(size - 2);
                Byte pitch = bytes.readSafely(size - 1);
                if (yaw != null && pitch != null) {
                    return new Float[] { Float.valueOf(angle(yaw.byteValue())), Float.valueOf(angle(pitch.byteValue())) };
                }
            }
        }
        catch (Throwable ignored) {}
        return null;
    }

    private static float angle(byte value) {
        return value * 360.0f / 256.0f;
    }

    private static int[] readPassengerIds(PacketContainer packet) {
        try {
            int[] ids = packet.getIntegerArrays().readSafely(0);
            if (ids != null) {
                return ids;
            }
        }
        catch (Throwable ignored) {}
        try {
            List<Integer> list = packet.getIntLists().readSafely(0);
            if (list != null) {
                int[] ids = new int[list.size()];
                for (int index = 0; index < list.size(); index++) {
                    Integer id = list.get(index);
                    ids[index] = id == null ? -1 : id.intValue();
                }
                return ids;
            }
        }
        catch (Throwable ignored) {}
        return new int[0];
    }

    private static Integer readInt(PacketContainer packet, int index) {
        try {
            return packet.getIntegers().readSafely(index);
        }
        catch (Throwable ignored) {
            return null;
        }
    }

    private static UUID readUuid(PacketContainer packet) {
        try {
            return packet.getUUIDs().readSafely(0);
        }
        catch (Throwable ignored) {
            return null;
        }
    }

    private static String readTypeName(PacketContainer packet) {
        try {
            EntityType type = packet.getEntityTypeModifier().readSafely(0);
            return type == null ? null : type.name();
        }
        catch (Throwable ignored) {
            return null;
        }
    }

    private static int readTypeId(PacketContainer packet) {
        try {
            if (packet.getDoubles().size() < 3) {
                return -1;
            }
            Integer type = packet.getIntegers().readSafely(1);
            return type == null ? -1 : type.intValue();
        }
        catch (Throwable ignored) {
            return -1;
        }
    }

    private static Byte asByte(Object value) {
        return value instanceof Byte ? (Byte) value : null;
    }

    /**
     * 26.3 encodes a relative move as VecDelta instead of three shorts.
     * Absent on older servers, in which case the reader falls through.
     */
    private static final class ModernDelta {
        private static final Class<?> DELTA;
        private static final Class<?> LINEAR;
        private static final Class<?> STEPPED;
        private static final Method STEPS;

        static {
            Class<?> delta = null;
            Class<?> linear = null;
            Class<?> stepped = null;
            Method steps = null;
            try {
                delta = Class.forName("net.minecraft.network.protocol.game.VecDelta");
                linear = Class.forName("net.minecraft.network.protocol.game.VecDelta$Linear");
                stepped = Class.forName("net.minecraft.network.protocol.game.VecDelta$Stepped");
                steps = stepped.getMethod("steps");
            }
            catch (Throwable ignored) {}
            DELTA = delta;
            LINEAR = linear;
            STEPPED = stepped;
            STEPS = steps;
        }

        static long[] read(PacketContainer packet) {
            if (DELTA == null) {
                return null;
            }
            try {
                Object delta = packet.getModifier().withType(DELTA).readSafely(0);
                if (delta == null) {
                    return null;
                }
                if (LINEAR.isInstance(delta)) {
                    return axes(delta);
                }
                if (STEPPED.isInstance(delta) && STEPS != null) {
                    long dx = 0L;
                    long dy = 0L;
                    long dz = 0L;
                    Object raw = STEPS.invoke(delta);
                    if (raw instanceof List) {
                        List<?> list = (List<?>) raw;
                        for (int index = 0; index < list.size(); index++) {
                            long[] step = axes(list.get(index));
                            if (step == null) {
                                return null;
                            }
                            dx += step[0];
                            dy += step[1];
                            dz += step[2];
                        }
                    }
                    return new long[] { dx, dy, dz };
                }
            }
            catch (Throwable ignored) {}
            return null;
        }

        private static long[] axes(Object value) {
            if (value == null) {
                return null;
            }
            StructureModifier<Short> shorts = new StructureModifier<Short>(value.getClass()).withTarget(value).withType(short.class);
            if (shorts.size() < 3) {
                return null;
            }
            return new long[] { shorts.read(0).shortValue(), shorts.read(1).shortValue(), shorts.read(2).shortValue() };
        }
    }

}
