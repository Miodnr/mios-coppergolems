package dev.mioscoppergolems.habour.harbour;

import dev.mioscoppergolems.habour.captain.Voyage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.golem.CopperGolem;

/**
 * Where the captains on a voyage were last seen. Every captain reports its position about once a second, so the route
 * book can say where they are even when they are far from the kontor. Not saved: after a restart a captain reports
 * again as soon as its chunk loads.
 */
public final class Fleet {
	/** A report this old is dropped (the captain was lost or its chunk never loads again). */
	private static final long EXPIRE_TICKS = 24000L;

	public record Report(UUID captain, String dimension, BlockPos origin, int routeId, BlockPos pos, Voyage.Phase phase, long time) {
	}

	private static final Map<UUID, Report> REPORTS = new HashMap<>();

	private Fleet() {
	}

	public static void report(ServerLevel level, CopperGolem golem, Voyage voyage) {
		REPORTS.put(golem.getUUID(), new Report(golem.getUUID(), HarbourRegistry.dimensionOf(level), voyage.origin, voyage.routeId, golem.blockPosition(), voyage.phase, level.getGameTime()));
	}

	public static void forget(CopperGolem golem) {
		REPORTS.remove(golem.getUUID());
	}

	public static void clear() {
		REPORTS.clear();
	}

	/** The captains of a route, the most recently seen first. */
	public static List<Report> of(ServerLevel level, BlockPos origin, int routeId) {
		long now = level.getGameTime();
		String dimension = HarbourRegistry.dimensionOf(level);
		REPORTS.values().removeIf(report -> now - report.time() > EXPIRE_TICKS);
		List<Report> found = new ArrayList<>();
		for (Report report : REPORTS.values()) {
			if (report.routeId() == routeId && report.origin().equals(origin) && report.dimension().equals(dimension)) {
				found.add(report);
			}
		}
		found.sort(Comparator.comparingLong(Report::time).reversed());
		return found;
	}
}
