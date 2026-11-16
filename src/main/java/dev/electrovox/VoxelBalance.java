package dev.electrovox;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.simulation.WireType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Survival price of voxel anchoring, matched to Electro Energetics' economy.
 *
 * <p>The addon charges one spool per run: {@code WireSpoolItem.useOn} shrinks the
 * held spool by one and hands an empty spool back, so a spool of
 * {@code wiresPerSpool} wires buys exactly one connection of any legal length.
 * Voxel anchoring mirrors that, plus the hardware the addon does not have:</p>
 *
 * <ul>
 *   <li>every anchor connector costs one connector block, the same item the
 *       addon uses for its own wire endpoints;</li>
 *   <li>a run between two anchors costs one spool from the hand and returns an
 *       empty spool, exactly like the addon's spool;</li>
 *   <li>creative and spectator pay nothing, exactly like the addon.</li>
 * </ul>
 *
 * <p>The server is authoritative. The client only asks the same question to
 * price the click in the HUD and to avoid sending one that cannot be paid.</p>
 */
public final class VoxelBalance {
    /** Connector blocks consumed per anchor. */
    public static final int CONNECTORS_PER_ANCHOR = 1;
    /** Spools consumed per run, which is one connection. */
    public static final int SPOOLS_PER_RUN = 1;

    /**
     * What a click would cost.
     *
     * @param affordable whether the player holds everything
     * @param tooLong    whether the run exceeds the wire type's length limit
     * @param connectors connector blocks needed
     * @param spool      whether a spool is also needed
     */
    public record Quote(boolean affordable, boolean tooLong, int connectors, boolean spool) {
    }

    private VoxelBalance() {
    }

    public static boolean freeFor(Player player) {
        return player == null || player.isCreative() || player.isSpectator();
    }

    /**
     * Prices one click.
     *
     * @param connecting true when a node is already selected, so the click also
     *                   creates the run and therefore needs a spool
     * @param held       the spool in hand
     * @param length     distance to the target point, used for the length check
     */
    public static Quote quote(Player player, boolean connecting, ItemStack held, double length) {
        if (freeFor(player)) {
            return new Quote(true, false, 0, false);
        }

        int connectors = CONNECTORS_PER_ANCHOR;
        boolean spool = connecting && SPOOLS_PER_RUN > 0;

        if (!connecting) {
            return new Quote(has(player, connectorItem(), connectors), false, connectors, false);
        }

        double max = maxLength(held);
        boolean tooLong = max > 0.0D && length > max;
        if (tooLong) {
            return new Quote(false, true, connectors, spool);
        }

        boolean canPay = has(player, connectorItem(), connectors)
                && (!spool || held != null && !held.isEmpty() && held.getCount() >= SPOOLS_PER_RUN);
        return new Quote(canPay, false, connectors, spool);
    }

    /** Charges a quote that {@link #quote} reported as affordable. */
    public static boolean pay(Player player, Quote quote) {
        if (freeFor(player) || quote == null) {
            return true;
        }
        if (!consume(player, connectorItem(), quote.connectors())) {
            return false;
        }
        if (!quote.spool()) {
            return true;
        }

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty() || held.getCount() < SPOOLS_PER_RUN) {
            return false;
        }
        held.shrink(SPOOLS_PER_RUN);
        if (returnsEmptySpool()) {
            Item spool = emptySpoolItem();
            if (spool != null) {
                player.getInventory().placeItemBackInInventory(new ItemStack(spool));
            }
        }
        return true;
    }

    /** The connector block, or null when the addon did not register it. */
    public static Item connectorItem() {
        try {
            Item item = CEEBlocks.CONNECTOR.asItem();
            if (item != null) {
                return item;
            }
        } catch (Throwable ignored) {
            // Registry not ready.
        }
        return null;
    }

    private static Item emptySpoolItem() {
        try {
            return CEEItems.EMPTY_SPOOL.asItem();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean returnsEmptySpool() {
        try {
            return !Boolean.TRUE.equals(CEEConfigs.server().alternateWirePlacement.get());
        } catch (Throwable ignored) {
            return true;
        }
    }

    /** Name of the wire the held spool carries, or null when it is not a spool. */
    public static Component wireName(ItemStack held) {
        WireType type = WireTypes.of(held);
        if (type == null) {
            return null;
        }
        try {
            return new ItemStack(type.getDrops()).getHoverName();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Longest run allowed for the held spool.
     *
     * <p>Voxel wires go through {@code InfrastructureSavedData.connect}, which
     * does not check length, so this limit has to be applied by the caller. It
     * mirrors the check in {@code WireSpoolItem.useOn}.</p>
     */
    public static double maxLength(ItemStack held) {
        WireType type = WireTypes.of(held);
        if (type != null) {
            try {
                return type.getMaxLength();
            } catch (Throwable ignored) {
                // Fall through to the global limit.
            }
        }
        try {
            return CEEConfigs.server().maxWireLength.get();
        } catch (Throwable ignored) {
            return 128.0D;
        }
    }

    private static boolean has(Player player, Item item, int count) {
        if (item == null || count <= 0) {
            return true;
        }
        return countIn(player.getInventory(), item) >= count;
    }

    private static int countIn(Inventory inventory, Item item) {
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (slot == inventory.selected) {
                continue;
            }
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static boolean consume(Player player, Item item, int count) {
        if (item == null || count <= 0) {
            return true;
        }
        Inventory inventory = player.getInventory();
        int remaining = count;
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            if (slot == inventory.selected) {
                continue;
            }
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !stack.is(item)) {
                continue;
            }
            int taken = Math.min(remaining, stack.getCount());
            stack.shrink(taken);
            remaining -= taken;
        }
        return remaining == 0;
    }
}
