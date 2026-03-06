package it.patric.cittaexp.ui.framework;

import dev.patric.commonlib.api.gui.GuiDefinition;
import dev.patric.commonlib.api.gui.GuiDefinitionRegistry;
import dev.patric.commonlib.api.gui.GuiOpenOptions;
import dev.patric.commonlib.api.gui.GuiSession;
import dev.patric.commonlib.api.gui.GuiSessionService;
import dev.patric.commonlib.api.gui.GuiSessionStatus;
import it.patric.cittaexp.preview.PreviewScenario;
import it.patric.cittaexp.preview.ThemeMode;
import it.patric.cittaexp.ui.contract.UiScreenKey;
import it.patric.cittaexp.ui.contract.UiViewState;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Player;

public final class GuiFlowOrchestrator {

    public enum OpenResult {
        OPENED,
        GUI_UNAVAILABLE,
        SCREEN_NOT_FOUND,
        OPEN_FAILED
    }

    private final GuiDefinitionRegistry definitionRegistry;
    private final GuiSessionService guiSessionService;
    private final GuiCatalog guiCatalog;
    private final GuiStateComposer stateComposer;

    public GuiFlowOrchestrator(
            GuiDefinitionRegistry definitionRegistry,
            GuiSessionService guiSessionService,
            GuiCatalog guiCatalog,
            GuiStateComposer stateComposer
    ) {
        this.definitionRegistry = Objects.requireNonNull(definitionRegistry, "definitionRegistry");
        this.guiSessionService = Objects.requireNonNull(guiSessionService, "guiSessionService");
        this.guiCatalog = Objects.requireNonNull(guiCatalog, "guiCatalog");
        this.stateComposer = Objects.requireNonNull(stateComposer, "stateComposer");
    }

    public OpenResult open(
            Player player,
            UiScreenKey screenKey,
            PreviewScenario scenario,
            ThemeMode themeMode
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(screenKey, "screenKey");

        UiViewState state = stateComposer.compose(player.getUniqueId(), scenario, themeMode);
        if (!state.guiAvailable()) {
            return OpenResult.GUI_UNAVAILABLE;
        }

        Map<UiScreenKey, GuiDefinition> definitions = guiCatalog.build(state);
        definitions.values().forEach(definitionRegistry::register);

        GuiDefinition target = definitions.get(screenKey);
        if (target == null) {
            return OpenResult.SCREEN_NOT_FOUND;
        }

        GuiSession session = guiSessionService.open(target, player.getUniqueId(), GuiOpenOptions.defaults());
        return session.status() == GuiSessionStatus.OPEN ? OpenResult.OPENED : OpenResult.OPEN_FAILED;
    }

    public UiViewState composeState(UUID viewerId, PreviewScenario scenario, ThemeMode themeMode) {
        return stateComposer.compose(viewerId, scenario, themeMode);
    }

    public Optional<GuiSession> activeSession(UUID playerId) {
        return guiSessionService.activeByPlayer(playerId).stream().findFirst();
    }
}
