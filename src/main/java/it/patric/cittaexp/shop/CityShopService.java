package it.patric.cittaexp.shop;

import it.patric.cittaexp.economy.PlayerEconomyService;
import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.levels.CityLevelService;
import it.patric.cittaexp.permissions.TownMemberPermission;
import it.patric.cittaexp.permissions.TownMemberPermissionService;
import it.patric.cittaexp.shop.CityShopFormatParser.PriceSpec;
import it.patric.cittaexp.shop.CityShopRepository.ShopRecord;
import it.patric.cittaexp.utils.PluginConfigUtils;
import it.patric.cittaexp.vault.ItemStackCodec;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.william278.husktowns.claim.TownClaim;
import net.william278.husktowns.events.TownDisbandEvent;
import net.william278.husktowns.events.TownUpdateEvent;
import net.william278.husktowns.town.Member;
import net.william278.husktowns.town.Town;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public final class CityShopService implements Listener {

    private static final Set<Material> SUPPORTED_SIGNS = Set.of(Material.OAK_SIGN, Material.OAK_WALL_SIGN);
    private static final Set<Material> SUPPORTED_CONTAINERS = Set.of(Material.CHEST, Material.TRAPPED_CHEST);
    private static final BlockFace[] ADJACENT_FACES = {
            BlockFace.NORTH,
            BlockFace.SOUTH,
            BlockFace.EAST,
            BlockFace.WEST,
            BlockFace.UP,
            BlockFace.DOWN
    };

    private final Plugin plugin;
    private final HuskTownsApiHook huskTownsApiHook;
    private final CityLevelService cityLevelService;
    private final PlayerEconomyService playerEconomyService;
    private final TownMemberPermissionService permissionService;
    private final CityShopRepository repository;
    private final PluginConfigUtils config;
    private final Map<Integer, ShopRecord> shopsById;
    private final Map<LocationKey, Integer> shopIdsBySign;
    private final Map<LocationKey, Integer> shopIdsByContainer;
    private final Map<Integer, Set<Integer>> shopIdsByTown;

    public CityShopService(
            Plugin plugin,
            HuskTownsApiHook huskTownsApiHook,
            CityLevelService cityLevelService,
            PlayerEconomyService playerEconomyService,
            TownMemberPermissionService permissionService
    ) {
        this.plugin = plugin;
        this.huskTownsApiHook = huskTownsApiHook;
        this.cityLevelService = cityLevelService;
        this.playerEconomyService = playerEconomyService;
        this.permissionService = permissionService;
        this.repository = new CityShopRepository(plugin);
        this.config = new PluginConfigUtils(plugin);
        this.shopsById = new HashMap<>();
        this.shopIdsBySign = new HashMap<>();
        this.shopIdsByContainer = new HashMap<>();
        this.shopIdsByTown = new HashMap<>();
    }

    public void start() {
        repository.initialize();
        loadAndReconcile();
    }

    public void stop() {
        shopsById.clear();
        shopIdsBySign.clear();
        shopIdsByContainer.clear();
        shopIdsByTown.clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (!SUPPORTED_SIGNS.contains(event.getBlock().getType())) {
            return;
        }
        String line1 = safe(event.getLine(0));
        String line4 = safe(event.getLine(3));
        if (!CityShopFormatParser.looksLikeShopAttempt(line1, line4)) {
            return;
        }

        Optional<PriceSpec> priceSpec = CityShopFormatParser.parsePrices(line1);
        if (priceSpec.isEmpty()) {
            msg(event.getPlayer(), "city.shop.create.errors.invalid_price",
                    "<red>Il bando mercantile e invalido.</red> <gray>Usa B10K, S8 o B10K : S8.</gray>");
            return;
        }
        Optional<Integer> tradeQuantity = CityShopFormatParser.parseTradeQuantity(line4);
        if (tradeQuantity.isEmpty()) {
            msg(event.getPlayer(), "city.shop.create.errors.invalid_quantity",
                    "<red>La quantita di scambio e invalida.</red> <gray>Usa Q:16.</gray>");
            return;
        }

        Player player = event.getPlayer();
        Optional<Member> member = huskTownsApiHook.getUserTown(player);
        if (member.isEmpty()) {
            msg(player, "city.shop.create.errors.no_town",
                    "<red>Devi appartenere a una citta per fondare un emporio.</red>");
            return;
        }
        Town town = member.get().town();
        if (!permissionService.isAllowed(town.getId(), town.getMayor(), player.getUniqueId(), TownMemberPermission.CITY_SHOP_CREATE)) {
            msg(player, "city.shop.create.errors.no_permission",
                    "<red>Non ti e stato concesso il sigillo per fondare empori cittadini.</red>");
            return;
        }
        int shopCap = cityLevelService.shopCapForTown(town.getId());
        if (shopCap <= 0) {
            msg(player, "city.shop.create.errors.stage_locked",
                    "<red>Gli empori si sbloccano solo da</red> <gold>Cittadina I</gold><red>.</red>");
            return;
        }
        int currentShopCount = shopIdsByTown.getOrDefault(town.getId(), Set.of()).size();
        if (currentShopCount >= shopCap) {
            msg(player, "city.shop.create.errors.cap_reached",
                    "<red>Il registro degli empori e saturo.</red> <gray>{current}/{cap}</gray>",
                    Placeholder.unparsed("current", Integer.toString(currentShopCount)),
                    Placeholder.unparsed("cap", Integer.toString(shopCap)));
            return;
        }

        String requestedTownName = safe(event.getLine(1));
        if (!town.getName().equalsIgnoreCase(requestedTownName)) {
            msg(player, "city.shop.create.errors.wrong_city_name",
                    "<red>La seconda riga deve riportare il nome esatto della tua citta.</red>");
            return;
        }

        Optional<TownClaim> signClaim = huskTownsApiHook.getClaimAt(event.getBlock().getLocation());
        if (signClaim.isEmpty() || signClaim.get().town().getId() != town.getId()) {
            msg(player, "city.shop.create.errors.wrong_claim",
                    "<red>L'emporio deve sorgere dentro un claim della tua citta.</red>");
            return;
        }

        ContainerResolution resolution = resolveAdjacentContainer(event.getBlock());
        if (!resolution.success()) {
            String key = resolution.reason() == ContainerResolutionReason.MULTIPLE
                    ? "city.shop.create.errors.multiple_containers"
                    : "city.shop.create.errors.invalid_container";
            String fallback = resolution.reason() == ContainerResolutionReason.MULTIPLE
                    ? "<red>Il cartello tocca piu casse.</red> <gray>Serve un solo container adiacente.</gray>"
                    : "<red>Serve una sola chest o trapped chest adiacente al cartello.</red>";
            msg(player, key, fallback);
            return;
        }
        Container container = resolution.container();
        Optional<TownClaim> containerClaim = huskTownsApiHook.getClaimAt(container.getBlock().getLocation());
        if (containerClaim.isEmpty() || containerClaim.get().town().getId() != town.getId()) {
            msg(player, "city.shop.create.errors.wrong_claim",
                    "<red>La cassa dell'emporio deve stare dentro un claim della tua citta.</red>");
            return;
        }

        LocationKey signKey = LocationKey.from(event.getBlock());
        LocationKey containerKey = LocationKey.from(container.getBlock());
        if (shopIdsBySign.containsKey(signKey)) {
            msg(player, "city.shop.create.errors.sign_used",
                    "<red>Questo cartello e gia legato a un emporio.</red>");
            return;
        }
        if (shopIdsByContainer.containsKey(containerKey)) {
            msg(player, "city.shop.create.errors.container_used",
                    "<red>Questa cassa e gia vincolata a un altro emporio.</red>");
            return;
        }

        Instant now = Instant.now();
        ShopRecord record = repository.insertShop(
                town.getId(),
                signKey.world(),
                signKey,
                containerKey,
                priceSpec.get().buyPrice(),
                priceSpec.get().sellPrice(),
                tradeQuantity.get(),
                player.getUniqueId(),
                now,
                now
        );
        cache(record);
        writeSign(event.getBlock(), record, town.getName(), null);
        event.setLine(0, CityShopFormatParser.formatPriceLine(priceSpec.get()));
        event.setLine(1, town.getName());
        event.setLine(2, "");
        event.setLine(3, CityShopFormatParser.formatQuantityLine(record.tradeQuantity()));
        msg(player, "city.shop.create.success",
                "<green>Emporio cittadino fondato.</green> <gray>{current}/{cap}</gray>",
                Placeholder.unparsed("current", Integer.toString(currentShopCount + 1)),
                Placeholder.unparsed("cap", Integer.toString(shopCap)));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null || event.getPlayer().isSneaking()) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Integer shopId = shopIdsBySign.get(LocationKey.from(event.getClickedBlock()));
        if (shopId == null) {
            return;
        }
        ShopRecord shop = shopsById.get(shopId);
        if (shop == null) {
            return;
        }
        event.setCancelled(true);

        if (action == Action.RIGHT_CLICK_BLOCK) {
            Player player = event.getPlayer();
            ItemStack held = player.getInventory().getItemInMainHand();
            if (canConfigure(player, shop) && held != null && held.getType() != Material.AIR) {
                configureShopItem(player, shop, held);
                return;
            }
            runBuy(player, shop);
            return;
        }

        runSell(event.getPlayer(), shop);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        LocationKey key = LocationKey.from(event.getBlock());
        Integer signShopId = shopIdsBySign.get(key);
        if (signShopId != null) {
            ShopRecord shop = shopsById.get(signShopId);
            if (shop != null && !canDestroy(event.getPlayer(), shop)) {
                event.setCancelled(true);
                msg(event.getPlayer(), "city.shop.destroy.errors.no_permission",
                        "<red>Non ti e stato concesso il sigillo per smantellare questo emporio.</red>");
                return;
            }
            deleteShop(signShopId);
            return;
        }
        Integer containerShopId = shopIdsByContainer.get(key);
        if (containerShopId != null) {
            ShopRecord shop = shopsById.get(containerShopId);
            if (shop != null && !canDestroy(event.getPlayer(), shop)) {
                event.setCancelled(true);
                msg(event.getPlayer(), "city.shop.destroy.errors.no_permission",
                        "<red>Non ti e stato concesso il sigillo per smantellare questo emporio.</red>");
                return;
            }
            deleteShop(containerShopId);
        }
    }

    @EventHandler
    public void onTownUpdate(TownUpdateEvent event) {
        int townId = event.getTown().getId();
        rewriteTownSigns(townId, event.getTown().getName());
    }

    @EventHandler(ignoreCancelled = true)
    public void onTownDisband(TownDisbandEvent event) {
        int townId = event.getTown().getId();
        repository.deleteShopsByTown(townId);
        for (Integer shopId : new ArrayList<>(shopIdsByTown.getOrDefault(townId, Set.of()))) {
            uncache(shopId);
        }
    }

    private void loadAndReconcile() {
        for (ShopRecord record : repository.loadAll()) {
            if (!isLoadedRecordValid(record)) {
                repository.deleteShop(record.shopId());
                continue;
            }
            cache(record);
            huskTownsApiHook.getTownById(record.townId()).ifPresent(town -> writeSign(
                    resolveBlock(record.signKey()).orElse(null),
                    record,
                    town.getName(),
                    decodeTemplate(record).orElse(null)
            ));
        }
    }

    private boolean isLoadedRecordValid(ShopRecord record) {
        if (record.tradeQuantity() <= 0 || (record.buyPrice() == null && record.sellPrice() == null)) {
            return false;
        }
        Optional<Town> town = huskTownsApiHook.getTownById(record.townId());
        if (town.isEmpty()) {
            return false;
        }
        Optional<Block> signBlock = resolveBlock(record.signKey());
        Optional<Block> containerBlock = resolveBlock(record.containerKey());
        if (signBlock.isEmpty() || containerBlock.isEmpty()) {
            return false;
        }
        if (!SUPPORTED_SIGNS.contains(signBlock.get().getType()) || !SUPPORTED_CONTAINERS.contains(containerBlock.get().getType())) {
            return false;
        }
        if (!(containerBlock.get().getState() instanceof Container)) {
            return false;
        }
        Optional<TownClaim> signClaim = huskTownsApiHook.getClaimAt(signBlock.get().getLocation());
        Optional<TownClaim> containerClaim = huskTownsApiHook.getClaimAt(containerBlock.get().getLocation());
        if (signClaim.isEmpty() || containerClaim.isEmpty()) {
            return false;
        }
        if (signClaim.get().town().getId() != record.townId() || containerClaim.get().town().getId() != record.townId()) {
            return false;
        }
        if (record.itemBase64() != null && !record.itemBase64().isBlank()) {
            try {
                return decodeTemplate(record).isPresent();
            } catch (RuntimeException exception) {
                return false;
            }
        }
        return true;
    }

    private void configureShopItem(Player player, ShopRecord shop, ItemStack held) {
        if (!canConfigure(player, shop)) {
            msg(player, "city.shop.configure.errors.no_permission",
                    "<red>Non ti e stato concesso il sigillo per configurare questo emporio.</red>");
            return;
        }
        Optional<ShopRuntimeContext> context = validateRuntime(shop, player);
        if (context.isEmpty()) {
            return;
        }
        ItemStack template = held.clone();
        template.setAmount(1);
        String itemBase64 = ItemStackCodec.encode(template);
        Instant updatedAt = Instant.now();
        repository.updateConfiguredItem(shop.shopId(), itemBase64, updatedAt);
        ShopRecord updated = shop.withItemBase64(itemBase64, updatedAt);
        replaceCache(updated);
        writeSign(context.get().signBlock(), updated, context.get().town().getName(), template);
        msg(player, "city.shop.configure.success",
                "<green>Emporio configurato.</green> <yellow>{item}</yellow> <gray>x{quantity}</gray>",
                Placeholder.unparsed("item", renderItemLabel(template)),
                Placeholder.unparsed("quantity", Integer.toString(updated.tradeQuantity())));
    }

    private void runBuy(Player player, ShopRecord shop) {
        Optional<ShopRuntimeContext> context = validateRuntime(shop, player);
        if (context.isEmpty()) {
            return;
        }
        if (shop.sellPrice() == null) {
            msg(player, "city.shop.trade.errors.buy_disabled",
                    "<red>Questo emporio non vende merci al pubblico.</red>");
            return;
        }
        Optional<ItemStack> templateOptional = decodeTemplate(shop);
        if (templateOptional.isEmpty()) {
            msg(player, "city.shop.trade.errors.not_configured",
                    "<red>Questo emporio non ha ancora una merce configurata.</red>");
            return;
        }
        ItemStack template = templateOptional.get();
        Inventory containerInventory = context.get().container().getInventory();
        int quantity = shop.tradeQuantity();
        if (countMatching(containerInventory, template) < quantity) {
            msg(player, "city.shop.trade.errors.stock_missing",
                    "<red>Le scorte dell'emporio sono insufficienti.</red>");
            return;
        }
        if (!canFit(containerInventory, template, 0)) {
            // no-op, keeps helper referenced consistently
        }
        if (!canFit(player.getInventory(), template, quantity)) {
            msg(player, "city.shop.trade.errors.inventory_full",
                    "<red>Non hai spazio sufficiente nel tuo inventario.</red>");
            return;
        }
        if (!playerEconomyService.available()) {
            msg(player, "city.shop.trade.errors.vault_unavailable",
                    "<red>Vault/Economy non disponibile. Lo scambio e sospeso.</red>");
            return;
        }
        double price = shop.sellPrice().doubleValue();
        if (!playerEconomyService.has(player, price)) {
            msg(player, "city.shop.trade.errors.player_funds",
                    "<red>Saldo insufficiente.</red> <gray>Servono {price}.</gray>",
                    Placeholder.unparsed("price", playerEconomyService.format(price)));
            return;
        }
        PlayerEconomyService.TransactionResult withdraw = playerEconomyService.withdraw(player, price);
        if (!withdraw.success()) {
            msg(player, "city.shop.trade.errors.player_funds",
                    "<red>Impossibile addebitare {price} dal tuo saldo.</red>",
                    Placeholder.unparsed("price", withdraw.formattedAmount()));
            return;
        }
        if (!huskTownsApiHook.adjustTownBalance(shop.townId(), BigDecimal.valueOf(price))) {
            playerEconomyService.deposit(player, price);
            msg(player, "city.shop.trade.errors.runtime_invalid",
                    "<red>Il tesoro cittadino non e raggiungibile in questo momento.</red>");
            return;
        }
        int removed = removeMatching(containerInventory, template, quantity);
        if (removed < quantity) {
            rollbackTownBalance(shop.townId(), BigDecimal.valueOf(-price));
            playerEconomyService.deposit(player, price);
            if (removed > 0) {
                addToInventory(containerInventory, template, removed);
            }
            msg(player, "city.shop.trade.errors.stock_missing",
                    "<red>Le scorte dell'emporio sono cambiate durante lo scambio.</red>");
            return;
        }
        if (!addToInventory(player.getInventory(), template, quantity)) {
            addToInventory(containerInventory, template, quantity);
            rollbackTownBalance(shop.townId(), BigDecimal.valueOf(-price));
            playerEconomyService.deposit(player, price);
            msg(player, "city.shop.trade.errors.inventory_full",
                    "<red>Non hai spazio sufficiente nel tuo inventario.</red>");
            return;
        }
        msg(player, "city.shop.trade.buy.success",
                "<green>Acquisto completato.</green> <yellow>{item}</yellow> <gray>x{quantity}</gray> <white>{price}</white>",
                Placeholder.unparsed("item", renderItemLabel(template)),
                Placeholder.unparsed("quantity", Integer.toString(quantity)),
                Placeholder.unparsed("price", withdraw.formattedAmount()));
    }

    private void runSell(Player player, ShopRecord shop) {
        Optional<ShopRuntimeContext> context = validateRuntime(shop, player);
        if (context.isEmpty()) {
            return;
        }
        if (shop.buyPrice() == null) {
            msg(player, "city.shop.trade.errors.sell_disabled",
                    "<red>Questo emporio non acquista questa merce.</red>");
            return;
        }
        Optional<ItemStack> templateOptional = decodeTemplate(shop);
        if (templateOptional.isEmpty()) {
            msg(player, "city.shop.trade.errors.not_configured",
                    "<red>Questo emporio non ha ancora una merce configurata.</red>");
            return;
        }
        ItemStack template = templateOptional.get();
        int quantity = shop.tradeQuantity();
        if (countMatching(player.getInventory(), template) < quantity) {
            msg(player, "city.shop.trade.errors.stock_missing",
                    "<red>Non possiedi abbastanza merce per questo scambio.</red>");
            return;
        }
        Town town = context.get().town();
        double price = shop.buyPrice().doubleValue();
        if (town.getMoney().doubleValue() + 0.0001D < price) {
            msg(player, "city.shop.trade.errors.town_funds",
                    "<red>Il tesoro cittadino non puo sostenere questo acquisto.</red>");
            return;
        }
        Inventory containerInventory = context.get().container().getInventory();
        if (!canFit(containerInventory, template, quantity)) {
            msg(player, "city.shop.trade.errors.container_full",
                    "<red>La cassa dell'emporio non ha spazio sufficiente.</red>");
            return;
        }
        int removed = removeMatching(player.getInventory(), template, quantity);
        if (removed < quantity) {
            if (removed > 0) {
                addToInventory(player.getInventory(), template, removed);
            }
            msg(player, "city.shop.trade.errors.stock_missing",
                    "<red>La tua merce non e piu sufficiente per completare lo scambio.</red>");
            return;
        }
        if (!addToInventory(containerInventory, template, quantity)) {
            addToInventory(player.getInventory(), template, quantity);
            msg(player, "city.shop.trade.errors.container_full",
                    "<red>La cassa dell'emporio non ha spazio sufficiente.</red>");
            return;
        }
        if (!huskTownsApiHook.adjustTownBalance(shop.townId(), BigDecimal.valueOf(-price))) {
            removeMatching(containerInventory, template, quantity);
            addToInventory(player.getInventory(), template, quantity);
            msg(player, "city.shop.trade.errors.town_funds",
                    "<red>Il tesoro cittadino non puo sostenere questo acquisto.</red>");
            return;
        }
        if (!playerEconomyService.available()) {
            rollbackTownBalance(shop.townId(), BigDecimal.valueOf(price));
            removeMatching(containerInventory, template, quantity);
            addToInventory(player.getInventory(), template, quantity);
            msg(player, "city.shop.trade.errors.vault_unavailable",
                    "<red>Vault/Economy non disponibile. Lo scambio e sospeso.</red>");
            return;
        }
        PlayerEconomyService.TransactionResult deposit = playerEconomyService.deposit(player, price);
        if (!deposit.success()) {
            rollbackTownBalance(shop.townId(), BigDecimal.valueOf(price));
            removeMatching(containerInventory, template, quantity);
            addToInventory(player.getInventory(), template, quantity);
            msg(player, "city.shop.trade.errors.runtime_invalid",
                    "<red>Impossibile accreditare il tuo saldo in questo momento.</red>");
            return;
        }
        msg(player, "city.shop.trade.sell.success",
                "<green>Vendita completata.</green> <yellow>{item}</yellow> <gray>x{quantity}</gray> <white>{price}</white>",
                Placeholder.unparsed("item", renderItemLabel(template)),
                Placeholder.unparsed("quantity", Integer.toString(quantity)),
                Placeholder.unparsed("price", deposit.formattedAmount()));
    }

    private Optional<ShopRuntimeContext> validateRuntime(ShopRecord shop, Player audience) {
        Optional<Town> town = huskTownsApiHook.getTownById(shop.townId());
        Optional<Block> signBlock = resolveBlock(shop.signKey());
        Optional<Block> containerBlock = resolveBlock(shop.containerKey());
        if (town.isEmpty() || signBlock.isEmpty() || containerBlock.isEmpty()) {
            deleteShop(shop.shopId());
            msg(audience, "city.shop.trade.errors.runtime_invalid",
                    "<red>Questo emporio e fuori sincronia ed e stato sigillato.</red>");
            return Optional.empty();
        }
        if (!SUPPORTED_SIGNS.contains(signBlock.get().getType()) || !SUPPORTED_CONTAINERS.contains(containerBlock.get().getType())) {
            deleteShop(shop.shopId());
            msg(audience, "city.shop.trade.errors.runtime_invalid",
                    "<red>Questo emporio e fuori sincronia ed e stato sigillato.</red>");
            return Optional.empty();
        }
        if (!(containerBlock.get().getState() instanceof Container container)) {
            deleteShop(shop.shopId());
            msg(audience, "city.shop.trade.errors.runtime_invalid",
                    "<red>Questo emporio e fuori sincronia ed e stato sigillato.</red>");
            return Optional.empty();
        }
        Optional<TownClaim> signClaim = huskTownsApiHook.getClaimAt(signBlock.get().getLocation());
        Optional<TownClaim> containerClaim = huskTownsApiHook.getClaimAt(containerBlock.get().getLocation());
        if (signClaim.isEmpty() || containerClaim.isEmpty()
                || signClaim.get().town().getId() != shop.townId()
                || containerClaim.get().town().getId() != shop.townId()) {
            deleteShop(shop.shopId());
            msg(audience, "city.shop.trade.errors.runtime_invalid",
                    "<red>Questo emporio ha perso il suo claim cittadino ed e stato dismesso.</red>");
            return Optional.empty();
        }
        return Optional.of(new ShopRuntimeContext(shop, town.get(), signBlock.get(), container));
    }

    private void rewriteTownSigns(int townId, String townName) {
        for (Integer shopId : new ArrayList<>(shopIdsByTown.getOrDefault(townId, Set.of()))) {
            ShopRecord shop = shopsById.get(shopId);
            if (shop == null) {
                continue;
            }
            writeSign(resolveBlock(shop.signKey()).orElse(null), shop, townName, decodeTemplate(shop).orElse(null));
        }
    }

    private void writeSign(Block block, ShopRecord shop, String townName, ItemStack template) {
        if (block == null) {
            return;
        }
        if (!(block.getState() instanceof Sign sign)) {
            return;
        }
        sign.setLine(0, CityShopFormatParser.formatPriceLine(new PriceSpec(shop.buyPrice(), shop.sellPrice())));
        sign.setLine(1, townName == null ? "" : townName);
        sign.setLine(2, template == null ? "" : truncate(renderItemLabel(template), 15));
        sign.setLine(3, CityShopFormatParser.formatQuantityLine(shop.tradeQuantity()));
        sign.update(true, false);
    }

    private ContainerResolution resolveAdjacentContainer(Block signBlock) {
        List<Container> containers = new ArrayList<>();
        for (BlockFace face : ADJACENT_FACES) {
            Block relative = signBlock.getRelative(face);
            if (!SUPPORTED_CONTAINERS.contains(relative.getType())) {
                continue;
            }
            if (relative.getState() instanceof Container container) {
                containers.add(container);
            }
        }
        if (containers.isEmpty()) {
            return new ContainerResolution(null, ContainerResolutionReason.NONE);
        }
        if (containers.size() > 1) {
            return new ContainerResolution(null, ContainerResolutionReason.MULTIPLE);
        }
        return new ContainerResolution(containers.get(0), ContainerResolutionReason.OK);
    }

    private boolean canConfigure(Player player, ShopRecord shop) {
        Optional<Town> town = huskTownsApiHook.getTownById(shop.townId());
        if (town.isEmpty()) {
            return false;
        }
        return permissionService.isAllowed(
                shop.townId(),
                town.get().getMayor(),
                player.getUniqueId(),
                TownMemberPermission.CITY_SHOP_CONFIGURE
        );
    }

    private boolean canDestroy(Player player, ShopRecord shop) {
        Optional<Town> town = huskTownsApiHook.getTownById(shop.townId());
        if (town.isEmpty()) {
            return false;
        }
        return permissionService.isAllowed(
                shop.townId(),
                town.get().getMayor(),
                player.getUniqueId(),
                TownMemberPermission.CITY_SHOP_DESTROY
        );
    }

    private Optional<UUID> resolveMayor(int townId) {
        return huskTownsApiHook.getTownById(townId).map(Town::getMayor);
    }

    private Optional<Block> resolveBlock(LocationKey key) {
        World world = Bukkit.getWorld(key.world());
        if (world == null) {
            return Optional.empty();
        }
        return Optional.of(world.getBlockAt(key.x(), key.y(), key.z()));
    }

    private Optional<ItemStack> decodeTemplate(ShopRecord shop) {
        if (shop.itemBase64() == null || shop.itemBase64().isBlank()) {
            return Optional.empty();
        }
        ItemStack decoded = ItemStackCodec.decode(shop.itemBase64());
        if (decoded == null || decoded.getType() == Material.AIR) {
            return Optional.empty();
        }
        decoded.setAmount(1);
        return Optional.of(decoded);
    }

    private int countMatching(Inventory inventory, ItemStack template) {
        int count = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            if (item.isSimilar(template)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private int removeMatching(Inventory inventory, ItemStack template, int quantity) {
        int remaining = quantity;
        ItemStack[] contents = inventory.getStorageContents();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType() == Material.AIR || !item.isSimilar(template)) {
                continue;
            }
            int take = Math.min(item.getAmount(), remaining);
            int updatedAmount = item.getAmount() - take;
            if (updatedAmount <= 0) {
                contents[slot] = null;
            } else {
                item.setAmount(updatedAmount);
                contents[slot] = item;
            }
            remaining -= take;
        }
        inventory.setStorageContents(contents);
        return quantity - remaining;
    }

    private boolean addToInventory(Inventory inventory, ItemStack template, int quantity) {
        if (quantity <= 0) {
            return true;
        }
        Map<Integer, ItemStack> leftovers = inventory.addItem(splitStacks(template, quantity).toArray(ItemStack[]::new));
        return leftovers.isEmpty();
    }

    private boolean canFit(Inventory inventory, ItemStack template, int quantity) {
        if (quantity <= 0) {
            return true;
        }
        ItemStack[] contents = inventory.getStorageContents();
        int remaining = quantity;
        for (ItemStack item : contents) {
            if (item == null || item.getType() == Material.AIR) {
                remaining -= template.getMaxStackSize();
            } else if (item.isSimilar(template)) {
                remaining -= Math.max(0, item.getMaxStackSize() - item.getAmount());
            }
            if (remaining <= 0) {
                return true;
            }
        }
        return remaining <= 0;
    }

    private List<ItemStack> splitStacks(ItemStack template, int quantity) {
        List<ItemStack> stacks = new ArrayList<>();
        int remaining = quantity;
        int maxStack = Math.max(1, template.getMaxStackSize());
        while (remaining > 0) {
            ItemStack copy = template.clone();
            copy.setAmount(Math.min(maxStack, remaining));
            stacks.add(copy);
            remaining -= copy.getAmount();
        }
        return stacks;
    }

    private String renderItemLabel(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            Component display = meta.displayName();
            if (display != null) {
                String plain = PlainTextComponentSerializer.plainText().serialize(display).trim();
                if (!plain.isEmpty()) {
                    return plain;
                }
            }
            if (meta.hasDisplayName()) {
                String legacy = PlainTextComponentSerializer.plainText().serialize(
                        LegacyComponentSerializer.legacySection().deserialize(meta.getDisplayName())
                ).trim();
                if (!legacy.isEmpty()) {
                    return legacy;
                }
            }
        }
        return friendlyMaterial(item.getType());
    }

    private String friendlyMaterial(Material material) {
        String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, Math.max(0, maxLength));
    }

    private void deleteShop(int shopId) {
        repository.deleteShop(shopId);
        uncache(shopId);
    }

    private void cache(ShopRecord record) {
        shopsById.put(record.shopId(), record);
        shopIdsBySign.put(record.signKey(), record.shopId());
        shopIdsByContainer.put(record.containerKey(), record.shopId());
        shopIdsByTown.computeIfAbsent(record.townId(), ignored -> new HashSet<>()).add(record.shopId());
    }

    private void replaceCache(ShopRecord record) {
        uncache(record.shopId());
        cache(record);
    }

    private void uncache(int shopId) {
        ShopRecord record = shopsById.remove(shopId);
        if (record == null) {
            return;
        }
        shopIdsBySign.remove(record.signKey());
        shopIdsByContainer.remove(record.containerKey());
        Set<Integer> byTown = shopIdsByTown.get(record.townId());
        if (byTown != null) {
            byTown.remove(shopId);
            if (byTown.isEmpty()) {
                shopIdsByTown.remove(record.townId());
            }
        }
    }

    private boolean rollbackTownBalance(int townId, BigDecimal delta) {
        if (delta == null || BigDecimal.ZERO.compareTo(delta) == 0) {
            return true;
        }
        boolean success = huskTownsApiHook.adjustTownBalance(townId, delta);
        if (!success) {
            plugin.getLogger().warning("[shop] rollback tesoro fallito town=" + townId + " delta=" + delta);
        }
        return success;
    }

    private void msg(Player player, String path, String fallback, TagResolver... placeholders) {
        player.sendMessage(config.msg(path, fallback, placeholders));
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private record ShopRuntimeContext(ShopRecord shop, Town town, Block signBlock, Container container) {
    }

    private record ContainerResolution(Container container, ContainerResolutionReason reason) {
        boolean success() {
            return reason == ContainerResolutionReason.OK && container != null;
        }
    }

    private enum ContainerResolutionReason {
        OK,
        NONE,
        MULTIPLE
    }

    public record LocationKey(String world, int x, int y, int z) {
        public static LocationKey from(Block block) {
            return new LocationKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof LocationKey that)) {
                return false;
            }
            return x == that.x && y == that.y && z == that.z && Objects.equals(world, that.world);
        }

        @Override
        public int hashCode() {
            return Objects.hash(world, x, y, z);
        }
    }
}
