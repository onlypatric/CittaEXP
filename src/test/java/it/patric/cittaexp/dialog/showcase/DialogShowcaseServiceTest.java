package it.patric.cittaexp.dialog.showcase;

import dev.patric.commonlib.api.dialog.CustomActionSpec;
import dev.patric.commonlib.api.dialog.DialogAfterAction;
import dev.patric.commonlib.api.dialog.DialogBaseSpec;
import dev.patric.commonlib.api.dialog.DialogButtonSpec;
import dev.patric.commonlib.api.dialog.DialogOpenRequest;
import dev.patric.commonlib.api.dialog.DialogResponse;
import dev.patric.commonlib.api.dialog.DialogService;
import dev.patric.commonlib.api.dialog.DialogSession;
import dev.patric.commonlib.api.dialog.DialogSessionStatus;
import dev.patric.commonlib.api.dialog.DialogSubmission;
import dev.patric.commonlib.api.dialog.DialogTemplate;
import dev.patric.commonlib.api.dialog.DialogTemplateRegistry;
import dev.patric.commonlib.api.dialog.NoticeTypeSpec;
import dev.patric.commonlib.api.dialog.PlainMessageBodySpec;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DialogShowcaseServiceTest {

    @Test
    void openReturnsTemplateNotFoundWhenRegistryMissesKey() {
        DialogService dialogService = mock(DialogService.class);
        DialogTemplateRegistry registry = mock(DialogTemplateRegistry.class);
        when(registry.find(DialogShowcaseKey.NOTICE.templateKey())).thenReturn(Optional.empty());

        DialogShowcaseService service = new DialogShowcaseService(dialogService, registry, Logger.getAnonymousLogger());
        Player player = mock(Player.class);

        DialogShowcaseService.OpenResult result = service.open(player, DialogShowcaseKey.NOTICE);

        assertEquals(DialogShowcaseService.OpenResult.TEMPLATE_NOT_FOUND, result);
    }

    @Test
    void submitCallbackSendsPreviewSafeFeedbackToPlayer() {
        DialogService dialogService = mock(DialogService.class);
        DialogTemplateRegistry registry = mock(DialogTemplateRegistry.class);
        DialogTemplate template = noticeTemplate(DialogShowcaseKey.NOTICE.templateKey());
        when(registry.find(DialogShowcaseKey.NOTICE.templateKey())).thenReturn(Optional.of(template));

        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);
        when(dialogService.open(any())).thenReturn(new DialogSession(
                UUID.randomUUID(),
                playerId,
                template.templateKey(),
                DialogSessionStatus.OPEN,
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                0L
        ));

        DialogShowcaseService service = new DialogShowcaseService(dialogService, registry, Logger.getAnonymousLogger());
        DialogShowcaseService.OpenResult result = service.open(player, DialogShowcaseKey.NOTICE);
        assertEquals(DialogShowcaseService.OpenResult.OPENED, result);

        ArgumentCaptor<DialogOpenRequest> requestCaptor = ArgumentCaptor.forClass(DialogOpenRequest.class);
        verify(dialogService).open(requestCaptor.capture());
        DialogOpenRequest request = requestCaptor.getValue();

        DialogResponse response = new DialogResponse() {
            @Override
            public Optional<String> text(String key) {
                return Optional.empty();
            }

            @Override
            public Optional<Boolean> bool(String key) {
                return Optional.empty();
            }

            @Override
            public Optional<Float> number(String key) {
                return Optional.empty();
            }

            @Override
            public String rawPayload() {
                return "{}";
            }

            @Override
            public Map<String, Object> asMap() {
                return Map.of("decision", "confirm", "tier", "regno");
            }
        };

        request.callbacks().onSubmit(
                new DialogSession(
                        UUID.randomUUID(),
                        playerId,
                        template.templateKey(),
                        DialogSessionStatus.OPEN,
                        System.currentTimeMillis(),
                        System.currentTimeMillis(),
                        0L
                ),
                new DialogSubmission(
                        UUID.randomUUID(),
                        playerId,
                        "submit",
                        response,
                        System.currentTimeMillis()
                )
        );

        verify(player).sendMessage(org.mockito.ArgumentMatchers.contains("Dialog submit notice"));
        verify(player).sendMessage(org.mockito.ArgumentMatchers.contains("decision=confirm"));
        verify(player).sendMessage(org.mockito.ArgumentMatchers.contains("tier=regno"));
    }

    @Test
    void openReturnsFailedWhenBackendReturnsErrorStatus() {
        DialogService dialogService = mock(DialogService.class);
        DialogTemplateRegistry registry = mock(DialogTemplateRegistry.class);
        DialogTemplate template = noticeTemplate(DialogShowcaseKey.NOTICE.templateKey());
        when(registry.find(DialogShowcaseKey.NOTICE.templateKey())).thenReturn(Optional.of(template));

        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(dialogService.open(any())).thenReturn(new DialogSession(
                UUID.randomUUID(),
                playerId,
                template.templateKey(),
                DialogSessionStatus.ERROR,
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                0L
        ));

        DialogShowcaseService service = new DialogShowcaseService(dialogService, registry, Logger.getAnonymousLogger());
        DialogShowcaseService.OpenResult result = service.open(player, DialogShowcaseKey.NOTICE);
        assertTrue(result == DialogShowcaseService.OpenResult.OPEN_FAILED);
    }

    private static DialogTemplate noticeTemplate(String key) {
        return new DialogTemplate(
                key,
                new DialogBaseSpec(
                        Component.text("Showcase"),
                        null,
                        true,
                        false,
                        DialogAfterAction.WAIT_FOR_RESPONSE,
                        java.util.List.of(new PlainMessageBodySpec(Component.text("Body"), 120)),
                        java.util.List.of()
                ),
                new NoticeTypeSpec(
                        new DialogButtonSpec(
                                Component.text("Conferma"),
                                null,
                                120,
                                new CustomActionSpec("submit", Map.of())
                        )
                ),
                Map.of("group", "showcase")
        );
    }
}
