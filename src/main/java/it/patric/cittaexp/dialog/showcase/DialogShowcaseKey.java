package it.patric.cittaexp.dialog.showcase;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum DialogShowcaseKey {
    NOTICE("notice", "cittaexp.dialog.showcase.notice"),
    CONFIRM("confirm", "cittaexp.dialog.showcase.confirm"),
    MULTI("multi", "cittaexp.dialog.showcase.multi"),
    FORM("form", "cittaexp.dialog.showcase.form"),
    LIST("list", "cittaexp.dialog.showcase.list.root"),
    LINKS("links", "cittaexp.dialog.showcase.links");

    private final String alias;
    private final String templateKey;

    DialogShowcaseKey(String alias, String templateKey) {
        this.alias = alias;
        this.templateKey = templateKey;
    }

    public String alias() {
        return alias;
    }

    public String templateKey() {
        return templateKey;
    }

    public static Optional<DialogShowcaseKey> parse(String token) {
        if (token == null) {
            return Optional.empty();
        }
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(value -> value.alias.equals(normalized))
                .findFirst();
    }

    public static List<String> aliases() {
        return Arrays.stream(values()).map(DialogShowcaseKey::alias).toList();
    }
}
