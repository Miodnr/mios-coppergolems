package dev.mioscoppergolems.habour.gametest;

import dev.mioscoppergolems.habour.captain.Captains;
import dev.mioscoppergolems.habour.captain.Dockhands;
import dev.mioscoppergolems.habour.harbour.Fleet;
import dev.mioscoppergolems.habour.menu.RouteMenu;
import dev.mioscoppergolems.habour.captain.Voyage;
import dev.mioscoppergolems.habour.harbour.HarbourRegistry;
import dev.mioscoppergolems.habour.kontor.KontorBlock;
import dev.mioscoppergolems.habour.kontor.KontorBlockEntity;
import dev.mioscoppergolems.habour.registry.HabourAttachments;
import dev.mioscoppergolems.habour.registry.HabourBlocks;
import dev.mioscoppergolems.habour.route.Cargo;
import dev.mioscoppergolems.habour.route.Route;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.vehicle.boat.AbstractChestBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Plays the add-on in a real world: two harbours at the ends of a 60 block channel, boats, captains, a first voyage with
 * iron, then a big shipment that needs two boats at once. Screenshots of the harbour and the route book are taken on the
 * way. Run it with {@code ./gradlew :habour:runClientGameTest}.
 */
public class HabourClientGameTest implements FabricClientGameTest {
	private record Setup(int ground, BlockPos kontorA, BlockPos kontorB, List<UUID> captains) {
	}

	/** The dockhands made in {@link #build}: two at harbour A, one at harbour B. */
	private static final List<UUID> HANDS = new ArrayList<>();

	private static void log(String message) {
		System.out.println("[HABOUR-TEST] " + message);
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (RailSpikeGameTest.enabled() || RailClientGameTest.enabled()) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			KontorBlockEntity.DEBUG = true;
			context.waitTicks(20);
			Setup setup = server.computeOnServer(HabourClientGameTest::build);
			log("built harbours, ground " + setup.ground());
			server.waitFor(s -> HarbourRegistry.get(s.overworld()).find(s.overworld(), setup.kontorB()) != null, 600);
			log("registry knows both harbours");

			int g = setup.ground();
			server.runCommand("tp @p 2 " + (g + 2) + " 6 -140 25");
			context.waitTicks(40);
			context.takeScreenshot("habour_a_kontor");
			server.runCommand("tp @p 20 " + (g + 6) + " 12 -150 35");
			context.waitTicks(20);
			context.takeScreenshot("habour_a_dock");

			// --- scenario 1: one captain, one boat, 100 iron ---
			server.runOnServer(s -> {
				KontorBlockEntity kontor = kontor(s, setup.kontorA());
				Route route = kontor.addRoute(setup.kontorB());
				kontor.setRoute(kontor.indexOf(route.id()), route.withCargo(List.of(new Cargo(Items.IRON_INGOT, 20))).withRepeats(1));
			});
			server.runCommand("tick rate 200");
			boolean done = pollUntil(context, server, setup, 600, s -> kontor(s, setup.kontorB()).count(Items.IRON_INGOT) >= 20
				&& Captains.voyage((CopperGolem) s.overworld().getEntity(setup.captains().get(0))) == null, "iron");
			server.runCommand("tick rate 20");
			check(done, "iron did not arrive and the captain did not return");
			int ironA = server.computeOnServer(s -> kontor(s, setup.kontorA()).count(Items.IRON_INGOT));
			int ironB = server.computeOnServer(s -> kontor(s, setup.kontorB()).count(Items.IRON_INGOT));
			int trips = server.computeOnServer(s -> kontor(s, setup.kontorA()).routes().get(0).trips());
			log("scenario 1: A has " + ironA + " iron, B has " + ironB + ", trips " + trips);
			check(ironA == 180 && ironB == 20 && trips == 1, "iron counts are wrong");
			log("oars moving on the client in " + oarsMoving + " of " + oarSamples + " samples of a sailing boat");
			check(oarSamples >= 5 && oarsMoving * 10 >= oarSamples * 8, "the oars were not moving while the boat sailed");

			if (System.getenv("HABOUR_QUICK") != null) {
				log("PASS (quick)");
				return;
			}
			server.runCommand("tp @p 74 " + (g + 4) + " 8 150 30");
			context.waitTicks(40);
			context.takeScreenshot("habour_b_after");

			// --- the route book on screen ---
			context.waitTicks(200);
			server.runCommand("tp @p 4 " + (g + 1) + " 3 -135 20");
			context.waitTicks(20);
			server.runOnServer(s -> {
				ServerPlayer player = s.getPlayerList().getPlayers().get(0);
				kontor(s, setup.kontorA()).openRoutes(player);
			});
			context.waitForScreen(AbstractContainerScreen.class);
			context.waitTicks(10);
			context.takeScreenshot("habour_routes");
			context.setScreen(() -> null);
			server.runCommand("tp @p 74 " + (g + 1) + " 5 150 20");
			context.waitTicks(20);
			server.runOnServer(s -> {
				ServerPlayer player = s.getPlayerList().getPlayers().get(0);
				kontor(s, setup.kontorB()).openRoutes(player);
			});
			context.waitForScreen(AbstractContainerScreen.class);
			context.waitTicks(10);
			context.takeScreenshot("habour_routes_empty");
			context.setScreen(() -> null);
			context.waitTicks(10);

			// --- scenario 2: two captains, two boats, 600 snowballs ---
			server.runOnServer(s -> {
				ServerLevel level = s.overworld();
				KontorBlockEntity kontor = kontor(s, setup.kontorA());
				int slot = 10;
				for (int i = 0; i < 38; i++) {
					kontor.setItem(slot + i, new ItemStack(Items.SNOWBALL, 16));
				}
				kontor.setItem(slot + 38, new ItemStack(Items.SNOWBALL, 8));
				Route route = kontor.addRoute(setup.kontorB());
				kontor.setRoute(kontor.indexOf(route.id()), route.withCargo(List.of(new Cargo(Items.SNOWBALL, 600))).withRepeats(1));
				setup.captains().add(spawnCaptain(level, setup.kontorA(), 8.5, setup.ground(), -2.5));
			});
			server.runCommand("tp @p 40 " + (g + 12) + " 14 180 40");
			server.runCommand("tick rate 200");
			int[] peak = {0};
			boolean done2 = pollUntil(context, server, setup, 6000, s -> {
				int sailing = 0;
				for (UUID id : setup.captains()) {
					Entity e = s.overworld().getEntity(id);
					if (e instanceof CopperGolem golem && Captains.voyage(golem) != null) {
						sailing++;
					}
				}
				peak[0] = Math.max(peak[0], sailing);
				if (sailing >= 2 && peak[0] == sailing) {
					log("two voyages at once");
				}
				boolean back = true;
				for (UUID id : setup.captains()) {
					back &= s.overworld().getEntity(id) instanceof CopperGolem golem && Captains.voyage(golem) == null;
				}
				return kontor(s, setup.kontorB()).count(Items.SNOWBALL) >= 600 && back;
			}, "snowballs");
			server.runCommand("tick rate 20");
			check(done2, "snowballs did not arrive");
			int ballsA = server.computeOnServer(s -> kontor(s, setup.kontorA()).count(Items.SNOWBALL));
			int tripsTotal = server.computeOnServer(s -> kontor(s, setup.kontorA()).routes().get(1).trips());
			log("scenario 2: A has " + ballsA + " snowballs, trips " + tripsTotal + ", peak boats at sea " + peak[0]);
			check(ballsA == 16 && tripsTotal == 2, "snowball counts are wrong");
			check(peak[0] >= 2, "the two captains never worked at the same time");


			// --- scenario 3: dockhands load and unload, a captain is located, and no item is lost on the way ---
			int[] seen = {0, 0, 0, 0};
			boolean[] flags = new boolean[6];
			UUID[] dockhands = new UUID[2];
			int[] worldTotal = {0};
			server.runOnServer(s -> {
				ServerLevel level = s.overworld();
				KontorBlockEntity kontor = kontor(s, setup.kontorA());
				Route route = kontor.addRoute(setup.kontorB());
				kontor.setRoute(kontor.indexOf(route.id()), route.withCargo(List.of(new Cargo(Items.IRON_INGOT, 100))).withRepeats(1));
				dockhands[0] = HANDS.get(0);
				dockhands[1] = HANDS.get(2);
				worldTotal[0] = goods(level, setup);
				log("scenario 3: " + worldTotal[0] + " goods in the world before the shipment");
				net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(watcher -> {
					ServerLevel w = watcher.overworld();
					if (flags[5]) {
						return;
					}
					int now = goods(w, setup);
					if (now != worldTotal[0]) {
						seen[3]++;
						if (seen[3] <= 3) {
							log("GOODS CHANGED from " + worldTotal[0] + " to " + now);
						}
					}
					for (UUID id : dockhands) {
						if (w.getEntity(id) instanceof CopperGolem hand) {
							flags[0] |= hand.getAttached(Dockhands.CLAIM) != null;
							flags[1] |= !hand.getMainHandItem().isEmpty();
							flags[2] |= hand.getVehicle() != null;
						}
					}
					for (UUID id : setup.captains()) {
						if (w.getEntity(id) instanceof CopperGolem golem && Captains.voyage(golem) != null && Captains.voyage(golem).routeId == 3) {
							flags[3] |= !Fleet.of(w, setup.kontorA(), 3).isEmpty();
						}
					}
				});
			});
			server.runCommand("tick rate 200");
			boolean located = false;
			boolean done3 = false;
			for (int i = 0; i < 4000 && !done3; i++) {
				context.waitTicks(10);
				located |= server.computeOnServer(s -> {
					ServerLevel level = s.overworld();
					ServerPlayer player = s.getPlayerList().getPlayers().get(0);
					if (Fleet.of(level, setup.kontorA(), 3).isEmpty()) {
						return false;
					}
					RouteMenu menu = new RouteMenu(0, player.getInventory(), kontor(s, setup.kontorA()));
					for (int k = 0; k < 3; k++) {
						menu.clickMenuButton(player, RouteMenu.NEXT_ROUTE);
					}
					menu.clickMenuButton(player, RouteMenu.LOCATE);
					menu.broadcastChanges();
					for (UUID id : setup.captains()) {
						if (level.getEntity(id) instanceof CopperGolem golem && golem.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING)) {
							log("located a captain: " + menu.fleetCount() + " out, " + menu.fleetDistance() + " blocks away, direction " + menu.fleetDirection());
							return menu.fleetCount() >= 1;
						}
					}
					return false;
				});
				done3 = server.computeOnServer(s -> kontor(s, setup.kontorB()).count(Items.IRON_INGOT) >= 120
					&& setup.captains().stream().allMatch(id -> s.overworld().getEntity(id) instanceof CopperGolem golem && Captains.voyage(golem) == null));
				if (i % 40 == 0) {
					log("dockhands: " + server.computeOnServer(s -> describe(s, setup)));
				}
			}
			server.runCommand("tick rate 20");
			flags[5] = true;
			int ironB2 = server.computeOnServer(s -> kontor(s, setup.kontorB()).count(Items.IRON_INGOT));
			int fleetLeft = server.computeOnServer(s -> Fleet.of(s.overworld(), setup.kontorA(), 3).size());
			log("scenario 3: done=" + done3 + " B iron " + ironB2 + ", dockhand claimed=" + flags[0] + " carried=" + flags[1] + " rode=" + flags[2]
				+ ", captain reported=" + flags[3] + ", located=" + located + ", goods changed " + seen[3] + " ticks, reports left " + fleetLeft);
			check(done3 && ironB2 == 120, "the shipment with dockhands did not arrive");
			check(flags[0] && flags[1], "the dockhands never carried anything");
			check(!flags[2], "a dockhand got into a boat");
			check(seen[3] == 0, "goods were lost or duplicated while several golems carried them");
			check(flags[3] && located, "the captain could not be located");
			check(fleetLeft == 0, "a finished voyage is still reported");
			int expectedInside = server.computeOnServer(s -> total(kontor(s, setup.kontorB())));
			check(expectedInside == 720, "kontor B should hold 720 items now, has " + expectedInside);

			// --- breaking a kontor drops its goods and removes the harbour ---
			int dropped = server.computeOnServer(s -> {
				ServerLevel level = s.overworld();
				BlockPos pos = setup.kontorB();
				KontorBlockEntity kontor = kontor(s, pos);
				int inside = total(kontor);
				level.destroyBlock(pos, true);
				int onGround = 0;
				for (net.minecraft.world.entity.item.ItemEntity item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(6.0))) {
					if (item.getItem().is(Items.SNOWBALL) || item.getItem().is(Items.IRON_INGOT)) {
						onGround += item.getItem().getCount();
					}
				}
				log("kontor held " + inside + ", " + onGround + " lie on the ground, still registered: " + (HarbourRegistry.get(level).find(level, pos) != null));
				return onGround == inside && HarbourRegistry.get(level).find(level, pos) == null ? inside : -1;
			});
			check(dropped == 720, "breaking the kontor lost goods or left the harbour registered");
			log("PASS");
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError("[HABOUR-TEST] " + message);
		}
	}

	private static KontorBlockEntity kontor(MinecraftServer server, BlockPos pos) {
		return (KontorBlockEntity) server.overworld().getBlockEntity(pos);
	}

	/** Waits (in client ticks) until the condition holds on the server; logs progress now and then. */
	private static boolean pollUntil(ClientGameTestContext context, TestServerContext server, Setup setup, int clientTicks, java.util.function.Predicate<MinecraftServer> condition, String what) {
		for (int i = 0; i < clientTicks; i++) {
			context.waitTicks(10);
			sampleOars(context, server);
			if (server.computeOnServer(condition::test)) {
				log(what + " done after " + i * 10 + " client ticks");
				return true;
			}
			if (i % 20 == 0) {
				log(what + ": " + server.computeOnServer(s -> describe(s, setup)));
			}
		}
		return false;
	}

	private static int oarSamples;
	private static int oarsMoving;

	/** Looks at the boats that are really sailing and checks on the client that their oars are moving. */
	private static void sampleOars(ClientGameTestContext context, TestServerContext server) {
		List<UUID> sailing = server.computeOnServer(s -> {
			List<UUID> found = new ArrayList<>();
			for (Entity e : s.overworld().getEntitiesOfClass(AbstractChestBoat.class, new net.minecraft.world.phys.AABB(-400, -64, -400, 400, 320, 400))) {
				if (e.isVehicle() && e.getDeltaMovement().horizontalDistance() > 0.08) {
					found.add(e.getUUID());
				}
			}
			return found;
		});
		for (UUID id : sailing) {
			boolean rowing = context.computeOnClient(mc -> mc.level != null && mc.level.getEntity(id) instanceof net.minecraft.world.entity.vehicle.boat.AbstractBoat boat && boat.getPaddleState(0) && boat.getPaddleState(1));
			if (oarSamples < 6) {
				log("oar sample: client rowing=" + rowing + ", server paddle=" + server.computeOnServer(sv -> sv.overworld().getEntity(id) instanceof net.minecraft.world.entity.vehicle.boat.AbstractBoat b && b.getPaddleState(0)));
			}
			oarSamples++;
			if (rowing) {
				oarsMoving++;
			}
		}
	}

	private static String describe(MinecraftServer server, Setup setup) {
		ServerLevel level = server.overworld();
		StringBuilder text = new StringBuilder();
		text.append("A=").append(total(kontor(server, setup.kontorA())));
		text.append(" B=").append(total(kontor(server, setup.kontorB())));
		for (UUID id : setup.captains()) {
			if (level.getEntity(id) instanceof CopperGolem golem) {
				Voyage voyage = Captains.voyage(golem);
				text.append(" captain@").append(golem.blockPosition().toShortString()).append(voyage == null ? " idle" : " " + voyage.phase + " manifest=" + voyage.manifest.size())
					.append(golem.getVehicle() != null ? " aboard" : "").append(" hand=").append(golem.getMainHandItem());
			}
		}
		text.append(" status=").append(kontor(server, setup.kontorA()).routes().stream().map(r -> kontor(server, setup.kontorA()).statusOf(r.id()).name()).toList());
		return text.toString();
	}

	private static int total(KontorBlockEntity kontor) {
		int sum = 0;
		for (int i = 0; i < kontor.getContainerSize(); i++) {
			sum += kontor.getItem(i).getCount();
		}
		return sum;
	}

	private static UUID spawnCaptain(ServerLevel level, BlockPos kontor, double x, int g, double z) {
		return spawnWorker(level, kontor, x, g, z, Items.SPYGLASS);
	}

	/** Iron and snowballs everywhere: kontors, boats, golem hands and the ground. */
	private static int goods(ServerLevel level, Setup setup) {
		int sum = 0;
		net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(-20, level.getMinY(), -60, 120, level.getMaxY(), 60);
		for (BlockPos pos : new BlockPos[] {setup.kontorA(), setup.kontorB()}) {
			if (level.getBlockEntity(pos) instanceof KontorBlockEntity kontor) {
				sum += count(kontor);
			}
		}
		for (AbstractChestBoat boat : level.getEntitiesOfClass(AbstractChestBoat.class, area)) {
			sum += count(boat);
		}
		for (CopperGolem golem : level.getEntitiesOfClass(CopperGolem.class, area)) {
			sum += goodsIn(golem.getMainHandItem());
		}
		for (net.minecraft.world.entity.item.ItemEntity item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area)) {
			sum += goodsIn(item.getItem());
		}
		return sum;
	}

	private static int count(net.minecraft.world.Container container) {
		int sum = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			sum += goodsIn(container.getItem(i));
		}
		return sum;
	}

	private static int goodsIn(ItemStack stack) {
		return stack.is(Items.IRON_INGOT) || stack.is(Items.SNOWBALL) ? stack.getCount() : 0;
	}

	private static UUID spawnWorker(ServerLevel level, BlockPos kontor, double x, int g, double z, net.minecraft.world.item.Item marker) {
		CopperGolem golem = EntityTypes.COPPER_GOLEM.create(level, EntitySpawnReason.COMMAND);
		golem.setPos(x, g, z);
		golem.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(marker));
		golem.setAttached(HabourAttachments.HARBOUR, kontor);
		level.addFreshEntity(golem);
		return golem.getUUID();
	}

	private static Setup build(MinecraftServer server) {
		ServerLevel level = server.overworld();
		int g = level.getHeight(Heightmap.Types.WORLD_SURFACE, 0, 0);
		for (int cx = -1; cx <= 5; cx++) {
			for (int cz = -2; cz <= 1; cz++) {
				level.setChunkForced(cx, cz, true);
			}
		}
		// A channel 9 wide and 2 deep from x=10 to x=70; harbours stand on the land at both ends.
		for (int x = 10; x <= 70; x++) {
			for (int z = -4; z <= 4; z++) {
				level.setBlock(new BlockPos(x, g - 1, z), Blocks.WATER.defaultBlockState(), 3);
				level.setBlock(new BlockPos(x, g - 2, z), Blocks.WATER.defaultBlockState(), 3);
			}
		}
		// A headland in the middle that the boats have to sail around.
		for (int x = 38; x <= 42; x++) {
			for (int z = -4; z <= 1; z++) {
				level.setBlock(new BlockPos(x, g - 1, z), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
				level.setBlock(new BlockPos(x, g - 2, z), Blocks.DIRT.defaultBlockState(), 3);
			}
		}
		BlockPos a = new BlockPos(6, g, 0);
		BlockPos b = new BlockPos(74, g, 0);
		level.setBlock(a, HabourBlocks.KONTOR.defaultBlockState().setValue(KontorBlock.FACING, Direction.EAST), 3);
		level.setBlock(b, HabourBlocks.KONTOR.defaultBlockState().setValue(KontorBlock.FACING, Direction.WEST), 3);
		level.setBlock(new BlockPos(9, g, -2), HabourBlocks.MOORING_POST.defaultBlockState(), 3);
		level.setBlock(new BlockPos(9, g, 2), HabourBlocks.MOORING_POST.defaultBlockState(), 3);
		level.setBlock(new BlockPos(71, g, -2), HabourBlocks.MOORING_POST.defaultBlockState(), 3);
		level.setBlock(new BlockPos(71, g, 2), HabourBlocks.MOORING_POST.defaultBlockState(), 3);
		KontorBlockEntity kontorA = (KontorBlockEntity) level.getBlockEntity(a);
		kontorA.setItem(0, new ItemStack(Items.IRON_INGOT, 64));
		kontorA.setItem(1, new ItemStack(Items.IRON_INGOT, 64));
		kontorA.setItem(2, new ItemStack(Items.IRON_INGOT, 64));
		kontorA.setItem(3, new ItemStack(Items.IRON_INGOT, 8));
		for (int boat = 0; boat < 2; boat++) {
			AbstractChestBoat chestBoat = EntityTypes.OAK_CHEST_BOAT.create(level, EntitySpawnReason.COMMAND);
			chestBoat.setPos(11.5, g - 0.5, boat == 0 ? -2.5 : 2.5);
			level.addFreshEntity(chestBoat);
		}
		HANDS.clear();
		HANDS.add(spawnWorker(level, a, 5.5, g, 3.5, Items.STRING));
		HANDS.add(spawnWorker(level, a, 5.5, g, -3.5, Items.STRING));
		HANDS.add(spawnWorker(level, b, 72.5, g, 3.5, Items.STRING));
		List<UUID> captains = new ArrayList<>();
		captains.add(spawnCaptain(level, a, 7.5, g, 2.5));
		return new Setup(g, a, b, captains);
	}
}
