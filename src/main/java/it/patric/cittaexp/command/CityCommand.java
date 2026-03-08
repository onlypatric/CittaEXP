package it.patric.cittaexp.command;

import dev.patric.commonlib.api.MessageService;
import it.patric.cittaexp.core.model.City;
import it.patric.cittaexp.core.model.CityRole;
import it.patric.cittaexp.core.model.CityTier;
import it.patric.cittaexp.core.model.MemberClaimPermissions;
import it.patric.cittaexp.core.model.RolePermissionSet;
import it.patric.cittaexp.core.model.StaffApprovalTicket;
import it.patric.cittaexp.core.model.TicketType;
import it.patric.cittaexp.core.runtime.DefaultCityLifecycleService;
import it.patric.cittaexp.core.service.CityClaimBlockService;
import it.patric.cittaexp.core.service.CityModerationService;
import it.patric.cittaexp.core.service.StaffApprovalService;
import it.patric.cittaexp.core.view.MemberClaimPermissionsView;
import it.patric.cittaexp.preview.PreviewScenario;
import it.patric.cittaexp.preview.ThemeMode;
import it.patric.cittaexp.style.CityBannerDisplayService;
import it.patric.cittaexp.style.CityStyleRecord;
import it.patric.cittaexp.style.CityStyleService;
import it.patric.cittaexp.ui.contract.UiScreenKey;
import it.patric.cittaexp.ui.framework.GuiFlowOrchestrator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class CityCommand implements CommandExecutor, TabCompleter {

    private static final String PLAYER_PERMISSION = "cittaexp.city.player";
    private static final String MODERATION_PERMISSION = "cittaexp.city.moderation";

    private final Plugin plugin;
    private final DefaultCityLifecycleService lifecycleService;
    private final StaffApprovalService staffApprovalService;
    private final CityModerationService cityModerationService;
    private final CityClaimBlockService claimBlockService;
    private final GuiFlowOrchestrator guiFlowOrchestrator;
    private final MessageService messageService;
    private final CityStyleService cityStyleService;
    private final CityBannerDisplayService bannerDisplayService;

    public CityCommand(
            Plugin plugin,
            DefaultCityLifecycleService lifecycleService,
            StaffApprovalService staffApprovalService,
            CityModerationService cityModerationService,
            CityClaimBlockService claimBlockService,
            GuiFlowOrchestrator guiFlowOrchestrator,
            MessageService messageService
    ) {
        this(
                plugin,
                lifecycleService,
                staffApprovalService,
                cityModerationService,
                claimBlockService,
                guiFlowOrchestrator,
                messageService,
                new InMemoryCityStyleService(),
                null
        );
    }

    public CityCommand(
            Plugin plugin,
            DefaultCityLifecycleService lifecycleService,
            StaffApprovalService staffApprovalService,
            CityModerationService cityModerationService,
            CityClaimBlockService claimBlockService,
            GuiFlowOrchestrator guiFlowOrchestrator,
            MessageService messageService,
            CityStyleService cityStyleService,
            CityBannerDisplayService bannerDisplayService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lifecycleService = Objects.requireNonNull(lifecycleService, "lifecycleService");
        this.staffApprovalService = Objects.requireNonNull(staffApprovalService, "staffApprovalService");
        this.cityModerationService = Objects.requireNonNull(cityModerationService, "cityModerationService");
        this.claimBlockService = Objects.requireNonNull(claimBlockService, "claimBlockService");
        this.guiFlowOrchestrator = Objects.requireNonNull(guiFlowOrchestrator, "guiFlowOrchestrator");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.cityStyleService = Objects.requireNonNull(cityStyleService, "cityStyleService");
        this.bannerDisplayService = bannerDisplayService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PLAYER_PERMISSION)) {
            sender.sendMessage(msg(sender, "cittaexp.city.no_permission"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg(sender, "cittaexp.city.player_only"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(msg(sender, "cittaexp.city.usage.root", Map.of("label", label)));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        try {
            return switch (sub) {
                case "create" -> handleCreate(player, args);
                case "info" -> handleInfo(player, args);
                case "invite" -> handleInvite(player, args);
                case "request" -> handleRequest(player, args);
                case "kick" -> handleKick(player, args);
                case "leave" -> handleLeave(player, args);
                case "roles" -> handleRoles(player, args);
                case "vice" -> handleVice(player, args);
                case "perms" -> handlePerms(player, args);
                case "freeze" -> handleFreezeStatus(player, args);
                case "claimblocks" -> handleClaimBlocks(player, args);
                case "style" -> handleStyle(player, args);
                case "banner" -> handleBanner(player, args);
                default -> {
                    sender.sendMessage(msg(sender, "cittaexp.city.usage.root", Map.of("label", label)));
                    yield true;
                }
            };
        } catch (RuntimeException ex) {
            sender.sendMessage(msg(sender, mapErrorKey(ex), Map.of("reason", safe(ex.getMessage()))));
            return true;
        }
    }

    private boolean handleCreate(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(msg(player, "cittaexp.city.usage.create"));
            return true;
        }
        String bannerSpec = args.length >= 4 ? args[3] : "";
        String armorSpec = args.length >= 5 ? args[4] : "";

        var location = player.getLocation();
        var createCommand = new it.patric.cittaexp.core.service.CityLifecycleService.CreateCityCommand(
                player.getUniqueId(),
                args[1],
                args[2],
                location.getWorld() == null ? "world" : location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockZ(),
                bannerSpec,
                armorSpec
        );

        player.sendMessage(msg(player, "cittaexp.city.create.in_progress", Map.of(
                "city", args[1],
                "tag", args[2].toUpperCase(Locale.ROOT)
        )));

        lifecycleService.createCityAsync(createCommand).whenComplete((created, throwable) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (throwable == null) {
                        CityStyleRecord style = cityStyleService.saveOnCreate(
                                created.cityId(),
                                bannerSpec,
                                armorSpec
                        );
                        plugin.getLogger().info(
                                "[CittaEXP][command] /city create success"
                                        + " player=" + player.getUniqueId()
                                        + " city=" + created.name()
                                        + " tag=" + created.tag()
                        );
                        player.sendMessage(msg(player, "cittaexp.city.create.success", Map.of(
                                "city", created.name(),
                                "tag", created.tag(),
                                "tier", created.tier().name(),
                                "banner", style.bannerSpec().isBlank() ? "-" : style.bannerSpec(),
                                "armor", style.armorSpec().isBlank() ? "-" : style.armorSpec()
                        )));
                        return;
                    }
                    RuntimeException runtime = unwrapRuntime(throwable);
                    plugin.getLogger().log(
                            Level.WARNING,
                            "[CittaEXP][command] /city create failed"
                                    + " player=" + player.getUniqueId()
                                    + " city=" + args[1]
                                    + " tag=" + args[2]
                                    + " mappedKey=" + mapErrorKey(runtime)
                                    + " reason=" + safe(runtime.getMessage()),
                            runtime
                    );
                    player.sendMessage(msg(player, mapErrorKey(runtime), Map.of("reason", safe(runtime.getMessage()))));
                })
        );
        return true;
    }

    private boolean handleInfo(Player player, String[] args) {
        if (args.length >= 2 && "gui".equalsIgnoreCase(args[1])) {
            ensurePlayerHasCity(player);
            openProductionGui(player, UiScreenKey.DASHBOARD, "cittaexp.city.gui.open.dashboard");
            return true;
        }
        var maybeCity = (args.length >= 2)
                ? lifecycleService.cityByReference(args[1])
                : lifecycleService.cityByPlayer(player.getUniqueId());
        if (maybeCity.isEmpty()) {
            player.sendMessage(msg(player, "cittaexp.city.info.not_found"));
            return true;
        }

        var city = maybeCity.get();
        player.sendMessage(msg(player, "cittaexp.city.info.line1", Map.of(
                "city", city.name(),
                "tag", city.tag(),
                "tier", city.tier().name()
        )));
        player.sendMessage(msg(player, "cittaexp.city.info.line2", Map.of(
                "status", city.status().name(),
                "members", String.valueOf(city.memberCount()),
                "max", String.valueOf(city.maxMembers())
        )));
        return true;
    }

    private boolean handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(msg(player, "cittaexp.city.usage.invite"));
            return true;
        }
        if ("accept".equalsIgnoreCase(args[1])) {
            if (args.length < 3) {
                player.sendMessage(msg(player, "cittaexp.city.usage.invite_accept"));
                return true;
            }
            UUID invitationId = UUID.fromString(args[2]);
            lifecycleService.acceptInvite(invitationId, player.getUniqueId());
            player.sendMessage(msg(player, "cittaexp.city.invite.accepted"));
            return true;
        }
        if ("deny".equalsIgnoreCase(args[1])) {
            if (args.length < 3) {
                player.sendMessage(msg(player, "cittaexp.city.usage.invite_deny"));
                return true;
            }
            UUID invitationId = UUID.fromString(args[2]);
            lifecycleService.declineInvite(invitationId, player.getUniqueId(), "player-deny");
            player.sendMessage(msg(player, "cittaexp.city.invite.denied"));
            return true;
        }
        if (!requireModerationPermission(player)) {
            return true;
        }

        OfflinePlayer target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            target = Bukkit.getOfflinePlayer(args[1]);
        }
        var city = lifecycleService.cityByPlayer(player.getUniqueId())
                .orElseThrow(() -> new IllegalStateException("player-not-in-city"));
        lifecycleService.invite(city.cityId(), player.getUniqueId(), target.getUniqueId());
        player.sendMessage(msg(player, "cittaexp.city.invite.sent", Map.of("player", target.getName() == null ? args[1] : target.getName())));
        return true;
    }

    private boolean handleRequest(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(msg(player, "cittaexp.city.usage.request"));
            return true;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        if ("join".equals(sub)) {
            if (args.length < 3) {
                player.sendMessage(msg(player, "cittaexp.city.usage.request_join"));
                return true;
            }
            var city = lifecycleService.cityByReference(args[2]).orElseThrow(() -> new IllegalStateException("city-not-found"));
            String note = args.length >= 4 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : "";
            lifecycleService.requestJoin(city.cityId(), player.getUniqueId(), note);
            player.sendMessage(msg(player, "cittaexp.city.request.join.sent", Map.of("city", city.name())));
            return true;
        }
        if ("approve".equals(sub)) {
            if (!requireModerationPermission(player)) {
                return true;
            }
            if (args.length < 3) {
                player.sendMessage(msg(player, "cittaexp.city.usage.request_approve"));
                return true;
            }
            UUID requestId = UUID.fromString(args[2]);
            lifecycleService.approveJoinRequest(requestId, player.getUniqueId(), "approved");
            player.sendMessage(msg(player, "cittaexp.city.request.approved"));
            return true;
        }
        if ("reject".equals(sub)) {
            if (!requireModerationPermission(player)) {
                return true;
            }
            if (args.length < 3) {
                player.sendMessage(msg(player, "cittaexp.city.usage.request_reject"));
                return true;
            }
            UUID requestId = UUID.fromString(args[2]);
            String note = args.length >= 4 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : "";
            lifecycleService.rejectJoinRequest(requestId, player.getUniqueId(), note);
            player.sendMessage(msg(player, "cittaexp.city.request.rejected"));
            return true;
        }
        if ("upgrade".equals(sub) || "unfreeze".equals(sub) || "delete".equals(sub)) {
            var city = lifecycleService.cityByPlayer(player.getUniqueId())
                    .orElseThrow(() -> new IllegalStateException("player-not-in-city"));
            String note = args.length >= 3 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim() : "";
            if (note.isBlank()) {
                throw new IllegalStateException("reason-too-short");
            }
            if ("delete".equals(sub) && city.tier() == CityTier.BORGO) {
                cityModerationService.deleteCity(city.cityId().toString(), player.getUniqueId(), "borgo-autonomous-delete:" + note);
                player.sendMessage(msg(
                        player,
                        "cittaexp.city.request.delete.immediate",
                        Map.of("city", city.name(), "tag", city.tag())
                ));
                return true;
            }
            TicketType type = switch (sub) {
                case "upgrade" -> TicketType.UPGRADE;
                case "unfreeze" -> TicketType.UNFREEZE;
                case "delete" -> TicketType.DELETE;
                default -> throw new IllegalStateException("ticket-type-invalid");
            };
            StaffApprovalTicket ticket = staffApprovalService.request(
                    city.cityId(),
                    player.getUniqueId(),
                    type,
                    note,
                    "{}"
            );
            player.sendMessage(msg(
                    player,
                    "cittaexp.city.request.ticket.created",
                    Map.of(
                            "ticket_id", ticket.ticketId().toString(),
                            "type", ticket.type().name(),
                            "status", ticket.status().name()
                    )
            ));
            return true;
        }
        if ("list".equals(sub)) {
            var city = lifecycleService.cityByPlayer(player.getUniqueId())
                    .orElseThrow(() -> new IllegalStateException("player-not-in-city"));
            boolean pendingOnly = args.length < 3 || !"all".equalsIgnoreCase(args[2]);
            openProductionGui(player, UiScreenKey.REQUESTS_QUEUE, "cittaexp.city.gui.open.requests");
            List<StaffApprovalTicket> tickets = staffApprovalService.list(
                    20,
                    pendingOnly ? it.patric.cittaexp.core.model.TicketStatus.PENDING : null,
                    null,
                    city.cityId()
            );
            player.sendMessage(msg(
                    player,
                    "cittaexp.city.request.ticket.list.title",
                    Map.of("count", String.valueOf(tickets.size()))
            ));
            for (StaffApprovalTicket ticket : tickets) {
                player.sendMessage(msg(
                        player,
                        "cittaexp.city.request.ticket.list.entry",
                        Map.of(
                                "ticket_id", ticket.ticketId().toString(),
                                "type", ticket.type().name(),
                                "status", ticket.status().name(),
                                "reason", ticket.reason()
                        )
                ));
            }
            return true;
        }
        if ("cancel".equals(sub)) {
            if (args.length < 3) {
                player.sendMessage(msg(player, "cittaexp.city.usage.request_cancel"));
                return true;
            }
            UUID ticketId = UUID.fromString(args[2]);
            String note = args.length >= 4 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)).trim() : "cancelled";
            StaffApprovalTicket cancelled = staffApprovalService.cancel(ticketId, player.getUniqueId(), note);
            player.sendMessage(msg(
                    player,
                    "cittaexp.city.request.ticket.cancelled",
                    Map.of(
                            "ticket_id", cancelled.ticketId().toString(),
                            "status", cancelled.status().name()
                    )
            ));
            return true;
        }

        player.sendMessage(msg(player, "cittaexp.city.usage.request"));
        return true;
    }

    private boolean handleKick(Player player, String[] args) {
        if (!requireModerationPermission(player)) {
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(msg(player, "cittaexp.city.usage.kick"));
            return true;
        }
        OfflinePlayer target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            target = Bukkit.getOfflinePlayer(args[1]);
        }
        var city = lifecycleService.cityByPlayer(player.getUniqueId())
                .orElseThrow(() -> new IllegalStateException("player-not-in-city"));
        lifecycleService.kickMember(city.cityId(), player.getUniqueId(), target.getUniqueId(), "kick-command");
        player.sendMessage(msg(player, "cittaexp.city.kick.success", Map.of("player", target.getName() == null ? args[1] : target.getName())));
        return true;
    }

    private boolean handleLeave(Player player, String[] args) {
        var city = lifecycleService.cityByPlayer(player.getUniqueId())
                .orElseThrow(() -> new IllegalStateException("player-not-in-city"));
        lifecycleService.leaveCity(city.cityId(), player.getUniqueId(), "leave-command");
        player.sendMessage(msg(player, "cittaexp.city.leave.success"));
        return true;
    }

    private boolean handleRoles(Player player, String[] args) {
        if (args.length == 1) {
            ensurePlayerHasCity(player);
            openProductionGui(player, UiScreenKey.ROLES, "cittaexp.city.gui.open.roles");
            return true;
        }
        var city = lifecycleService.cityByPlayer(player.getUniqueId())
                .orElseThrow(() -> new IllegalStateException("player-not-in-city"));

        String sub = args[1].toLowerCase(Locale.ROOT);
        if ("list".equals(sub)) {
            player.sendMessage(msg(player, "cittaexp.city.roles.list.title", Map.of("city", city.name())));
            for (CityRole role : lifecycleService.listRoles(city.cityId())) {
                player.sendMessage(msg(player, "cittaexp.city.roles.list.entry", Map.of(
                        "role", role.roleKey(),
                        "invite", String.valueOf(role.permissions().canInvite()),
                        "kick", String.valueOf(role.permissions().canKick()),
                        "manage", String.valueOf(role.permissions().canManageMembers())
                )));
            }
            return true;
        }

        if ("toggle".equals(sub)) {
            if (!requireModerationPermission(player)) {
                return true;
            }
            if (args.length < 5) {
                player.sendMessage(msg(player, "cittaexp.city.usage.roles_toggle"));
                return true;
            }
            String roleKey = args[2];
            String flag = args[3].toLowerCase(Locale.ROOT);
            boolean enabled = parseBoolean(args[4]);

            CityRole role = lifecycleService.listRoles(city.cityId()).stream()
                    .filter(value -> value.roleKey().equalsIgnoreCase(roleKey))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("role-not-found"));
            RolePermissionSet current = role.permissions();
            RolePermissionSet updated = switch (flag) {
                case "invite" -> new RolePermissionSet(enabled, current.canKick(), current.canManageMembers(), current.canManageRoles(), current.canRequestUpgrade(), current.canExpandClaims(), current.canManageSettings());
                case "kick" -> new RolePermissionSet(current.canInvite(), enabled, current.canManageMembers(), current.canManageRoles(), current.canRequestUpgrade(), current.canExpandClaims(), current.canManageSettings());
                case "manage_members" -> new RolePermissionSet(current.canInvite(), current.canKick(), enabled, current.canManageRoles(), current.canRequestUpgrade(), current.canExpandClaims(), current.canManageSettings());
                case "manage_roles" -> new RolePermissionSet(current.canInvite(), current.canKick(), current.canManageMembers(), enabled, current.canRequestUpgrade(), current.canExpandClaims(), current.canManageSettings());
                case "request_upgrade" -> new RolePermissionSet(current.canInvite(), current.canKick(), current.canManageMembers(), current.canManageRoles(), enabled, current.canExpandClaims(), current.canManageSettings());
                case "expand_claims" -> new RolePermissionSet(current.canInvite(), current.canKick(), current.canManageMembers(), current.canManageRoles(), current.canRequestUpgrade(), enabled, current.canManageSettings());
                case "manage_settings" -> new RolePermissionSet(current.canInvite(), current.canKick(), current.canManageMembers(), current.canManageRoles(), current.canRequestUpgrade(), current.canExpandClaims(), enabled);
                default -> throw new IllegalStateException("roles-toggle-flag-invalid");
            };
            lifecycleService.updateRole(city.cityId(), player.getUniqueId(), role.roleKey(), role.displayName(), role.priority(), updated);
            player.sendMessage(msg(player, "cittaexp.city.roles.toggle.success", Map.of(
                    "role", role.roleKey(),
                    "flag", flag,
                    "value", String.valueOf(enabled)
            )));
            return true;
        }

        player.sendMessage(msg(player, "cittaexp.city.usage.roles"));
        return true;
    }

    private boolean handleVice(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(msg(player, "cittaexp.city.usage.vice"));
            return true;
        }
        var city = lifecycleService.cityByPlayer(player.getUniqueId())
                .orElseThrow(() -> new IllegalStateException("player-not-in-city"));
        String sub = args[1].toLowerCase(Locale.ROOT);
        if ("info".equals(sub)) {
            var vice = lifecycleService.vice(city.cityId());
            if (vice.isEmpty()) {
                player.sendMessage(msg(player, "cittaexp.city.vice.info.none"));
                return true;
            }
            OfflinePlayer vicePlayer = Bukkit.getOfflinePlayer(vice.get().viceUuid());
            String name = vicePlayer.getName() == null ? vice.get().viceUuid().toString() : vicePlayer.getName();
            player.sendMessage(msg(player, "cittaexp.city.vice.info.value", Map.of("player", name)));
            return true;
        }
        if (!requireModerationPermission(player)) {
            return true;
        }
        if ("clear".equals(sub)) {
            lifecycleService.clearVice(city.cityId(), player.getUniqueId(), "city-vice-clear-command");
            player.sendMessage(msg(player, "cittaexp.city.vice.clear.success"));
            return true;
        }
        if ("set".equals(sub)) {
            if (args.length < 3) {
                player.sendMessage(msg(player, "cittaexp.city.usage.vice_set"));
                return true;
            }
            OfflinePlayer target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                target = Bukkit.getOfflinePlayer(args[2]);
            }
            lifecycleService.setVice(
                    city.cityId(),
                    player.getUniqueId(),
                    target.getUniqueId(),
                    "city-vice-set-command"
            );
            player.sendMessage(msg(
                    player,
                    "cittaexp.city.vice.set.success",
                    Map.of("player", target.getName() == null ? args[2] : target.getName())
            ));
            return true;
        }

        player.sendMessage(msg(player, "cittaexp.city.usage.vice"));
        return true;
    }

    private boolean handlePerms(Player player, String[] args) {
        var city = lifecycleService.cityByPlayer(player.getUniqueId())
                .orElseThrow(() -> new IllegalStateException("player-not-in-city"));
        if (args.length < 2) {
            player.sendMessage(msg(player, "cittaexp.city.usage.perms"));
            return true;
        }

        if ("set".equalsIgnoreCase(args[1])) {
            if (!requireModerationPermission(player)) {
                return true;
            }
            if (args.length < 5) {
                player.sendMessage(msg(player, "cittaexp.city.usage.perms_set"));
                return true;
            }
            OfflinePlayer target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                target = Bukkit.getOfflinePlayer(args[2]);
            }
            String flag = args[3].toLowerCase(Locale.ROOT);
            if (!isBooleanToken(args[4])) {
                throw new IllegalStateException("roles-toggle-flag-invalid");
            }
            boolean enabled = parseBoolean(args[4]);
            MemberClaimPermissionsView current = lifecycleService.claimPermissions(city.cityId(), target.getUniqueId())
                    .orElseThrow(() -> new IllegalStateException("member-not-found"));
            MemberClaimPermissions updated = switch (flag) {
                case "access" -> new MemberClaimPermissions(enabled, current.permissions().container(), current.permissions().build());
                case "container" -> new MemberClaimPermissions(current.permissions().access(), enabled, current.permissions().build());
                case "build" -> new MemberClaimPermissions(current.permissions().access(), current.permissions().container(), enabled);
                default -> throw new IllegalStateException("claim-permission-invalid");
            };
            lifecycleService.setMemberClaimPermission(
                    city.cityId(),
                    player.getUniqueId(),
                    target.getUniqueId(),
                    updated,
                    "city-perms-set-command"
            );
            player.sendMessage(msg(
                    player,
                    "cittaexp.city.perms.set.success",
                    Map.of(
                            "player", target.getName() == null ? args[2] : target.getName(),
                            "flag", flag,
                            "value", String.valueOf(enabled)
                    )
            ));
            return true;
        }

        OfflinePlayer target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            target = Bukkit.getOfflinePlayer(args[1]);
        }
        MemberClaimPermissionsView view = lifecycleService.claimPermissions(city.cityId(), target.getUniqueId())
                .orElseThrow(() -> new IllegalStateException("member-not-found"));
        player.sendMessage(msg(
                player,
                "cittaexp.city.perms.view",
                Map.of(
                        "player", target.getName() == null ? args[1] : target.getName(),
                        "access", String.valueOf(view.permissions().access()),
                        "container", String.valueOf(view.permissions().container()),
                        "build", String.valueOf(view.permissions().build())
                )
        ));
        return true;
    }

    private boolean handleFreezeStatus(Player player, String[] args) {
        if (args.length < 2 || !"status".equalsIgnoreCase(args[1])) {
            player.sendMessage(msg(player, "cittaexp.city.usage.freeze"));
            return true;
        }
        var city = lifecycleService.cityByPlayer(player.getUniqueId())
                .orElseThrow(() -> new IllegalStateException("player-not-in-city"));
        boolean frozen = lifecycleService.isCityFrozen(city.cityId());
        player.sendMessage(msg(player, "cittaexp.city.freeze.status", Map.of(
                "city", city.name(),
                "value", frozen ? "FROZEN" : "ACTIVE"
        )));
        return true;
    }

    private boolean handleClaimBlocks(Player player, String[] args) {
        var city = lifecycleService.cityByPlayer(player.getUniqueId())
                .orElseThrow(() -> new IllegalStateException("player-not-in-city"));
        if (args.length < 2) {
            player.sendMessage(msg(player, "cittaexp.city.usage.claimblocks"));
            return true;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        if ("info".equals(sub)) {
            CityClaimBlockService.QuotaSnapshot snapshot = claimBlockService.quotaSnapshot(city.cityId());
            player.sendMessage(msg(player, "cittaexp.city.claimblocks.info", Map.of(
                    "owner", snapshot.ownerUuid().toString(),
                    "total", String.valueOf(snapshot.totalPurchasedBlocks()),
                    "active", String.valueOf(snapshot.activeAllocatedBlocks())
            )));
            return true;
        }
        if ("shop".equals(sub)) {
            openProductionGui(player, UiScreenKey.CLAIM_SHOP, "cittaexp.city.gui.open.claim_shop");
            return true;
        }
        if ("buy".equals(sub)) {
            if (args.length < 3) {
                player.sendMessage(msg(player, "cittaexp.city.usage.claimblocks_buy"));
                return true;
            }
            int blocks = Integer.parseInt(args[2]);
            CityClaimBlockService.PurchaseOutcome outcome = claimBlockService.purchaseBlocks(
                    city.cityId(),
                    player.getUniqueId(),
                    blocks,
                    "command-buy"
            );
            player.sendMessage(msg(player, "cittaexp.city.claimblocks.shop.purchase.success", Map.of(
                    "blocks", String.valueOf(outcome.purchasedBlocks()),
                    "cost", String.valueOf(outcome.totalCost()),
                    "treasury", String.valueOf(outcome.treasuryContribution()),
                    "leader", String.valueOf(outcome.leaderContribution()),
                    "active", String.valueOf(outcome.newActiveAllocatedBlocks())
            )));
            return true;
        }
        player.sendMessage(msg(player, "cittaexp.city.usage.claimblocks"));
        return true;
    }

    private boolean handleStyle(Player player, String[] args) {
        var city = lifecycleService.cityByPlayer(player.getUniqueId())
                .orElseThrow(() -> new IllegalStateException("player-not-in-city"));
        if (args.length < 2) {
            player.sendMessage(msg(player, "cittaexp.city.usage.style"));
            return true;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        if ("show".equals(sub)) {
            CityStyleRecord style = cityStyleService.find(city.cityId())
                    .orElse(new CityStyleRecord(city.cityId(), "", "", CityStyleRecord.BannerLocation.empty(), 0L));
            String location = style.bannerLocation().configured()
                    ? style.bannerLocation().world() + " "
                    + Math.round(style.bannerLocation().x()) + ","
                    + Math.round(style.bannerLocation().y()) + ","
                    + Math.round(style.bannerLocation().z())
                    : "-";
            player.sendMessage(msg(
                    player,
                    "cittaexp.city.style.show",
                    Map.of(
                            "banner", style.bannerSpec().isBlank() ? "-" : style.bannerSpec(),
                            "armor", style.armorSpec().isBlank() ? "-" : style.armorSpec(),
                            "location", location
                    )
            ));
            return true;
        }
        if (!isLeader(player, city)) {
            throw new IllegalStateException("leader-required");
        }
        if (!"set".equals(sub)) {
            player.sendMessage(msg(player, "cittaexp.city.usage.style"));
            return true;
        }
        if (args.length < 4) {
            player.sendMessage(msg(player, "cittaexp.city.usage.style_set"));
            return true;
        }
        String field = args[2].toLowerCase(Locale.ROOT);
        String value = String.join(" ", Arrays.copyOfRange(args, 3, args.length)).trim();
        if (value.isBlank()) {
            throw new IllegalStateException(field + "Spec-blank");
        }
        if ("banner".equals(field)) {
            CityStyleRecord updated = cityStyleService.updateBanner(city.cityId(), value);
            if (bannerDisplayService != null && updated.bannerLocation().configured()) {
                bannerDisplayService.render(city.cityId(), updated);
            }
            player.sendMessage(msg(player, "cittaexp.city.style.set.banner.success", Map.of("banner", updated.bannerSpec())));
            return true;
        }
        if ("armor".equals(field)) {
            CityStyleRecord updated = cityStyleService.updateArmor(city.cityId(), value);
            player.sendMessage(msg(player, "cittaexp.city.style.set.armor.success", Map.of("armor", updated.armorSpec())));
            return true;
        }
        player.sendMessage(msg(player, "cittaexp.city.usage.style_set"));
        return true;
    }

    private boolean handleBanner(Player player, String[] args) {
        var city = lifecycleService.cityByPlayer(player.getUniqueId())
                .orElseThrow(() -> new IllegalStateException("player-not-in-city"));
        if (args.length < 2) {
            player.sendMessage(msg(player, "cittaexp.city.usage.banner"));
            return true;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        if ("show".equals(sub)) {
            CityStyleRecord style = cityStyleService.find(city.cityId())
                    .orElse(new CityStyleRecord(city.cityId(), "", "", CityStyleRecord.BannerLocation.empty(), 0L));
            String location = style.bannerLocation().configured()
                    ? style.bannerLocation().world() + " "
                    + Math.round(style.bannerLocation().x()) + ","
                    + Math.round(style.bannerLocation().y()) + ","
                    + Math.round(style.bannerLocation().z())
                    : "-";
            player.sendMessage(msg(
                    player,
                    "cittaexp.city.banner.show",
                    Map.of(
                            "banner", style.bannerSpec().isBlank() ? "-" : style.bannerSpec(),
                            "location", location
                    )
            ));
            return true;
        }
        if (!isLeader(player, city)) {
            throw new IllegalStateException("leader-required");
        }
        if ("setlocation".equals(sub)) {
            Location location = player.getLocation();
            World world = location.getWorld();
            if (world == null) {
                throw new IllegalStateException("city-banner-world-missing");
            }
            CityStyleRecord style = cityStyleService.setBannerLocation(
                    city.cityId(),
                    world.getName(),
                    location.getX(),
                    location.getY(),
                    location.getZ(),
                    location.getYaw(),
                    location.getPitch()
            );
            if (args.length >= 3) {
                String bannerSpec = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim();
                if (!bannerSpec.isBlank()) {
                    style = cityStyleService.updateBanner(city.cityId(), bannerSpec);
                    style = cityStyleService.setBannerLocation(
                            city.cityId(),
                            world.getName(),
                            location.getX(),
                            location.getY(),
                            location.getZ(),
                            location.getYaw(),
                            location.getPitch()
                    );
                }
            }
            boolean rendered = bannerDisplayService != null && bannerDisplayService.render(city.cityId(), style);
            player.sendMessage(msg(
                    player,
                    "cittaexp.city.banner.location.set",
                    Map.of(
                            "world", world.getName(),
                            "x", String.valueOf(Math.round(location.getX())),
                            "y", String.valueOf(Math.round(location.getY())),
                            "z", String.valueOf(Math.round(location.getZ())),
                            "rendered", String.valueOf(rendered)
                    )
            ));
            return true;
        }
        if ("clearlocation".equals(sub)) {
            cityStyleService.clearBannerLocation(city.cityId());
            int removed = bannerDisplayService == null ? 0 : bannerDisplayService.remove(city.cityId());
            player.sendMessage(msg(player, "cittaexp.city.banner.location.cleared", Map.of("removed", String.valueOf(removed))));
            return true;
        }
        if ("place".equals(sub)) {
            CityStyleRecord style = cityStyleService.find(city.cityId())
                    .orElseThrow(() -> new IllegalStateException("city-banner-style-missing"));
            boolean rendered = bannerDisplayService != null && bannerDisplayService.render(city.cityId(), style);
            player.sendMessage(msg(player, "cittaexp.city.banner.place", Map.of("rendered", String.valueOf(rendered))));
            return true;
        }
        if ("remove".equals(sub)) {
            int removed = bannerDisplayService == null ? 0 : bannerDisplayService.remove(city.cityId());
            player.sendMessage(msg(player, "cittaexp.city.banner.remove", Map.of("removed", String.valueOf(removed))));
            return true;
        }
        player.sendMessage(msg(player, "cittaexp.city.usage.banner"));
        return true;
    }

    private Component msg(CommandSender sender, String key) {
        return msg(sender, key, Map.of());
    }

    private Component msg(CommandSender sender, String key, Map<String, String> placeholders) {
        try {
            return messageService.render(key, placeholders, resolveLocale(sender));
        } catch (RuntimeException ex) {
            plugin.getLogger().log(
                    Level.WARNING,
                    "Message render failed key=" + key + " placeholders=" + placeholders,
                    ex
            );
            return messageService.render("cittaexp.city.error.generic", Map.of("reason", key), resolveLocale(sender));
        }
    }

    private static Locale resolveLocale(CommandSender sender) {
        if (sender instanceof Player player) {
            String raw = player.getLocale();
            if (raw != null && !raw.isBlank()) {
                return Locale.forLanguageTag(raw.replace('_', '-'));
            }
        }
        return Locale.ITALIAN;
    }

    private static boolean parseBoolean(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.equals("on") || normalized.equals("true") || normalized.equals("yes") || normalized.equals("1");
    }

    private static boolean isBooleanToken(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.equals("on")
                || normalized.equals("off")
                || normalized.equals("true")
                || normalized.equals("false")
                || normalized.equals("yes")
                || normalized.equals("no")
                || normalized.equals("1")
                || normalized.equals("0");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String mapErrorKey(RuntimeException ex) {
        String reason = safe(ex.getMessage());
        if (reason.isBlank()) {
            return "cittaexp.city.error.generic";
        }
        if (reason.startsWith("claim-create-failed")) {
            return "cittaexp.city.create.failed.claim";
        }
        if (reason.startsWith("city-create-db-failed-rollback-failed")) {
            return "cittaexp.city.create.failed.persistence_rollback_failed";
        }
        if (reason.startsWith("city-create-db-failed-rollback-ok")) {
            return "cittaexp.city.create.failed.persistence";
        }
        if (reason.contains("city-name-or-tag-already-exists")) {
            return "cittaexp.city.error.city_name_tag_conflict";
        }
        return switch (reason) {
            case "player-already-in-city" -> "cittaexp.city.error.player_already_in_city";
            case "city-name-tag-conflict" -> "cittaexp.city.error.city_name_tag_conflict";
            case "city-not-found" -> "cittaexp.city.error.city_not_found";
            case "city-banner-style-missing" -> "cittaexp.city.banner.error.style_missing";
            case "city-banner-world-missing" -> "cittaexp.city.banner.error.world_missing";
            case "bannerSpec-blank" -> "cittaexp.city.style.error.invalid_banner";
            case "armorSpec-blank" -> "cittaexp.city.style.error.invalid_armor";
            case "claims-unavailable", "huskclaims-unavailable" -> "cittaexp.city.create.failed.claims_unavailable";
            case "claim-block-shop-disabled" -> "cittaexp.city.claimblocks.error.disabled";
            case "claim-block-shop-quest-gated" -> "cittaexp.city.claimblocks.error.quest_gated";
            case "claim-block-purchase-tier-blocked" -> "cittaexp.city.claimblocks.error.tier_blocked";
            case "claim-block-purchase-funds-missing" -> "cittaexp.city.claimblocks.error.funds_missing";
            case "claim-block-purchase-out-of-range" -> "cittaexp.city.claimblocks.error.range";
            case "claim-block-purchase-grant-failed", "claim-block-purchase-persistence-failed" -> "cittaexp.city.claimblocks.error.purchase_failed";
            case "claim-block-reclaim-failed:remove-failed" -> "cittaexp.city.claimblocks.error.reclaim_failed";
            case "city-freeze-restricted" -> "cittaexp.city.error.freeze_restricted";
            case "city-not-frozen" -> "cittaexp.city.error.city_not_frozen";
            case "ticket-pending-exists" -> "cittaexp.city.error.ticket_pending_exists";
            case "ticket-not-found" -> "cittaexp.city.error.ticket_not_found";
            case "ticket-not-pending" -> "cittaexp.city.error.ticket_not_pending";
            case "ticket-cancel-not-allowed" -> "cittaexp.city.error.ticket_cancel_not_allowed";
            case "reason-too-short" -> "cittaexp.city.error.reason_too_short";
            case "reason-too-long" -> "cittaexp.city.error.reason_too_long";
            case "permission-denied-invite", "permission-denied-kick", "permission-denied-manage-members", "permission-denied-manage-roles", "permission-denied-expand-claim", "permission-denied-request-upgrade" -> "cittaexp.city.error.permission_denied";
            case "leader-must-transfer-before-leave", "leader-must-assign-vice-before-leave", "cannot-kick-leader", "cannot-kick-higher-role", "system-role-is-immutable", "vice-cannot-be-leader", "leader-required" -> "cittaexp.city.error.leader_restricted";
            case "invitation-expired", "join-request-expired" -> "cittaexp.city.error.expired";
            case "city-member-limit-reached" -> "cittaexp.city.error.member_limit";
            case "role-not-found", "roles-toggle-flag-invalid", "claim-permission-invalid" -> "cittaexp.city.error.role_invalid";
            case "claim-binding-missing", "member-claim-sync-failed", "member-claim-sync-clear-failed", "vice-claim-sync-failed",
                 "leader-succession-sync-failed", "claim-owner-transfer-failed", "leader-succession-claim-transfer-failed" -> "cittaexp.city.error.claim_sync_failed";
            case "claim-block-quota-transfer-failed", "leader-succession-quota-transfer-failed" -> "cittaexp.city.claimblocks.error.purchase_failed";
            case "cannot-modify-system-role-claim-permissions" -> "cittaexp.city.error.claim_system_role_immutable";
            case "player-not-in-city", "member-not-found" -> "cittaexp.city.error.player_not_in_city";
            case "invalid-tier-upgrade-path" -> "cittaexp.city.error.invalid_upgrade_path";
            default -> "cittaexp.city.error.generic";
        };
    }

    private boolean requireModerationPermission(Player player) {
        if (player.hasPermission(MODERATION_PERMISSION)) {
            return true;
        }
        player.sendMessage(msg(player, "cittaexp.city.no_permission_moderation"));
        return false;
    }

    private static boolean isLeader(Player player, City city) {
        return city.leaderUuid().equals(player.getUniqueId());
    }

    private void ensurePlayerHasCity(Player player) {
        lifecycleService.cityByPlayer(player.getUniqueId())
                .orElseThrow(() -> new IllegalStateException("player-not-in-city"));
    }

    private void openProductionGui(Player player, UiScreenKey screenKey, String openedMessageKey) {
        GuiFlowOrchestrator.OpenResult result;
        try {
            result = guiFlowOrchestrator.open(
                    player,
                    screenKey,
                    PreviewScenario.DEFAULT,
                    ThemeMode.AUTO
            );
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "City GUI open failed screen=" + screenKey.key(), ex);
            result = GuiFlowOrchestrator.OpenResult.OPEN_FAILED;
        }
        if (result == null) {
            result = GuiFlowOrchestrator.OpenResult.OPEN_FAILED;
        }
        if (result == GuiFlowOrchestrator.OpenResult.OPENED) {
            player.sendMessage(msg(player, openedMessageKey));
            return;
        }
        player.sendMessage(msg(player, "cittaexp.city.gui.open.failed", Map.of(
                "screen", screenKey.commandToken(),
                "result", result.name()
        )));
    }

    private static RuntimeException unwrapRuntime(Throwable throwable) {
        Throwable current = throwable;
        if (current instanceof CompletionException completionException && completionException.getCause() != null) {
            current = completionException.getCause();
        }
        if (current instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException(current.getMessage() == null ? "create-city-failed" : current.getMessage(), current);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PLAYER_PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return complete(List.of("create", "info", "invite", "request", "kick", "leave", "roles", "vice", "perms", "freeze", "claimblocks", "style", "banner"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("info")) {
            List<String> options = new ArrayList<>();
            options.add("gui");
            options.addAll(cityReferences());
            return complete(options, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("invite")) {
            List<String> options = new ArrayList<>();
            options.add("accept");
            options.add("deny");
            options.addAll(onlinePlayerNames());
            return complete(options, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("request")) {
            return complete(List.of("join", "approve", "reject", "upgrade", "unfreeze", "delete", "list", "cancel"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("request") && args[1].equalsIgnoreCase("list")) {
            return complete(List.of("pending", "all"), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("request") && args[1].equalsIgnoreCase("join")) {
            return complete(cityReferences(), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("request") && args[1].equalsIgnoreCase("cancel")) {
            return complete(currentCityTicketIds(sender), args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("kick")) {
            return complete(currentCityMemberNames(sender), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("roles")) {
            return complete(List.of("list", "toggle"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("vice")) {
            return complete(List.of("set", "clear", "info"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("vice") && args[1].equalsIgnoreCase("set")) {
            return complete(currentCityMemberNames(sender), args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("perms")) {
            List<String> options = new ArrayList<>();
            options.add("set");
            options.addAll(currentCityMemberNames(sender));
            return complete(options, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("perms") && args[1].equalsIgnoreCase("set")) {
            return complete(currentCityMemberNames(sender), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("perms") && args[1].equalsIgnoreCase("set")) {
            return complete(List.of("access", "container", "build"), args[3]);
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("perms") && args[1].equalsIgnoreCase("set")) {
            return complete(List.of("on", "off"), args[4]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("roles") && args[1].equalsIgnoreCase("toggle")) {
            return complete(currentCityRoleKeys(sender), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("roles") && args[1].equalsIgnoreCase("toggle")) {
            return complete(List.of("invite", "kick", "manage_members", "manage_roles", "request_upgrade", "expand_claims", "manage_settings"), args[3]);
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("roles") && args[1].equalsIgnoreCase("toggle")) {
            return complete(List.of("on", "off"), args[4]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("freeze")) {
            return complete(List.of("status"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("claimblocks")) {
            return complete(List.of("info", "shop", "buy"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("claimblocks") && args[1].equalsIgnoreCase("buy")) {
            return complete(List.of("512", "1024", "2048", "4096"), args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("style")) {
            return complete(List.of("show", "set"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("style") && args[1].equalsIgnoreCase("set")) {
            return complete(List.of("banner", "armor"), args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("banner")) {
            return complete(List.of("show", "setlocation", "clearlocation", "place", "remove"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("banner") && args[1].equalsIgnoreCase("setlocation")) {
            return complete(List.of("white_banner", "red_banner", "blue_banner", "black_banner"), args[2]);
        }
        return List.of();
    }

    private List<String> cityReferences() {
        return lifecycleService.listCityReferences();
    }

    private static List<String> onlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(Objects::nonNull)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private List<String> currentCityMemberNames(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        var city = lifecycleService.cityByPlayer(player.getUniqueId());
        if (city.isEmpty()) {
            return List.of();
        }
        Set<String> names = new LinkedHashSet<>();
        lifecycleService.listActiveMembers(city.get().cityId()).forEach(member -> {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(member.playerUuid());
            if (offlinePlayer.getName() != null && !offlinePlayer.getName().isBlank()) {
                names.add(offlinePlayer.getName());
            }
        });
        return List.copyOf(names);
    }

    private List<String> currentCityRoleKeys(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        var city = lifecycleService.cityByPlayer(player.getUniqueId());
        if (city.isEmpty()) {
            return List.of();
        }
        return lifecycleService.listRoles(city.get().cityId()).stream()
                .map(CityRole::roleKey)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private List<String> currentCityTicketIds(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        var city = lifecycleService.cityByPlayer(player.getUniqueId());
        if (city.isEmpty()) {
            return List.of();
        }
        return staffApprovalService.list(20, null, null, city.get().cityId()).stream()
                .filter(ticket -> ticket.status().name().equalsIgnoreCase("PENDING"))
                .map(ticket -> ticket.ticketId().toString())
                .toList();
    }

    private static List<String> complete(List<String> options, String token) {
        String normalized = token == null ? "" : token.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(normalized))
                .sorted()
                .collect(Collectors.toList());
    }

    private static final class InMemoryCityStyleService implements CityStyleService {

        private final Map<UUID, CityStyleRecord> store = new ConcurrentHashMap<>();

        @Override
        public Optional<CityStyleRecord> find(UUID cityId) {
            return Optional.ofNullable(store.get(cityId));
        }

        @Override
        public CityStyleRecord saveOnCreate(UUID cityId, String bannerSpec, String armorSpec) {
            CityStyleRecord current = store.get(cityId);
            CityStyleRecord next = new CityStyleRecord(
                    cityId,
                    nonBlank(bannerSpec, current == null ? "" : current.bannerSpec()),
                    nonBlank(armorSpec, current == null ? "" : current.armorSpec()),
                    current == null ? CityStyleRecord.BannerLocation.empty() : current.bannerLocation(),
                    System.currentTimeMillis()
            );
            store.put(cityId, next);
            return next;
        }

        @Override
        public CityStyleRecord updateBanner(UUID cityId, String bannerSpec) {
            CityStyleRecord current = store.get(cityId);
            CityStyleRecord next = new CityStyleRecord(
                    cityId,
                    bannerSpec,
                    current == null ? "" : current.armorSpec(),
                    current == null ? CityStyleRecord.BannerLocation.empty() : current.bannerLocation(),
                    System.currentTimeMillis()
            );
            store.put(cityId, next);
            return next;
        }

        @Override
        public CityStyleRecord updateArmor(UUID cityId, String armorSpec) {
            CityStyleRecord current = store.get(cityId);
            CityStyleRecord next = new CityStyleRecord(
                    cityId,
                    current == null ? "" : current.bannerSpec(),
                    armorSpec,
                    current == null ? CityStyleRecord.BannerLocation.empty() : current.bannerLocation(),
                    System.currentTimeMillis()
            );
            store.put(cityId, next);
            return next;
        }

        @Override
        public CityStyleRecord setBannerLocation(UUID cityId, String world, double x, double y, double z, float yaw, float pitch) {
            CityStyleRecord current = store.get(cityId);
            CityStyleRecord next = new CityStyleRecord(
                    cityId,
                    current == null ? "" : current.bannerSpec(),
                    current == null ? "" : current.armorSpec(),
                    new CityStyleRecord.BannerLocation(world, x, y, z, yaw, pitch),
                    System.currentTimeMillis()
            );
            store.put(cityId, next);
            return next;
        }

        @Override
        public CityStyleRecord clearBannerLocation(UUID cityId) {
            CityStyleRecord current = store.get(cityId);
            CityStyleRecord next = new CityStyleRecord(
                    cityId,
                    current == null ? "" : current.bannerSpec(),
                    current == null ? "" : current.armorSpec(),
                    CityStyleRecord.BannerLocation.empty(),
                    System.currentTimeMillis()
            );
            store.put(cityId, next);
            return next;
        }

        private static String nonBlank(String incoming, String fallback) {
            String normalized = incoming == null ? "" : incoming.trim();
            if (!normalized.isBlank()) {
                return normalized;
            }
            return fallback == null ? "" : fallback.trim();
        }
    }
}
