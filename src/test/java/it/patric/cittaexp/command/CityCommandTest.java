package it.patric.cittaexp.command;

import dev.patric.commonlib.api.MessageService;
import it.patric.cittaexp.core.model.City;
import it.patric.cittaexp.core.model.CityStatus;
import it.patric.cittaexp.core.model.CityTier;
import it.patric.cittaexp.core.runtime.DefaultCityLifecycleService;
import java.util.concurrent.CompletableFuture;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mockStatic;

class CityCommandTest {

    @Test
    void commandDeniesSenderWithoutPermission() {
        DefaultCityLifecycleService lifecycleService = mock(DefaultCityLifecycleService.class);
        MessageService messageService = messageService();
        CityCommand command = new CityCommand(mock(Plugin.class), lifecycleService, messageService);

        Player sender = mock(Player.class);
        when(sender.hasPermission("cittaexp.city.player")).thenReturn(false);

        command.onCommand(sender, mock(Command.class), "city", new String[]{"info"});

        verify(messageService).render(anyString(), anyMap(), any(Locale.class));
    }

    @Test
    void freezeStatusReadsLifecycleState() {
        DefaultCityLifecycleService lifecycleService = mock(DefaultCityLifecycleService.class);
        MessageService messageService = messageService();
        CityCommand command = new CityCommand(mock(Plugin.class), lifecycleService, messageService);

        UUID playerUuid = UUID.randomUUID();
        City city = new City(
                UUID.randomUUID(),
                "Aurora",
                "AUR",
                playerUuid,
                CityTier.BORGO,
                CityStatus.ACTIVE,
                false,
                0L,
                1,
                10,
                0,
                System.currentTimeMillis(),
                System.currentTimeMillis()
        );

        Player sender = mock(Player.class);
        when(sender.hasPermission("cittaexp.city.player")).thenReturn(true);
        when(sender.getUniqueId()).thenReturn(playerUuid);
        when(lifecycleService.cityByPlayer(playerUuid)).thenReturn(Optional.of(city));
        when(lifecycleService.isCityFrozen(city.cityId())).thenReturn(true);

        command.onCommand(sender, mock(Command.class), "city", new String[]{"freeze", "status"});

        verify(messageService).render(anyString(), anyMap(), any(Locale.class));
    }

    @Test
    void moderationPathRequiresModerationPermission() {
        DefaultCityLifecycleService lifecycleService = mock(DefaultCityLifecycleService.class);
        MessageService messageService = messageService();
        CityCommand command = new CityCommand(mock(Plugin.class), lifecycleService, messageService);

        Player sender = mock(Player.class);
        when(sender.hasPermission("cittaexp.city.player")).thenReturn(true);
        when(sender.hasPermission("cittaexp.city.moderation")).thenReturn(false);

        command.onCommand(sender, mock(Command.class), "city", new String[]{"invite", "TargetPlayer"});

        verify(messageService).render(anyString(), anyMap(), any(Locale.class));
        verify(lifecycleService, never()).invite(any(), any(), any());
    }

    @Test
    void createCommandTriggersAsyncLifecycleCall() {
        DefaultCityLifecycleService lifecycleService = mock(DefaultCityLifecycleService.class);
        MessageService messageService = messageService();
        CityCommand command = new CityCommand(mock(Plugin.class), lifecycleService, messageService);

        Player sender = mock(Player.class);
        when(sender.hasPermission("cittaexp.city.player")).thenReturn(true);
        when(sender.getUniqueId()).thenReturn(UUID.randomUUID());
        when(sender.getLocation()).thenReturn(new org.bukkit.Location(null, 0, 64, 0));

        City created = new City(
                UUID.randomUUID(),
                "Aurora",
                "AUR",
                UUID.randomUUID(),
                CityTier.BORGO,
                CityStatus.ACTIVE,
                false,
                0L,
                1,
                10,
                0,
                System.currentTimeMillis(),
                System.currentTimeMillis()
        );
        when(lifecycleService.createCityAsync(any())).thenReturn(CompletableFuture.completedFuture(created));

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTask(any(Plugin.class), any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(1, Runnable.class);
            runnable.run();
            return mock(BukkitTask.class);
        });

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            command.onCommand(sender, mock(Command.class), "city", new String[]{"create", "Aurora", "AUR"});
        }

        verify(lifecycleService).createCityAsync(any());
    }

    private static MessageService messageService() {
        MessageService service = mock(MessageService.class);
        when(service.render(anyString(), anyMap(), any(Locale.class)))
                .thenAnswer(invocation -> Component.text(invocation.getArgument(0, String.class)));
        when(service.render(anyString(), any(Locale.class)))
                .thenAnswer(invocation -> Component.text(invocation.getArgument(0, String.class)));
        when(service.render(any())).thenAnswer(invocation -> Component.text("message"));
        return service;
    }
}
