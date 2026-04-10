package keystrokesmod.client.module.modules.player;

import com.google.common.eventbus.Subscribe;

import keystrokesmod.client.event.EventTiming;
import keystrokesmod.client.event.impl.Render2DEvent;
import keystrokesmod.client.event.impl.TickEvent;
import keystrokesmod.client.event.impl.UpdateEvent;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.setting.impl.ComboSetting;
import keystrokesmod.client.module.setting.impl.DescriptionSetting;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import keystrokesmod.client.utils.Utils;

import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.BlockTNT;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.awt.Color;

/**
 * Clutch module — automatically places a block beneath the player
 * when they are about to die from falling, void, or a configurable
 * fall-distance threshold.
 * <p>
 * The module rotates the player's view downward (server-side or
 * client-side), silently switches to a valid block slot, places the
 * block, and optionally restores the original rotation and hotbar slot.
 */
public class Clutch extends Module {

    /* ── Settings ───────────────────────────────────────────────── */

    // Activation mode
    public static ComboSetting<ActivationMode> activationMode;

    // Fall distance threshold (only used in DISTANCE mode)
    public static SliderSetting fallDistanceThreshold;

    // Rotation / placement
    public static TickSetting silentAim;
    public static TickSetting resetAngle;
    public static TickSetting returnToSlot;

    // Block-count HUD overlay
    public static TickSetting showBlockCount;

    // Movement freeze after clutch
    public static SliderSetting clutchMoveDelay;

    // Max blocks the module will try to place (pillar height limit)
    public static SliderSetting maxBlocks;

    // Staircase – allow repeated placements
    public static TickSetting allowStaircase;

    // Block selection mode
    public static ComboSetting<BlockSelectionMode> blockSelectionMode;

    /* ── Internal state ─────────────────────────────────────────── */

    /** The current phase of the clutch attempt. */
    private ClutchPhase phase = ClutchPhase.IDLE;

    /** The slot the player was using before the clutch. */
    private int originalSlot = -1;

    /** The player's yaw / pitch before the clutch rotation. */
    private float originalYaw, originalPitch;

    /** The target yaw / pitch for the clutch look-down. */
    private float targetYaw, targetPitch;

    /** How many ticks to freeze movement after a clutch. */
    private int freezeTicks = 0;

    /** How many blocks have been placed during the current clutch. */
    private int blocksPlaced = 0;

    /** Whether we are currently spoofing the rotation via UpdateEvent. */
    private boolean spoofingRotation = false;

    /** Countdown ticks for the place step (gives one tick for the rotation packet to arrive). */
    private int placeWaitTicks = 0;

    /** Countdown for restoring rotation after place. */
    private int restoreWaitTicks = 0;

    /* ── Enums ──────────────────────────────────────────────────── */

    public enum ActivationMode {
        Void, Lethal, Distance
    }

    public enum BlockSelectionMode {
        Normal, Blacklist, Whitelist
    }

    private enum ClutchPhase {
        /** Not clutching. */
        IDLE,
        /** Rotation has been set; wait one tick for server to receive it. */
        ROTATING,
        /** Place the block this tick. */
        PLACING,
        /** Block placed; optionally restore rotation / slot. */
        RESTORING,
        /** Freeze movement for clutchMoveDelay ticks. */
        FREEZING
    }

    /* ── Constructor ────────────────────────────────────────────── */

    public Clutch() {
        super("Clutch", ModuleCategory.player);

        this.registerSetting(new DescriptionSetting("Auto-places a block when falling"));
        this.registerSetting(activationMode = new ComboSetting<>("Activation", ActivationMode.Lethal));
        this.registerSetting(fallDistanceThreshold = new SliderSetting("Fall distance", 5.0, 3.0, 30.0, 1.0));
        this.registerSetting(silentAim = new TickSetting("Silent aim", true));
        this.registerSetting(resetAngle = new TickSetting("Reset angle", true));
        this.registerSetting(returnToSlot = new TickSetting("Return to slot", true));
        this.registerSetting(showBlockCount = new TickSetting("Show block count", false));
        this.registerSetting(clutchMoveDelay = new SliderSetting("Move delay", 2.0, 0.0, 10.0, 1.0));
        this.registerSetting(maxBlocks = new SliderSetting("Max blocks", 5.0, 1.0, 20.0, 1.0));
        this.registerSetting(allowStaircase = new TickSetting("Allow staircase", false));
        this.registerSetting(blockSelectionMode = new ComboSetting<>("Block selection", BlockSelectionMode.Normal));
    }

    /* ── Lifecycle ──────────────────────────────────────────────── */

    @Override
    public void onEnable() {
        resetState();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        // Make sure rotation / slot are cleaned up even on manual toggle-off.
        if (spoofingRotation && !silentAim.isToggled()) {
            mc.thePlayer.rotationYaw = originalYaw;
            mc.thePlayer.rotationPitch = originalPitch;
        }
        spoofingRotation = false;
        resetState();
    }

    private void resetState() {
        phase = ClutchPhase.IDLE;
        originalSlot = -1;
        freezeTicks = 0;
        blocksPlaced = 0;
        spoofingRotation = false;
        placeWaitTicks = 0;
        restoreWaitTicks = 0;
    }

    /* ── Tick handler — main state machine ──────────────────────── */

    @Subscribe
    public void onTick(TickEvent e) {
        if (!Utils.Player.isPlayerInGame() || mc.thePlayer.capabilities.isCreativeMode) {
            return;
        }

        // Movement freeze after clutch
        if (freezeTicks > 0) {
            mc.thePlayer.motionX = 0;
            mc.thePlayer.motionZ = 0;
            // keep motionY untouched so gravity still works
            freezeTicks--;
            if (freezeTicks <= 0) {
                resetState();
            }
            return;
        }

        switch (phase) {
            case IDLE:
                handleIdle();
                break;

            case ROTATING:
                handleRotating();
                break;

            case PLACING:
                handlePlacing();
                break;

            case RESTORING:
                handleRestoring();
                break;

            case FREEZING:
                // Handled by the freeze block above
                break;
        }
    }

    /* ── State handlers ─────────────────────────────────────────── */

    /**
     * IDLE: check whether clutch conditions are met; if so, begin the clutch sequence.
     */
    private void handleIdle() {
        if (!shouldClutch()) {
            return;
        }

        int blockSlot = findBlockSlot();
        if (blockSlot == -1) {
            return; // No block available
        }

        // Count available blocks — bail if we'd need more than maxBlocks
        int availableBlocks = countAvailableBlocks();
        if (availableBlocks <= 0) {
            return;
        }

        // Save original state
        originalSlot = mc.thePlayer.inventory.currentItem;
        originalYaw = mc.thePlayer.rotationYaw;
        originalPitch = mc.thePlayer.rotationPitch;

        // Calculate rotation to look straight down below the player
        computeTargetRotation();

        // Switch hotbar slot (silently via packet)
        if (blockSlot != mc.thePlayer.inventory.currentItem) {
            mc.thePlayer.sendQueue.addToSendQueue(new C09PacketHeldItemChange(blockSlot));
            mc.thePlayer.inventory.currentItem = blockSlot;
        }

        // Apply rotation
        if (silentAim.isToggled()) {
            spoofingRotation = true;
            // Rotation will be sent in the UpdateEvent handler
        } else {
            mc.thePlayer.rotationYaw = targetYaw;
            mc.thePlayer.rotationPitch = targetPitch;
        }

        placeWaitTicks = 1; // give one tick for rotation packet
        phase = ClutchPhase.ROTATING;
    }

    /**
     * ROTATING: wait one tick for the rotation packet to register server-side, then proceed.
     */
    private void handleRotating() {
        placeWaitTicks--;
        if (placeWaitTicks <= 0) {
            phase = ClutchPhase.PLACING;
        }
    }

    /**
     * PLACING: find a valid placement target and place the block.
     */
    private void handlePlacing() {
        boolean placed = attemptBlockPlacement();

        if (placed) {
            blocksPlaced++;
            // If staircase is on and we're still falling, loop back to ROTATING
            if (allowStaircase.isToggled() && blocksPlaced < (int) maxBlocks.getInput()
                    && !mc.thePlayer.onGround && shouldClutch()) {
                computeTargetRotation();
                placeWaitTicks = 1;
                phase = ClutchPhase.ROTATING;
                return;
            }
        }

        phase = ClutchPhase.RESTORING;
        restoreWaitTicks = 1;
    }

    /**
     * RESTORING: give one tick then restore rotation / hotbar.
     */
    private void handleRestoring() {
        restoreWaitTicks--;
        if (restoreWaitTicks > 0) {
            return;
        }

        // Restore rotation
        if (resetAngle.isToggled()) {
            if (silentAim.isToggled()) {
                spoofingRotation = false;
                // No visual change needed; the server rotation will auto-restore
                // on the next packet without our override.
            } else {
                mc.thePlayer.rotationYaw = originalYaw;
                mc.thePlayer.rotationPitch = originalPitch;
            }
        }
        spoofingRotation = false;

        // Restore hotbar slot
        if (returnToSlot.isToggled() && originalSlot != -1
                && originalSlot != mc.thePlayer.inventory.currentItem) {
            mc.thePlayer.sendQueue.addToSendQueue(new C09PacketHeldItemChange(originalSlot));
            mc.thePlayer.inventory.currentItem = originalSlot;
        }

        int delay = (int) clutchMoveDelay.getInput();
        if (delay > 0) {
            freezeTicks = delay;
            phase = ClutchPhase.FREEZING;
        } else {
            resetState();
        }
    }

    /* ── Rotation spoofing via UpdateEvent ──────────────────────── */

    @Subscribe
    public void onUpdate(UpdateEvent e) {
        if (e.getTiming() != EventTiming.PRE) {
            return;
        }

        if (spoofingRotation && silentAim.isToggled()) {
            e.setYaw(targetYaw);
            e.setPitch(targetPitch);
        }
    }

    /* ── Block-count HUD overlay ────────────────────────────────── */

    @Subscribe
    public void onRender2D(Render2DEvent e) {
        if (!showBlockCount.isToggled() || !Utils.Player.isPlayerInGame()) {
            return;
        }
        if (mc.currentScreen != null) {
            return;
        }

        int count = countAvailableBlocks();
        if (count <= 0) {
            return;
        }

        ScaledResolution res = new ScaledResolution(mc);

        int rgb;
        if (count < 8) {
            rgb = Color.RED.getRGB();
        } else if (count < 16) {
            rgb = new Color(255, 165, 0).getRGB(); // orange
        } else if (count < 32) {
            rgb = Color.YELLOW.getRGB();
        } else {
            rgb = Color.GREEN.getRGB();
        }

        String text = count + " clutch blocks";
        int x = res.getScaledWidth() / 2 - mc.fontRendererObj.getStringWidth(text) / 2;
        int y = res.getScaledHeight() / 2 + 20;
        mc.fontRendererObj.drawString(text, (float) x, (float) y, rgb, false);
    }

    /* ── Condition checks ───────────────────────────────────────── */

    /**
     * Determines whether the clutch should activate based on the
     * selected {@link ActivationMode}.
     */
    private boolean shouldClutch() {
        if (mc.thePlayer.onGround || mc.thePlayer.capabilities.isFlying
                || mc.thePlayer.isInWater() || mc.thePlayer.isOnLadder()) {
            return false;
        }

        // Must be falling
        if (mc.thePlayer.motionY >= 0) {
            return false;
        }

        switch ((ActivationMode) activationMode.getMode()) {
            case Void:
                return mc.thePlayer.posY < 0;

            case Lethal: {
                float effectiveFallDist = mc.thePlayer.fallDistance;
                // Predict additional fall distance from current velocity
                // (rough estimate: at this tick the player will fall at least motionY more)
                // Fall damage = fallDistance - 3.0 (in half-hearts / damage points)
                float reductionFromJumpBoost = 0;
                PotionEffect jumpBoost = mc.thePlayer.getActivePotionEffect(Potion.jump);
                if (jumpBoost != null) {
                    reductionFromJumpBoost = (jumpBoost.getAmplifier() + 1);
                }
                float predictedDamage = effectiveFallDist - 3.0f - reductionFromJumpBoost;

                // Trigger when predicted damage would kill or bring health dangerously low
                return predictedDamage >= mc.thePlayer.getHealth();
            }

            case Distance:
                return mc.thePlayer.fallDistance >= fallDistanceThreshold.getInput();

            default:
                return false;
        }
    }

    /* ── Block selection ────────────────────────────────────────── */

    /**
     * Scan hotbar (slots 0-8) for the best valid block slot.
     * Returns the slot index or -1 if none found.
     */
    private int findBlockSlot() {
        int bestSlot = -1;
        int bestCount = 0;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack == null) continue;
            if (!(stack.getItem() instanceof ItemBlock)) continue;

            Block block = ((ItemBlock) stack.getItem()).getBlock();
            if (!isBlockValid(block)) continue;

            if (stack.stackSize > bestCount) {
                bestCount = stack.stackSize;
                bestSlot = i;
            }
        }

        return bestSlot;
    }

    /**
     * Count all valid blocks across the hotbar.
     */
    private int countAvailableBlocks() {
        int total = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack == null) continue;
            if (!(stack.getItem() instanceof ItemBlock)) continue;

            Block block = ((ItemBlock) stack.getItem()).getBlock();
            if (!isBlockValid(block)) continue;

            total += stack.stackSize;
        }
        return total;
    }

    /**
     * Validates a block according to the current {@link BlockSelectionMode}.
     */
    private boolean isBlockValid(Block block) {
        if (block == null || block instanceof BlockAir) return false;

        // Always reject certain dangerous / non-solid blocks
        if (block instanceof BlockTNT) return false;
        if (block instanceof BlockFalling) return false; // sand, gravel, etc.
        if (block instanceof BlockLiquid) return false;

        // The block must be full-cube and collidable
        if (!block.isFullCube()) return false;
        if (!block.isCollidable()) return false;

        switch ((BlockSelectionMode) blockSelectionMode.getMode()) {
            case Blacklist:
                // In blacklist mode the dangerous blocks above are already rejected.
                // Extend here with additional user-defined blocks if desired.
                return true;

            case Whitelist:
                // Only allow common building blocks
                return block == Blocks.cobblestone
                        || block == Blocks.stone
                        || block == Blocks.dirt
                        || block == Blocks.planks
                        || block == Blocks.sandstone
                        || block == Blocks.netherrack
                        || block == Blocks.end_stone
                        || block == Blocks.obsidian
                        || block == Blocks.wool
                        || block == Blocks.stained_hardened_clay
                        || block == Blocks.hardened_clay;

            case Normal:
            default:
                return true;
        }
    }

    /* ── Rotation calculation ───────────────────────────────────── */

    /**
     * Compute the yaw/pitch needed to look at the placement position
     * directly below the player.  Accounts for horizontal momentum so
     * the crosshair target leads the player's actual position.
     */
    private void computeTargetRotation() {
        // Target: the block position directly under the player's feet,
        // adjusted for horizontal momentum
        double targetX = mc.thePlayer.posX + mc.thePlayer.motionX;
        double targetZ = mc.thePlayer.posZ + mc.thePlayer.motionZ;
        // We want to look at the top-center of the block below us
        BlockPos below = new BlockPos(
                MathHelper.floor_double(targetX),
                MathHelper.floor_double(mc.thePlayer.posY) - 1,
                MathHelper.floor_double(targetZ)
        );

        double dx = (below.getX() + 0.5) - mc.thePlayer.posX;
        double dz = (below.getZ() + 0.5) - mc.thePlayer.posZ;
        double dy = (below.getY() + 1.0) - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());

        double dist = Math.sqrt(dx * dx + dz * dz);
        targetYaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        targetPitch = (float) -(Math.atan2(dy, dist) * 180.0 / Math.PI);

        // Clamp pitch to valid range (should be ~80-90 when looking straight down)
        if (targetPitch > 90.0f) targetPitch = 90.0f;
        if (targetPitch < -90.0f) targetPitch = -90.0f;
    }

    /* ── Block placement ────────────────────────────────────────── */

    /**
     * Attempts to place a block beneath the player using the currently
     * held item.  Searches for a valid adjacent solid face to place against.
     *
     * @return true if a block was successfully placed.
     */
    private boolean attemptBlockPlacement() {
        ItemStack heldItem = mc.thePlayer.getHeldItem();
        if (heldItem == null || !(heldItem.getItem() instanceof ItemBlock)) {
            return false;
        }

        // Target position: directly under the player's feet
        BlockPos targetPos = new BlockPos(
                MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(mc.thePlayer.posY) - 1,
                MathHelper.floor_double(mc.thePlayer.posZ)
        );

        // Try placing against an adjacent solid block
        PlacementInfo info = findPlacementInfo(targetPos);

        if (info == null) {
            // Try one block lower if the direct-below position already has a block
            targetPos = targetPos.down();
            info = findPlacementInfo(targetPos);
        }

        if (info == null) {
            return false;
        }

        // Check reach — 4.5 blocks for block placement in 1.8.9
        Vec3 eyePos = new Vec3(
                mc.thePlayer.posX,
                mc.thePlayer.posY + mc.thePlayer.getEyeHeight(),
                mc.thePlayer.posZ
        );
        Vec3 placeVec = new Vec3(
                info.neighbor.getX() + 0.5 + info.face.getFrontOffsetX() * 0.5,
                info.neighbor.getY() + 0.5 + info.face.getFrontOffsetY() * 0.5,
                info.neighbor.getZ() + 0.5 + info.face.getFrontOffsetZ() * 0.5
        );
        if (eyePos.distanceTo(placeVec) > 4.5) {
            return false;
        }

        // Place the block
        if (mc.playerController.onPlayerRightClick(
                mc.thePlayer, mc.theWorld, heldItem,
                info.neighbor, info.face, placeVec)) {
            mc.thePlayer.swingItem();
            return true;
        }

        return false;
    }

    /**
     * Searches the six faces around {@code targetPos} for a solid
     * neighbor block that can be placed against.
     */
    private PlacementInfo findPlacementInfo(BlockPos targetPos) {
        // The target position must be air (or replaceable)
        Block targetBlock = mc.theWorld.getBlockState(targetPos).getBlock();
        if (targetBlock != Blocks.air && !targetBlock.isReplaceable(mc.theWorld, targetPos)) {
            return null;
        }

        // Priority order: DOWN, NORTH, SOUTH, EAST, WEST, UP
        EnumFacing[] facePriority = {
                EnumFacing.DOWN, EnumFacing.NORTH, EnumFacing.SOUTH,
                EnumFacing.EAST, EnumFacing.WEST, EnumFacing.UP
        };

        for (EnumFacing face : facePriority) {
            BlockPos neighbor = targetPos.offset(face);
            Block neighborBlock = mc.theWorld.getBlockState(neighbor).getBlock();

            if (neighborBlock == Blocks.air || neighborBlock instanceof BlockLiquid) {
                continue;
            }

            // The neighbor must be solid enough to place against
            if (!neighborBlock.isCollidable()) {
                continue;
            }

            // face.getOpposite() is the side of the neighbor we click on
            return new PlacementInfo(neighbor, face.getOpposite());
        }

        return null;
    }

    /**
     * Small helper to bundle placement neighbor + face information.
     */
    private static class PlacementInfo {
        final BlockPos neighbor;
        final EnumFacing face;

        PlacementInfo(BlockPos neighbor, EnumFacing face) {
            this.neighbor = neighbor;
            this.face = face;
        }
    }
}
