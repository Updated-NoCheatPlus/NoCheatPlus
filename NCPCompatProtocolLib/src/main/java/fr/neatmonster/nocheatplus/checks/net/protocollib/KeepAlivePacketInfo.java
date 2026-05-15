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
package fr.neatmonster.nocheatplus.checks.net.protocollib;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.reflect.StructureModifier;

/**
 * ProtocolLib packet-shape extraction for keepalive request/response ids.
 */
final class KeepAlivePacketInfo {

    final boolean idAvailable;
    final long id;
    final String idType;
    final int longCount;
    final int intCount;

    private KeepAlivePacketInfo(final boolean idAvailable, final long id, final String idType,
                                final int longCount, final int intCount) {
        this.idAvailable = idAvailable;
        this.id = id;
        this.idType = idType;
        this.longCount = longCount;
        this.intCount = intCount;
    }

    static KeepAlivePacketInfo read(final PacketContainer packet) {
        boolean idAvailable = false;
        long id = 0L;
        String idType = "none";
        int longCount = -1;
        int intCount = -1;
        try {
            final StructureModifier<Long> longs = packet.getLongs();
            longCount = longs.size();
            if (longCount > 0) {
                final Long value = longs.read(0);
                if (value != null) {
                    idAvailable = true;
                    id = value.longValue();
                    idType = "long";
                }
            }
        }
        catch (Throwable ignored) {}
        try {
            final StructureModifier<Integer> integers = packet.getIntegers();
            intCount = integers.size();
            if (!idAvailable && intCount > 0) {
                final Integer value = integers.read(0);
                if (value != null) {
                    idAvailable = true;
                    id = value.longValue();
                    idType = "int";
                }
            }
        }
        catch (Throwable ignored) {}
        return new KeepAlivePacketInfo(idAvailable, id, idType, longCount, intCount);
    }
}
