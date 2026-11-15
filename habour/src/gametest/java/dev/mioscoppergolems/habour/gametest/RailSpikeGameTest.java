package dev.mioscoppergolems.habour.gametest;

import dev.mioscoppergolems.entity.CopperChestMinecart;
import dev.mioscoppergolems.registry.ModEntities;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * A physics spike for the railway work, run only with the environment variable HABOUR_SPIKE=1 (and then instead of the
 * main test). A golem rides a locomotive along a track with a curve and a slope, with copper chest minecarts chained
 * behind it; positions are logged once a second.
 */
public class RailSpikeGameTest implements FabricClientGameTest {
	public static boolean enabled() {
		return "1".equals(System.getenv("HABOUR_SPIKE"));
	}

	private static void log(String message) {
		System.out.println("[RAIL-SPIKE] " + message);
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!enabled()) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			context.waitTicks(20);
			List<Object> things = new ArrayList<>();
			int ground = server.computeOnServer(s -> build(s, things));
			log("track built at ground " + ground);
			server.runCommand("tp @p 5 " + (ground + 4) + " 8 -150 30");
			server.runCommand("tick rate 100");
			for (int i = 0; i < 60; i++) {
				context.waitTicks(10);
				server.runOnServer(s -> report(s, things));
			}
			server.runCommand("tick rate 20");
			log("done");
		}
	}

	private static Minecart loco;
	private static final List<CopperChestMinecart> WAGONS = new ArrayList<>();
	private static CopperGolem driver;
	private static boolean riding;
	private static boolean ridingWagon;

	private static void rail(ServerLevel level, int x, int y, int z, RailShape shape) {
		level.setBlock(new BlockPos(x, y - 1, z), Blocks.STONE.defaultBlockState(), 3);
		BlockState state = Blocks.RAIL.defaultBlockState().setValue(net.minecraft.world.level.block.RailBlock.SHAPE, shape);
		level.setBlock(new BlockPos(x, y, z), state, 3);
	}

	private static int build(MinecraftServer server, List<Object> things) {
		ServerLevel level = server.overworld();
		int g = level.getHeight(Heightmap.Types.WORLD_SURFACE, 0, 0);
		for (int cx = -1; cx <= 4; cx++) {
			for (int cz = -2; cz <= 3; cz++) {
				level.setChunkForced(cx, cz, true);
			}
		}
		// A clear box above the ground.
		for (int x = 0; x <= 60; x++) {
			for (int z = -3; z <= 30; z++) {
				for (int y = g; y <= g + 8; y++) {
					level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
				}
				level.setBlock(new BlockPos(x, g - 1, z), Blocks.STONE.defaultBlockState(), 3);
			}
		}
		int y = g;
		// East along z=0 from x=2 to x=20, a curve to the south at x=21, south along x=21 to z=15, a slope up to z=18, flat to z=24.
		for (int x = 2; x <= 20; x++) {
			rail(level, x, y, 0, RailShape.EAST_WEST);
		}
		rail(level, 21, y, 0, RailShape.SOUTH_WEST);
		for (int z = 1; z <= 14; z++) {
			rail(level, 21, y, z, RailShape.NORTH_SOUTH);
		}
		for (int z = 15; z <= 17; z++) {
			y++;
			rail(level, 21, y, z, RailShape.ASCENDING_SOUTH);
		}
		// ascending rails need a support block under the high end: stone fills below
		for (int z = 15; z <= 17; z++) {
			for (int yy = g; yy < g + (z - 14); yy++) {
				level.setBlock(new BlockPos(21, yy, z), Blocks.STONE.defaultBlockState(), 3);
			}
		}
		for (int z = 18; z <= 24; z++) {
			rail(level, 21, y, z, RailShape.NORTH_SOUTH);
		}
		// the locomotive and three wagons, with the driver
		loco = EntityTypes.MINECART.create(level, EntitySpawnReason.COMMAND);
		loco.setPos(8.5, g, 0.5);
		level.addFreshEntity(loco);
		WAGONS.clear();
		Entity previous = loco;
		for (int i = 1; i <= 3; i++) {
			CopperChestMinecart wagon = ModEntities.COPPER_CHEST_MINECART.create(level, EntitySpawnReason.COMMAND);
			wagon.setPos(8.5 - i * 1.6, g, 0.5);
			level.addFreshEntity(wagon);
			wagon.chainTo(previous);
			WAGONS.add(wagon);
			previous = wagon;
		}
		driver = EntityTypes.COPPER_GOLEM.create(level, EntitySpawnReason.COMMAND);
		driver.setPos(8.5, g, 0.5);
		level.addFreshEntity(driver);
		riding = driver.startRiding(loco);
		log("golem rides the locomotive: " + riding);
		// can a golem ride a copper chest cart?
		CopperGolem tester = EntityTypes.COPPER_GOLEM.create(level, EntitySpawnReason.COMMAND);
		tester.setPos(30.5, g, 10.5);
		level.addFreshEntity(tester);
		CopperChestMinecart lone = ModEntities.COPPER_CHEST_MINECART.create(level, EntitySpawnReason.COMMAND);
		lone.setPos(30.5, g, 10.5);
		level.addFreshEntity(lone);
		ridingWagon = tester.startRiding(lone);
		log("golem rides a copper chest cart: " + ridingWagon);
		ServerTickEvents.END_SERVER_TICK.register(spike -> push());
		return g;
	}

	private static void push() {
		if (loco == null || !loco.isAlive()) {
			return;
		}
		Vec3 motion = loco.getDeltaMovement();
		double flat = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
		if (flat < 0.3) {
			Vec3 dir;
			if (flat < 0.02) {
				dir = new Vec3(1, 0, 0);
			} else {
				dir = new Vec3(motion.x, 0, motion.z).normalize();
			}
			loco.setDeltaMovement(motion.add(dir.scale(0.03)));
		}
	}

	private static void report(MinecraftServer server, List<Object> things) {
		StringBuilder text = new StringBuilder();
		text.append("loco ").append(fmt(loco.position())).append(loco.isOnRails() ? " rail" : " OFF").append(" v=").append(String.format("%.2f", loco.getDeltaMovement().horizontalDistance()));
		text.append(" driver aboard=").append(driver.getVehicle() == loco);
		int i = 1;
		for (CopperChestMinecart wagon : WAGONS) {
			text.append(" | w").append(i++).append(' ').append(fmt(wagon.position())).append(wagon.isOnRails() ? " rail" : " OFF");
		}
		log(text.toString());
	}

	private static String fmt(Vec3 v) {
		return String.format("(%.1f,%.1f,%.1f)", v.x, v.y, v.z);
	}
}
