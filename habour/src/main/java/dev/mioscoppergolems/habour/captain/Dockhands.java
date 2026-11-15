package dev.mioscoppergolems.habour.captain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.mioscoppergolems.api.GolemJob;
import dev.mioscoppergolems.habour.Habour;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The dockhand: a copper golem holding a piece of string (a mooring rope), linked to a kontor like a captain. It does the
 * carrying at its harbour, so captains can stay by their boats: it loads boats that wait at its kontor and unloads
 * boats that arrive there, together with other dockhands and the captain itself.
 */
public final class Dockhands {
	/** The boat a dockhand is working on. {@code seen} is renewed all the time; a stale claim does not count. */
	public record Claim(UUID boat, long seen) {
		public static final Codec<Claim> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(UUIDUtil.CODEC.fieldOf("boat").forGetter(Claim::boat), Codec.LONG.fieldOf("seen").forGetter(Claim::seen)).apply(instance, Claim::new)
		);
	}

	/** Not saved: a dockhand starts afresh after a restart. */
	public static final AttachmentType<Claim> CLAIM = AttachmentRegistry.create(Habour.id("dock_claim"));

	/** A claim older than this many ticks is ignored (the dockhand went away, was unloaded or died). */
	public static final int CLAIM_TICKS = 60;
	private static final double SEARCH_RANGE = 48.0;

	public static final GolemJob JOB = new GolemJob() {
		@Override
		public boolean has(CopperGolem golem) {
			return isDockhand(golem);
		}

		@Override
		public Component name() {
			return Component.translatable("job.mioscoppergolems_habour.dockhand");
		}

		@Override
		public @Nullable BlockPos workplace(CopperGolem golem) {
			return Captains.harbour(golem);
		}

		@Override
		public boolean busy(CopperGolem golem) {
			return golem.getAttached(CLAIM) != null;
		}
	};

	private Dockhands() {
	}

	public static void init() {
	}

	public static boolean isDockhand(CopperGolem golem) {
		return golem.getOffhandItem().is(Items.STRING);
	}

	/** Whether any dockhand is working on this boat right now. */
	public static boolean isCrewed(ServerLevel level, Vec3 near, UUID boat) {
		long now = level.getGameTime();
		AABB area = new AABB(near, near).inflate(SEARCH_RANGE);
		return !level.getEntitiesOfClass(CopperGolem.class, area, golem -> {
			Claim claim = golem.getAttached(CLAIM);
			return claim != null && claim.boat().equals(boat) && now - claim.seen() < CLAIM_TICKS;
		}).isEmpty();
	}

	/** Whether a dockhand is linked to this kontor and loaded (asleep or tired ones count: they will come back). */
	public static boolean anyLinked(ServerLevel level, BlockPos kontor) {
		return !linked(level, kontor).isEmpty();
	}

	public static List<CopperGolem> linked(ServerLevel level, BlockPos kontor) {
		AABB area = new AABB(kontor).inflate(SEARCH_RANGE);
		return level.getEntitiesOfClass(CopperGolem.class, area, golem -> isDockhand(golem) && kontor.equals(Captains.harbour(golem)) && !golem.isLeashed());
	}

	/** Loading a boat is starting work and needs daylight; unloading a boat that has moored is finishing it, and may go on at night. */
	public static boolean canWork(ServerLevel level, CopperGolem golem, Voyage voyage) {
		if (voyage.phase == Voyage.Phase.LOAD) {
			return dev.mioscoppergolems.upkeep.Upkeep.canWork(level, golem);
		}
		return !dev.mioscoppergolems.repair.Errands.isBusy(golem) && dev.mioscoppergolems.upkeep.Upkeep.get(golem).energy() > 0
			&& golem.getWeatherState() != net.minecraft.world.level.block.WeatheringCopper.WeatherState.OXIDIZED && !dev.mioscoppergolems.upkeep.Upkeep.isWaxed(golem);
	}

	/** The captains near a harbour that have dock work for its dockhands: a boat to load, or one to unload. */
	public static List<CopperGolem> work(ServerLevel level, BlockPos harbour) {
		AABB area = new AABB(harbour).inflate(SEARCH_RANGE);
		return level.getEntitiesOfClass(CopperGolem.class, area, captain -> {
			Voyage voyage = Captains.voyage(captain);
			if (voyage == null || !voyage.isDockwork() || !voyage.harbour().equals(harbour)) {
				return false;
			}
			Vessel vessel = Vessel.of(level, voyage);
			if (vessel == null) {
				return false;
			}
			return voyage.phase == Voyage.Phase.LOAD ? !voyage.manifest.isEmpty() && vessel.partWithRoom() != null : !voyage.bringHome && !vessel.isEmpty();
		});
	}
}
