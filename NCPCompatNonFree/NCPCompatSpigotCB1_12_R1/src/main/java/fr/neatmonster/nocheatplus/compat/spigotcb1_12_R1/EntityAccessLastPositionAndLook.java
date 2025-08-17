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
package fr.neatmonster.nocheatplus.compat.spigotcb1_12_R1;

import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.entity.Entity;

import fr.neatmonster.nocheatplus.components.entity.IEntityAccessLastPositionAndLook;
import fr.neatmonster.nocheatplus.components.location.IGetPositionWithLook;
import fr.neatmonster.nocheatplus.components.location.ISetPositionWithLook;
import fr.neatmonster.nocheatplus.utilities.ReflectionUtil;

public class EntityAccessLastPositionAndLook implements IEntityAccessLastPositionAndLook {

    public EntityAccessLastPositionAndLook() {
        ReflectionUtil.checkMembers(net.minecraft.server.v1_12_R1.Entity.class, double.class, new String[] {
                "lastX", "lastY", "lastZ"
        });
        ReflectionUtil.checkMembers(net.minecraft.server.v1_12_R1.Entity.class, float.class, new String[] {
                "lastYaw", "lastPitch"
        });
    }

    @Override
    public void getPositionAndLook(final Entity entity, final ISetPositionWithLook location) {
        // TODO: Error handling / conventions.
        final net.minecraft.server.v1_12_R1.Entity nmsEntity = ((CraftEntity) entity).getHandle();
        location.setX(nmsEntity.lastX);
        location.setY(nmsEntity.lastY);
        location.setZ(nmsEntity.lastZ);
        location.setYaw(nmsEntity.lastYaw);
        location.setPitch(nmsEntity.lastPitch);
    }

    @Override
    public void setPositionAndLook(Entity entity, IGetPositionWithLook location) {
        final net.minecraft.server.v1_12_R1.Entity nmsEntity = ((CraftEntity) entity).getHandle();
        nmsEntity.lastX = location.getX();
        nmsEntity.lastY = location.getY();
        nmsEntity.lastZ = location.getZ();
        nmsEntity.lastYaw = location.getYaw();
        nmsEntity.lastPitch = location.getPitch();
    }

}
