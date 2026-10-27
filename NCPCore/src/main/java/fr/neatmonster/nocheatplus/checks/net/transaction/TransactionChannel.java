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
package fr.neatmonster.nocheatplus.checks.net.transaction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.components.data.IData;
import fr.neatmonster.nocheatplus.components.data.IDataOnJoin;
import fr.neatmonster.nocheatplus.components.data.IDataOnLeave;
import fr.neatmonster.nocheatplus.logging.StaticLog;
import fr.neatmonster.nocheatplus.players.IPlayerData;

/**
 * Per-player transaction clock other checks can queue confirmations on.
 * <p>
 * A check sends a ping, or waits for the one written at the end of the server
 * tick, and runs work when the client answers it. Answers come back in order.
 * A task stored on id {@code N} runs once the reply for {@code N} arrives. The
 * id just after the latest reply is left waiting, because that ping may still
 * be in flight. Before 1.17 the ping is a window confirmation. On 1.17+ it is
 * a ping/pong.
 * <p>
 * Short ids stay in {@code -32767..-31269}. On 1.17+ the int sent to the client is
 * {@link #PING_MASK} with the short in the low 16 bits.
 * <pre>
 * TransactionChannel clock = pData.getGenericInstance(TransactionChannel.class);
 * final int confirm = clock.getLastSent() + 1;
 * clock.addTask(confirm, new Runnable() {
 *     public void run() {
 *         // The client has processed packets sent before the next ping.
 *     }
 * });
 * </pre>
 * {@code send(player)} forces a ping and returns its sequence. The end-of-tick
 * ping already confirms tasks queued on {@code lastSent + 1}.
 */
public final class TransactionChannel implements IData, IDataOnJoin, IDataOnLeave {

    /** Inclusive. Lowest short this channel will use. */
    public static final short MIN_ID = (short) -32767;
    /** Inclusive. Highest short this channel will use. */
    public static final short MAX_ID = (short) -31269;

    public static final int PING_MASK = 0xC0DE0000;

    private static final int MAX_PENDING = 300;

    /**
     * Writes one ping. Installed by the ProtocolLib hook so this class does not
     * reference ProtocolLib itself.
     */
    public interface Sender {
        /**
         * @param wireId full ping int on 1.17+, or the short id on older servers
         * @param legacy true when the packet is a window confirmation
         */
        boolean send(Player player, int wireId, boolean legacy);
    }

    private static volatile Sender sender;
    private static volatile boolean usePing;
    private static boolean warned;

    private final Object lock = new Object();
    private final ArrayDeque<Pending> pending = new ArrayDeque<Pending>();
    private final Set<Short> outstanding = new HashSet<Short>();
    private final ArrayList<Task> tasks = new ArrayList<Task>();

    private int lastSent;
    private int lastReceived;
    private short cursor;
    private long lastPingNanos = -1L;
    /** New data can send immediately. Leave closes it until the next join. */
    private boolean open = true;

    public static void setSender(Sender transactionSender, boolean ping) {
        sender = transactionSender;
        usePing = ping;
    }

    public static void setSender(Sender transactionSender) {
        sender = transactionSender;
    }

    /** True once the packet hook is installed and pings can actually be written. */
    public static boolean isHooked() {
        return sender != null;
    }

    public static boolean usesPing() {
        return usePing;
    }

    @Override
    public boolean dataOnJoin(Player player, IPlayerData pData) {
        onJoin();
        return false;
    }

    @Override
    public boolean dataOnLeave(Player player, IPlayerData pData) {
        onLeave();
        return false;
    }

    public void onJoin() {
        synchronized (lock) {
            reset();
            open = true;
        }
    }

    public void onLeave() {
        synchronized (lock) {
            open = false;
            reset();
        }
    }

    /**
     * Write one ping.
     *
     * @return the sequence just sent, or 0 when nothing was written
     */
    public int send(Player player) {
        List<Runnable> due = null;
        int sequence = 0;
        synchronized (lock) {
            Sender current = sender;
            if (current == null || player == null || !open) {
                return 0;
            }
            int reached = lastReceived;
            boolean dropped = false;
            while (pending.size() > MAX_PENDING) {
                Pending old = pending.poll();
                if (old == null) {
                    break;
                }
                outstanding.remove(old.id);
                reached = old.sequence;
                dropped = true;
            }
            if (dropped) {
                lastReceived = reached;
                due = drain(reached);
            }
            short id = nextShort();
            int wire = usePing ? (PING_MASK | (id & 0xFFFF)) : id;
            boolean written = false;
            try {
                written = current.send(player, wire, !usePing);
            }
            catch (Throwable throwable) {
                log(throwable);
                written = false;
            }
            if (written) {
                lastSent++;
                pending.add(new Pending(id, lastSent, System.nanoTime()));
                outstanding.add(Short.valueOf(id));
                sequence = lastSent;
            }
        }
        runAll(due);
        return sequence;
    }

    /**
     * Run {@code runnable} once transaction {@code sequence} has been answered.
     * A sequence the client has already passed runs immediately, on this thread,
     * after the clock lock is released.
     */
    public void addTask(int sequence, Runnable runnable) {
        if (runnable == null) {
            return;
        }
        if (sequence <= 0) {
            try {
                runnable.run();
            }
            catch (Throwable throwable) {
                log(throwable);
            }
            return;
        }
        boolean now = false;
        synchronized (lock) {
            if (lastReceived >= sequence) {
                now = true;
            }
            else {
                insert(sequence, runnable);
            }
        }
        if (now) {
            try {
                runnable.run();
            }
            catch (Throwable throwable) {
                log(throwable);
            }
        }
    }

    /**
     * @param wire full pong int, or the echoed transaction short
     * @return true when this was one of our pings and the packet can be cancelled
     */
    public boolean onReply(int wire) {
        short id;
        if (usePing) {
            if ((wire & 0xFFFF0000) != PING_MASK) {
                return false;
            }
            id = (short) wire;
        }
        else {
            id = (short) wire;
            if (wire != id) {
                return false;
            }
        }
        if (id > MAX_ID || id < MIN_ID) {
            return false;
        }
        List<Runnable> due = null;
        synchronized (lock) {
            if (!outstanding.contains(Short.valueOf(id))) {
                return false;
            }
            int reached = lastReceived;
            while (!pending.isEmpty()) {
                Pending next = pending.poll();
                outstanding.remove(next.id);
                reached = next.sequence;
                lastPingNanos = System.nanoTime() - next.sentNano;
                if (next.id == id) {
                    break;
                }
            }
            lastReceived = reached;
            due = drain(reached);
        }
        runAll(due);
        return true;
    }

    public int getLastSent() {
        synchronized (lock) {
            return lastSent;
        }
    }

    public int getLastReceived() {
        synchronized (lock) {
            return lastReceived;
        }
    }

    /** Round trip of the latest answered ping, or -1 if none has come back yet. */
    public long getPingMillis() {
        synchronized (lock) {
            return lastPingNanos < 0L ? -1L : lastPingNanos / 1000000L;
        }
    }

    private short nextShort() {
        if (cursor == 0 || cursor <= MIN_ID) {
            cursor = MAX_ID;
        }
        else {
            cursor--;
        }
        return cursor;
    }

    private void insert(int sequence, Runnable runnable) {
        int index = tasks.size();
        while (index > 0 && tasks.get(index - 1).sequence > sequence) {
            index--;
        }
        tasks.add(index, new Task(sequence, runnable));
    }

    /**
     * Tasks are ordered by sequence. Skip the one id past the latest reply and
     * stop once the rest are further ahead than that.
     */
    private List<Runnable> drain(int sequence) {
        List<Runnable> ready = null;
        Iterator<Task> iterator = tasks.iterator();
        while (iterator.hasNext()) {
            Task task = iterator.next();
            if (sequence + 1 < task.sequence) {
                break;
            }
            if (sequence == task.sequence - 1) {
                continue;
            }
            if (ready == null) {
                ready = new ArrayList<Runnable>();
            }
            ready.add(task.runnable);
            iterator.remove();
        }
        return ready;
    }

    private void reset() {
        pending.clear();
        outstanding.clear();
        tasks.clear();
        lastSent = 0;
        lastReceived = 0;
        cursor = 0;
        lastPingNanos = -1L;
    }

    private static void runAll(List<Runnable> due) {
        if (due == null) {
            return;
        }
        for (int index = 0; index < due.size(); index++) {
            try {
                due.get(index).run();
            }
            catch (Throwable throwable) {
                log(throwable);
            }
        }
    }

    private static void log(Throwable throwable) {
        if (!warned) {
            warned = true;
            StaticLog.logWarning("Transaction confirmation failed: " + throwable.getMessage());
        }
    }

    private static final class Pending {
        final short id;
        final int sequence;
        final long sentNano;

        Pending(short id, int sequence, long sentNano) {
            this.id = id;
            this.sequence = sequence;
            this.sentNano = sentNano;
        }
    }

    private static final class Task {
        final int sequence;
        final Runnable runnable;

        Task(int sequence, Runnable runnable) {
            this.sequence = sequence;
            this.runnable = runnable;
        }
    }
}
