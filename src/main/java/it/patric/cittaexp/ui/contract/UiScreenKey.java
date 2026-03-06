package it.patric.cittaexp.ui.contract;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum UiScreenKey {
    DASHBOARD("city.main.dashboard", "dashboard"),
    MEMBERS("city.members.list", "members"),
    ROLES("city.roles.manage", "roles"),
    TAXES("city.taxes.status", "taxes"),
    WIZARD_START("city.creation.wizard.start", "wizard"),
    WIZARD_BANNER_ARMOR("city.creation.wizard.banner_armor", "wizard-armor"),
    WIZARD_CONFIRM("city.creation.wizard.confirm", "wizard-confirm");

    private final String key;
    private final String commandToken;

    UiScreenKey(String key, String commandToken) {
        this.key = key;
        this.commandToken = commandToken;
    }

    public String key() {
        return key;
    }

    public String commandToken() {
        return commandToken;
    }

    public static Optional<UiScreenKey> fromCommandToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(value -> value.commandToken.equals(normalized))
                .findFirst();
    }
}
