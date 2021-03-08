package me.geek.tom.parkourtimer.parkour.storage;

import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import me.geek.tom.parkourtimer.parkour.ActiveParkourManager;
import me.geek.tom.parkourtimer.parkour.Parkour;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.MessageType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class ParkourTimeStorage {
    private List<ParkourTime> topTen;
    private final Object2LongMap<UUID> times;

    public ParkourTimeStorage(List<ParkourTime> topTen, Object2LongMap<UUID> times) {
        this.topTen = topTen;
        this.times = times;
    }

    public ActiveParkourManager.ParkourCompletionResult updatePlayerTime(Parkour parkour, ServerPlayerEntity player, long time) {
        if (times.containsKey(player.getUuid())) {
            long personalBest = times.getLong(player.getUuid());
            if (time < personalBest) {
                this.times.put(player.getUuid(), time);
                this.recomputeTopTen(player, parkour.displayName);
                return new ActiveParkourManager.ParkourCompletionResult(time, true, personalBest);
            } else {
                return new ActiveParkourManager.ParkourCompletionResult(time, false, personalBest);
            }
        } else {
            this.times.put(player.getUuid(), time);
            this.recomputeTopTen(player, parkour.displayName);
            return new ActiveParkourManager.ParkourCompletionResult(time, true, -1);
        }
    }

    private void recomputeTopTen(ServerPlayerEntity player, Text parkourName) {
        ParkourTime oldLeader;
        if (!this.topTen.isEmpty()) {
            oldLeader = this.topTen.get(0);
        } else {
            oldLeader = null;
        }

        this.topTen = this.times.object2LongEntrySet().stream()
                .sorted(Comparator.comparingLong(Object2LongMap.Entry::getLongValue))
                .limit(10).map(e -> new ParkourTime(e.getKey(), e.getLongValue()))
                .collect(Collectors.toList());

        ParkourTime leader;
        if (!this.topTen.isEmpty()) {
            leader = this.topTen.get(0);
        } else {
            leader = null;
        }

        if (!Objects.equals(leader, oldLeader) && leader != null) {
            MinecraftServer server = player.getServer();
            assert server != null;
            GameProfile leaderProfile = server.getUserCache().getByUuid(leader.player);
            if (leaderProfile != null) {
                server.getPlayerManager().broadcastChatMessage(new TranslatableText(
                        "text.parkour_timer.parkour.leaderboard.topspot", parkourName, leaderProfile.getName())
                                .formatted(Formatting.GOLD),
                        MessageType.SYSTEM, Util.NIL_UUID);
            }
        }
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ParkourTime that = (ParkourTime) o;

            if (time != that.time) return false;
            return Objects.equals(player, that.player);
        }

        @Override
        public int hashCode() {
            int result = player != null ? player.hashCode() : 0;
            result = 31 * result + (int) (time ^ (time >>> 32));
            return result;
        }
    }
}
