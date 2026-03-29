package it.patric.cittaexp.wiki;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import it.patric.cittaexp.text.MiniMessageHelper;
import it.patric.cittaexp.utils.DialogViewUtils;
import it.patric.cittaexp.utils.PluginConfigUtils;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class CityWikiDialogService {

    private static final ClickCallback.Options CLICK_OPTIONS = ClickCallback.Options.builder()
            .uses(1)
            .lifetime(Duration.ofMinutes(3))
            .build();
    private static final String ROOT_PAGE_ID = "home";

    private final Plugin plugin;
    private final PluginConfigUtils cfg;
    private Consumer<Player> hubOpener = player -> { };
    private Consumer<Player> bankOpener = player -> { };
    private Consumer<Player> claimSettingsOpener = player -> { };
    private Consumer<Player> territoriesOpener = player -> { };
    private Consumer<Player> defenseOpener = player -> { };

    public CityWikiDialogService(Plugin plugin) {
        this.plugin = plugin;
        this.cfg = new PluginConfigUtils(plugin);
    }

    public void setHubOpener(Consumer<Player> hubOpener) {
        this.hubOpener = safeConsumer(hubOpener);
    }

    public void setBankOpener(Consumer<Player> bankOpener) {
        this.bankOpener = safeConsumer(bankOpener);
    }

    public void setClaimSettingsOpener(Consumer<Player> claimSettingsOpener) {
        this.claimSettingsOpener = safeConsumer(claimSettingsOpener);
    }

    public void setTerritoriesOpener(Consumer<Player> territoriesOpener) {
        this.territoriesOpener = safeConsumer(territoriesOpener);
    }

    public void setDefenseOpener(Consumer<Player> defenseOpener) {
        this.defenseOpener = safeConsumer(defenseOpener);
    }

    public void openRoot(Player player) {
        openRoot(player, CityWikiBackTarget.CLOSE);
    }

    public void openRoot(Player player, CityWikiBackTarget backTarget) {
        openPage(player, ROOT_PAGE_ID, backTarget, backTarget);
    }

    public void openPage(Player player, String pageId, CityWikiBackTarget backTarget) {
        openPage(player, pageId, backTarget, backTarget);
    }

    public void openChapter(Player player, CityWikiChapter chapter, CityWikiBackTarget backTarget) {
        openPage(player, mapChapterToPageId(chapter), backTarget, backTarget);
    }

    private void openPage(Player player, String pageId, CityWikiBackTarget originTarget, CityWikiBackTarget backTarget) {
        if (player == null) {
            return;
        }
        WikiPageSpec page = loadPage(normalizePageId(pageId));
        Dialog dialog = buildDialog(page, originTarget == null ? CityWikiBackTarget.CLOSE : originTarget,
                backTarget == null ? CityWikiBackTarget.CLOSE : backTarget);
        DialogViewUtils.showDialog(
                plugin,
                player,
                dialog,
                cfg.msg("city.wiki.errors.unavailable", "<red>Wiki non disponibile su questo server.</red>"),
                "city-wiki"
        );
    }

    private Dialog buildDialog(WikiPageSpec page, CityWikiBackTarget originTarget, CityWikiBackTarget backTarget) {
        DialogBase base = DialogBase.builder(mm(page.title()))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(renderBody(page).stream().map(DialogBody::plainMessage).toList())
                .build();

        List<ActionButton> buttons = new ArrayList<>();
        for (WikiButtonSpec button : page.buttons()) {
            buttons.add(createActionButton(mm(button.label()), audience -> {
                if (!(audience instanceof Player actor)) {
                    return;
                }
                if ("action".equals(button.type())) {
                    handleAction(actor, button.target());
                    return;
                }
                openPage(actor, button.target(), originTarget, CityWikiBackTarget.ROOT);
            }));
        }
        buttons.add(createActionButton(
                cfg.msg("city.wiki.common.buttons.back", "<yellow>Indietro</yellow>"),
                audience -> {
                    if (audience instanceof Player actor) {
                        handleBack(actor, originTarget, backTarget);
                    }
                }
        ));
        buttons.add(createActionButton(
                cfg.msg("city.wiki.common.buttons.index", "<gold>Indice</gold>"),
                audience -> {
                    if (audience instanceof Player actor) {
                        openRoot(actor, originTarget);
                    }
                }
        ));
        ActionButton closeButton = createActionButton(
                cfg.msg("city.wiki.common.buttons.close", "<red>Chiudi</red>"),
                audience -> { }
        );
        return Dialog.create(factory -> factory.empty().base(base).type(DialogType.multiAction(buttons, closeButton, 2)));
    }

    private List<Component> renderBody(WikiPageSpec page) {
        List<Component> lines = new ArrayList<>();
        addIfPresent(lines, gray(page.subtitle()));
        addSection(lines, "city.wiki.common.headers.when_to_use", "<yellow><bold>Quando usarlo</bold></yellow>", page.whenToUse(), this::white);
        addListSection(lines, "city.wiki.common.headers.requirements", "<yellow><bold>Cosa serve</bold></yellow>", page.requirements(), this::bulletInfo);
        if (!page.steps().isEmpty()) {
            addSpacer(lines);
            lines.add(cfg.msg("city.wiki.common.headers.steps", "<yellow><bold>Come si fa</bold></yellow>"));
            for (int i = 0; i < page.steps().size(); i++) {
                lines.add(Component.text((i + 1) + ".", NamedTextColor.AQUA, TextDecoration.BOLD)
                        .append(Component.text(" ", NamedTextColor.WHITE))
                        .append(Component.text(page.steps().get(i), NamedTextColor.WHITE)));
            }
        }
        addSection(lines, "city.wiki.common.headers.result", "<green><bold>Risultato</bold></green>", page.result(),
                value -> Component.text("Risultato: ", NamedTextColor.GREEN, TextDecoration.BOLD)
                        .append(Component.text(value, NamedTextColor.WHITE)));
        addListSection(lines, "city.wiki.common.headers.warnings", "<red><bold>Attenzione</bold></red>", page.warnings(), this::warning);
        if (!page.commands().isEmpty()) {
            addSpacer(lines);
            lines.add(cfg.msg("city.wiki.common.headers.commands", "<aqua><bold>Comandi utili</bold></aqua>"));
            for (String command : page.commands()) {
                lines.add(renderCommand(command));
            }
        }
        return lines;
    }

    private void addSection(List<Component> lines, String headerPath, String headerFallback, String value, java.util.function.Function<String, Component> renderer) {
        if (isBlank(value)) {
            return;
        }
        addSpacer(lines);
        lines.add(cfg.msg(headerPath, headerFallback));
        lines.add(renderer.apply(value));
    }

    private void addListSection(
            List<Component> lines,
            String headerPath,
            String headerFallback,
            List<String> values,
            java.util.function.Function<String, Component> renderer
    ) {
        if (values == null || values.isEmpty()) {
            return;
        }
        addSpacer(lines);
        lines.add(cfg.msg(headerPath, headerFallback));
        for (String value : values) {
            if (!isBlank(value)) {
                lines.add(renderer.apply(value));
            }
        }
    }

    private Component renderCommand(String raw) {
        if (isBlank(raw)) {
            return Component.empty();
        }
        int separator = raw.indexOf(" - ");
        if (separator <= 0) {
            return Component.text(raw, NamedTextColor.AQUA, TextDecoration.BOLD);
        }
        String command = raw.substring(0, separator).trim();
        String description = raw.substring(separator + 3).trim();
        return Component.text(command, NamedTextColor.AQUA, TextDecoration.BOLD)
                .append(Component.text(" - " + description, NamedTextColor.GRAY));
    }

    private Component bulletInfo(String value) {
        return Component.text("• ", NamedTextColor.GRAY).append(Component.text(value, NamedTextColor.WHITE));
    }

    private Component warning(String value) {
        return Component.text("! ", NamedTextColor.RED, TextDecoration.BOLD)
                .append(Component.text(value, NamedTextColor.WHITE));
    }

    private Component gray(String value) {
        return isBlank(value) ? null : Component.text(value, NamedTextColor.GRAY);
    }

    private Component white(String value) {
        return isBlank(value) ? null : Component.text(value, NamedTextColor.WHITE);
    }

    private void addIfPresent(List<Component> lines, Component component) {
        if (component != null) {
            lines.add(component);
        }
    }

    private void addSpacer(List<Component> lines) {
        if (!lines.isEmpty()) {
            lines.add(Component.text(" "));
        }
    }

    private WikiPageSpec loadPage(String pageId) {
        String basePath = "city.wiki.pages." + pageId;
        String title = cfg.missingKeyFallback(basePath + ".title", missingPage().title());
        String subtitle = cfg.cfgString(basePath + ".subtitle", "");
        String whenToUse = cfg.cfgString(basePath + ".when_to_use", "");
        List<String> requirements = stringList(basePath + ".requirements");
        List<String> steps = stringList(basePath + ".steps");
        String result = cfg.cfgString(basePath + ".result", "");
        List<String> warnings = stringList(basePath + ".warnings");
        List<String> commands = stringList(basePath + ".commands");
        List<WikiButtonSpec> buttons = buttons(basePath + ".buttons");
        if (isBlank(title) || (isBlank(subtitle) && isBlank(whenToUse) && requirements.isEmpty() && steps.isEmpty()
                && isBlank(result) && warnings.isEmpty() && commands.isEmpty() && buttons.isEmpty())) {
            return missingPage();
        }
        return new WikiPageSpec(pageId, title, subtitle, whenToUse, requirements, steps, result, warnings, commands, buttons);
    }

    private List<String> stringList(String path) {
        List<String> values = plugin.getConfig().getStringList(path);
        return values == null ? List.of() : values.stream().filter(value -> value != null && !value.isBlank()).toList();
    }

    private List<WikiButtonSpec> buttons(String path) {
        List<Map<?, ?>> rawButtons = plugin.getConfig().getMapList(path);
        if (rawButtons == null || rawButtons.isEmpty()) {
            return List.of();
        }
        List<WikiButtonSpec> buttons = new ArrayList<>();
        for (Map<?, ?> raw : rawButtons) {
            String type = normalizeType(stringValue(raw.get("type")));
            String target = normalizeTarget(stringValue(raw.get("target")));
            String label = stringValue(raw.get("label"));
            if (isBlank(type) || isBlank(target) || isBlank(label)) {
                continue;
            }
            buttons.add(new WikiButtonSpec(type, target, label));
        }
        return buttons;
    }

    private String normalizeType(String raw) {
        if (isBlank(raw)) {
            return "page";
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("action") ? "action" : "page";
    }

    private String normalizeTarget(String raw) {
        if (isBlank(raw)) {
            return "";
        }
        String normalized = raw.trim();
        if (normalized.startsWith("page:")) {
            return normalizePageId(normalized.substring("page:".length()));
        }
        if (normalized.startsWith("action:")) {
            return normalized.substring("action:".length()).trim().toLowerCase(Locale.ROOT);
        }
        return normalized.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizePageId(String pageId) {
        return isBlank(pageId) ? ROOT_PAGE_ID : pageId.trim().toLowerCase(Locale.ROOT);
    }

    private void handleAction(Player player, String target) {
        switch (normalizeTarget(target)) {
            case "hub" -> hubOpener.accept(player);
            case "bank" -> bankOpener.accept(player);
            case "claim_settings" -> claimSettingsOpener.accept(player);
            case "territories" -> territoriesOpener.accept(player);
            case "defense" -> defenseOpener.accept(player);
            default -> openRoot(player, CityWikiBackTarget.CLOSE);
        }
    }

    private void handleBack(Player player, CityWikiBackTarget originTarget, CityWikiBackTarget backTarget) {
        switch (backTarget) {
            case ROOT -> openRoot(player, originTarget);
            case HUB -> hubOpener.accept(player);
            case BANK -> bankOpener.accept(player);
            case CLAIM_SETTINGS -> claimSettingsOpener.accept(player);
            case TERRITORIES -> territoriesOpener.accept(player);
            case DEFENSE -> defenseOpener.accept(player);
            case CLOSE -> {
            }
        }
    }

    private String mapChapterToPageId(CityWikiChapter chapter) {
        if (chapter == null) {
            return ROOT_PAGE_ID;
        }
        return switch (chapter) {
            case HOME -> ROOT_PAGE_ID;
            case CITY_HUB -> "open_city_hub";
            case MEMBERS_ROLES -> "manage_members";
            case TERRITORIES -> "claim_territory";
            case BANK_VAULT -> "use_bank";
            case LEVELS_GROWTH -> "level_up_city";
            case DIPLOMACY -> "manage_relations";
            case CHALLENGES -> "basic_commands";
            case DEFENSE -> "start_invasion";
            case DISCORD_LINK -> "link_discord";
            case BASIC_COMMANDS -> "basic_commands";
        };
    }

    private ActionButton createActionButton(Component label, Consumer<Object> clickConsumer) {
        return ActionButton.create(
                Objects.requireNonNullElse(label, Component.empty()),
                null,
                180,
                DialogAction.customClick((response, audience) -> clickConsumer.accept(audience), CLICK_OPTIONS)
        );
    }

    private Consumer<Player> safeConsumer(Consumer<Player> consumer) {
        return consumer == null ? player -> { } : consumer;
    }

    private WikiPageSpec missingPage() {
        return new WikiPageSpec(
                "home",
                "<gold><bold>Wiki citta</bold></gold>",
                "<gray>Questa pagina non e disponibile su questo server.</gray>",
                "Apri una pagina principale della wiki per continuare.",
                List.of(),
                List.of("Torna all'indice.", "Apri uno dei task principali.", "Se il problema continua, avvisa lo staff."),
                "Tornerai a una pagina valida della wiki.",
                List.of(),
                List.of(),
                List.of()
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Component mm(String value) {
        return MiniMessageHelper.parse(value == null ? "" : value);
    }

    private record WikiPageSpec(
            String pageId,
            String title,
            String subtitle,
            String whenToUse,
            List<String> requirements,
            List<String> steps,
            String result,
            List<String> warnings,
            List<String> commands,
            List<WikiButtonSpec> buttons
    ) {
    }

    private record WikiButtonSpec(String type, String target, String label) {
    }
}
