package selic.re.discordbridge.mixin;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import selic.re.discordbridge.DiscordBot;

@Mixin(PlayerEntity.class)
abstract class PlayerEntityMixin extends LivingEntity {
    PlayerEntityMixin(EntityType<? extends LivingEntity> type, World world) {
        super(type, world);
    }

    @Inject(
        method = "getDisplayName()Lnet/minecraft/text/Text;",
        at = @At("HEAD"), require = 1, cancellable = true)
    private void getDisplayName(CallbackInfoReturnable<Text> ci) {
        if (DiscordBot.getInstance().isEmpty()) {
            return;
        }
        Text name = DiscordBot.getInstance().get().getDiscordName((PlayerEntity) (Object) this);
        if (name != null) {
            ci.setReturnValue(name);
        }
    }
}
