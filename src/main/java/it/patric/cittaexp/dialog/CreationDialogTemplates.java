package it.patric.cittaexp.dialog;

import dev.patric.commonlib.api.dialog.CustomActionSpec;
import dev.patric.commonlib.api.dialog.DialogAfterAction;
import dev.patric.commonlib.api.dialog.DialogBaseSpec;
import dev.patric.commonlib.api.dialog.DialogButtonSpec;
import dev.patric.commonlib.api.dialog.DialogTemplate;
import dev.patric.commonlib.api.dialog.DialogTemplateRegistry;
import dev.patric.commonlib.api.dialog.NoticeTypeSpec;
import dev.patric.commonlib.api.dialog.PlainMessageBodySpec;
import dev.patric.commonlib.api.dialog.TextInputSpec;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;

public final class CreationDialogTemplates {

    public static final String TEMPLATE_CITY_NAME = "cittaexp.dialog.creation.city_name";
    public static final String TEMPLATE_CITY_TAG = "cittaexp.dialog.creation.city_tag";

    public void register(DialogTemplateRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        registry.register(cityNameTemplate());
        registry.register(cityTagTemplate());
    }

    private DialogTemplate cityNameTemplate() {
        return new DialogTemplate(
                TEMPLATE_CITY_NAME,
                new DialogBaseSpec(
                        Component.text("CittaEXP - City Name"),
                        null,
                        true,
                        false,
                        DialogAfterAction.WAIT_FOR_RESPONSE,
                        List.of(new PlainMessageBodySpec(Component.text("Insert the city name used in preview wizard."), 240)),
                        List.of(new TextInputSpec("city_name", 200, Component.text("City name"), true, "", 24, null, null))
                ),
                new NoticeTypeSpec(new DialogButtonSpec(
                        Component.text("Confirm"),
                        null,
                        120,
                        new CustomActionSpec("submit", Map.of())
                )),
                Map.of("group", "wizard")
        );
    }

    private DialogTemplate cityTagTemplate() {
        return new DialogTemplate(
                TEMPLATE_CITY_TAG,
                new DialogBaseSpec(
                        Component.text("CittaEXP - City Tag"),
                        null,
                        true,
                        false,
                        DialogAfterAction.WAIT_FOR_RESPONSE,
                        List.of(new PlainMessageBodySpec(Component.text("Insert a 3-letter city tag for preview."), 240)),
                        List.of(new TextInputSpec("city_tag", 120, Component.text("City tag"), true, "", 3, null, null))
                ),
                new NoticeTypeSpec(new DialogButtonSpec(
                        Component.text("Confirm"),
                        null,
                        120,
                        new CustomActionSpec("submit", Map.of())
                )),
                Map.of("group", "wizard")
        );
    }
}
