package it.patric.cittaexp.dialog.showcase;

import dev.patric.commonlib.api.dialog.DialogCallbacks;
import dev.patric.commonlib.api.dialog.DialogOpenRequest;
import dev.patric.commonlib.api.dialog.DialogService;
import dev.patric.commonlib.api.dialog.DialogSession;
import dev.patric.commonlib.api.dialog.DialogSessionStatus;
import dev.patric.commonlib.api.dialog.DialogSubmission;
import dev.patric.commonlib.api.dialog.DialogTemplate;
import dev.patric.commonlib.api.dialog.DialogTemplateRegistry;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.bukkit.entity.Player;

public final class DialogShowcaseService {

    public enum OpenResult {
        OPENED,
        TEMPLATE_NOT_FOUND,
        OPEN_FAILED
    }

    private final DialogService dialogService;
    private final DialogTemplateRegistry dialogTemplateRegistry;
    private final Logger logger;

    public DialogShowcaseService(
            DialogService dialogService,
            DialogTemplateRegistry dialogTemplateRegistry,
            Logger logger
    ) {
        this.dialogService = Objects.requireNonNull(dialogService, "dialogService");
        this.dialogTemplateRegistry = Objects.requireNonNull(dialogTemplateRegistry, "dialogTemplateRegistry");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public OpenResult open(Player player, DialogShowcaseKey showcaseKey) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(showcaseKey, "showcaseKey");

        DialogTemplate template = dialogTemplateRegistry.find(showcaseKey.templateKey()).orElse(null);
        if (template == null) {
            return OpenResult.TEMPLATE_NOT_FOUND;
        }

        try {
            DialogSession session = dialogService.open(new DialogOpenRequest(
                    player.getUniqueId(),
                    template,
                    0L,
                    Locale.ITALIAN,
                    Map.of("showcase", showcaseKey.alias()),
                    new DialogCallbacks() {
                        @Override
                        public void onSubmit(DialogSession session, DialogSubmission submission) {
                            sendPreviewSubmitFeedback(player, showcaseKey, session.sessionId(), submission);
                        }
                    }
            ));
            if (session.status() == DialogSessionStatus.ERROR) {
                return OpenResult.OPEN_FAILED;
            }
            return OpenResult.OPENED;
        } catch (RuntimeException ex) {
            logger.log(Level.SEVERE, "Dialog showcase open failed: " + showcaseKey.alias(), ex);
            return OpenResult.OPEN_FAILED;
        }
    }

    private static void sendPreviewSubmitFeedback(
            Player player,
            DialogShowcaseKey showcaseKey,
            UUID sessionId,
            DialogSubmission submission
    ) {
        if (!player.isOnline()) {
            return;
        }
        player.sendMessage(
                "[CittaEXP] Dialog submit "
                        + showcaseKey.alias()
                        + " session="
                        + sessionId
                        + " action="
                        + submission.actionId()
                        + " payload="
                        + normalizePayload(submission.response().asMap())
        );
    }

    private static String normalizePayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return "{}";
        }
        Map<String, Object> ordered = new TreeMap<>(payload);
        return ordered.entrySet()
                .stream()
                .map(entry -> entry.getKey() + "=" + String.valueOf(entry.getValue()))
                .collect(Collectors.joining(", ", "{", "}"));
    }
}
