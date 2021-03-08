package me.geek.tom.parkourtimer.parkour.storage;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import me.geek.tom.parkourtimer.parkour.ActiveParkourManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.network.ServerPlayerEntity;
import xyz.nucleoid.plasmid.util.PlayerRef;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

public class ParkourTimeStorage {
    private List<ParkourTime> topTen;
    private final Object2LongMap<UUID> times;

    public ParkourTimeStorage(List<ParkourTime> topTen, Object2LongMap<UUID> times) {
        this.topTen = topTen;
        this.times = times;
    }

    public ActiveParkourManager.ParkourCompletionResult updatePlayerTime(PlayerRef player, long time) {
        if (times.containsKey(player.getId())) {
            long personalBest = times.getLong(player.getId());
            if (time < personalBest) {
                this.times.put(player.getId(), time);
                this.recomputeTopTen();
                return new ActiveParkourManager.ParkourCompletionResult(time, true, personalBest);
            } else {
                return new ActiveParkourManager.ParkourCompletionResult(time, false, personalBest);
            }
        } else {
            this.times.put(player.getId(), time);
            this.recomputeTopTen();
            return new ActiveParkourManager.ParkourCompletionResult(time, true, -1);
        }
    }

    private void recomputeTopTen() {
        // TODO: Broadcast when top 3 changes for a parkour
        this.topTen = this.times.object2LongEntrySet().stream()
                // compiler can't infer the type here for some reason, so a (kinda)ugly cast is required
                .sorted(Comparator.comparingLong((ToLongFunction<Object2LongMap.Entry<UUID>>) Object2LongMap.Entry::getLongValue).reversed())
                .limit(10).map(e -> new ParkourTime(e.getKey(), e.getLongValue()))
                .collect(Collectors.toList());
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        ListTag topTenTag = new ListTag();
        this.topTen.stream().map(ParkourTime::toTag).forEach(topTenTag::add);
        tag.put("TopTen", topTenTag);

        CompoundTag timesTag = new CompoundTag();
        for (Object2LongMap.Entry<UUID> entry : this.times.object2LongEntrySet()) {
            timesTag.putLong(entry.getKey().toString(), entry.getLongValue());
        }

        tag.put("Times", timesTag);

        return tag;
    }

    public static ParkourTimeStorage fromTag(CompoundTag tag) {
        ListTag topTenTag = tag.getList("TopTen", 10);
        List<ParkourTime> topTen = topTenTag.stream().map(tg -> (CompoundTag) tg).map(ParkourTime::fromTag).collect(Collectors.toList());

        Object2LongMap<UUID> times = new Object2LongOpenHashMap<>();
        CompoundTag timesTag = tag.getCompound("Times");
        for (String key : timesTag.getKeys()) {
            UUID uuid = UUID.fromString(key);
            long time = timesTag.getLong(key);
            times.put(uuid, time);
        }

        return new ParkourTimeStorage(topTen, times);
    }

    public long getPlayerTime(ServerPlayerEntity player) {
        if (!this.times.containsKey(player.getUuid())) {
            return -1;
        }
        return this.times.getLong(player.getUuid());
    }

    public static class ParkourTime {
        private final UUID player;
        private final long time;

        public ParkourTime(UUID player, long time) {
            this.player = player;
            this.time = time;
        }

        private CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putUuid("Uuid", this.player);
            tag.putLong("Time", this.time);
            return tag;
        }

        private static ParkourTime fromTag(CompoundTag tag) {
            return new ParkourTime(
                    tag.getUuid("Uuid"),
                    tag.getLong("Time")
            );
        }
    }
}
