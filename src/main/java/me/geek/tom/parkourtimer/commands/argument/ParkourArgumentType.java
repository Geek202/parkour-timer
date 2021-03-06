package me.geek.tom.parkourtimer.commands.argument;

import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import me.geek.tom.parkourtimer.parkour.Parkour;
import me.geek.tom.parkourtimer.ParkourTimer;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

// Based on: https://github.com/NucleoidMC/plasmid/blob/1.16/src/main/java/xyz/nucleoid/plasmid/command/argument/GameConfigArgument.java
public final class ParkourArgumentType {

    private static final DynamicCommandExceptionType PARKOUR_NOT_FOUND = new DynamicCommandExceptionType(id ->
        new TranslatableText("text.parkour_timer.parkour.not_found", id));

    public static RequiredArgumentBuilder<ServerCommandSource, Identifier> parkour(String name) {
        return CommandManager.argument(name, IdentifierArgumentType.identifier())
                .suggests((ctx, builder) -> {
                    String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);

                    CommandSource.forEachMatching(ParkourTimer.getStorage().getIds(), remaining, Function.identity(), id ->
                            builder.suggest(id.toString(), Objects.requireNonNull(ParkourTimer.getStorage().getParkour(id)).displayName));
                    return builder.buildFuture();
                });
    }

    public static Pair<Identifier, Parkour> getParkourArgument(CommandContext<ServerCommandSource> context, String name) throws CommandSyntaxException {
        Identifier identifier = IdentifierArgumentType.getIdentifier(context, name);

        Parkour parkour = ParkourTimer.getStorage().getParkour(identifier);
        if (parkour == null) {
            throw PARKOUR_NOT_FOUND.create(identifier);
        }

        return new Pair<>(identifier, parkour);
    }
}
