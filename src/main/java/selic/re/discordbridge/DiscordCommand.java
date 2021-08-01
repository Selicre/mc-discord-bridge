package selic.re.discordbridge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.argument.MessageArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.literal;

public class DiscordCommand {
    public void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("discord")
                .then(literal("reload")
                        .requires(source -> source.hasPermissionLevel(4))
                        .executes(this::reloadConfig))
                .then(literal("status")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(this::status))
                .then(literal("broadcast").then(CommandManager.argument("message", MessageArgumentType.message())
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(this::broadcast)))
        );
    }
    public int status(CommandContext<ServerCommandSource> ctx) {
        Text feedback;
        var instance = DiscordBot.getInstance();
        if (instance.isPresent()) {
            if (instance.get().getStatus()) {
                feedback = new LiteralText("The bridge seems to be connected");
            } else {
                feedback = new LiteralText("The bridge doesn't seem to be connected");
            }
        } else {
            feedback = new LiteralText("The bridge doesn't seem to be initialized");
        }
        ctx.getSource().sendFeedback(feedback, false);
        return 1;
    }
    public int broadcast(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Text text = MessageArgumentType.getMessage(ctx, "message");
        DiscordBot.getInstance().ifPresent(c -> c.sendSystemMessage(text));
        return 1;
    }
    public int reloadConfig(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        int res = DiscordBotConfig.performInit(ctx.getSource().getServer());
        if (res > 1) {
            source.sendError(new LiteralText("There was an error reloading the config"));
        } else {
            source.sendFeedback(new LiteralText("Successfully reloaded the bridge config"), true);
        }
        return res;
    }
}
