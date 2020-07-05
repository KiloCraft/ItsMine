package me.drexhd.itsmine.mixin;

import me.drexhd.itsmine.ClaimManager;
import me.drexhd.itsmine.ItsMineConfig;
import me.drexhd.itsmine.claim.Claim;
import me.drexhd.itsmine.util.BlockUtil;
import me.drexhd.itsmine.util.MessageUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.LiteralText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Pair;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;


/**
 * @author Indigo Amann
 */
@Mixin(ServerPlayerInteractionManager.class)
public abstract class ServerPlayerInteractionManagerMixin {
    @Shadow public ServerPlayerEntity player;

    @Shadow public ServerWorld world;

    public BlockPos blockPos;

    /*This method injects at the beginning of the method to get the block position*/
    @Inject(method = "interactBlock", at = @At(value = "HEAD"))
    private void getBlock(ServerPlayerEntity serverPlayerEntity, World world, ItemStack stack, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir){
        blockPos = hitResult.getBlockPos().offset(hitResult.getSide());
    }

    /*Check whether or not the player can interact with the block he is clicking*/
    @Redirect(method = "interactBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;onUse(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Hand;Lnet/minecraft/util/hit/BlockHitResult;)Lnet/minecraft/util/ActionResult;"))
        private ActionResult interactIfPossible(BlockState blockState, World world, PlayerEntity player, Hand hand, BlockHitResult hit){
        BlockPos pos = hit.getBlockPos();
        Claim claim = ClaimManager.INSTANCE.getClaimAt(pos, player.world.getDimension());
        if (claim != null) {
            UUID uuid = player.getUuid();
            Block block = blockState.getBlock();
            if ((BlockUtil.isInteractAble(block)/*isBlockEntity(block) || BlockUtil.isShulkerBox(block) || BlockUtil.isButton(block) || BlockUtil.isTrapdoor(block) || BlockUtil.isDoor(block) || BlockUtil.isContainer(block)*/) && !(claim.hasPermission(uuid, "interact_block") ||
                claim.hasPermission(uuid, "interact_block", Registry.BLOCK.getId(block).getPath()) ||
                (BlockUtil.isButton(block) && claim.hasPermission(uuid, "interact_block", "BUTTONS")) ||
                (BlockUtil.isTrapdoor(block) && claim.hasPermission(uuid, "interact_block", "TRAPDOORS")) ||
                (BlockUtil.isDoor(block) && claim.hasPermission(uuid, "interact_block", "DOORS")) ||
                (BlockUtil.isContainer(block) && claim.hasPermission(uuid, "interact_block", "CONTAINERS")) ||
                (BlockUtil.isSign(block) && claim.hasPermission(uuid, "interact_block", "SIGNS")) /*||
                (BlockUtil.isShulkerBox(block) && claim.hasPermission(uuid, "interact_block", "shulker_box"))*/)) {
                MessageUtil.sendTranslatableMessage(player, "messages", "interactBlock");
                return ActionResult.FAIL;
            }
        }
        return blockState.onUse(world, player, hand, hit);
    }

/*    Check whether or not the player is allowed to use the item in his hand
    returning false means the interaction will pass*/
    @Redirect(method = "interactBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;isEmpty()Z", ordinal = 2))
    private boolean interactWithItemIfPossible(ItemStack stack) {
        Claim claim = ClaimManager.INSTANCE.getClaimAt(blockPos, world.getDimension());
        if (claim != null && !stack.isEmpty()) {
            Item item = stack.getItem();
            UUID uuid = player.getUuid();
            if(item instanceof BlockItem || item instanceof BucketItem) {
                if ((claim.hasPermission(uuid, "build")) || claim.hasPermission(uuid, "place", Registry.ITEM.getId(item).getPath())) {
                    return false;
                } else {
                    MessageUtil.sendTranslatableMessage(player, "messages", "placeBlock");
                    return true;
                }
            } else {
                if(claim.hasPermission(uuid, "use_item", Registry.ITEM.getId(item).getPath())) {
                    return false;
                } else {
                    MessageUtil.sendTranslatableMessage(player, "messages", "useItem");
                    return true;
                }
            }
        }

        return stack.isEmpty();
    }


    /*Mixin to check for left click of stick (for selecting claim) and to check block breaking*/
    @Redirect(method = "processBlockBreakingAction", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;canPlayerModifyAt(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/math/BlockPos;)Z"))
    public boolean canBreak(ServerWorld world, PlayerEntity player, BlockPos pos) {
        if (player.inventory.getMainHandStack().getItem() == Items.STICK) {
            if (!player.isSneaking()) {
                Pair<BlockPos, BlockPos> posPair = ClaimManager.INSTANCE.stickPositions.get(player);
                if (posPair != null) {
                    posPair = new Pair<>(posPair.getLeft(), pos);
                    ClaimManager.INSTANCE.stickPositions.put(player, posPair);
                    player.sendSystemMessage(new LiteralText("Position #1 set: " + pos.getX() + (ItsMineConfig.main().claims2d ? "" : " " + pos.getY()) + " " + pos.getZ()).formatted(Formatting.GREEN), player.getUuid());
                    if (posPair.getLeft() != null) {
                        player.sendSystemMessage(new LiteralText("Area Selected. Type /claim create <name> to create your claim!").formatted(Formatting.GOLD), player.getUuid());
                        if (!ItsMineConfig.main().claims2d)
                            player.sendSystemMessage(new LiteralText("Remember that claims are three dimensional. Don't forget to expand up/down or select a big enough area...").formatted(Formatting.LIGHT_PURPLE).formatted(Formatting.ITALIC), player.getUuid());
                    }
                    return false;
                }
            }
        }
        Claim claim = ClaimManager.INSTANCE.getClaimAt(pos, player.world.getDimension());
            String block = Registry.BLOCK.getId(player.getEntityWorld().getBlockState(pos).getBlock()).getPath();
            if(claim != null){
                if(claim.hasPermission(player.getUuid(), "build") || claim.hasPermission(player.getUuid(), "break", block)){
                    return true;
                } else {
                    MessageUtil.sendTranslatableMessage(player, "messages", "breakBlock");
                    return false;
                }
            }
        return world.canPlayerModifyAt(player, pos);
    }
}
