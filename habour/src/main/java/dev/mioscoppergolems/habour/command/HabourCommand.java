package dev.mioscoppergolems.habour.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.mioscoppergolems.habour.captain.Captains;
import dev.mioscoppergolems.habour.captain.Voyage;
import dev.mioscoppergolems.habour.harbour.HarbourRegistry;
import dev.mioscoppergolems.habour.kontor.KontorBlockEntity;
import dev.mioscoppergolems.habour.route.Cargo;
import dev.mioscoppergolems.habour.route.Route;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.golem.CopperGolem;

/**
 * {@code /harbour}: for server admins and for testing. The kontor argument is the position of a kontor block.
 * <ul>
 * <li>{@code /harbour list}: all harbours of this dimension</li>
 * <li>{@code /harbour info <kontor>}: routes, status and captains of one kontor</li>
 * <li>{@code /harbour route <kontor> add <destination kontor>}: a new route (everything, once)</li>
 * <li>{@code /harbour route <kontor> cargo <id> <item> <amount>}: replaces the cargo list with one line (0 = all)</li>
 * <li>{@code /harbour route <kontor> run <id>}: starts a run now</li>
 * <li>{@code /harbour route <kontor> remove <id>}</li>
 * </ul>
 */
public final class HabourCommand {
	private HabourCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection environment) {
		dispatcher.register(
			Commands.literal("harbour")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("list").executes(HabourCommand::list))
				.then(Commands.literal("info").then(Commands.argument("kontor", BlockPosArgument.blockPos()).executes(HabourCommand::info)))
				.then(
					Commands.literal("route").then(
						Commands.argument("kontor", BlockPosArgument.blockPos())
							.then(Commands.literal("add").then(Commands.argument("destination", BlockPosArgument.blockPos()).executes(HabourCommand::add)))
							.then(
								Commands.literal("cargo").then(
									Commands.argument("id", IntegerArgumentType.integer(1)).then(
										Commands.argument("item", ItemArgument.item(context)).then(
											Commands.argument("amount", IntegerArgumentType.integer(0, Cargo.MAX_AMOUNT)).executes(HabourCommand::cargo)
										)
									)
								)
							)
							.then(Commands.literal("run").then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(HabourCommand::run)))
							.then(Commands.literal("remove").then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(HabourCommand::remove)))
					)
				)
		);
	}

	private static int list(CommandContext<CommandSourceStack> context) {
		ServerLevel level = context.getSource().getLevel();
		List<HarbourRegistry.Entry> harbours = HarbourRegistry.get(level).inDimension(level);
		if (harbours.isEmpty()) {
			context.getSource().sendSuccess(() -> Component.literal("No harbours yet."), false);
		}
		for (HarbourRegistry.Entry entry : harbours) {
			context.getSource().sendSuccess(
				() -> Component.literal(entry.name() + " at " + entry.pos().toShortString() + (entry.active() ? " (has active routes)" : "")), false
			);
		}
		return harbours.size();
	}

	private static CommandSyntaxException fail(Component message) {
		return new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(message).create();
	}

	private static KontorBlockEntity kontor(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "kontor");
		if (context.getSource().getLevel().getBlockEntity(pos) instanceof KontorBlockEntity kontor) {
			return kontor;
		}
		throw fail(Component.literal("There is no kontor at " + pos.toShortString()));
	}

	private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		KontorBlockEntity kontor = kontor(context);
		ServerLevel level = context.getSource().getLevel();
		CommandSourceStack source = context.getSource();
		source.sendSuccess(() -> Component.literal(kontor.harbourName() + ": " + kontor.routes().size() + " routes"), false);
		for (Route route : kontor.routes()) {
			List<String> cargo = new ArrayList<>();
			for (Cargo line : route.cargo()) {
				cargo.add(line.item().getDescriptionId() + " x" + (line.isAll() ? "all" : String.valueOf(line.amount())));
			}
			source.sendSuccess(
				() -> Component.literal(
					"#" + route.id() + " to " + route.dest().toShortString() + " " + kontor.statusOf(route.id()) + (route.enabled() ? "" : " (paused)")
						+ ", runs left " + route.repeats() + ", trips " + route.trips() + ", delivered " + route.delivered() + ", cargo " + (cargo.isEmpty() ? "everything" : cargo)
				),
				false
			);
		}
		for (CopperGolem captain : kontor.captains(level)) {
			Voyage voyage = Captains.voyage(captain);
			source.sendSuccess(
				() -> Component.literal("Captain at " + captain.blockPosition().toShortString() + (voyage == null ? " idle" : " " + voyage.phase + " route " + voyage.routeId)), false
			);
		}
		return kontor.routes().size();
	}

	private static int add(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		KontorBlockEntity kontor = kontor(context);
		BlockPos dest = BlockPosArgument.getBlockPos(context, "destination");
		Route route = kontor.addRoute(dest);
		if (route == null) {
			throw fail(Component.literal("Too many routes"));
		}
		context.getSource().sendSuccess(() -> Component.literal("Added route #" + route.id()), true);
		return route.id();
	}

	private static int cargo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		KontorBlockEntity kontor = kontor(context);
		int index = kontor.indexOf(IntegerArgumentType.getInteger(context, "id"));
		if (index < 0) {
			throw fail(Component.literal("No such route"));
		}
		Cargo line = new Cargo(ItemArgument.getItem(context, "item").item().value(), IntegerArgumentType.getInteger(context, "amount"));
		kontor.setRoute(index, kontor.routes().get(index).withCargo(List.of(line)));
		return 1;
	}

	private static int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		KontorBlockEntity kontor = kontor(context);
		int index = kontor.indexOf(IntegerArgumentType.getInteger(context, "id"));
		if (index < 0) {
			throw fail(Component.literal("No such route"));
		}
		kontor.runNow(index, context.getSource().getLevel().getGameTime());
		return 1;
	}

	private static int remove(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		KontorBlockEntity kontor = kontor(context);
		kontor.removeRoute(kontor.indexOf(IntegerArgumentType.getInteger(context, "id")));
		return 1;
	}
}
