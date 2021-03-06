package me.geek.tom.parkourtimer.parkour;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import xyz.nucleoid.codecs.MoreCodecs;

import java.util.List;

public class Parkour {
    public static final Codec<Parkour> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            MoreCodecs.TEXT.fieldOf("display_name").forGetter(parkour -> parkour.displayName),
            BlockPos.CODEC.fieldOf("start_pos").forGetter(parkour -> parkour.start),
            BlockPos.CODEC.fieldOf("end_pos").forGetter(parkour -> parkour.end),
            BlockPos.CODEC.listOf().fieldOf("checkpoints").forGetter(parkour ->  parkour.checkpoints)
    ).apply(instance, Parkour::new));

    public @NotNull Text displayName;
    public final BlockPos start;
    public final BlockPos end;
    public final List<BlockPos> checkpoints;

    public Parkour(@NotNull Text displayName, BlockPos start, BlockPos end, List<BlockPos> checkpoints) {
        this.displayName = displayName;
        this.start = start;
        this.end = end;
        this.checkpoints = checkpoints;
    }
}
