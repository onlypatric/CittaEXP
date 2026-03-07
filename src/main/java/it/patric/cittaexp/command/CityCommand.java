package it.patric.cittaexp.command;

import dev.patric.commonlib.api.MessageService;
import it.patric.cittaexp.core.model.CityRole;
import it.patric.cittaexp.core.model.RolePermissionSet;
import it.patric.cittaexp.core.runtime.DefaultCityLifecycleService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class CityCommand implements CommandExecutor, TabCompleter {

    private static final String PLAYER_PERMISSION = "cittaexp.city.player";
    private static final String MODERATION_PERMISSION = "cittaexp.city.moderation";

    private final DefaultCityLifecycleService lifecycleService;
    private final MessageService messageService;

    public CityCommand(DefaultCityLifecycleService lifecycleService, MessageService messageService) {
        this.lifecycleService = Objects.requireNonNull(lifecycleService, "lifecycleService");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
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
                case "freeze" -> handleFreezeStatus(player, args);
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

        var location = player.getLocation();
        var created = lifecycleService.createCity(new it.patric.cittaexp.core.service.CityLifecycleService.CreateCityCommand(
                player.getUniqueId(),
                args[1],
                args[2],
                location.getWorld() == null ? "world" : location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockZ(),
                "",
                ""
        ));

        player.sendMessage(msg(player, "cittaexp.city.create.success", Map.of(
                "city", created.name(),
                "tag", created.tag(),
                "tier", created.tier().name()
        )));
        return true;
    }

    private boolean handleInfo(Player player, String[] args) {
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
        if (args.length < 2) {
            player.sendMessage(msg(player, "cittaexp.city.usage.roles"));
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

    private Component msg(CommandSender sender, String key) {
        return msg(sender, key, Map.of());
    }

    private Component msg(CommandSender sender, String key, Map<String, String> placeholders) {
        return messageService.render(key, placeholders, resolveLocale(sender));
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

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String mapErrorKey(RuntimeException ex) {
        String reason = safe(ex.getMessage());
        if (reason.isBlank()) {
            return "cittaexp.city.error.generic";
        }
        return switch (reason) {
            case "player-already-in-city" -> "cittaexp.city.error.player_already_in_city";
            case "city-name-tag-conflict" -> "cittaexp.city.error.city_name_tag_conflict";
            case "city-not-found" -> "cittaexp.city.error.city_not_found";
            case "city-freeze-restricted" -> "cittaexp.city.error.freeze_restricted";
            case "permission-denied-invite", "permission-denied-kick", "permission-denied-manage-members", "permission-denied-manage-roles", "permission-denied-expand-claim", "permission-denied-request-upgrade" -> "cittaexp.city.error.permission_denied";
            case "leader-must-transfer-before-leave", "cannot-kick-leader", "leader-role-is-immutable" -> "cittaexp.city.error.leader_restricted";
            case "invitation-expired", "join-request-expired" -> "cittaexp.city.error.expired";
            case "city-member-limit-reached" -> "cittaexp.city.error.member_limit";
            case "role-not-found", "roles-toggle-flag-invalid" -> "cittaexp.city.error.role_invalid";
            case "player-not-in-city", "member-not-found" -> "cittaexp.city.error.player_not_in_city";
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PLAYER_PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return complete(List.of("create", "info", "invite", "request", "kick", "leave", "roles", "freeze"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("invite")) {
            return complete(List.of("accept", "deny"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("request")) {
            return complete(List.of("join", "approve", "reject"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("roles")) {
            return complete(List.of("list", "toggle"), args[1]);
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
        return List.of();
    }

    private static List<String> complete(List<String> options, String token) {
        String normalized = token == null ? "" : token.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(normalized))
                .sorted()
                .collect(Collectors.toList());
    }
}
