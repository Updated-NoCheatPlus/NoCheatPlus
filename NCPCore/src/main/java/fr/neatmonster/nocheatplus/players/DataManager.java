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

import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.utilities.entity.PassengerUtil;


/**
 * Static access API remaining from the previous mix of static/non-static.
 * 
 * Will be moved or removed.
 * 
 * @author asofold
 *
 */
public class DataManager {

    /*
     * TODO: Detach (I)PlayerDataManager, DataManager remains (legacy?) static
     * API... NCPStatic or so will be the "direct" static API, if at all.
     */
    // TODO: Should/can some data structures share the same lock?
    static PlayerDataManager instance = null;

    /**
     * Get the exact player name, stored internally.
     * @param playerId
     */
    public static String getPlayerName(final UUID playerId) {
        return instance.getPlayerName(playerId);
    }

    /**
     * Used by checks to register the history for external access.<br>
     * NOTE: This method is not really meant ot be used from outside NCP.
     * Do note that this method was meant to be deprecated due to a new implementation pending by the original developer.
     * 
     * @param type
     * @param histories
     */
    public static void registerExecutionHistory(CheckType type, Map<String, ExecutionHistory> histories) {
        instance.registerExecutionHistory(type, histories);
    }

    /**
     * Access method to the the execution history for check type for a player.
     * Do note that this method was meant to be deprecated due to a new implementation pending by the original developer.
     * 
     * @param type
     * @param playerName
     *            Exact case for player name.
     * @return null if not present.
     */
    public static ExecutionHistory getExecutionHistory(final CheckType type, final String playerName) {
        return instance.getExecutionHistory(type, playerName);
    }

    /**
     * Remove the execution history for a player for the given check type.
     * Do note that this method was meant to be deprecated due to a new implementation pending by the original developer.
     * 
     * @param type
     * @param playerName
     * @return
     */
    public static boolean removeExecutionHistory(final CheckType type, final String playerName) {
        return instance.removeExecutionHistory(type, playerName);
    }

    /**
     * Remove data and history of all players for the given check type and sub
     * checks. Also removes check related data from the world manager.
     * 
     * @param checkType
     */
    public static void clearData(final CheckType checkType) {
        NCPAPIProvider.getNoCheatPlusAPI().getWorldDataManager().clearData(checkType);
        instance.clearData(checkType);
    }

    /**
     * Adjust to the system time having run backwards. This is much like
     * clearData(CheckType.ALL), with the exception of calling
     * ICanHandleTimeRunningBackwards.handleTimeRanBackwards for data instances
     * which implement this.
     */
    public static void handleSystemTimeRanBackwards() {
        // TODO: Non static call from core plugin ?
        instance.handleSystemTimeRanBackwards();
    }

    /**
     * Restore the default debug flags within player data, as given in
     * corresponding configurations. This only yields the correct result, if the
     * the data uses the same configuration for initialization which is
     * registered under the same check type.
     * 
     * @TODO Explain the java-docs at some point.
     */
    public static void restoreDefaultDebugFlags() {
        instance.restoreDefaultDebugFlags();
    }

    /**
     * Remove the player data for a given player and a given check type.
     * CheckType.ALL and null will be interpreted as removing all data.<br>
     * 
     * @param playerName
     *            Exact player name.
     * @param checkType
     *            Check type to remove data for, null is regarded as ALL.
     * @return If any data was present (not strict).
     */
    public static boolean removeData(final String playerName, CheckType checkType) {
        return instance.removeData(playerName, checkType);
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
    public static boolean clearComponentData(final CheckType checkType, final String PlayerName) {
        // TODO: UUID.
        return instance.clearComponentData(checkType, PlayerName);
    }

    /**
     * This gets an online player by exact player name or lower-case player name
     * only [subject to change].
     * 
     * @param playerName
     * @return
     */
    public static Player getPlayerExact(final String playerName) {
        return instance.getPlayerExact(playerName);
    }

    /**
     * Retrieve the UUID for a given input (name or UUID string of with or
     * without '-'). Might later also query a cache, if appropriate. Convenience
     * method for use with commands.
     * 
     * @param input
     * @return
     */
    public static UUID getUUID(final String input) {
        return instance.getUUID(input);
    }

    /**
     * Get an online player by UUID.
     * 
     * @param id
     * @return
     */
    public static Player getPlayer(final UUID id) {
        return instance.getPlayer(id);
    }

    /**
     * This gets the online player with the exact name, but transforms the input
     * to lower case.
     * 
     * @param playerName
     * @return
     */
    public static Player getPlayer(final String playerName) {
        return instance.getPlayer(playerName);
    }

    /**
     * Get a PlayerData instance in any case - always creates a PlayerData
     * instance, if none is present. This method should be preferred, as it
     * hides details.
     * 
     * @param player
     * @return
     */
    public static IPlayerData getPlayerData(final Player player) {
        return instance.getPlayerData(player, true);
    }
    
    /**
     * Wrapper around {@link #getPlayerData(Player)}.<br>
     * If the given entity is not a player, data will be retrieved for the first player passenger 
     * 
     * @param entity
     * @param passengerUtil Utilities for passenger handling 
     * @return
     */
    public static IPlayerData getPlayerDataForEntity(final Entity entity, PassengerUtil passengerUtil) {
        if (entity instanceof Player) {
            return getPlayerData((Player) entity);
        }
        return getPlayerData(passengerUtil.getFirstPlayerPassenger(entity));
    }

    /**
     * Get the player data, if present.
     * 
     * @param playerName
     * @return The PlayerData instance if present, null otherwise.
     */
    public static IPlayerData getPlayerData(final String playerName) {
        return instance.getPlayerData(playerName);
    }

    /**
     * Get the player data, if present.
     * 
     * @param playerId
     * @return The PlayerData instance if present, null otherwise.
     */
    public static IPlayerData getPlayerData(final UUID playerId) {
        return instance.getPlayerData(playerId);
    }

    static boolean isFrequentPlayerTaskScheduled(final UUID playerId) {
        return instance.isFrequentPlayerTaskScheduled(playerId);
    }

    static void registerFrequentPlayerTaskPrimaryThread(final UUID playerId) {
        instance.registerFrequentPlayerTaskPrimaryThread(playerId);
    }

    static void registerFrequentPlayerTaskAsynchronous(final UUID playerId) {
        instance.registerFrequentPlayerTaskAsynchronous(playerId);
    }

    /**
     * 
     */
    public static void clearAllExemptions() {
        instance.clearAllExemptions();
    }

    /**
     * Convenience method, allowing to skip fetching PlayerData.
     * 
     * @param player
     * @param registeredFor
     * @return
     */
    public static <T> T getGenericInstance(final Player player, final Class<T> registeredFor) {
        return instance.getPlayerData(player).getGenericInstance(registeredFor);
    }

    static <T> T getFromFactory(final Class<T> registeredFor, 
            final PlayerFactoryArgument arg) {
        return instance.getNewInstance(registeredFor, arg);
    }

    /**
     * Attempt to get or create an IPlayerData instance. Creation will only be
     * done, if the player name, UUID and world can be fetched.
     * 
     * @param player
     * @return null in case of failures.
     */
    public static IPlayerData getPlayerDataSafe(final Player player) {
        try {
            return getPlayerData(player);
        } 
        catch (UnsupportedOperationException e) {}
        try {
            return getPlayerData(player.getUniqueId());
        } 
        catch (UnsupportedOperationException e) {}
        try {
            return getPlayerData(player.getName());
        } 
        catch (UnsupportedOperationException e) {}
        // Failure.
        return null;
    }

}
