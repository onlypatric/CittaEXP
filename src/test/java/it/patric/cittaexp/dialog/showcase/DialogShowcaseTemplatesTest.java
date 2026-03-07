package it.patric.cittaexp.dialog.showcase;

import dev.patric.commonlib.api.dialog.DialogListTypeSpec;
import dev.patric.commonlib.api.dialog.DialogTemplate;
import dev.patric.commonlib.api.dialog.DialogTemplateRegistry;
import dev.patric.commonlib.runtime.DefaultDialogTemplateRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogShowcaseTemplatesTest {

    @Test
    void allShowcaseTemplatesAreRegisteredAndResolvable() {
        DialogTemplateRegistry registry = new DefaultDialogTemplateRegistry();
        new DialogShowcaseTemplates().register(registry);

        for (DialogShowcaseKey key : DialogShowcaseKey.values()) {
            assertTrue(registry.find(key.templateKey()).isPresent(), "missing template: " + key.templateKey());
        }
        assertTrue(registry.find(DialogShowcaseTemplates.TEMPLATE_LIST_STEP_1).isPresent());
        assertTrue(registry.find(DialogShowcaseTemplates.TEMPLATE_LIST_STEP_2).isPresent());
    }

    @Test
    void dialogListRootReferencesExistingChildren() {
        DialogTemplateRegistry registry = new DefaultDialogTemplateRegistry();
        new DialogShowcaseTemplates().register(registry);

        DialogTemplate root = registry.find(DialogShowcaseKey.LIST.templateKey()).orElseThrow();
        DialogListTypeSpec listType = assertInstanceOf(DialogListTypeSpec.class, root.type());

        for (String childKey : listType.dialogTemplateKeys()) {
            assertTrue(registry.find(childKey).isPresent(), "missing list child template: " + childKey);
        }
    }
}
