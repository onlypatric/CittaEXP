package it.patric.cittaexp.manageclaim;

import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.utils.PluginConfigUtils;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

public final class CityClaimToolListener implements Listener {

    private static final double TOOL_RANGE_BLOCKS = 80.0D;

    private final HuskTownsApiHook huskTownsApiHook;
    private final CitySectionMapGuiService citySectionMapGuiService;
    private final ClaimBorderOverlayService claimBorderOverlayService;
    private final PluginConfigUtils cfg;

    public CityClaimToolListener(
            HuskTownsApiHook huskTownsApiHook,
            CitySectionMapGuiService citySectionMapGuiService,
            ClaimBorderOverlayService claimBorderOverlayService,
            PluginConfigUtils cfg
    ) {
        this.huskTownsApiHook = huskTownsApiHook;
        this.citySectionMapGuiService = citySectionMapGuiService;
        this.claimBorderOverlayService = claimBorderOverlayService;
        this.cfg = cfg;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack stack = player.getInventory().getItemInMainHand();
        if (stack == null) {
            return;
        }
        if (stack.getType() == Material.BLAZE_ROD) {
            handleInspectorTool(player, event.getClickedBlock());
            return;
        }
        if (stack.getType() == Material.WOODEN_SHOVEL) {
            handleClaimTool(event, player, event.getClickedBlock());
        }
    }

    private void handleInspectorTool(Player player, Block clickedBlock) {
        resolveTargetBlock(player, clickedBlock).ifPresentOrElse(
                targetBlock -> claimBorderOverlayService.showInspectorBorders(player, targetBlock.getChunk()),
                () -> claimBorderOverlayService.clearSource(player.getUniqueId(), ClaimBorderOverlayService.Source.INSPECTOR_TOOL)
        );
    }

    private void handleClaimTool(PlayerInteractEvent event, Player player, Block clickedBlock) {
        if (huskTownsApiHook.getUserTown(player).isEmpty()) {
            claimBorderOverlayService.clearSource(player.getUniqueId(), ClaimBorderOverlayService.Source.CLAIM_TOOL);
            return;
        }
        Optional<Block> target = resolveTargetBlock(player, clickedBlock);
        if (target.isEmpty()) {
            event.setCancelled(true);
            claimBorderOverlayService.clearSource(player.getUniqueId(), ClaimBorderOverlayService.Source.CLAIM_TOOL);
            player.sendMessage(cfg.msg(
                    "city.section.gui.errors.no_target_chunk",
                    "<red>Nessun chunk valido nel punto guardato.</red>"
            ));
            return;
        }
        Block targetBlock = target.get();
        World world = targetBlock.getWorld();
        citySectionMapGuiService.previewClaimToolTarget(player, world, targetBlock.getChunk().getX(), targetBlock.getChunk().getZ())
                .ifPresent(cell -> claimBorderOverlayService.showClaimToolBorders(
                        player,
                        targetBlock.getChunk(),
                        switch (cell.state()) {
                            case WILDERNESS_CLAIMABLE -> ClaimBorderOverlayService.ClaimToolPreview.CLAIMABLE;
                            case OWN_CLAIM -> ClaimBorderOverlayService.ClaimToolPreview.OWN_CLAIM;
                            case OTHER_CLAIM -> ClaimBorderOverlayService.ClaimToolPreview.OTHER_CLAIM;
                            case BLOCKED_HUSKCLAIMS, BLOCKED_POLICY -> ClaimBorderOverlayService.ClaimToolPreview.BLOCKED;
                        }
                ));
        event.setCancelled(true);
        citySectionMapGuiService.handleClaimToolTarget(
                player,
                world,
                targetBlock.getChunk().getX(),
                targetBlock.getChunk().getZ()
        );
    }

    private Optional<Block> resolveTargetBlock(Player player, Block clickedBlock) {
        if (clickedBlock != null) {
            return Optional.of(clickedBlock);
        }
        RayTraceResult rayTraceResult = player.rayTraceBlocks(TOOL_RANGE_BLOCKS);
        if (rayTraceResult == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(rayTraceResult.getHitBlock());
    }
}
