package me.geek.tom.parkourtimer;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import me.geek.tom.parkourtimer.parkour.ActiveParkourManager;
import me.geek.tom.parkourtimer.parkour.Parkour;
import me.geek.tom.parkourtimer.parkour.creation.ParkourCreationManager;
import me.geek.tom.parkourtimer.parkour.storage.ParkourStorageManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.nucleoid.plasmid.storage.ServerStorage;

import static me.geek.tom.parkourtimer.commands.argument.ParkourArgumentType.getParkourArgument;
import static me.geek.tom.parkourtimer.commands.argument.ParkourArgumentType.parkour;
import static net.minecraft.command.argument.IdentifierArgumentType.getIdentifier;
import static net.minecraft.command.argument.IdentifierArgumentType.identifier;
import static net.minecraft.command.argument.TextArgumentType.getTextArgument;
import static net.minecraft.command.argument.TextArgumentType.text;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class ParkourTimer implements ModInitializer {
    public static Logger LOGGER = LogManager.getLogger();
    public static final String MOD_ID = "parkour-timer";
    private static final SimpleCommandExceptionType REQUIRES_OVERWORLD = new SimpleCommandExceptionType(new TranslatableText("text.parkour_timer.parkour.create.requires_overworld"));

    private static ParkourStorageManager storage;

    public static void onPressurePlatePressed(ServerPlayerEntity player, BlockPos pos) {
        Pair<Identifier, Parkour> p = getStorage().getByPos(pos);
        if (p == null) return;
        Parkour parkour = p.getRight();
        Identifier id = p.getLeft();
        ActiveParkourManager parkourManager = ActiveParkourManager.getInstance();
        if (pos.equals(parkour.start)) {
            boolean ok = parkourManager.startParkour(player, id, parkour);
            if (ok) {
                player.sendMessage(new TranslatableText("text.parkour_timer.parkour.started", parkour.displayName), false);
            }
        } else if (pos.equals(parkour.end)) {
            ActiveParkourManager.ParkourCompletionResult result = parkourManager.stopParkour(player, id);
            if (result != null) {
                player.sendMessage(result.asText(parkour.displayName), false);
            }
        } else { // must be a checkpoint.
            for (int i = 0; i < parkour.checkpoints.size(); i++) {
                BlockPos checkpoint = parkour.checkpoints.get(i);
                if (pos.equals(checkpoint)) {
                    boolean ok = parkourManager.reachedCheckpoint(player, i);
                    if (ok) {
                        player.sendMessage(new TranslatableText("text.parkour_timer.parkour.checkpoint.reached", i + 1, parkour.displayName), false);
                    }
                    return;
                }
            }
            throw new IllegalStateException("we shouldn't be here!");
        }
    }

    @Override
    public void onInitialize() {
        storage = ServerStorage.createStorage(new Identifier(MOD_ID, "parkour_times"), new ParkourStorageManager());
        CommandRegistrationCallback.EVENT.register(((dispatcher, dedicated) -> {
            dispatcher.register(literal("parkour_admin")
                    .requires(ctx -> ctx.hasPermissionLevel(2))
                    .then(literal("create")
                            .then(literal("start").executes(this::startCreatingParkour))
                            .then(literal("checkpoint").executes(this::createParkourCheckpoint))
                            .then(literal("end").then(argument("id", identifier())
                                            .then(argument("display_name", text())
                                                    .executes(ctx -> finishCreatingParkour(ctx, getIdentifier(ctx, "id"), getTextArgument(ctx, "display_name")))
                                            )
                            ))
                    ).then(literal("delete")
                            .then(parkour("parkour")
                                    .then(literal("confirm").executes(this::deleteParkour))
                                    .executes(ctx -> {
                                        ctx.getSource().sendError(new TranslatableText("text.parkour_timer.parkour.delete.confirm"));
                                        return 0;
                                    })
                            ))
                    .then(literal("rename")
                            .then(parkour("parkour").then(argument("name", text()).executes(this::renameParkour))))
            );

            // TODO: Leaderboard command, tp to last checkpoint command.
            dispatcher.register(literal("parkour")
                    .then(literal("time")
                            .then(parkour("parkour")
                                    .executes(this::showParkourTime)
                    )).then(literal("cancel").executes(this::cancelParkour))
            );
        }));
    }

    private int renameParkour(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Pair<Identifier, Parkour> pair = getParkourArgument(ctx, "parkour");
        Parkour parkour = pair.getRight();
        parkour.displayName = getTextArgument(ctx, "name");
        ctx.getSource().sendFeedback(new TranslatableText("text.parkour_timer.parkour.renamed", pair.getLeft(), parkour.displayName), false);

        return Command.SINGLE_SUCCESS;
    }

    private int deleteParkour(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Pair<Identifier, Parkour> parkour = getParkourArgument(ctx, "parkour");
        getStorage().removeParkour(parkour.getLeft());
        ctx.getSource().sendFeedback(new TranslatableText("text.parkour_timer.parkour.deleted", parkour.getLeft()).formatted(Formatting.YELLOW), true);

        return Command.SINGLE_SUCCESS;
    }

    private int cancelParkour(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        boolean ok = ActiveParkourManager.getInstance().cancelParkour(player);
        if (ok) {
            ctx.getSource().sendFeedback(new TranslatableText("text.parkour_timer.parkour.cancel").formatted(Formatting.YELLOW), false);
        } else {
            ctx.getSource().sendError(new TranslatableText("text.parkour_timer.parkour.not_active"));
            return 0;
        }

        return Command.SINGLE_SUCCESS;
    }

    private int showParkourTime(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Pair<Identifier, Parkour> parkour = getParkourArgument(ctx, "parkour");
        long time = getStorage().getPlayerTime(ctx.getSource().getPlayer(), parkour.getLeft());
        if (time < 0) {
            ctx.getSource().sendError(new TranslatableText("text.parkour_timer.parkour.not_completed", parkour.getRight().displayName));
            return 0;
        }

        double seconds = time / 1000d;
        ctx.getSource().sendFeedback(new TranslatableText("text.parkour_timer.parkour.time", parkour.getRight().displayName, seconds), false);

        return Command.SINGLE_SUCCESS;
    }

    private int finishCreatingParkour(CommandContext<ServerCommandSource> ctx, Identifier id, Text displayName) throws CommandSyntaxException {
        this.requiresOverworld(ctx);
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        BlockPos pos = player.getBlockPos();
        boolean success = ParkourCreationManager.INSTANCE.finish(player, pos, id, displayName);

        if (success) {
            ctx.getSource().sendFeedback(new TranslatableText("text.parkour_timer.parkour.create.finished", id).formatted(Formatting.GREEN), false);
        } else {
            ctx.getSource().sendError(new TranslatableText("text.parkour_timer.parkour.create.not_creating"));
        }

        return Command.SINGLE_SUCCESS;
    }

    private int createParkourCheckpoint(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        this.requiresOverworld(ctx);
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        BlockPos pos = player.getBlockPos();
        boolean success = ParkourCreationManager.INSTANCE.addCheckpoint(player, pos);

        if (success) {
            ctx.getSource().sendFeedback(new TranslatableText("text.parkour_timer.parkour.create.added_checkpoint").formatted(Formatting.GREEN), false);
        } else {
            ctx.getSource().sendError(new TranslatableText("text.parkour_timer.parkour.create.not_creating"));
        }

        return Command.SINGLE_SUCCESS;
    }

    public int startCreatingParkour(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        this.requiresOverworld(ctx);
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        BlockPos pos = player.getBlockPos();
        boolean success = ParkourCreationManager.INSTANCE.startBuilding(player, pos);

        if (success) {
            ctx.getSource().sendFeedback(new TranslatableText("text.parkour_timer.parkour.create.started").formatted(Formatting.GREEN), false);
        } else {
            ctx.getSource().sendError(new TranslatableText("text.parkour_timer.parkour.create.already_creating"));
        }

        return Command.SINGLE_SUCCESS;
    }

    public static ParkourStorageManager getStorage() {
        return storage;
    }

    private void requiresOverworld(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        if (!ctx.getSource().getWorld().equals(ctx.getSource().getMinecraftServer().getOverworld())) {
            throw REQUIRES_OVERWORLD.create();
        }
    }
}
