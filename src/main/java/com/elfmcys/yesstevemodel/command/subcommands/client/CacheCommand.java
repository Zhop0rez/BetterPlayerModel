package com.elfmcys.yesstevemodel.command.subcommands.client;

import com.elfmcys.yesstevemodel.client.ClientModelManager;
import com.elfmcys.yesstevemodel.util.YSMMessageFormatter;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.ysm.architectury.event.events.client.ClientCommandRegistrationEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class CacheCommand {

    public static LiteralArgumentBuilder<ClientCommandRegistrationEvent.ClientCommandSourceStack> register() {
        return LiteralArgumentBuilder.<ClientCommandRegistrationEvent.ClientCommandSourceStack>literal("cache")
                .then(LiteralArgumentBuilder.<ClientCommandRegistrationEvent.ClientCommandSourceStack>literal("dump")
                        .executes(CacheCommand::dumpCache));
    }

    private static int dumpCache(CommandContext<ClientCommandRegistrationEvent.ClientCommandSourceStack> context) {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return 0;
        }

        player.sendSystemMessage(YSMMessageFormatter.withPrefix(Component.literal("ејЂе§‹и§Јжћђе№¶еЇје‡єе®ўж€·з«Їзј“е­жЁЎећ‹...")));

        ClientModelManager.exportAllCachedModels(null, exportResult -> {
            if (exportResult.getMessage() != null) {
                player.sendSystemMessage(YSMMessageFormatter.withPrefix(exportResult.getMessage()));
            }
            if (exportResult.isSuccess()) {
                player.sendSystemMessage(Component.translatable("commands.better_player_model.export.success", exportResult.getFilePath()));
            }
        });

        return Command.SINGLE_SUCCESS;
    }
}


