package it.patric.cittaexp.manageclaim;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import it.patric.cittaexp.utils.CommandGuards;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;

public final class SectionCommand {

    private SectionCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> create(
            CitySectionMapGuiService citySectionMapGuiService
    ) {
        return Commands.literal("section")
                .executes(ctx -> openSectionMap(ctx, citySectionMapGuiService));
    }

    private static int openSectionMap(
            CommandContext<CommandSourceStack> ctx,
            CitySectionMapGuiService citySectionMapGuiService
    ) {
        Player player = CommandGuards.cityPlayer(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        citySectionMapGuiService.openForOwnTown(player);
        return Command.SINGLE_SUCCESS;
    }
}
