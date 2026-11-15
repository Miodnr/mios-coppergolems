package dev.mioscoppergolems.habour.gametest;

import dev.mioscoppergolems.entity.CopperChestMinecart;
import dev.mioscoppergolems.habour.captain.Captains;
import dev.mioscoppergolems.habour.captain.Dockhands;
import dev.mioscoppergolems.habour.harbour.Fleet;
import dev.mioscoppergolems.habour.harbour.HarbourRegistry;
import dev.mioscoppergolems.habour.kontor.KontorBlock;
import dev.mioscoppergolems.habour.kontor.KontorBlockEntity;
import dev.mioscoppergolems.habour.registry.HabourAttachments;
import dev.mioscoppergolems.habour.registry.HabourBlocks;
import dev.mioscoppergolems.habour.route.Cargo;
import dev.mioscoppergolems.habour.route.Route;
import dev.mioscoppergolems.registry.ModEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Plays a railway in a real world (run with HABOUR_RAIL=1, instead of the harbour test): two stations 70 blocks apart
 * with a track between them that has a bend and a hill, a conductor, dockhands, two free carts and one chain. A shipment
 * that needs two carts is sent: the conductor must couple the second cart, the train must be turned around at both ends,
 * and no item (chains included) may be lost or duplicated on the way.
 */
public class RailClientGameTest implements FabricClientGameTest {
	public static boolean enabled() {
		return "1".equals(System.getenv("HABOUR_RAIL"));
	}

	private static void log(String message) {
		System.out.println("[RAIL-TEST] " + message);
	}

	private record Setup(int ground, BlockPos stationA, BlockPos stationB, UUID conductor, List<UUID> hands, List<UUID> carts) {
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!enabled()) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			KontorBlockEntity.DEBUG = true;
			context.waitTicks(20);
			Setup setup = server.computeOnServer(RailClientGameTest::build);
			log("built the railway, ground " + setup.ground());
			server.waitFor(s -> HarbourRegistry.get(s.overworld()).find(s.overworld(), setup.stationB()) != null, 600);
			int g = setup.ground();
			server.runCommand("tp @p 8 " + (g + 5) + " 8 -150 30");
			context.waitTicks(40);
			context.takeScreenshot("rail_station_a");

			int[] total = {0};
			int[] problems = {0, 0};
			boolean[] flags = new boolean[5];
			server.runOnServer(s -> {
				KontorBlockEntity station = station(s, setup.stationA());
				Route route = station.addRoute(setup.stationB());
				station.setRoute(station.indexOf(route.id()), route.withCargo(List.of(new Cargo(Items.SNOWBALL, 800))).withRepeats(1));
				total[0] = goods(s.overworld(), setup);
				log("goods before: " + total[0]);
				ServerTickEvents.END_SERVER_TICK.register(watcher -> {
					if (flags[4]) {
						return;
					}
					ServerLevel w = watcher.overworld();
					int now = goods(w, setup);
					if (now != total[0]) {
						problems[0]++;
						if (problems[0] <= 3) {
							log("GOODS CHANGED from " + total[0] + " to " + now);
						}
					}
					for (UUID id : setup.hands()) {
						flags[1] |= w.getEntity(id) instanceof CopperGolem hand && hand.getAttached(Dockhands.CLAIM) != null;
						flags[2] |= w.getEntity(id) instanceof CopperGolem hand && hand.getVehicle() != null;
					}
					for (UUID id : setup.carts()) {
						if (w.getEntity(id) instanceof CopperChestMinecart cart) {
							flags[0] |= cart.isChained();
							if (!cart.isOnRails() && cart.tickCount > 40) {
								problems[1]++;
							}
						}
					}
					if (w.getEntity(setup.conductor()) instanceof CopperGolem golem && Captains.voyage(golem) != null) {
						flags[3] |= !Fleet.of(w, setup.stationA(), 1).isEmpty();
					}
				});
			});
			server.runCommand("tick rate 200");
			boolean done = false;
			for (int i = 0; i < 700 && !done; i++) {
				context.waitTicks(10);
				done = server.computeOnServer(s -> station(s, setup.stationB()).count(Items.SNOWBALL) >= 800
					&& s.overworld().getEntity(setup.conductor()) instanceof CopperGolem golem && Captains.voyage(golem) == null);
				if (i % 20 == 0) {
					log("progress: " + server.computeOnServer(s -> describe(s, setup)));
				}
			}
			server.runCommand("tick rate 20");
			flags[4] = true;
			int snow = server.computeOnServer(s -> station(s, setup.stationB()).count(Items.SNOWBALL));
			log("done=" + done + ", snowballs at B " + snow + ", coupled=" + flags[0] + ", dockhands worked=" + flags[1] + ", dockhand rode=" + flags[2]
				+ ", conductor reported=" + flags[3] + ", goods changed ticks=" + problems[0] + ", derailed ticks=" + problems[1]);
			check(done && snow == 800, "the shipment did not arrive");
			check(flags[0], "no cart was ever coupled");
			check(flags[1] && !flags[2], "dockhands did not work or rode a cart");
			check(problems[0] == 0, "goods or chains were lost or duplicated");
			check(problems[1] == 0, "a cart left the track");
			check(flags[3], "the conductor was never reported");
			server.runCommand("tp @p 70 " + (g + 5) + " 14 150 30");
			context.waitTicks(40);
			context.takeScreenshot("rail_station_b");
			log("PASS");
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError("[RAIL-TEST] " + message);
		}
	}

	private static KontorBlockEntity station(MinecraftServer server, BlockPos pos) {
		return (KontorBlockEntity) server.overworld().getBlockEntity(pos);
	}

	private static String describe(MinecraftServer server, Setup setup) {
		ServerLevel level = server.overworld();
		StringBuilder text = new StringBuilder();
		if (level.getEntity(setup.conductor()) instanceof CopperGolem golem) {
			var voyage = Captains.voyage(golem);
			text.append("conductor@").append(golem.blockPosition().toShortString()).append(voyage == null ? " idle" : " " + voyage.phase + " manifest=" + voyage.manifest.size() + " wagons=" + voyage.wagons.size())
				.append(golem.getVehicle() != null ? " seated" : "");
		}
		for (UUID id : setup.carts()) {
			if (level.getEntity(id) instanceof CopperChestMinecart cart) {
				text.append(" cart@").append(cart.blockPosition().toShortString()).append(cart.isChained() ? "*" : "");
			}
		}
		text.append(" A=").append(station(server, setup.stationA()).count(Items.SNOWBALL)).append(" B=").append(station(server, setup.stationB()).count(Items.SNOWBALL));
		return text.toString();
	}

	private static int goodsIn(ItemStack stack) {
		return stack.is(Items.SNOWBALL) || CopperChestMinecart.isChain(stack) ? stack.getCount() : 0;
	}

	private static int count(Container container) {
		int sum = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			sum += goodsIn(container.getItem(i));
		}
		return sum;
	}

	/** Snowballs and chains everywhere: stations, carts (and the chains that hold them together), golem hands, the ground. */
	private static int goods(ServerLevel level, Setup setup) {
		int sum = 0;
		AABB area = new AABB(-20, level.getMinY(), -60, 130, level.getMaxY(), 60);
		for (BlockPos pos : new BlockPos[] {setup.stationA(), setup.stationB()}) {
			if (level.getBlockEntity(pos) instanceof KontorBlockEntity station) {
				sum += count(station);
			}
		}
		for (CopperChestMinecart cart : level.getEntitiesOfClass(CopperChestMinecart.class, area)) {
			sum += count(cart);
			if (cart.isChained()) {
				sum += 1;
			}
		}
		for (CopperGolem golem : level.getEntitiesOfClass(CopperGolem.class, area)) {
			sum += goodsIn(golem.getMainHandItem());
		}
		for (net.minecraft.world.entity.item.ItemEntity item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area)) {
			sum += goodsIn(item.getItem());
		}
		return sum;
	}

	private static void rail(ServerLevel level, int x, int y, int z, RailShape shape) {
		BlockPos below = new BlockPos(x, y - 1, z);
		level.setBlock(below, Blocks.STONE.defaultBlockState(), 3);
		level.setBlock(new BlockPos(x, y, z), Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, shape), 3);
	}

	private static UUID worker(ServerLevel level, BlockPos station, double x, int g, double z, Item marker) {
		CopperGolem golem = EntityTypes.COPPER_GOLEM.create(level, EntitySpawnReason.COMMAND);
		golem.setPos(x, g, z);
		golem.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(marker));
		golem.setAttached(HabourAttachments.HARBOUR, station);
		level.addFreshEntity(golem);
		return golem.getUUID();
	}

	private static Setup build(MinecraftServer server) {
		ServerLevel level = server.overworld();
		int g = level.getHeight(Heightmap.Types.WORLD_SURFACE, 0, 0);
		for (int cx = -1; cx <= 8; cx++) {
			for (int cz = -2; cz <= 2; cz++) {
				level.setChunkForced(cx, cz, true);
			}
		}
		for (int x = 0; x <= 90; x++) {
			for (int z = -6; z <= 14; z++) {
				for (int y = g; y <= g + 10; y++) {
					level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
				}
				level.setBlock(new BlockPos(x, g - 1, z), Blocks.STONE.defaultBlockState(), 3);
			}
		}
		// Along z=2 from x=8 to x=40, a bend south at x=41, down x=41 to z=8, a bend east at z=9, then a hill, and on to x=72.
		for (int x = 8; x <= 40; x++) {
			rail(level, x, g, 2, RailShape.EAST_WEST);
		}
		rail(level, 41, g, 2, RailShape.SOUTH_WEST);
		for (int z = 3; z <= 8; z++) {
			rail(level, 41, g, z, RailShape.NORTH_SOUTH);
		}
		rail(level, 41, g, 9, RailShape.NORTH_EAST);
		for (int x = 42; x <= 49; x++) {
			rail(level, x, g, 9, RailShape.EAST_WEST);
		}
		rail(level, 50, g, 9, RailShape.ASCENDING_EAST);
		level.setBlock(new BlockPos(51, g, 9), Blocks.STONE.defaultBlockState(), 3);
		rail(level, 51, g + 1, 9, RailShape.ASCENDING_EAST);
		for (int x = 52; x <= 54; x++) {
			level.setBlock(new BlockPos(x, g, 9), Blocks.STONE.defaultBlockState(), 3);
			rail(level, x, g + 2, 9, RailShape.EAST_WEST);
		}
		level.setBlock(new BlockPos(52, g + 1, 9), Blocks.STONE.defaultBlockState(), 3);
		level.setBlock(new BlockPos(53, g + 1, 9), Blocks.STONE.defaultBlockState(), 3);
		level.setBlock(new BlockPos(54, g + 1, 9), Blocks.STONE.defaultBlockState(), 3);
		level.setBlock(new BlockPos(55, g, 9), Blocks.STONE.defaultBlockState(), 3);
		rail(level, 55, g + 1, 9, RailShape.ASCENDING_WEST);
		rail(level, 56, g, 9, RailShape.ASCENDING_WEST);
		for (int x = 57; x <= 72; x++) {
			rail(level, x, g, 9, RailShape.EAST_WEST);
		}
		BlockPos a = new BlockPos(6, g, 4);
		BlockPos b = new BlockPos(74, g, 11);
		level.setBlock(a, HabourBlocks.STATION.defaultBlockState().setValue(KontorBlock.FACING, Direction.EAST), 3);
		level.setBlock(b, HabourBlocks.STATION.defaultBlockState().setValue(KontorBlock.FACING, Direction.WEST), 3);
		KontorBlockEntity stationA = (KontorBlockEntity) level.getBlockEntity(a);
		for (int i = 0; i < 54; i++) {
			stationA.setItem(i, new ItemStack(Items.SNOWBALL, 16));
		}
		stationA.setItem(53, new ItemStack(Items.IRON_CHAIN, 2));
		List<UUID> carts = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			CopperChestMinecart cart = ModEntities.COPPER_CHEST_MINECART.create(level, EntitySpawnReason.COMMAND);
			cart.setPos(12.5 + i * 3, g, 2.5);
			level.addFreshEntity(cart);
			carts.add(cart.getUUID());
		}
		UUID conductor = worker(level, a, 8.5, g, 4.5, Items.CLOCK);
		List<UUID> hands = new ArrayList<>();
		hands.add(worker(level, a, 8.5, g, 6.5, Items.STRING));
		hands.add(worker(level, a, 9.5, g, 6.5, Items.STRING));
		hands.add(worker(level, b, 72.5, g, 11.5, Items.STRING));
		hands.add(worker(level, b, 72.5, g, 12.5, Items.STRING));
		return new Setup(g, a, b, conductor, hands, carts);
	}
}
