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
package fr.neatmonster.nocheatplus.players;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.ViolationHistory;
import fr.neatmonster.nocheatplus.checks.combined.CombinedData;
import fr.neatmonster.nocheatplus.compat.BridgeMisc;
import fr.neatmonster.nocheatplus.compat.versions.BukkitVersion;
import fr.neatmonster.nocheatplus.compat.versions.GenericVersion;
import fr.neatmonster.nocheatplus.compat.versions.ServerVersion;
import fr.neatmonster.nocheatplus.components.NoCheatPlusAPI;
import fr.neatmonster.nocheatplus.components.config.ICheckConfig;
import fr.neatmonster.nocheatplus.components.config.IConfig;
import fr.neatmonster.nocheatplus.components.data.ICanHandleTimeRunningBackwards;
import fr.neatmonster.nocheatplus.components.data.ICheckData;
import fr.neatmonster.nocheatplus.components.data.IData;
import fr.neatmonster.nocheatplus.components.data.IDataOnJoin;
import fr.neatmonster.nocheatplus.components.data.IDataOnLeave;
import fr.neatmonster.nocheatplus.components.data.IDataOnReload;
import fr.neatmonster.nocheatplus.components.data.IDataOnRemoveSubCheckData;
import fr.neatmonster.nocheatplus.components.data.IDataOnWorldChange;
import fr.neatmonster.nocheatplus.components.data.IDataOnWorldUnload;
import fr.neatmonster.nocheatplus.components.registry.factory.IFactoryOne;
import fr.neatmonster.nocheatplus.components.registry.factory.RichFactoryRegistry;
import fr.neatmonster.nocheatplus.components.registry.factory.RichFactoryRegistry.CheckRemovalSpec;
import fr.neatmonster.nocheatplus.components.registry.feature.ComponentWithName;
import fr.neatmonster.nocheatplus.components.registry.feature.ConsistencyChecker;
import fr.neatmonster.nocheatplus.components.registry.feature.IDisableListener;
import fr.neatmonster.nocheatplus.components.registry.feature.IHaveCheckType;
import fr.neatmonster.nocheatplus.components.registry.feature.INeedConfig;
import fr.neatmonster.nocheatplus.components.registry.feature.IRemoveData;
import fr.neatmonster.nocheatplus.components.registry.feature.TickListener;
import fr.neatmonster.nocheatplus.components.registry.order.RegistrationOrder.RegisterMethodWithOrder;
import fr.neatmonster.nocheatplus.components.registry.order.RegistrationOrder.RegisterWithOrder;
import fr.neatmonster.nocheatplus.config.ConfPaths;
import fr.neatmonster.nocheatplus.config.ConfigFile;
import fr.neatmonster.nocheatplus.config.ConfigManager;
import fr.neatmonster.nocheatplus.event.mini.MiniListener;
import fr.neatmonster.nocheatplus.logging.StaticLog;
import fr.neatmonster.nocheatplus.logging.Streams;
import fr.neatmonster.nocheatplus.permissions.PermissionPolicy;
import fr.neatmonster.nocheatplus.permissions.PermissionRegistry;
import fr.neatmonster.nocheatplus.permissions.PermissionSettings;
import fr.neatmonster.nocheatplus.permissions.RegisteredPermission;
import fr.neatmonster.nocheatplus.players.PlayerMap.PlayerInfo;
import fr.neatmonster.nocheatplus.utilities.CheckTypeUtil;
import fr.neatmonster.nocheatplus.utilities.CheckUtils;
import fr.neatmonster.nocheatplus.utilities.IdUtil;
import fr.neatmonster.nocheatplus.utilities.StringUtil;
import fr.neatmonster.nocheatplus.utilities.TickTask;
import fr.neatmonster.nocheatplus.utilities.ds.corw.DualSet;
import fr.neatmonster.nocheatplus.utilities.ds.map.HashMapLOW;
import fr.neatmonster.nocheatplus.worlds.IWorldData;
import fr.neatmonster.nocheatplus.worlds.WorldDataManager;

/**
 * Player data storage. May contain functionality of the DataManager, which
 * isn't intended to be in a PlayerDataManager (refactoring stage).
 * 
 * @author asofold
 *
 */
// TODO: RegisterWithOrder still relevant ?
// TODO: Tag utility (common stuff).
@RegisterWithOrder(tag = "system.nocheatplus.datamanager", beforeTag = "(^feature.*)", basePriority = "-80")
public class PlayerDataManager  implements IPlayerDataManager, ComponentWithName, INeedConfig, ConsistencyChecker, IDisableListener {

    /////////////////////
    // Instance
    /////////////////////

    private int foundInconsistencies = 0;

    /** PlayerData storage. */
    private final HashMapLOW<UUID, PlayerData> playerData = new HashMapLOW<UUID, PlayerData>(100);

    /** Primary thread only (no lock for this field): UUIDs to remove upon next bulk removal. */
    private final Set<UUID> bulkPlayerDataRemoval = new LinkedHashSet<UUID>();

    private final DualSet<UUID> frequentPlayerTasks = new DualSet<UUID>();

    /**
     * Access order for playerName (exact) -> ms time of logout.
     * <hr>
     * Later this might hold central player data objects instead of the long
     * only.
     */
    private final Map<UUID, Long> lastLogout = new LinkedHashMap<UUID, Long>(50, 0.75f, true);

    /**
     * Keeping track of online players. Currently id/name mappings are not kept
     * on logout, but might be later.
     */
    // TODO: Switch to UUIDs as keys, get data by uuid when possible, use PlayerMap for getting the UUID.
    private final PlayerMap playerMap;

    /**
     * IRemoveData instances.
     */
    // TODO: might use a map for those later (extra or not).
    private final ArrayList<IRemoveData> iRemoveData = new ArrayList<IRemoveData>();

    /**
     * Execution histories of the checks.
     */
    // TODO: Move to PlayerData / CheckTypeTree (NodeS).
    private final Map<CheckType, Map<String, ExecutionHistory>> executionHistories = new HashMap<CheckType, Map<String,ExecutionHistory>>();

    /** Flag if data expiration is active at all. */
    private boolean doExpireData = false;

    /**
     * Duration in milliseconds for expiration of logged off players data. In
     * the config minutes are used as unit.
     */
    private long durExpireData = 0;

    /** Data and execution history. */
    private boolean deleteData = true;
    /** Violation history and execution history. */
    private boolean deleteHistory = false;

    /**
     * Reference for passing to PlayerData for handling permission caching and
     * policies.
     */
    /*
     * TODO: Per world (rule/proxy) registries, with one central registry for
     * ids (per-world registries would proxy id registration, but have their own
     * rule settings).
     */
    private final PermissionRegistry permissionRegistry;

    private WorldDataManager worldDataManager;

    private final Lock lock = new ReentrantLock();
    // TODO: Consider same lock for some registry parts (deadlocking possibilities with exposed API).
    private final RichFactoryRegistry<PlayerFactoryArgument> factoryRegistry = new RichFactoryRegistry<PlayerFactoryArgument>(lock);
    private final TickListener tickListener = new TickListener() {

        private int delayRareTasks = 0;

        @Override
        public void onTick(final int tick, final long timeLast) {
            if (rareTasks(tick, timeLast)) {
                delayRareTasks = 10;
            }
            else {
                if (delayRareTasks == 0) {
                }
                else {
                    delayRareTasks --;
                }
            }
            frequentTasks(tick, timeLast);
        }
    };

    private final MiniListener<?>[] miniListeners = new MiniListener<?>[] {
        /*
         * TODO: Constants in a class 'ListenerTags', plus a plan
         * (system.data.player.nocheatplus, system.nocheatplus.data ??,
         * nocheatplus.system.data.player...). (RegistryTags for other?).
         */
        new MiniListener<PlayerQuitEvent>() {
            @EventHandler(priority = EventPriority.MONITOR)
            @RegisterMethodWithOrder(tag = "system.nocheatplus.datamanager", afterTag = ".*")
            @Override
            public void onEvent(final PlayerQuitEvent event) {
                playerLeaves(event.getPlayer());
            }
        },
        new MiniListener<PlayerKickEvent>() {
            // TODO: ignoreCancelled !?
            // TODO: afterTag !?
            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            @RegisterMethodWithOrder(tag = "system.nocheatplus.datamanager", afterTag = "feature.*")
            @Override
            public void onEvent(final PlayerKickEvent event) {
                playerLeaves(event.getPlayer());
            }
        },
        new MiniListener<AsyncPlayerPreLoginEvent>() {
            @EventHandler(priority = EventPriority.MONITOR)
            @RegisterMethodWithOrder(tag = "system.nocheatplus.datamanager", beforeTag = ".*")
            @Override
            public void onEvent(final AsyncPlayerPreLoginEvent event) {
                // TODO: Maintain a flag for precondition (e.g. ProtocolLib present).
                if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
                    onAsyncPlayerPreLogin(event);
                }
            }
        },
        new MiniListener<PlayerLoginEvent>() {
            @EventHandler(priority = EventPriority.MONITOR)
            @RegisterMethodWithOrder(tag = "system.nocheatplus.datamanager", beforeTag = ".*")
            @Override
            public void onEvent(final PlayerLoginEvent event) {
                // TODO: Maintain a flag for precondition (e.g. ProtocolLib present).
                if (event.getResult() == PlayerLoginEvent.Result.ALLOWED) {
                    onPlayerLogin(event);
                }
                else {
                    onPlayerLoginDenied(event);
                }
            }
        },
        new MiniListener<PlayerJoinEvent>() {
            @EventHandler(priority = EventPriority.LOWEST)
            @RegisterMethodWithOrder(tag = "system.nocheatplus.datamanager", beforeTag = ".*")
            @Override
            public void onEvent(final PlayerJoinEvent event) {
                playerJoins(event);
            }
        },
        new MiniListener<PlayerChangedWorldEvent>() {
            @EventHandler(priority = EventPriority.LOWEST)
            @RegisterMethodWithOrder(tag = "system.nocheatplus.datamanager", beforeTag = ".*")
            @Override
            public void onEvent(final PlayerChangedWorldEvent event) {
                playerChangedWorld(event);
            }
        },
        new MiniListener<WorldUnloadEvent>() {
            @EventHandler(priority = EventPriority.LOWEST)
            @RegisterMethodWithOrder(tag = "system.nocheatplus.datamanager", beforeTag = ".*")
            @Override
            public void onEvent(final WorldUnloadEvent event) {
                onWorldUnload(event);
            }
        },
    };


    /**
     * 
     * @param worldDataManager
     * @param permissionRegistry
     */
    public PlayerDataManager(final WorldDataManager worldDataManager, final PermissionRegistry permissionRegistry) {
        DataManager.instance = this; // TODO: Let NoCheatPlus do this, DataManager returns an ILockable.
        if (ServerVersion.isMinecraftVersionUnknown()) {
            // True hacks.
            BukkitVersion.init();
        }
        final String version = ServerVersion.getMinecraftVersion();
        if (GenericVersion.compareVersions(version, "1.8") >= 0 || version.equals("1.7.10") && Bukkit.getServer().getVersion().toLowerCase().indexOf("spigot") != -1) {
            // Safe to assume Spigot, don't store Player instances.
            playerMap = new PlayerMap(false);
        }
        else {
            // Likely an older version without efficient mapping.
            playerMap = new PlayerMap(true);
        }
        this.permissionRegistry = permissionRegistry; // TODO: World specific.
        this.worldDataManager = worldDataManager;
        // (Call support.) 
        factoryRegistry.createAutoGroup(IDataOnReload.class);
        factoryRegistry.createAutoGroup(IDataOnWorldUnload.class);
        factoryRegistry.createAutoGroup(IDataOnJoin.class);
        factoryRegistry.createAutoGroup(IDataOnLeave.class);
        factoryRegistry.createAutoGroup(IDataOnWorldChange.class);
        factoryRegistry.createAutoGroup(IDataOnRemoveSubCheckData.class);
        // Data/config removal.
        factoryRegistry.createAutoGroup(IData.class);
        factoryRegistry.createAutoGroup(IConfig.class);
        factoryRegistry.createAutoGroup(ICheckData.class);
        factoryRegistry.createAutoGroup(ICheckConfig.class);
    }

    /**
     * Check the logged out players for if any data can be removed.<br>
     * Currently only "dumb" full removal is performed. Later it is thinkable to
     * remove "as much as reasonable".
     */
    public void checkExpiration() {
        if (!doExpireData || durExpireData <= 0) {
            return;
        }
        final long now = System.currentTimeMillis();
        final Set<Entry<UUID, Long>> entries = lastLogout.entrySet();
        final Iterator<Entry<UUID, Long>> iterator = entries.iterator();
        while (iterator.hasNext()) {
            final Entry<UUID, Long> entry = iterator.next();
            // TODO: Multi stage expiration.
            final long ts = entry.getValue();
            if (now - ts <= durExpireData) {
                break;
            }
            final UUID playerId = entry.getKey();
            // TODO: LEGACY handling: switch to UUIDs here for sure.
            legacyPlayerDataExpirationRemovalByName(playerId, deleteData);
            bulkPlayerDataRemoval.add(playerId); // For bulk removal.
            iterator.remove();
        }
        // Bulk removal of PlayerData.
        if (!bulkPlayerDataRemoval.isEmpty()) {
            doBulkPlayerDataRemoval(); // Using this method allows checking for delayed removal etc.
        }
    }

    private final void legacyPlayerDataExpirationRemovalByName(final UUID playerId, 
            final boolean deleteData) {
        final String playerName = DataManager.getPlayerName(playerId);
        if (playerName == null) {
            // TODO: WARN
            return;
        }
        // TODO: Validity of name?
        if (deleteData) {
            final PlayerData pData = playerData.get(playerId);
            if (pData != null) {
                pData.removeData(false); // TODO: staged ...
            }
            clearComponentData(CheckType.ALL, playerName);
        }
        if (deleteData || deleteHistory) {
            removeExecutionHistory(CheckType.ALL, playerName);
        }
        if (deleteHistory) {
            ViolationHistory.removeHistory(playerName);
        }
    }

    /**
     * Called by the rareTasksListener (OnDemandTickListener).
     * @return "Did something" - true if data was removed or similar, i.e. reset the removal delay counter. False if nothing relevant had been done.
     */
    private final boolean rareTasks(final int tick, final long timeLast) {
        boolean something = false;
        if (!bulkPlayerDataRemoval.isEmpty()) {
            doBulkPlayerDataRemoval();
            something = true;
        }
        // TODO: Process rarePlayerTasks
        return something;
    }

    /**
     * On tick.
     */
    private final void frequentTasks(final int tick, final long timeLast) {
        frequentPlayerTasks.mergePrimaryThread();
        final Iterator<UUID> it = frequentPlayerTasks.iteratorPrimaryThread();
        while (it.hasNext()) {
            final PlayerData pData = getPlayerData(it.next(), null, false, null);
            if (pData == null || pData.processTickFrequent(tick, timeLast)) {
                it.remove();
            }
        }
    }

    /**
     * Primary thread only. This checks for if players are/should be online.
     */
    private final void doBulkPlayerDataRemoval() {
        int size = bulkPlayerDataRemoval.size();
        if (size > 0) {
            // Test for online players.
            final Iterator<UUID> it = bulkPlayerDataRemoval.iterator();
            while (it.hasNext()) {
                final UUID playerId = it.next();
                boolean skip = !lastLogout.containsKey(playerId);
                // TODO: Also remove fake players, thus test for logged in too.
                /*
                 * TODO: Multi stage removal: (1) non essential like permission
                 * cache, (2) essential like set-back location, (3) all. In
                 * addition things will get shifty, once we use PlayerData
                 * during asynchronous login - possibly we'll need parked data
                 * then, also considering offline servers.
                 */
                if (skip) {
                    it.remove();
                    size --;
                    final PlayerData data = playerData.get(playerId);
                    if (data != null) {
                        // Should be online, keep essential data.
                        data.removeData(true);
                    }
                    continue;
                }
            }
            // Actually remove data.
            if (size > 0) {
                playerData.remove(bulkPlayerDataRemoval);
                bulkPlayerDataRemoval.clear();
                if (ConfigManager.getConfigFile().getBoolean(ConfPaths.LOGGING_EXTENDED_STATUS)) {
                    NCPAPIProvider.getNoCheatPlusAPI().getLogManager().debug(Streams.STATUS, "Bulk PlayerData removal: " + size);
                }
            }
        }
    }

    /**
     * Remove data instances from the cache for a given player and a given check
     * type. CheckType.ALL and null will be interpreted as removing all data.
     * <hr/>
     * Does not touch the execution history.
     * <hr/>
     * 
     * @param playerName
     *            Exact player name.
     * @param checkType
     *            Check type to remove data for, null is regarded as ALL.
     * @return If any data was present (not strict).
     */
    public boolean removeData(final String playerName, CheckType checkType) {

        PlayerData pData = getPlayerData(playerName);
        // TODO: Once working, use the most correct name from PlayerData.
        final UUID playerId = pData == null ? getUUID(playerName) : pData.getPlayerId();
        if (pData == null && playerId != null) {
            pData = playerData.get(playerId);
        }
        boolean somethingFound = pData != null || playerId != null;

        // TODO: Method signature with UUID / (I)PlayerData ?

        if (checkType == null) {
            checkType = CheckType.ALL;
        }

        // Check extended registered components.
        /*
         *  TODO: "System data" might not be wise to erase for online players.
         *  ICheckData vs IData (...), except if registered for per check 
         *  type removal.
         */
        somethingFound |= clearComponentData(checkType, playerName);

        if (pData != null) {
            final CheckRemovalSpec removalSpec = new CheckRemovalSpec(checkType, true, this);
            final boolean hasComplete = !removalSpec.completeRemoval.isEmpty();
            final boolean hasSub = !removalSpec.subCheckRemoval.isEmpty();
            if (hasComplete || hasSub) {
                if (hasComplete) {
                    pData.removeAllGenericInstances(removalSpec.completeRemoval);
                }
                if (hasSub) {
                    pData.removeSubCheckData(removalSpec.subCheckRemoval, removalSpec.checkTypes);
                }
                // TODO: Remove the PlayerData instance, if necessary?
            }
            // TODO: Maintain a shouldBeOnline flag for fast skipping?
            if (checkType == CheckType.ALL) {
                // TODO: Fetch/use UUID early, and check validity of name.
                if (playerId != null) {
                    bulkPlayerDataRemoval.add(playerId);
                }
            }
            if (pData.isDebugActive(checkType)) {
                NCPAPIProvider.getNoCheatPlusAPI().getLogManager().debug(
                        Streams.TRACE_FILE, 
                        CheckUtils.getLogMessagePrefix(playerName, checkType)
                        + "Removed data.");
            }
        }
        return somethingFound;
    }

    /**
     * Check if player instances are stored for efficiency (legacy).
     * 
     * @return
     */
    public boolean storesPlayerInstances() {
        return playerMap.storesPlayerInstances();
    }

    @Override
    public boolean addComponent(IRemoveData obj) {
        if (iRemoveData.contains(obj)) {
            return false;
        }
        else {
            iRemoveData.add((IRemoveData) obj);
            return true;
        }
    }

    @Override
    public void removeComponent(IRemoveData obj) {
        iRemoveData.remove((IRemoveData) obj);
    }

    /**
     * Initializing with online players.
     */
    public void onEnable() {
        TickTask.addTickListener(tickListener);
        final NoCheatPlusAPI api = NCPAPIProvider.getNoCheatPlusAPI();
        for (final MiniListener<?> listener : miniListeners) {
            api.addComponent(listener, false);
        }
        for (final Player player : BridgeMisc.getOnlinePlayers()) {
            addOnlinePlayer(player);
        }
    }

    /**
     * Cleanup method, removes all data and config, but does not call
     * ConfigManager.cleanup.
     */
    @Override
    public void onDisable() {
        // TODO: Process pending set backs etc. -> iterate playerData -> onDisable.
        clearData(CheckType.ALL);
        playerData.clear(); // Also clear for online players.
        iRemoveData.clear();
        lastLogout.clear();
        executionHistories.clear();
        playerMap.clear();
        // Finally alert (summary) if inconsistencies found.
        if (foundInconsistencies > 0) {
            StaticLog.logWarning("DataMan found " + foundInconsistencies + " inconsistencies (warnings suppressed).");
            foundInconsistencies = 0;
        }
    }

    public void onWorldUnload(final WorldUnloadEvent event) {
        final Collection<Class<? extends IDataOnWorldUnload>> types = factoryRegistry.getGroupedTypes(IDataOnWorldUnload.class);
        for (final Entry<UUID, PlayerData> entry : playerData.iterable()) {
            entry.getValue().onWorldUnload(event.getWorld(), types);
        }
    }

    /**
     * Add mappings for player names variations.
     * @param player
     */
    private void addOnlinePlayer(final Player player) {
        playerMap.updatePlayer(player);
    }

    /**
     * Remove mappings for player names variations.
     * @param player
     */
    private void removeOnlinePlayer(final Player player) {
        // TODO: Consider to only remove the Player instance? Yes do so... and remove the mapping if the full data expires only.
        playerMap.remove(player);
    }

    /**
     * Ensure a PlayerData instance exists for later use.
     * 
     * @param event
     */
    private void onAsyncPlayerPreLogin(final AsyncPlayerPreLoginEvent event) {
        final UUID playerId = event.getUniqueId(); // Treat carefully :).
        if (playerData.containsKey(playerId)) {
            // Skip if a PlayerData instance already exists.
            return;
        }
        else {
            // Create with default world data.
            getPlayerData(playerId, event.getName(), true, worldDataManager.getDefaultWorldData()).addTag(PlayerData.TAG_OPTIMISTIC_CREATE);
        }
    }

    private void onPlayerLoginDenied(final PlayerLoginEvent event) {
        // Consistency check existing data.
        final UUID playerId = event.getPlayer().getUniqueId();
        final PlayerData pData = getPlayerData(playerId);
        if (pData != null && pData.hasTag(PlayerData.TAG_OPTIMISTIC_CREATE)) {
            bulkPlayerDataRemoval.add(playerId);
        }
    }

    /**
     * Just update the world data for later use.
     * 
     * @param event
     */
    private void onPlayerLogin(final PlayerLoginEvent event) {
        // Consistency check existing data.
        final Player player = event.getPlayer();
        final UUID playerId = player.getUniqueId();
        final PlayerData pData = getPlayerData(playerId);
        if (pData == null) {
            // Create an instance.
            // TODO: Legacy server compatibility with world getting?
            getPlayerData(player);
        }
        else {
            // Consistency check.
            final String playerName = pData.getPlayerName();
            if (!playerName.equals(player.getName())) {
                updatePlayerName(playerId, playerName, pData, "login");
            }
            // Update world.
            pData.updateCurrentWorld(worldDataManager.getWorldData(player.getWorld()));
        }
        pData.removeTag(PlayerData.TAG_OPTIMISTIC_CREATE);
    }

    private void updatePlayerName(final UUID playerId, final String playerName,
            final PlayerData pData, String tag) {
        // Name change.
        pData.updatePlayerName(playerName);
        NCPAPIProvider.getNoCheatPlusAPI().getLogManager().info(Streams.STATUS, 
                CheckUtils.getLogMessagePrefix(playerName, null) 
                + " Update player name for id " + playerId + ": " + playerName 
                + "(" + tag + (pData.hasTag(PlayerData.TAG_OPTIMISTIC_CREATE) ? 
                        ", optimistically created data" : "") + ")");
    }

    private void playerJoins(final PlayerJoinEvent event) {
        final long timeNow = System.currentTimeMillis();
        final Player player = event.getPlayer();
        final UUID playerId = player.getUniqueId();
        //
        lastLogout.remove(playerId);
        addOnlinePlayer(player);
        //
        final PlayerData pData = getPlayerData(player, true);
        // Consistency check.
        final String playerName = pData.getPlayerName();
        if (!playerName.equals(player.getName())) {
            updatePlayerName(playerId, playerName, pData, "login");
        }
        // Data stuff.
        final Collection<Class<? extends IDataOnJoin>> types = factoryRegistry.getGroupedTypes(IDataOnJoin.class);
        pData.onPlayerJoin(player, player.getWorld(), timeNow, worldDataManager, types);
        pData.getGenericInstance(CombinedData.class).lastJoinTime = timeNow; 
    }

    /**
     * Quit or kick.
     * @param player
     */
    private void playerLeaves(final Player player) {
        final long timeNow = System.currentTimeMillis();
        final UUID playerId = player.getUniqueId();
        lastLogout.put(playerId, timeNow);
        final PlayerData pData = playerData.get(playerId);
        if (pData != null) {
            final Collection<Class<? extends IDataOnLeave>> types = factoryRegistry.getGroupedTypes(IDataOnLeave.class);
            pData.onPlayerLeave(player, timeNow, types);
            pData.getGenericInstance(CombinedData.class).lastLogoutTime = timeNow;
        }
        else {
            // TODO: put lastLogoutTime to OfflinePlayerData ?
        }
        removeOnlinePlayer(player);
    }

    private void playerChangedWorld(final PlayerChangedWorldEvent event) {
        final Player player = event.getPlayer();
        final PlayerData pData = getPlayerData(player, true);
        final Collection<Class<? extends IDataOnWorldChange>> types = factoryRegistry.getGroupedTypes(IDataOnWorldChange.class);
        pData.onPlayerChangedWorld(player, event.getFrom(), player.getWorld(), 
                worldDataManager, types);
    }

    /**
     * Fetch settings from the current default config.
     */
    private void adjustSettings() {
        final ConfigFile config = ConfigManager.getConfigFile();
        doExpireData = config.getBoolean(ConfPaths.DATA_EXPIRATION_ACTIVE);
        durExpireData = config.getLong(ConfPaths.DATA_EXPIRATION_DURATION, 1, 1000000, 60) * 60000L; // in minutes
        deleteData = config.getBoolean(ConfPaths.DATA_EXPIRATION_DATA, true); // hidden.
        deleteHistory = config.getBoolean(ConfPaths.DATA_EXPIRATION_HISTORY);
        // TODO: Per world permission registries: need world configs (...).
        Set<RegisteredPermission> changedPermissions = null;
        try {
            // TODO: Only update if changes are there - should have a config-path hash+size thing (+ setting).
            changedPermissions = permissionRegistry.updateSettings(PermissionSettings.fromConfig(config, 
                    ConfPaths.PERMISSIONS_POLICY_DEFAULT, ConfPaths.PERMISSIONS_POLICY_RULES));
        }
        catch (Exception e) {
            StaticLog.logSevere("Failed to read the permissions setup. Relay to ALWAYS policy.");
            StaticLog.logSevere(e);
            permissionRegistry.updateSettings(new PermissionSettings(null, null, new PermissionPolicy()));
        }
        // Invalidate all already fetched permissions.
        for(final Entry<UUID, PlayerData> entry : playerData.iterable()) {
            entry.getValue().adjustSettings(changedPermissions);
        }
    }

    @Override
    public void onReload() {
        // present.
        adjustSettings();
        final Collection<Class<? extends IDataOnReload>> types = factoryRegistry.getGroupedTypes(IDataOnReload.class);
        for (final Entry<UUID, PlayerData> entry : playerData.iterable()) {
            entry.getValue().onReload(types);
        }
    }

    @Override
    public String getComponentName() {
        return "NoCheatPlus_DataManager";
    }

    @Override
    public void checkConsistency(final Player[] onlinePlayers) {
        // Check online player tracking consistency.
        int missing = 0;
        int changed = 0;
        for (int i = 0; i < onlinePlayers.length; i++) {
            final Player player = onlinePlayers[i];
            final UUID id = player.getUniqueId();
            //          if (player.isOnline()) {
            // TODO: Add a consistency check method !?
            if (!playerMap.hasPlayerInfo(id)) {
                missing ++;
                // TODO: Add the player [problem: messy NPC plugins?]?
            }
            if (playerMap.storesPlayerInstances() && player != playerMap.getPlayer(id)) {
                changed ++;
                // Update the reference.
                addOnlinePlayer(player);
                //              }
            }
        }

        // TODO: Consider checking lastLogout for too long gone players.

        // TODO: Later the map size will not work, if we keep name/id mappings after logout. Other checking methods are possible.
        final int storedSize = this.playerMap.size();
        if (missing != 0 || changed != 0 || onlinePlayers.length != storedSize) {
            foundInconsistencies ++;
            if (!ConfigManager.getConfigFile().getBoolean(ConfPaths.DATA_CONSISTENCYCHECKS_SUPPRESSWARNINGS)) {
                final List<String> details = new LinkedList<String>();
                if (missing != 0) {
                    details.add("missing online players (" + missing + ")");
                }
                if (onlinePlayers.length != storedSize) {
                    // TODO: Consider checking for not online players and remove them.
                    details.add("wrong number of online players (" + storedSize + " instead of " + onlinePlayers.length + ")");
                }
                if (changed != 0) {
                    details.add("changed player instances (" + changed + ")");
                }

                StaticLog.logWarning("DataMan inconsistencies: " + StringUtil.join(details, " | "));
            }
        }
    }

    void registerFrequentPlayerTaskPrimaryThread(final UUID playerId) {
        frequentPlayerTasks.addPrimaryThread(playerId);
    }

    void registerFrequentPlayerTaskAsynchronous(final UUID playerId) {
        frequentPlayerTasks.addAsynchronous(playerId);
    }

    /**
     * Might yield false negatives, should be reasonable on performance.
     * 
     * @param playerId
     * @return
     */
    boolean isFrequentPlayerTaskScheduled(final UUID playerId) {
        // TODO : Efficient impl / optimized methods?
        if (Bukkit.isPrimaryThread()) {
            return frequentPlayerTasks.containsPrimaryThread(playerId);
        }
        else {
            return frequentPlayerTasks.containsAsynchronous(playerId);
        }
    }

    /**
     * Clear player related data, only for registered components (not execution
     * history, violation history, normal check data).<br>
     * That should at least go for chat engine data.
     * 
     * @param CheckType
     * @param PlayerName
     * @return If something was removed.
     */
    public boolean clearComponentData(final CheckType checkType, final String PlayerName) {
        // TODO: UUID.
        boolean removed = false;
        for (final IRemoveData rmd : iRemoveData) {
            if (checkType == CheckType.ALL) {
                // Not sure this is really good, though.
                if (rmd.removeData(PlayerName) != null) {
                    removed = true;
                }
            }
            else if (rmd instanceof IHaveCheckType) {
                final CheckType refType = ((IHaveCheckType) rmd).getCheckType();
                if (refType == checkType || CheckTypeUtil.isAncestor(checkType, refType)) {
                    if (rmd.removeData(PlayerName) != null) {
                        removed = true;
                    }
                }
            }
        }
        return removed;
    }

    /**
     * Adjust to the system time having run backwards. This is much like
     * clearData(CheckType.ALL), with the exception of calling
     * ICanHandleTimeRunningBackwards.handleTimeRanBackwards for data instances
     * which implement this.
     */
    public void handleSystemTimeRanBackwards() {
        // TODO: WorldDataManager should have an extra method and be called before this.
        // Collect data factories and clear execution history.
        for (final CheckType type : CheckTypeUtil.getWithDescendants(CheckType.ALL)) {
            final Map<String, ExecutionHistory> map = executionHistories.get(type);
            if (map != null) {
                map.clear();
            }
        }
        for (final IRemoveData rmd : iRemoveData) {
            if (rmd instanceof ICanHandleTimeRunningBackwards) {
                ((ICanHandleTimeRunningBackwards) rmd).handleTimeRanBackwards();
            }
            else {
                rmd.removeAllData();
            }
        }
        ViolationHistory.clear(CheckType.ALL);
        // PlayerData
        // TODO: Register explicitly (adding IDataOnTimeRanBackwards)?
        Collection<Class<? extends IData>> dataTypes = factoryRegistry.getGroupedTypes(IData.class);
        for (final Entry<UUID, PlayerData> entry : playerData.iterable()){
            entry.getValue().handleTimeRanBackwards(dataTypes);
        }
    }

    /**
     * Fetch a PlayerData instance. If none is present and create is set, a new
     * instance will be created.
     * 
     * @param playerId
     * @param playerName
     *            Exact player name (rather).
     * @param create
     * @param worldData
     *            WorldData is only used for creating new instances, in which
     *            case it must not be null.
     * @return
     */
    PlayerData getPlayerData(final UUID playerId,
            final String playerName, final boolean create,
            final IWorldData worldData) {
        final PlayerData data = playerData.get(playerId);
        if (!create || data != null) {
            return data;
        }
        else {
            // Creating this should be mostly harmless.
            // TODO: Might want to lock still (same lock as used within the
            // playerData map).
            final PlayerData newData = new PlayerData(playerId, playerName,
                    permissionRegistry);
            final PlayerData oldData = playerData.putIfAbsent(playerId,
                    newData);
            final PlayerData usedData = oldData == null ? newData : oldData;
            usedData.updateCurrentWorld(
                    worldData == null ? worldDataManager.getDefaultWorldData()
                            : worldData);
            return usedData;
        }
    }

    @Override
    public PlayerData getPlayerData(final Player player, boolean create) {
        try {
        return getPlayerData(player.getUniqueId(), player.getName(), 
                create, create ? worldDataManager.getWorldDataSafe(player) : null);
        } catch (Exception e) {
    		// Fake player
    		return null;
        }    
    }

    @Override
    public PlayerData getPlayerData(final Player player) {
        return getPlayerData(player, true);
    }

    @Override
    public PlayerData getPlayerData(final String playerName) {
        final UUID playerId = DataManager.getUUID(playerName);
        return playerId == null ? null : playerData.get(playerId);
    }

    @Override
    public PlayerData getPlayerData(final UUID playerId) {
        return playerData.get(playerId);
    }

    @Override
    public Player getPlayerExact(final String playerName) {
        return playerMap.getPlayerExact(playerName);
    }

    @Override
    public UUID getUUID(final String input) {
        // TODO: Use player map.
        final Player player = getPlayer(input);
        if (player != null) {
            return player.getUniqueId();
        }
        return IdUtil.UUIDFromStringSafe(input);
    }

    @Override
    public String getPlayerName(final UUID playerId) {
        final PlayerInfo info = playerMap.getPlayerInfo(playerId);
        if (info != null && info.exactName != null) {
            return info.exactName;
        }
        final PlayerData data = playerData.get(playerId);
        if (data != null) {
            return data.getPlayerName();
        }
        return null;
    }

    @Override
    public Player getPlayer(final UUID id) {
        return playerMap.getPlayer(id);
    }

    @Override
    public Player getPlayer(final String playerName) {
        return playerMap.getPlayer(playerName);
    }

    @Override
    public void restoreDefaultDebugFlags() {
        // (Note that WorldData is resetting differently, and before this.)
        for (final Entry<UUID, PlayerData> entry : playerData.iterable()) {
            entry.getValue().resetDebug();
        }
    }

    @Override
    public void clearAllExemptions() {
        final Iterator<Entry<UUID, PlayerData>> it = playerData.iterator();
        while (it.hasNext()) {
            it.next().getValue().clearAllExemptions();
        }
    }

    @Override
    public <T> void removeGenericInstance(Class<T> registeredFor) {
        // TODO: Really needs OfflinePlayerData for more frequent data removal.
        for (final Entry<UUID, PlayerData> entry : playerData.iterable()) {
            entry.getValue().removeGenericInstance(registeredFor);
        }
    }

    @Override
    public void clearData(final CheckType checkType) {
        final CheckRemovalSpec removalSpec = new CheckRemovalSpec(checkType, true, this);
        final boolean hasComplete = !removalSpec.completeRemoval.isEmpty();
        final boolean hasSub = !removalSpec.subCheckRemoval.isEmpty();
        if (hasComplete || hasSub) {
            for (final Entry<UUID, PlayerData> entry : playerData.iterable()) {
                final IPlayerData pData = entry.getValue();
                if (hasComplete) {
                    pData.removeAllGenericInstances(removalSpec.completeRemoval);
                }
                if (hasSub) {
                    pData.removeSubCheckData(removalSpec.subCheckRemoval, removalSpec.checkTypes);
                }
                // TODO: Remove the PlayerData instance, if suitable?
            }
        }
        // TODO: IRemoveData - why register here at all ?
        for (final IRemoveData rmd : iRemoveData) {
            if (checkType == CheckType.ALL) {
                // Not sure this is really good, though.
                rmd.removeAllData();
            }
            else if (rmd instanceof IHaveCheckType) {
                final CheckType refType = ((IHaveCheckType) rmd).getCheckType();
                if (refType == checkType || CheckTypeUtil.isAncestor(checkType, refType)) {
                    rmd.removeAllData();
                }
            }
        }
        for (final CheckType type : removalSpec.checkTypes) {
            final Map<String, ExecutionHistory> map = executionHistories.get(type);
            if (map != null) {
                map.clear();
            }
        }
        ViolationHistory.clear(checkType);
        // TODO: PlayerData removal should have other mechanisms (stages).
        if (checkType == CheckType.ALL) {
            bulkPlayerDataRemoval.addAll(playerData.getKeys());
            doBulkPlayerDataRemoval(); // Only removes offline player data.
        }
    }

    /**
     * Used by checks to register the history for external access.<br>
     * NOTE: This method is not really meant to be used from outside NCP.
     * Do note that this method was meant to be deprecated due to a new implementation pending by the original developer.
     * 
     * @param type
     * @param histories
     */
    public void registerExecutionHistory(CheckType type, Map<String, ExecutionHistory> histories) {
        executionHistories.put(type, histories);
    }

    /**
     * Access method to the the execution history for check type for a player.
     * Do note that this method was meant to be deprecated due to a new iteration pending by the original developer
     * 
     * @param type
     * @param playerName
     *            Exact case for player name.
     * @return null if not present.
     */
    public ExecutionHistory getExecutionHistory(final CheckType type, final String playerName) {
        final Map<String, ExecutionHistory> map = executionHistories.get(type);
        if (map != null) {
            return map.get(playerName);
        }
        return null;
    }

    /**
     * Remove the execution history for a player for the given check type.
     * Do note that this method was meant to be deprecated due to a new iteration pending by the original developer
     * 
     * @param type
     * @param playerName
     * @return
     */
    public boolean removeExecutionHistory(final CheckType type, final String playerName) {
        boolean removed = false;
        // TODO: design ...
        for (final CheckType refType : CheckTypeUtil.getWithDescendants(type)) {
            final Map<String, ExecutionHistory> map = executionHistories.get(refType);
            if (map != null && map.remove(playerName) != null) {
                removed = true;
            }
        }
        return removed;
    }

    @Override
    public <T> void registerFactory(final Class<T> registerFor,
            final IFactoryOne<PlayerFactoryArgument, T> factory) {
        factoryRegistry.registerFactory(registerFor, factory);
    }

    @Override
    public <T> Collection<Class<? extends T>> getGroupedTypes(final Class<T> groupType) {
        return factoryRegistry.getGroupedTypes(groupType);
    }

    @Override
    public <T> Collection<Class<? extends T>> getGroupedTypes(final Class<T> groupType,
            final CheckType checkType) {
        return factoryRegistry.getGroupedTypes(groupType, checkType);
    }

    @Override
    public <I> void addToGroups(final Class<I> itemType, 
            final Class<? super I>... groupTypes) {
        factoryRegistry.addToGroups(itemType, groupTypes);
    }

    @Override
    public <I> void addToGroups(CheckType checkType, Class<I> itemType,
            Class<? super I>... groupTypes) {
        factoryRegistry.addToGroups(checkType, itemType, groupTypes);
    }

    @Override
    public void addToExistingGroups(Class<?> itemType) {
        factoryRegistry.addToExistingGroups(itemType);
    }

    @Override
    public <I> void addToExistingGroups(final CheckType checkType,
            final Class<I> itemType) {
        factoryRegistry.addToExistingGroups(checkType, itemType);
    }

    @Override
    public <G> void createGroup(final Class<G> groupType) {
        factoryRegistry.createGroup(groupType);
    }

    @Override
    public <G> void createAutoGroup(final Class<G> groupType) {
        factoryRegistry.createAutoGroup(groupType);
    }

    @Override
    public <I> void addToGroups(final Collection<CheckType> checkTypes,
            final Class<I> itemType, final Class<? super I>... groupTypes) {
        factoryRegistry.addToGroups(checkTypes, itemType, groupTypes);
    }

    @Override
    public <I> void addToExistingGroups(final Collection<CheckType> checkTypes,
            final Class<I> itemType) {
        factoryRegistry.addToExistingGroups(checkTypes, itemType);
    }

    @Override
    public <T> T getNewInstance(final Class<T> registeredFor,
            final PlayerFactoryArgument arg) {
        return factoryRegistry.getNewInstance(registeredFor, arg);
    }

    @Override
    public void removeCachedConfigs() {
        final Collection<Class<?>> types = new LinkedHashSet<Class<?>>(
                factoryRegistry.getGroupedTypes(IConfig.class));
        if (!types.isEmpty()) {
            for (final Entry<UUID, PlayerData> entry : playerData.iterable()) {
                entry.getValue().removeAllGenericInstances(types);
            }
        }
    }

}
