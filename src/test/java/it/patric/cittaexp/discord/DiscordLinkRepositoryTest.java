package it.patric.cittaexp.discord;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DiscordLinkRepositoryTest {

    @Test
    void storesAndConsumesCodesAndLinks() throws Exception {
        Path tempDir = Files.createTempDirectory("discord-link-repo-test");
        DiscordLinkRepository repository = new DiscordLinkRepository(tempDir.resolve("discord.db"));
        repository.initialize();

        UUID playerId = UUID.randomUUID();
        Instant now = Instant.now();
        repository.createOrReplaceLinkCode(playerId, "ABCDE-FGHI", now, now.plusSeconds(600));
        Optional<DiscordLinkRepository.LinkCodeRecord> code = repository.consumeLinkCode("ABCDE-FGHI", now.plusSeconds(1));
        Assertions.assertTrue(code.isPresent());
        Assertions.assertEquals(playerId, code.get().minecraftUuid());

        repository.upsertPlayerLink(playerId, 123456789L, "user", "Display", now, now);
        Optional<DiscordLinkRepository.PlayerDiscordLink> link = repository.findLinkByMinecraft(playerId);
        Assertions.assertTrue(link.isPresent());
        Assertions.assertEquals(123456789L, link.get().discordUserId());
    }
}
