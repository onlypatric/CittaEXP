package it.patric.cittaexp.dialog.showcase;

import dev.patric.commonlib.api.dialog.BooleanInputSpec;
import dev.patric.commonlib.api.dialog.ConfirmationTypeSpec;
import dev.patric.commonlib.api.dialog.CustomActionSpec;
import dev.patric.commonlib.api.dialog.DialogAfterAction;
import dev.patric.commonlib.api.dialog.DialogBaseSpec;
import dev.patric.commonlib.api.dialog.DialogButtonSpec;
import dev.patric.commonlib.api.dialog.DialogListTypeSpec;
import dev.patric.commonlib.api.dialog.DialogTemplate;
import dev.patric.commonlib.api.dialog.DialogTemplateRegistry;
import dev.patric.commonlib.api.dialog.MultiActionTypeSpec;
import dev.patric.commonlib.api.dialog.NoticeTypeSpec;
import dev.patric.commonlib.api.dialog.NumberRangeInputSpec;
import dev.patric.commonlib.api.dialog.PlainMessageBodySpec;
import dev.patric.commonlib.api.dialog.ServerLinksTypeSpec;
import dev.patric.commonlib.api.dialog.SingleOptionEntrySpec;
import dev.patric.commonlib.api.dialog.SingleOptionInputSpec;
import dev.patric.commonlib.api.dialog.TextInputSpec;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;

public final class DialogShowcaseTemplates {

    public static final String TEMPLATE_LIST_STEP_1 = "cittaexp.dialog.showcase.list.step1";
    public static final String TEMPLATE_LIST_STEP_2 = "cittaexp.dialog.showcase.list.step2";

    public void register(DialogTemplateRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        registry.register(noticeTemplate());
        registry.register(confirmTemplate());
        registry.register(multiTemplate());
        registry.register(formTemplate());
        registry.register(listStep1Template());
        registry.register(listStep2Template());
        registry.register(listRootTemplate());
        registry.register(linksTemplate());
    }

    private DialogTemplate noticeTemplate() {
        return new DialogTemplate(
                DialogShowcaseKey.NOTICE.templateKey(),
                new DialogBaseSpec(
                        Component.text("CittaEXP - Showcase Notice"),
                        null,
                        true,
                        false,
                        DialogAfterAction.WAIT_FOR_RESPONSE,
                        List.of(new PlainMessageBodySpec(
                                Component.text("Notice informativo: questo dialog serve solo per review UX."),
                                280
                        )),
                        List.of()
                ),
                new NoticeTypeSpec(button("Conferma", "submit", Map.of("showcase", "notice"))),
                metadata(DialogShowcaseKey.NOTICE)
        );
    }

    private DialogTemplate confirmTemplate() {
        return new DialogTemplate(
                DialogShowcaseKey.CONFIRM.templateKey(),
                new DialogBaseSpec(
                        Component.text("CittaEXP - Showcase Confirm"),
                        null,
                        true,
                        false,
                        DialogAfterAction.WAIT_FOR_RESPONSE,
                        List.of(new PlainMessageBodySpec(
                                Component.text("Conferma sensibile preview: nessuna azione reale verra eseguita."),
                                280
                        )),
                        List.of()
                ),
                new ConfirmationTypeSpec(
                        button("Conferma", "submit", Map.of("decision", "confirm")),
                        button("Annulla", "submit", Map.of("decision", "cancel"))
                ),
                metadata(DialogShowcaseKey.CONFIRM)
        );
    }

    private DialogTemplate multiTemplate() {
        return new DialogTemplate(
                DialogShowcaseKey.MULTI.templateKey(),
                new DialogBaseSpec(
                        Component.text("CittaEXP - Showcase Multi"),
                        null,
                        true,
                        false,
                        DialogAfterAction.WAIT_FOR_RESPONSE,
                        List.of(new PlainMessageBodySpec(
                                Component.text("Multi-action: scegli una CTA di anteprima per validare il layout."),
                                280
                        )),
                        List.of()
                ),
                new MultiActionTypeSpec(
                        List.of(
                                button("Apri Dashboard", "submit", Map.of("cta", "dashboard")),
                                button("Apri Members", "submit", Map.of("cta", "members")),
                                button("Apri Taxes", "submit", Map.of("cta", "taxes"))
                        ),
                        button("Chiudi", "submit", Map.of("cta", "close")),
                        2
                ),
                metadata(DialogShowcaseKey.MULTI)
        );
    }

    private DialogTemplate formTemplate() {
        return new DialogTemplate(
                DialogShowcaseKey.FORM.templateKey(),
                new DialogBaseSpec(
                        Component.text("CittaEXP - Showcase Form"),
                        null,
                        true,
                        false,
                        DialogAfterAction.WAIT_FOR_RESPONSE,
                        List.of(new PlainMessageBodySpec(
                                Component.text("Form composito preview: valori raccolti e mostrati solo in chat staff."),
                                300
                        )),
                        List.of(
                                new TextInputSpec(
                                        "nome_citta",
                                        220,
                                        Component.text("Nome citta"),
                                        true,
                                        "Aurora",
                                        24,
                                        null,
                                        null
                                ),
                                new BooleanInputSpec(
                                        "freeze_mode",
                                        Component.text("Freeze mode"),
                                        false,
                                        "true",
                                        "false"
                                ),
                                new NumberRangeInputSpec(
                                        "tasse_percento",
                                        200,
                                        Component.text("Tasse %"),
                                        "Tasse: %s%%",
                                        0f,
                                        100f,
                                        12f,
                                        1f
                                ),
                                new SingleOptionInputSpec(
                                        "tier",
                                        180,
                                        Component.text("Tier citta"),
                                        true,
                                        List.of(
                                                new SingleOptionEntrySpec("borgo", Component.text("Borgo"), true),
                                                new SingleOptionEntrySpec("villaggio", Component.text("Villaggio"), false),
                                                new SingleOptionEntrySpec("regno", Component.text("Regno"), false)
                                        )
                                )
                        )
                ),
                new NoticeTypeSpec(button("Invia preview", "submit", Map.of("showcase", "form"))),
                metadata(DialogShowcaseKey.FORM)
        );
    }

    private DialogTemplate listStep1Template() {
        return new DialogTemplate(
                TEMPLATE_LIST_STEP_1,
                new DialogBaseSpec(
                        Component.text("CittaEXP - Showcase List Step 1"),
                        null,
                        true,
                        false,
                        DialogAfterAction.WAIT_FOR_RESPONSE,
                        List.of(new PlainMessageBodySpec(
                                Component.text("Step 1: schermata introduttiva del flusso DialogList."),
                                260
                        )),
                        List.of()
                ),
                new NoticeTypeSpec(button("Continua", "submit", Map.of("step", "1"))),
                Map.of("group", "showcase", "showcase", "list-step")
        );
    }

    private DialogTemplate listStep2Template() {
        return new DialogTemplate(
                TEMPLATE_LIST_STEP_2,
                new DialogBaseSpec(
                        Component.text("CittaEXP - Showcase List Step 2"),
                        null,
                        true,
                        false,
                        DialogAfterAction.WAIT_FOR_RESPONSE,
                        List.of(new PlainMessageBodySpec(
                                Component.text("Step 2: validazione finale del dialog list."),
                                260
                        )),
                        List.of()
                ),
                new NoticeTypeSpec(button("Conferma", "submit", Map.of("step", "2"))),
                Map.of("group", "showcase", "showcase", "list-step")
        );
    }

    private DialogTemplate listRootTemplate() {
        return new DialogTemplate(
                DialogShowcaseKey.LIST.templateKey(),
                new DialogBaseSpec(
                        Component.text("CittaEXP - Showcase List"),
                        null,
                        true,
                        false,
                        DialogAfterAction.WAIT_FOR_RESPONSE,
                        List.of(new PlainMessageBodySpec(
                                Component.text("DialogList root: naviga i template figlio in sequenza."),
                                280
                        )),
                        List.of()
                ),
                new DialogListTypeSpec(
                        List.of(TEMPLATE_LIST_STEP_1, TEMPLATE_LIST_STEP_2),
                        button("Esci", "submit", Map.of("list", "exit")),
                        1,
                        220
                ),
                metadata(DialogShowcaseKey.LIST)
        );
    }

    private DialogTemplate linksTemplate() {
        return new DialogTemplate(
                DialogShowcaseKey.LINKS.templateKey(),
                new DialogBaseSpec(
                        Component.text("CittaEXP - Showcase Links"),
                        null,
                        true,
                        false,
                        DialogAfterAction.WAIT_FOR_RESPONSE,
                        List.of(new PlainMessageBodySpec(
                                Component.text("ServerLinks: visualizzazione link server Paper con uscita preview-safe."),
                                300
                        )),
                        List.of()
                ),
                new ServerLinksTypeSpec(button("Chiudi", "submit", Map.of("showcase", "links")), 1, 220),
                metadata(DialogShowcaseKey.LINKS)
        );
    }

    private static DialogButtonSpec button(String label, String actionId, Map<String, String> additions) {
        return new DialogButtonSpec(
                Component.text(label),
                null,
                160,
                new CustomActionSpec(actionId, additions)
        );
    }

    private static Map<String, String> metadata(DialogShowcaseKey key) {
        return Map.of(
                "group", "showcase",
                "showcase", key.alias()
        );
    }
}
