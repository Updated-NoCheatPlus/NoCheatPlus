package fr.neatmonster.nocheatplus.hooks.violationfrequency;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.access.IViolationInfo;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.hooks.IFirst;
import fr.neatmonster.nocheatplus.hooks.NCPHook;
import fr.neatmonster.nocheatplus.hooks.NCPHookManager;
import fr.neatmonster.nocheatplus.logging.LogManager;
import fr.neatmonster.nocheatplus.logging.Streams;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.StringUtil;

/**
 * A simple hook good at preventing false flags on small Vls and help generate larger on checks given out too small vl.
 * 
 * In default config, it will make NCP won't setback anythings till VL reach to 35,
 * SurvivalFly actions it will look like: vl>35 cancel ....
 * But it can catch if they are using shortfly depend on the flag frequency between moves.
 * Need to set the survivalfly.actions always cancel at vl 0 to make this hook work properly
 * 
 * @author xaw3ep
 *
 */
public class ViolationFrequencyHook implements NCPHook, IFirst {
    private ViolationFrequencyConfig config;
    private Integer hookId = null;
    
    public ViolationFrequencyHook() {}
    
    public void setConfig(ViolationFrequencyConfig config) {
        this.config = config;
        if (config == null || !config.active) {
            unregister();
        } 
        else {
            register();
        }
    }

    public void unregister() {
        if (hookId != null) {
            NCPHookManager.removeHook(this);
            this.hookId = null;
        }
    }

    public void register() {
        unregister();
        this.hookId = NCPHookManager.addHook(CheckType.MOVING_SURVIVALFLY, this);
    }
    
    @Override
    public String getHookName() {
        return "ViolationFrequency(NCP)";
    }

    @Override
    public String getHookVersion() {
        return "1.1";
    }

    @Override
    public boolean onCheckFailure(final CheckType checkType, final Player player, final IViolationInfo info) {
        final ViolationFrequencyConfig config = this.config;
        final StringBuilder builder = new StringBuilder(300);
        if (info.getTotalVl() > config.maxThresholdVL) {
            // Beyond max threshold; hook is not needed.
            return false;
        }
        final IPlayerData pData = DataManager.getPlayerData(player);
        if (pData != null) {
            final MovingData data = pData.getGenericInstance(MovingData.class);
            if (info.getAddedVl() > config.noAdditionVL) {
                // Current violation is higher than the custom threshold.
                // No need to increase the VL, return.
                if (config.debug) {
                    builder.append("SurvivalFly: ");
                    builder.append("VL=" + StringUtil.fdec1.format(info.getTotalVl()));
                    builder.append("(+" + StringUtil.fdec1.format(info.getAddedVl()) + ")");
                    log(builder.toString(), player);
                }
                return true;
            }

            // Violation is lower than noAdditionVL
            final int lastViolatedMoveCount = data.getPlayerMoveCount() - data.sfVLMoveCount;
            if (lastViolatedMoveCount <= config.lastViolatedMoveCount) {
                // A too small violation (lower than noAdditionVL) has recently happened after another, do increase the violation.
                data.survivalFlyVL += config.amountToAdd;
                if (config.debug) {
                    builder.append("SurvivalFly: ");
                    builder.append("VL=" + StringUtil.fdec1.format(info.getTotalVl()));
                    builder.append("(+" + StringUtil.fdec1.format(info.getAddedVl()) + ") -> ");
                    builder.append("VL=" + StringUtil.fdec1.format(info.getTotalVl() + config.amountToAdd));
                    builder.append("(+" + StringUtil.fdec1.format(info.getAddedVl() + config.amountToAdd) + ")");
                    log(builder.toString(), player);
                }
                if (info.getTotalVl() + config.amountToAdd <= config.maxThresholdVL) {
                    return true; 
                }
                else {
                    return false;                
                }
            }
            
            if (config.debug) {
                builder.append("SurvivalFly: ");
                builder.append("VL=" + StringUtil.fdec1.format(info.getTotalVl()));
                builder.append("(+" + StringUtil.fdec1.format(info.getAddedVl()) + ")");
                log(builder.toString(), player);
            }
            // Move count from last violated move is too high
            return true;
        }
        // Player data is null.
        return false;
    }
    
    private void log(String s, Player p) {
        final LogManager logManager = NCPAPIProvider.getNoCheatPlusAPI().getLogManager();
        final StringBuilder builder = new StringBuilder(300);
        builder.append("ViolationFrequency");
        builder.append(" [" + ChatColor.YELLOW + p.getName());
        builder.append(ChatColor.WHITE + "] ");
        builder.append(s);
        final String message = builder.toString();
        logManager.info(Streams.NOTIFY_INGAME, message);
    }
}