package me.geek.tom.parkourtimer.parkour.storage;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import me.geek.tom.parkourtimer.ParkourTimer;
import me.geek.tom.parkourtimer.parkour.ActiveParkourManager;
import me.geek.tom.parkourtimer.parkour.Parkour;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.plasmid.storage.ServerStorage;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ParkourStorageManager implements ServerStorage {
    private final Object2ObjectMap<Identifier, ParkourTimeStorage> timeStorage = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<Identifier, Parkour> parkours = new Object2ObjectOpenHashMap<>();
    private final Long2ObjectMap<Pair<Identifier, Parkour>> byPos = new Long2ObjectOpenHashMap<>();

    public boolean exists(Identifier parkourId) {
        return parkours.containsKey(parkourId);
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();

        CompoundTag timesTag = new CompoundTag();
        for (Map.Entry<Identifier, ParkourTimeStorage> entry : this.timeStorage.entrySet()) {
            timesTag.put(entry.getKey().toString(), entry.getValue().toTag());
        }
        tag.put("Times", timesTag);

        CompoundTag parkoursTag = new CompoundTag();
        for (Map.Entry<Identifier, Parkour> entry : parkours.entrySet()) {
            Tag element = Parkour.CODEC.encodeStart(NbtOps.INSTANCE, entry.getValue())
                    .resultOrPartial(ParkourTimer.LOGGER::error)
                    .orElseThrow(() -> new IllegalStateException("failed to encode parkour!"));
            parkoursTag.put(entry.getKey().toString(), element);
        }
        tag.put("Parkours", parkoursTag);

        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        CompoundTag timesTag = tag.getCompound("Times");
        timeStorage.clear();
        for (String key : timesTag.getKeys()) {
            Identifier parkourId = new Identifier(key);
            timeStorage.put(parkourId, ParkourTimeStorage.fromTag(tag.getCompound(key)));
        }

        CompoundTag parkoursTag = tag.getCompound("Parkours");
        this.parkours.clear();
        this.byPos.clear();
        for (String key : parkoursTag.getKeys()) {
            Identifier id = new Identifier(key);
            Parkour parkour = Parkour.CODEC.parse(NbtOps.INSTANCE, parkoursTag.get(key))
                    .resultOrPartial(ParkourTimer.LOGGER::error)
                    .orElseThrow(() -> new IllegalStateException("failed to parse parkour!"));
            parkours.put(id, parkour);
            byPos.put(parkour.start.asLong(), new Pair<>(id, parkour));
            byPos.put(parkour.end.asLong(), new Pair<>(id, parkour));
            for (BlockPos checkpoint : parkour.checkpoints) {
                byPos.put(checkpoint.asLong(), new Pair<>(id, parkour));
            }
        }
    }

    public ActiveParkourManager.ParkourCompletionResult setOrUpdatePlayerTime(Identifier parkour, ServerPlayerEntity player, long time) {
        ParkourTimeStorage storage = this.timeStorage.get(parkour);
        if (storage == null) {
            storage = new ParkourTimeStorage(new ArrayList<>(), new Object2LongOpenHashMap<>());
            this.timeStorage.put(parkour, storage);
        }
        return storage.updatePlayerTime(Objects.requireNonNull(this.parkours.get(parkour), "parkour " + parkour), player, time);
    }

    @Nullable
    public Pair<Identifier, Parkour> getByPos(BlockPos pos) {
        return byPos.get(pos.asLong());
    }

    public void createParkour(Identifier id, Parkour parkour) {
        this.parkours.put(id, parkour);
        byPos.put(parkour.start.asLong(), new Pair<>(id, parkour));
        byPos.put(parkour.end.asLong(), new Pair<>(id, parkour));
        for (BlockPos checkpoint : parkour.checkpoints) {
            byPos.put(checkpoint.asLong(), new Pair<>(id, parkour));
        }
    }

    public long getPlayerTime(ServerPlayerEntity player, Identifier parkour) {
        ParkourTimeStorage storage = this.timeStorage.get(parkour);
        if (storage == null) return -1;
        return storage.getPlayerTime(player);
    }

    public void removeParkour(Identifier id) {
        this.parkours.remove(id);
        this.timeStorage.remove(id);
    }

    @Nullable
    public Parkour getParkour(Identifier id) {
        return this.parkours.get(id);
    }

    public Set<Identifier> getIds() {
        return this.parkours.keySet();
    }
}
