package me.geek.tom.parkourtimer.mixin;

import me.geek.tom.parkourtimer.ParkourTimer;
import net.minecraft.block.AbstractPressurePlateBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Mixin(AbstractPressurePlateBlock.class)
public class MixinAbstractPressurePlateBlock {
    @Inject(method = "onEntityCollision",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/block/AbstractPressurePlateBlock;getRedstoneOutput(Lnet/minecraft/block/BlockState;)I"))
    private void hook_onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity, CallbackInfo ci) {
        if (!(world instanceof ServerWorld) || !world.equals(Objects.requireNonNull(world.getServer(),
                "server").getOverworld())) return;

        if (entity instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) entity;
            if (!player.interactionManager.getGameMode().isSurvivalLike()) return; // no cheaty pls
            ParkourTimer.onPressurePlatePressed(player, pos);
        }
    }
}
