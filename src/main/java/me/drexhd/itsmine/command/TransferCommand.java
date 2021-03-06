package me.drexhd.itsmine.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.drexhd.itsmine.ClaimManager;
import me.drexhd.itsmine.ItsMine;
import me.drexhd.itsmine.claim.Claim;
import me.drexhd.itsmine.util.ArgumentUtil;
import me.drexhd.itsmine.util.ClaimUtil;
import me.drexhd.itsmine.util.PermissionUtil;
import net.minecraft.command.EntitySelector;
import net.minecraft.command.arguments.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class TransferCommand {

    private static Map<UUID, String> pendingClaimTransfers = new HashMap<>();

    public static void register(LiteralArgumentBuilder<ServerCommandSource> command) {

        LiteralArgumentBuilder<ServerCommandSource> transfer = literal("transfer");
        RequiredArgumentBuilder<ServerCommandSource, String> claim = ArgumentUtil.getClaims();
        RequiredArgumentBuilder<ServerCommandSource, EntitySelector> player = argument("player", EntityArgumentType.player());
        LiteralArgumentBuilder<ServerCommandSource> confirm = literal("confirm");
        confirm.executes(context -> {
            final String string = "-accept-";
            ServerPlayerEntity p = EntityArgumentType.getPlayer(context, "player");
            String input = getString(context, "claim");
            String claimName = input.replace(string, "");
            Claim claim1 = ClaimManager.INSTANCE.getClaim(context.getSource().getPlayer().getUuid(), claimName);
            ClaimUtil.validateClaim(claim1);
            if (input.startsWith(string)) {
                return acceptTransfer(context.getSource());
            }
            return transfer(context.getSource(), claim1, p, false);
        });
        player.executes(context -> requestTransfer(context.getSource(), ClaimManager.INSTANCE.getClaim(context.getSource().getPlayer().getUuid(), getString(context, "claim")), EntityArgumentType.getPlayer(context, "player"), false));
        player.then(confirm);
        claim.then(player);
        transfer.then(claim);
        transfer.executes(context -> requestTransfer(context.getSource(), ClaimManager.INSTANCE.getClaimAt(new BlockPos(context.getSource().getPosition()), context.getSource().getWorld().getDimension()), EntityArgumentType.getPlayer(context, "player"), false));
        command.then(transfer);
    }

    public static int acceptTransfer(ServerCommandSource sender) throws CommandSyntaxException {
        Claim claim = ClaimManager.INSTANCE.getClaim(sender.getPlayer().getUuid(), pendingClaimTransfers.get(sender.getPlayer().getGameProfile().getId()));
        ClaimUtil.validateClaim(claim);
        ServerPlayerEntity player = sender.getMinecraftServer().getPlayerManager().getPlayer(claim.claimBlockOwner);
        if (player != null) {
            player.sendSystemMessage(new LiteralText("").append(new LiteralText(sender.getPlayer().getGameProfile().getName() + " has taken ownership of the claim \"" + claim.name + "\"").formatted(Formatting.YELLOW)), player.getUuid());
        }
        UUID uuid = sender.getPlayer().getGameProfile().getId();
        //Update claim
        ClaimManager.INSTANCE.removeClaim(claim);
        claim.claimBlockOwner = uuid;
        ClaimManager.INSTANCE.removeClaim(claim);
        //Update subzones
        claim.subzones.forEach(subzone -> {
            ClaimManager.INSTANCE.removeClaim(subzone);
            subzone.claimBlockOwner = uuid;
            ClaimManager.INSTANCE.addClaim(subzone);
        });
        return 0;
    }

    private static int requestTransfer(ServerCommandSource sender, Claim claim, ServerPlayerEntity player, boolean admin) throws CommandSyntaxException {
        ClaimUtil.validateClaim(claim);
        if (claim.isChild) {
            sender.sendFeedback(new LiteralText("You can't transfer ownership of subzones").formatted(Formatting.RED), false);
            return 0;
        }
        if (sender.getPlayer().getUuidAsString().equalsIgnoreCase(player.getUuidAsString())) {
            sender.sendFeedback(new LiteralText("Huh? That makes no sence").formatted(Formatting.RED), true);
            return 0;
        }
        if (!claim.claimBlockOwner.equals(sender.getPlayer().getGameProfile().getId())) {
            if (admin && ItsMine.permissions().hasPermission(sender, PermissionUtil.Command.ADMIN_MODIFY, 2)) {
                sender.sendFeedback(new LiteralText("WARNING: This is not your claim...").formatted(Formatting.DARK_RED).formatted(Formatting.BOLD), false);
            } else {
                sender.sendFeedback(new LiteralText("You can't transfer ownership of that claim").formatted(Formatting.RED), false);
                return 0;
            }
        }
        sender.sendFeedback(new LiteralText("").append(new LiteralText("Are you sure you want to transfer ownership of \"" + claim.name + "\" to " + player.getGameProfile().getName() + "? ").formatted(Formatting.GOLD))
                .append(new LiteralText("[YES]").styled(style -> style.withColor(Formatting.DARK_RED).withBold(true).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, (admin ? "/claim admin" : "/claim") + " transfer " + claim.name + " " + player.getGameProfile().getName() + " confirm")))), false);
        return 0;
    }

    private static int transfer(ServerCommandSource sender, Claim claim, ServerPlayerEntity player, boolean admin) throws CommandSyntaxException {
        ClaimUtil.validateClaim(claim);
        if (claim.isChild) {
            sender.sendFeedback(new LiteralText("You can't transfer ownership of subzones").formatted(Formatting.RED), false);
            return 0;
        }
        if (sender.getPlayer().getUuidAsString().equalsIgnoreCase(player.getUuidAsString())) {
            sender.sendFeedback(new LiteralText("Huh? That makes no sence").formatted(Formatting.RED), true);
            return 0;
        }
        if (!claim.claimBlockOwner.equals(sender.getPlayer().getGameProfile().getId())) {
            if (admin && ItsMine.permissions().hasPermission(sender, PermissionUtil.Command.ADMIN_MODIFY, 2)) {
                sender.sendFeedback(new LiteralText("Transfering ownership of a claim belonging to somebody else").formatted(Formatting.DARK_RED).formatted(Formatting.BOLD), false);
            } else {
                sender.sendFeedback(new LiteralText("You can't transfer ownership of that claim").formatted(Formatting.RED), false);
                return 0;
            }
        }
        GameProfile profile = sender.getWorld().getServer().getUserCache().getByUuid(claim.claimBlockOwner);
        sender.sendFeedback(new LiteralText("Transferring ownership of the claim \"" + claim.name + "\" to " + player.getGameProfile().getName() + " if they accept").formatted(Formatting.GREEN), claim.claimBlockOwner != player.getGameProfile().getId());
        player.sendSystemMessage(new LiteralText("").append(new LiteralText("Do you want to accept ownership of the claim \"" + claim.name + "\" from " + (profile == null ? "Not Present" : profile.getName()) + "? ").formatted(Formatting.GOLD))
                .append(new LiteralText("[ACCEPT]").styled(style -> style.withColor(Formatting.GREEN).withBold(true).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim transfer -accept-" + claim.name + " " + player.getEntityName() + " confirm")))), player.getUuid());
        pendingClaimTransfers.put(player.getGameProfile().getId(), claim.name);
        return 0;
    }
}
