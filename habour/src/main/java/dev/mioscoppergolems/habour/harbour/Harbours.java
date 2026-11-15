package dev.mioscoppergolems.habour.harbour;

import dev.mioscoppergolems.courier.Couriers;
import dev.mioscoppergolems.habour.kontor.KontorBlockEntity;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

/**
 * Keeps harbours working while nobody is around. A harbour with routes that could run is woken up now and then: its
 * chunks load for a few seconds, so its kontor can look at its routes and send boats off. Boats on their way keep the
 * chunks around them loaded themselves.
 */
public final class Harbours {
	/** Ticks between two wake-ups of a harbour that has routes. */
	private static final int WAKE_INTERVAL = 600;
	/** How long a wake-up keeps the harbour's chunks loaded. */
	private static final int AWAKE_TICKS = 160;
	/** Radius 3, like the couriers use: the kontor's chunk and its neighbours tick entities, so captains next to it work. */
	public static final int TICKET_RADIUS = 3;

	private static final Map<String, long[]> WAKE = new HashMap<>();

	private Harbours() {
	}

	public static void tick(ServerLevel level) {
		long now = level.getGameTime();
		if (now % 10 != 0) {
			return;
		}
		HarbourRegistry registry = HarbourRegistry.get(level);
		for (HarbourRegistry.Entry entry : registry.inDimension(level)) {
			if (!entry.active()) {
				continue;
			}
			String key = entry.dimension() + "|" + entry.pos().asLong();
			long[] state = WAKE.computeIfAbsent(key, k -> new long[] {0L, now + Math.floorMod(entry.pos().hashCode(), WAKE_INTERVAL)});
			if (now >= state[1]) {
				state[0] = now + AWAKE_TICKS;
				state[1] = now + WAKE_INTERVAL;
			}
			if (now < state[0]) {
				keepLoaded(level, entry.pos(), TICKET_RADIUS);
			}
		}
	}

	/** Wakes a harbour at once (a redstone pulse, a command), if it is not loaded. */
	public static void wake(ServerLevel level, BlockPos pos) {
		String key = HarbourRegistry.dimensionOf(level) + "|" + pos.asLong();
		long[] state = WAKE.computeIfAbsent(key, k -> new long[] {0L, 0L});
		state[0] = level.getGameTime() + AWAKE_TICKS;
	}

	public static void keepLoaded(ServerLevel level, BlockPos pos, int radius) {
		level.getChunkSource().addTicketWithRadius(Couriers.TICKET, ChunkPos.containing(pos), radius);
	}

	public static void keepLoaded(ServerLevel level, Entity entity, int radius) {
		keepLoaded(level, entity.blockPosition(), radius);
	}

	/** The kontor at this position, if its chunk is loaded and there is one. */
	public static @Nullable KontorBlockEntity kontor(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return null;
		}
		return level.getBlockEntity(pos) instanceof KontorBlockEntity kontor ? kontor : null;
	}
}
