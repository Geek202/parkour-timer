package me.geek.tom.parkourtimer.parkour;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import me.geek.tom.parkourtimer.ParkourTimer;
import me.geek.tom.parkourtimer.parkour.storage.ParkourStorageManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.plasmid.util.PlayerRef;

import java.util.Map;

public class ActiveParkourManager {
    private static final ActiveParkourManager instance = new ActiveParkourManager();

    private final Map<PlayerRef, ActiveParkour> activeParkour = new Object2ObjectOpenHashMap<>();

    public boolean startParkour(ServerPlayerEntity player, Identifier id, Parkour parkour) {
        PlayerRef ref = PlayerRef.of(player);
        if (activeParkour.containsKey(ref)) {
            return false; // already doing a parkour.
        }

        this.activeParkour.put(ref, new ActiveParkour(id, parkour, System.currentTimeMillis()));

        return true;
    }

    public boolean reachedCheckpoint(ServerPlayerEntity player, int checkpoint) {
        ActiveParkour parkour = this.activeParkour.get(PlayerRef.of(player));
        if (parkour == null) return false;

        if (checkpoint > parkour.currentCheckpoint) {
            parkour.currentCheckpoint = checkpoint;
            return true;
        }
        return false;
    }

    public boolean cancelParkour(ServerPlayerEntity player) {
        return this.activeParkour.remove(PlayerRef.of(player)) != null;
    }

    @Nullable
    public ParkourCompletionResult stopParkour(ServerPlayerEntity player, Identifier parkour) {
        PlayerRef playerRef = PlayerRef.of(player);
        if (!activeParkour.containsKey(playerRef)) {
            return null;
        }

        ActiveParkour activeParkour = this.activeParkour.get(playerRef);
        if (!activeParkour.id.equals(parkour)) {
            return null;
        }

        long endTime = System.currentTimeMillis();
        long time = endTime - activeParkour.startTime;

        this.activeParkour.remove(playerRef);
        return this.recordPlayerTime(playerRef, parkour, time);
    }

    private ParkourCompletionResult recordPlayerTime(PlayerRef player, Identifier parkour, long time) {
        ParkourStorageManager storage = ParkourTimer.getStorage();
        return storage.setOrUpdatePlayerTime(parkour, player, time);
    }

    public static ActiveParkourManager getInstance() {
        return instance;
    }

    public static class ParkourCompletionResult {
        public final long time;
        public final boolean isPersonalBest;
        public final long currentPersonalBest;

        public ParkourCompletionResult(long time, boolean isPersonalBest, long currentPersonalBest) {
            this.time = time;
            this.isPersonalBest = isPersonalBest;
            this.currentPersonalBest = currentPersonalBest;
        }

        public Text asText(Text parkourName) {
            if (this.currentPersonalBest == -1) {
                return new TranslatableText("text.parkour_timer.parkour.completed.first", parkourName, this.time / 1000d);
            } else {
                if (this.isPersonalBest) {
                    return new TranslatableText("text.parkour_timer.parkour.completed.new_personal_best", parkourName, this.time / 1000d);
                } else {
                    return new TranslatableText("text.parkour_timer.parkour.completed", parkourName, this.time / 1000d, this.currentPersonalBest / 1000d);
                }
            }
        }
    }

    public static class ActiveParkour {
        private final Identifier id;
        private final Parkour parkour;
        private final long startTime;
        private int currentCheckpoint = -1;

        public ActiveParkour(Identifier id, Parkour parkour, long startTime) {
            this.id = id;
            this.parkour = parkour;
            this.startTime = startTime;
        }
    }
}
