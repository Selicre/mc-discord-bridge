package selic.re.discordbridge.mixin;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.argument.TextArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.TellRawCommand;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.text.Texts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import selic.re.discordbridge.DiscordBot;

@Mixin(TellRawCommand.class)
public class TellRawHandlerMixin {
    // This hijacks the closure within the /tellraw handler.
    @SuppressWarnings("UnresolvedMixinReference")
    @Inject(at = @At("HEAD"), method = "method_13777")
    private static void preTellRaw(CommandContext<ServerCommandSource> context, CallbackInfoReturnable<Integer> ci) {
        // TODO: this is terrible. Figure out how to properly check what the selector is
        if (!context.getInput().startsWith("/tellraw @a")) return;
        try {
            var message = Texts.parse(context.getSource(), TextArgumentType.getTextArgument(context, "message"), null, 0);
            var db = DiscordBot.getInstance();
            if (db != null) {
                db.sendSystemMessage(message);
            }
        } catch (CommandSyntaxException e) {
            // just eat it
        }
    }
}
