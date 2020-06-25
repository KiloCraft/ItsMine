package me.drexhd.itsmine;

import me.drexhd.itsmine.claim.Claim;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.dimension.DimensionType;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Indigo Amann
 */
public class ClaimManager {
    public Map<PlayerEntity, Pair<BlockPos, BlockPos>> stickPositions = new HashMap<>();
    public static ClaimManager INSTANCE = null;
    private HashMap<UUID, Integer> blocksLeft = new HashMap<>();
    public List<UUID> ignoringClaims = new ArrayList<>();
    public List<UUID> flyers = new ArrayList<>();
    public static MinecraftServer server;
    private ClaimList claimList = new ClaimList();
    public int getClaimBlocks(UUID id) {
        return blocksLeft.getOrDefault(id, ItsMineConfig.main().claims2d ? ItsMineConfig.main().claimBlock().default2D : ItsMineConfig.main().claimBlock().default3D);
    }
    public boolean useClaimBlocks(UUID player, int amount) {
        int blocks = getClaimBlocks(player) - amount;
        if (blocks < 0) return false;
        blocksLeft.put(player, blocks);
        return true;
    }
    public void addClaimBlocks(UUID player, int amount) {
        useBlocksUntil0(player, -amount);
    }
    public void addClaimBlocks(Collection<ServerPlayerEntity> players, int amount) {
        players.forEach(player -> useBlocksUntil0(player.getGameProfile().getId(), -amount));
    }
    public void useBlocksUntil0(UUID player, int amount) {
        if (!useClaimBlocks(player, amount)) blocksLeft.put(player, 0);
    }
    public void setClaimBlocks(Collection<ServerPlayerEntity> players, int amount) {
        players.forEach(player -> setClaimBlocks(player.getGameProfile().getId(), amount));
    }

    public Claim getClaim(String name){
        return claimList.get(name);
    }



    public ArrayList<Claim> getClaimList() {
        return claimList.get();
    }

    public void removeClaim(Claim claim) {
        claimList.remove(claim);
    }

    public void updateClaim(Claim claim) {
        removeClaim(claim);
        addClaim(claim);
    }

    public void setClaimBlocks(UUID player, int amount) {
        blocksLeft.put(player, Math.max(amount, 0));
    }

    public void releaseBlocksToOwner(Claim claim) {
        if (claim.claimBlockOwner != null) addClaimBlocks(claim.claimBlockOwner, claim.getArea());
    }

    public List<Claim> getPlayerClaims(UUID id) {
        return claimList.get(id) == null ? new ArrayList<>() : claimList.get(id);
    }

    public boolean addClaim(Claim claim) {
        return claimList.add(claim);
    }
    public boolean wouldIntersect(Claim claim) {
        for (Claim value : claimList.get()) {
            if(!value.isChild && !claim.name.equals(value.name) && (claim.intersects(value) || value.intersects(claim))) return true;
        }
        return false;
    }

    public boolean wouldSubzoneIntersect(Claim claim) {
        for (Claim value : claimList.get()) {
                if(!claim.name.equals(value.name) && claim.intersects(value, true)){
                    return true;
                }
        }
        return false;
    }
    public CompoundTag toNBT() {
        CompoundTag tag =  new CompoundTag();
        ListTag list = new ListTag();
        claimList.get().forEach(claim -> {
            if(!claim.isChild){
                list.add(claim.toTag());
            }
        });
        tag.put("claims", list);
        CompoundTag blocksLeftTag = new CompoundTag();
        blocksLeft.forEach((id, amount) -> {if (id != null) blocksLeftTag.putInt(id.toString(), amount);});
        tag.put("blocksLeft", blocksLeftTag);
        ListTag ignoring = new ListTag();
        CompoundTag tvargetter = new CompoundTag();
        ignoringClaims.forEach(id -> {
            tvargetter.putString("id", id.toString());
            ignoring.add(tvargetter.get("id"));
        });
        ListTag listTag = new ListTag();
        for (UUID flyer : flyers) {
            CompoundTag tag1 = new CompoundTag();
            tag1.putUuid("uuid", flyer);
            listTag.add(tag1);
        }
        tag.put("flyers", listTag);
        tag.put("ignoring", ignoring);
        return tag;
    }

/*    public Claim getMainClaimAt(BlockPos pos, DimensionType dimension){
        for (Claim claim : claimsByName.values()) {
            if(claim.dimension.equals(dimension) && claim.includesPosition(pos)){
                return claim.getZoneCovering(pos);
            }
        }
        return null;
    }

    public Claim getSubzoneClaimAt(BlockPos pos, DimensionType dimension) {
        for (Claim claim : claimsByName.values()) {
            if (claim.dimension.equals(dimension) && claim.includesPosition(pos)) {
                for(Claim subzone : claim.children){
                    if(subzone.dimension.equals(dimension) && subzone.includesPosition(pos)){
                        return subzone.getZoneCovering(pos);
                    }
                }
                return null;
            }
        }
        return null;
    }
    */

    @Nullable
    public Claim getClaimAt(BlockPos pos, DimensionType dimension) {
        return claimList.get(pos.getX(), pos.getY(), pos.getZ(), dimension);
    }


    public void fromNBT(CompoundTag tag) {
        ListTag list = (ListTag) tag.get("claims");
        claimList = new ClaimList();
        list.forEach(it -> {
            Claim claim = new Claim();
            claim.fromTag((CompoundTag) it);
            claimList.add(claim);
            for(Claim subzone : claim.subzones){
                claimList.add(subzone);
            }
        });
        CompoundTag blocksLeftTag = tag.getCompound("blocksLeft");
        blocksLeft.clear();
        blocksLeftTag.getKeys().forEach(key -> blocksLeft.put(UUID.fromString(key), blocksLeftTag.getInt(key)));
        ListTag ignoringTag = (ListTag) tag.get("ignoring");
        ignoringTag.forEach(it -> ignoringClaims.add(UUID.fromString(it.asString())));
        ListTag listTag = tag.getList("flyers", 11);
        flyers.clear();
        for (int i = 0; i < listTag.size(); i++) {
            CompoundTag tag1 = listTag.getCompound(i);
            flyers.add(tag1.getUuid("uuid"));
        }
    }
}
