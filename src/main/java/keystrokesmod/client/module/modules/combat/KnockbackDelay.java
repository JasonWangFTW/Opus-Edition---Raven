package keystrokesmod.client.module.modules.combat;

import com.google.common.eventbus.Subscribe;
import keystrokesmod.client.event.EventDirection;
import keystrokesmod.client.event.impl.PacketEvent;
import keystrokesmod.client.event.impl.TickEvent;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.setting.impl.ComboSetting;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * KnockbackDelay — delays velocity packets AND the surrounding
 * transaction packets so anti-cheat sees a coherent "lag spike"
 * instead of instant transaction acks with missing velocity.
 *
 * <p>Packet sequence from server around a hit:
 * <pre>
 *   S32 (transaction) → S12 (velocity) → S32 (transaction)
 * </pre>
 * The client normally responds to each S32 with a C0F immediately.
 * This module holds the C0F responses and the S12 in a queue, then
 * releases them all together after the configured delay.
 */
public class KnockbackDelay extends Module {

    /* ── Settings (unchanged) ───────────────────────────────────── */

    private static SliderSetting airDelay;
    private static SliderSetting groundDelay;
    private static SliderSetting chance;
    private static ComboSetting<Mode> mode;

    /* ── Safety limits ──────────────────────────────────────────── */

    /** Maximum time (ms) packets may be held before a forced release. */
    private static final long MAX_CAPTURE_DURATION_MS = 1000L;

    /** Maximum number of transaction packets to capture before a forced release. */
    private static final int MAX_CAPTURED_TRANSACTIONS = 20;

    /* ── Capture state ──────────────────────────────────────────── */

    /**
     * Ordered queue of packets captured during the delay window.
     * Each entry knows whether it is an outgoing C0F that must be
     * resent, or velocity data that must be applied to the player.
     */
    private final ConcurrentLinkedQueue<DelayedEntry> packetQueue = new ConcurrentLinkedQueue<>();

    /** Whether we are currently intercepting transactions. */
    private volatile boolean capturing = false;

    /** Timestamp at which the current capture window should release. */
    private volatile long releaseAt = -1;

    /** Timestamp at which the current capture window began (safety). */
    private volatile long captureStartTime = -1;

    /** Running count of transactions captured in the current window. */
    private volatile int capturedTransactionCount = 0;

    /**
     * Flag to prevent our own resent C0F packets from being
     * re-intercepted by onPacket().
     */
    private volatile boolean releasing = false;

    /* ── Constructor ─────────────────────────────────────────────── */

    public KnockbackDelay() {
        super("KnockbackDelay", ModuleCategory.combat);
        this.registerSetting(airDelay    = new SliderSetting("Air Delay (ms)",    150, 0, 500, 5));
        this.registerSetting(groundDelay = new SliderSetting("Ground Delay (ms)", 100, 0, 500, 5));
        this.registerSetting(chance      = new SliderSetting("Chance (%)",         80, 0, 100, 1));
        this.registerSetting(mode        = new ComboSetting<>("Mode", Mode.Normal));
    }

    /* ── Lifecycle ──────────────────────────────────────────────── */

    @Override
    public void onDisable() {
        flushAllPackets();
    }

    /* ── Packet handler ─────────────────────────────────────────── */

    @Subscribe
    public void onPacket(PacketEvent e) {
        if (mc.thePlayer == null) return;

        // Never intercept packets we are replaying
        if (releasing) return;

        Packet<?> pkt = e.getPacket();

        /* ─── Incoming: S12 velocity ─────────────────────────────── */
        if (e.isIncoming() && pkt instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity velPkt = (S12PacketEntityVelocity) pkt;

            // Only care about our own player
            if (velPkt.getEntityID() != mc.thePlayer.getEntityId()) return;

            // Only intercept player-vs-player hits
            if (!(mc.thePlayer.getLastAttacker() instanceof EntityPlayer)) return;

            // Chance roll — if it fails, let everything through normally
            if (Math.random() * 100 > chance.getInput()) return;

            // Cancel the velocity packet
            e.setCancelled(true);

            // Begin a new capture window (or extend an existing one for
            // back-to-back velocity packets)
            long delay = mc.thePlayer.onGround
                    ? (long) groundDelay.getInput()
                    : (long) airDelay.getInput();

            long now = System.currentTimeMillis();
            releaseAt = now + delay;

            if (!capturing) {
                captureStartTime = now;
                capturedTransactionCount = 0;
            }
            capturing = true;

            // Queue the velocity for later application
            packetQueue.add(new DelayedEntry(EntryType.VELOCITY, velPkt));
            return;
        }

        /* ─── Incoming: S32 transaction (during capture) ─────────── */
        if (capturing && e.isIncoming() && pkt instanceof S32PacketConfirmTransaction) {
            // We do NOT cancel the incoming S32 — let the client process it
            // and generate the C0F response naturally.  We intercept the
            // outgoing C0F below instead.  This way vanilla bookkeeping
            // (container state) stays correct on the client side.
            return;
        }

        /* ─── Outgoing: C0F transaction response (during capture) ── */
        if (capturing && e.isOutgoing() && pkt instanceof C0FPacketConfirmTransaction) {
            // Cancel the outgoing response and queue it for delayed send
            e.setCancelled(true);

            packetQueue.add(new DelayedEntry(EntryType.TRANSACTION_OUT, pkt));
            capturedTransactionCount++;

            // Safety: too many transactions captured → force flush
            if (capturedTransactionCount >= MAX_CAPTURED_TRANSACTIONS) {
                flushAllPackets();
            }
            return;
        }
    }

    /* ── Tick handler — release timer + safety ──────────────────── */

    @Subscribe
    public void onTick(TickEvent e) {
        if (!capturing || packetQueue.isEmpty()) return;

        if (mc.thePlayer == null || mc.thePlayer.isDead) {
            flushAllPackets();
            return;
        }

        long now = System.currentTimeMillis();

        // Safety: force-release if capture has been held too long
        if (now - captureStartTime >= MAX_CAPTURE_DURATION_MS) {
            flushAllPackets();
            return;
        }

        // Normal release: delay has elapsed
        if (now >= releaseAt) {
            flushAllPackets();
        }
    }

    /* ── Flush / release logic ──────────────────────────────────── */

    /**
     * Releases all held packets in order:
     * <ul>
     *   <li>C0F outgoing transactions are resent through the network manager</li>
     *   <li>S12 velocity values are applied to the player's motion</li>
     * </ul>
     * From the server's perspective this looks like a brief lag spike
     * followed by all responses arriving at once.
     */
    private void flushAllPackets() {
        capturing = false;
        releaseAt = -1;
        captureStartTime = -1;
        capturedTransactionCount = 0;

        if (packetQueue.isEmpty()) return;

        // Set flag so our onPacket() doesn't re-intercept the resent C0Fs
        releasing = true;
        try {
            boolean didJumpReset = false;

            DelayedEntry entry;
            while ((entry = packetQueue.poll()) != null) {
                switch (entry.type) {
                    case TRANSACTION_OUT:
                        // Resend the held C0F via the network manager
                        if (mc.thePlayer != null && mc.thePlayer.sendQueue != null) {
                            mc.thePlayer.sendQueue.getNetworkManager().sendPacket(entry.packet);
                        }
                        break;

                    case VELOCITY:
                        S12PacketEntityVelocity velPkt = (S12PacketEntityVelocity) entry.packet;

                        // JumpReset: jump once right before the first velocity application
                        if (!didJumpReset
                                && mode.getMode() == Mode.JumpReset
                                && mc.thePlayer != null
                                && mc.thePlayer.onGround) {
                            mc.thePlayer.jump();
                            didJumpReset = true;
                        }

                        // Apply velocity (mirrors vanilla S12 handling)
                        if (mc.thePlayer != null) {
                            mc.thePlayer.motionX = velPkt.getMotionX() / 8000.0;
                            mc.thePlayer.motionY = velPkt.getMotionY() / 8000.0;
                            mc.thePlayer.motionZ = velPkt.getMotionZ() / 8000.0;
                        }
                        break;
                }
            }
        } finally {
            releasing = false;
        }
    }

    /* ── Inner types ────────────────────────────────────────────── */

    public enum Mode {
        Normal, JumpReset
    }

    private enum EntryType {
        /** Outgoing C0F transaction response to be resent. */
        TRANSACTION_OUT,
        /** Incoming S12 velocity to be applied to player motion. */
        VELOCITY
    }

    /**
     * A single entry in the delay queue — either an outgoing C0F packet
     * or an S12 velocity packet, stored in arrival order.
     */
    private static class DelayedEntry {
        final EntryType type;
        final Packet<?> packet;

        DelayedEntry(EntryType type, Packet<?> packet) {
            this.type = type;
            this.packet = packet;
        }
    }
}