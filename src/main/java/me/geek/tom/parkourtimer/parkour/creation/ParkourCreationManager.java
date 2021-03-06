package me.geek.tom.parkourtimer.parkour.creation;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import me.geek.tom.parkourtimer.parkour.Parkour;
import me.geek.tom.parkourtimer.ParkourTimer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import xyz.nucleoid.plasmid.util.PlayerRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ParkourCreationManager {
    private static final DynamicCommandExceptionType ALREADY_EXISTS = new DynamicCommandExceptionType(msg ->
            new TranslatableText("text.parkour_timer.parkour.create.already_exists", msg));

    public static final ParkourCreationManager INSTANCE = new ParkourCreationManager();
    private final Map<PlayerRef, CreationState> storage = new Object2ObjectOpenHashMap<>();

    public boolean startBuilding(ServerPlayerEntity player, BlockPos start) {
        PlayerRef ref = PlayerRef.of(player);
        if (this.storage.containsKey(ref)) {
            return false;
        }

        this.storage.put(ref, new CreationState(start));
        return true;
    }

    public boolean addCheckpoint(ServerPlayerEntity player, BlockPos checkpoint) {
        PlayerRef ref = PlayerRef.of(player);
        CreationState state = this.storage.get(ref);
        if (state == null) {
            return false;
        }

        state.checkpoints.add(checkpoint);
        return true;
    }

    public boolean finish(ServerPlayerEntity player, BlockPos end, Identifier id, Text name) throws CommandSyntaxException {
        PlayerRef ref = PlayerRef.of(player);
        CreationState state = this.storage.get(ref);
        if (state == null) {
            return false;
        }

        if (ParkourTimer.getStorage().exists(id)) {
            throw ALREADY_EXISTS.create(id);
        }

        state.end = end;
        state.displayName = name;

        ParkourTimer.getStorage().createParkour(id, state.build());
        this.storage.remove(ref);

        return true;
    }

    public static class CreationState {
        private final BlockPos start;
        private List<BlockPos> checkpoints = new ArrayList<>();
        private BlockPos end;
        private Text displayName;

        public CreationState(BlockPos start) {
            this.start = start;
        }

        public Parkour build() {
            return new Parkour(displayName, this.start, this.end, this.checkpoints);
        }
    }
}
