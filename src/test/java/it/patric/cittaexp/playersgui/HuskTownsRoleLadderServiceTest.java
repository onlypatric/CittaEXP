package it.patric.cittaexp.playersgui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.OptionalInt;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class HuskTownsRoleLadderServiceTest {

    @Test
    void fallbackIsUsedWhenRolesFileIsMissing() {
        File missing = new File("build/tmp/tests/missing-roles-" + System.nanoTime() + ".yml");
        HuskTownsRoleLadderService service = new HuskTownsRoleLadderService(missing, Logger.getLogger("test"));

        HuskTownsRoleLadderService.RoleLadderSnapshot snapshot = service.snapshot();
        assertEquals("Mayor", snapshot.roleName(snapshot.mayorWeight()));
        assertEquals(OptionalInt.of(2), snapshot.lowerThan(3));
        assertEquals(OptionalInt.empty(), snapshot.higherThan(3));
    }

    @Test
    void rolesFileIsParsedInOrder() throws IOException {
        File dir = Files.createTempDirectory("cittaexp-roles-test").toFile();
        File roles = new File(dir, "roles.yml");
        String yaml = ""
                + "names:\n"
                + "  \"1\": \"Cittadino\"\n"
                + "  \"5\": \"Ufficiale\"\n"
                + "  \"9\": \"Capo\"\n"
                + "roles:\n"
                + "  \"1\": [\"deposit\"]\n"
                + "  \"5\": [\"invite\"]\n"
                + "  \"9\": [\"promote\"]\n";
        Files.writeString(roles.toPath(), yaml);

        HuskTownsRoleLadderService service = new HuskTownsRoleLadderService(roles, Logger.getLogger("test"));
        HuskTownsRoleLadderService.RoleLadderSnapshot snapshot = service.snapshot();

        assertEquals(9, snapshot.mayorWeight());
        assertEquals("Cittadino", snapshot.roleName(1));
        assertEquals("Ufficiale", snapshot.roleName(5));
        assertEquals(OptionalInt.of(5), snapshot.higherThan(1));
        assertEquals(OptionalInt.of(5), snapshot.lowerThan(9));
        assertTrue(roles.delete() || !roles.exists());
        assertTrue(dir.delete() || !dir.exists());
    }
}
