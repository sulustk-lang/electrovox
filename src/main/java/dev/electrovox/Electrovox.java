package dev.electrovox;

import dev.electrovox.client.BlockAnchorHook;
import dev.electrovox.client.ElectrovoxConfig;
import dev.electrovox.client.Keybinds;
import dev.electrovox.client.ModeIndicator;
import dev.electrovox.client.PreviewHook;
import dev.electrovox.client.VoxelWirePass;
import dev.electrovox.net.AnchorPlotPayload;
import dev.electrovox.net.Network;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Electrovox - voxel wire rendering and block anchoring for
 * Create: Electro Energetics.
 */
@Mod(Electrovox.MOD_ID)
public class Electrovox {
    public static final String MOD_ID = "electrovox";
    public static final Logger LOGGER = LoggerFactory.getLogger("Electrovox");

    public Electrovox(IEventBus modEventBus, ModContainer modContainer) {
        // Networking exists on both sides: the server half creates the nodes.
        modEventBus.addListener(Network::register);

        // Anchors on a Sable contraption are moved on the server, so this hook
        // is registered on both sides and simply does nothing without Sable.
        NeoForge.EVENT_BUS.addListener(Electrovox::onLevelTick);
        NeoForge.EVENT_BUS.addListener(Electrovox::onPlayerLogin);

        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }

        modContainer.registerConfig(ModConfig.Type.CLIENT, ElectrovoxConfig.SPEC);
        modEventBus.addListener(Keybinds::registerKeys);
        modEventBus.addListener(dev.electrovox.client.ClientSetup::onClientSetup);

        NeoForge.EVENT_BUS.addListener(ModeIndicator::onRenderGui);
        NeoForge.EVENT_BUS.addListener(PreviewHook::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(VoxelWirePass::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(dev.electrovox.client.AnchorRenderer::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(BlockAnchorHook::onUseInput);

        LOGGER.info("Electrovox loaded: hold the voxel key to wire to any block");
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            AnchorTracking.get(level).tick(level);
        }
    }

    /**
     * Re-sends the plot positions to a player who just joined, so anchors on a
     * contraption render in the right place immediately instead of only after
     * the server next moves them.
     */
    private static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        AnchorTracking.get(level).syncTo(player);
    }
}
