package dev.electrovox.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * The voxel mode key, bound the same way Create: Power Grid binds its alternate
 * wire placement: hold left control while wiring.
 *
 * <p>The mode only makes sense with a wire spool in hand, so {@link #isHeld()}
 * reports false for anything else. That keeps the hotbar badge, the preview and
 * the click handling from firing while the player is holding a pickaxe.</p>
 */
public final class VoxelKey {
    public static final String CATEGORY = "key.electrovox.category";
    public static final String ALTERNATE_PATH_ID = "key.electrovox.alternate_path";

    public static final KeyMapping ALTERNATE_PATH = new KeyMapping(
            ALTERNATE_PATH_ID,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_CONTROL,
            CATEGORY);

    private VoxelKey() {
    }

    /** True while the key is down and the player is holding a wire spool. */
    public static boolean isHeld() {
        if (!isKeyDown()) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }
        return isSpool(minecraft.player.getItemInHand(InteractionHand.MAIN_HAND))
                || isSpool(minecraft.player.getItemInHand(InteractionHand.OFF_HAND));
    }

    /** Raw key state, independent of what is in hand. */
    public static boolean isKeyDown() {
        try {
            return ALTERNATE_PATH.isDown();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isSpool(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && stack.getItem()
                instanceof com.george_vi.electroenergetics.content.wire_spool.WireSpoolItem;
    }

    public static String displayName() {
        try {
            return ALTERNATE_PATH.getTranslatedKeyMessage().getString();
        } catch (Throwable ignored) {
            return "Ctrl";
        }
    }
}
