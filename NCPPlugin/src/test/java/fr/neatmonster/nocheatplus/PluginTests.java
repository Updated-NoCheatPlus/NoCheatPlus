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
package fr.neatmonster.nocheatplus;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import fr.neatmonster.nocheatplus.actions.ActionFactory;
import fr.neatmonster.nocheatplus.actions.ActionFactoryFactory;
import fr.neatmonster.nocheatplus.compat.MCAccess;
import fr.neatmonster.nocheatplus.compat.blocks.changetracker.BlockChangeTracker;
import fr.neatmonster.nocheatplus.compat.bukkit.MCAccessBukkit;
import fr.neatmonster.nocheatplus.components.NoCheatPlusAPI;
import fr.neatmonster.nocheatplus.components.registry.ComponentRegistry;
import fr.neatmonster.nocheatplus.components.registry.DefaultGenericInstanceRegistry;
import fr.neatmonster.nocheatplus.components.registry.event.IGenericInstanceHandle;
import fr.neatmonster.nocheatplus.components.registry.setup.RegistrationContext;
import fr.neatmonster.nocheatplus.config.ConfigFile;
import fr.neatmonster.nocheatplus.config.DefaultConfig;
import fr.neatmonster.nocheatplus.event.mini.EventRegistryBukkit;
import fr.neatmonster.nocheatplus.logging.LogManager;
import fr.neatmonster.nocheatplus.logging.StaticLog;
import fr.neatmonster.nocheatplus.permissions.PermissionRegistry;
import fr.neatmonster.nocheatplus.permissions.Permissions;
import fr.neatmonster.nocheatplus.permissions.RegisteredPermission;
import fr.neatmonster.nocheatplus.players.IPlayerDataManager;
import fr.neatmonster.nocheatplus.worlds.IWorldData;
import fr.neatmonster.nocheatplus.worlds.IWorldDataManager;
import fr.neatmonster.nocheatplus.worlds.WorldDataManager;

public class PluginTests {

    /**
     * Dummy API, providing only a minimal subset of functionality for
     * offline-testing. Some methods do nothing, some throw an
     * UnsupportedOperationException, some will do something (set/get MCAccess).
     * 
     * @author asofold
     *
     */
    public static class UnitTestNoCheatPlusAPI implements NoCheatPlusAPI {

        /*
         * TODO: Mix-in style base functionality, common for testing API and
         * live plugin. ALT: DefaultNoCheatPlusAPI class and a common base class
         * (plugin would no further implement that API).
         */

        private final WorldDataManager worldDataManager = new WorldDataManager();
        private final DefaultGenericInstanceRegistry genericInstanceRegistry = new DefaultGenericInstanceRegistry();
        private final PermissionRegistry permissionRegistry = new PermissionRegistry(10000);

        public UnitTestNoCheatPlusAPI() {
            StaticLog.setUseLogManager(false); // Ensure either this instance provides it or it's unset.
            genericInstanceRegistry.registerGenericInstance(MCAccess.class, new MCAccessBukkit());
            for (RegisteredPermission rp : Permissions.getPermissions()) {
                permissionRegistry.addRegisteredPermission(rp);
            }
            Map<String, ConfigFile> rawConfigs = new HashMap<String, ConfigFile>();
            rawConfigs.put(null, new DefaultConfig());
            worldDataManager.applyConfiguration((rawConfigs));
        }

        @Override
        public boolean addComponent(Object component) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeComponent(Object component) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> Collection<ComponentRegistry<T>> getComponentRegistries(Class<ComponentRegistry<T>> clazz) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T registerGenericInstance(T instance) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T, TI extends T> T registerGenericInstance(Class<T> registerFor, TI instance) {
            return genericInstanceRegistry.registerGenericInstance(registerFor, instance);
        }

        @Override
        public <T> T getGenericInstance(Class<T> registeredFor) {
            return genericInstanceRegistry.getGenericInstance(registeredFor);
        }

        @Override
        public <T> T unregisterGenericInstance(Class<T> registeredFor) {
            return genericInstanceRegistry.unregisterGenericInstance(registeredFor);
        }

        @Override
        public <T> IGenericInstanceHandle<T> getGenericInstanceHandle(Class<T> registeredFor) {
            return genericInstanceRegistry.getGenericInstanceHandle(registeredFor);
        }

        @Override
        public boolean addComponent(Object obj, boolean allowComponentFactory) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addFeatureTags(String key, Collection<String> featureTags) {
            // IGNORE
        }

        @Override
        public void setFeatureTags(String key, Collection<String> featureTags) {
            // IGNORE
        }

        @Override
        public Map<String, Set<String>> getAllFeatureTags() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int sendAdminNotifyMessage(String message) {
            StaticLog.logInfo("AdminNotifyMessage: " + message);
            return 1;
        }

        @Override
        public void sendMessageOnTick(String playerName, String message) {
            StaticLog.logInfo("sendMessageOnTick (-> " + playerName + "): " + message);
        }

        @Override
        public boolean allowLogin(String playerName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int allowLoginAll() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void denyLogin(String playerName, long duration) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isLoginDenied(String playerName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String[] getLoginDeniedPlayers() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isLoginDenied(String playerName, long time) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LogManager getLogManager() {
            // TODO: Maybe do implement a dummy log manager (with file?) 
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasFeatureTag(String key, String feature) {
            throw new UnsupportedOperationException();
        }

        @Override
        public BlockChangeTracker getBlockChangeTracker() {
            throw new UnsupportedOperationException();
        }

        @Override
        public EventRegistryBukkit getEventRegistry() {
            throw new UnsupportedOperationException();
        }

        @Override
        public PermissionRegistry getPermissionRegistry() {
            return permissionRegistry;
        }

        @Override
        public WorldDataManager getWorldDataManager() {
            return worldDataManager;
        }

        @Override
        public IPlayerDataManager getPlayerDataManager() {
            throw new UnsupportedOperationException();
        }

        @Override
        public RegistrationContext newRegistrationContext() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void register(RegistrationContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ActionFactoryFactory getActionFactoryFactory() {
            ActionFactoryFactory factory = getGenericInstance(ActionFactoryFactory.class);
            if (factory == null) {
                setActionFactoryFactory(null);
                factory = getGenericInstance(ActionFactoryFactory.class);
            }
            return factory;
        }

        @Override
        public ActionFactoryFactory setActionFactoryFactory(
                ActionFactoryFactory actionFactoryFactory) {
            if (actionFactoryFactory == null) {
                actionFactoryFactory = new ActionFactoryFactory() {
                    @Override
                    public final ActionFactory newActionFactory(
                            final Map<String, Object> library) {
                        return new ActionFactory(library);
                    }
                };
            }
            final ActionFactoryFactory previous = registerGenericInstance(
                    ActionFactoryFactory.class, actionFactoryFactory);
            // Use lazy resetting.
            final IWorldDataManager worldMan = NCPAPIProvider.getNoCheatPlusAPI().getWorldDataManager();
            final Iterator<Entry<String, IWorldData>> it = worldMan.getWorldDataIterator();
            while (it.hasNext()) {
                final ConfigFile config = it.next().getValue().getRawConfiguration();
                config.setActionFactory(actionFactoryFactory);
            }
            // (Removing cached configurations and update are to be called externally.)
            return previous;
        }

    }

    public static void setUnitTestNoCheatPlusAPI(boolean force) {
        if (force || NCPAPIProvider.getNoCheatPlusAPI() == null) {
            NCPAPIProvider.setNoCheatPlusAPI(new UnitTestNoCheatPlusAPI());
        }
    }

}
