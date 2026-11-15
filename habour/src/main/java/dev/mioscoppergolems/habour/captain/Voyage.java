package dev.mioscoppergolems.habour.captain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.mioscoppergolems.habour.route.Cargo;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.StringRepresentable;

/**
 * A captain's or conductor's trip with one vessel (a chest boat, or a train of chest minecarts): load the manifest at the
 * origin, travel to the destination, unload, travel home. It is saved with the golem, so a trip survives restarts; the
 * vessel is found again by its ids.
 */
public final class Voyage {
	public enum Phase implements StringRepresentable {
		/** Trains only: the conductor couples extra carts to the train with chains from the station. */
		COUPLE("couple"),
		LOAD("load"),
		SAIL_OUT("sail_out"),
		UNLOAD("unload"),
		SAIL_BACK("sail_back"),
		UNLOAD_HOME("unload_home");

		public static final Codec<Phase> CODEC = StringRepresentable.fromEnum(Phase::values);

		private final String key;

		Phase(String key) {
			this.key = key;
		}

		@Override
		public String getSerializedName() {
			return this.key;
		}
	}

	public static final Codec<Voyage> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
			BlockPos.CODEC.fieldOf("origin").forGetter(voyage -> voyage.origin),
			BlockPos.CODEC.fieldOf("dest").forGetter(voyage -> voyage.dest),
			Codec.INT.fieldOf("route").forGetter(voyage -> voyage.routeId),
			UUIDUtil.CODEC.fieldOf("boat").forGetter(voyage -> voyage.boat),
			Phase.CODEC.fieldOf("phase").forGetter(voyage -> voyage.phase),
			Cargo.CODEC.listOf().fieldOf("manifest").forGetter(voyage -> voyage.manifest),
			Codec.INT.fieldOf("delivered").forGetter(voyage -> voyage.delivered),
			Codec.BOOL.optionalFieldOf("bring_home", false).forGetter(voyage -> voyage.bringHome),
			UUIDUtil.CODEC.listOf().optionalFieldOf("wagons", List.of()).forGetter(voyage -> voyage.wagons),
			UUIDUtil.CODEC.listOf().optionalFieldOf("couple", List.of()).forGetter(voyage -> voyage.couple)
		).apply(instance, Voyage::new)
	);

	public final BlockPos origin;
	public final BlockPos dest;
	public final int routeId;
	/** The boat, or the lead cart of a train. */
	public UUID boat;
	public Phase phase;
	/** What is still to be fetched from the origin kontor and put in the vessel. */
	public final List<Cargo> manifest;
	/** Items put into the destination kontor so far. */
	public int delivered;
	/** The far kontor stayed full: the rest of the cargo goes back home (shared by the captain and its dockhands). */
	public boolean bringHome;
	/** Trains: the carts chained behind the lead cart, front to back. */
	public final List<UUID> wagons;
	/** Trains: carts still to be coupled on at the station, in order. */
	public final List<UUID> couple;

	public Voyage(BlockPos origin, BlockPos dest, int routeId, UUID boat, Phase phase, List<Cargo> manifest, int delivered) {
		this(origin, dest, routeId, boat, phase, manifest, delivered, false, List.of(), List.of());
	}

	public Voyage(BlockPos origin, BlockPos dest, int routeId, UUID boat, Phase phase, List<Cargo> manifest, int delivered, boolean bringHome, List<UUID> wagons, List<UUID> couple) {
		this.origin = origin;
		this.dest = dest;
		this.routeId = routeId;
		this.boat = boat;
		this.phase = phase;
		this.manifest = new ArrayList<>(manifest);
		this.delivered = delivered;
		this.bringHome = bringHome;
		this.wagons = new ArrayList<>(wagons);
		this.couple = new ArrayList<>(couple);
	}

	/** Phases in which the cargo is carried between the vessel and a kontor, by dockhands. */
	public boolean isDockwork() {
		return this.phase == Phase.LOAD || this.phase == Phase.UNLOAD || this.phase == Phase.UNLOAD_HOME;
	}

	public boolean isSailing() {
		return this.phase == Phase.SAIL_OUT || this.phase == Phase.SAIL_BACK;
	}

	/** The kontor the golem is working at in this phase (the one it fetches from or delivers to). */
	public BlockPos harbour() {
		return switch (this.phase) {
			case COUPLE, LOAD, UNLOAD_HOME, SAIL_BACK -> this.origin;
			case UNLOAD, SAIL_OUT -> this.dest;
		};
	}

	/** Where the vessel is heading while travelling. */
	public BlockPos target() {
		return this.phase == Phase.SAIL_OUT ? this.dest : this.origin;
	}

	/** A train that was turned around: a new lead cart and the carts behind it. */
	public void setTrain(UUID lead, List<UUID> behind) {
		this.boat = lead;
		this.wagons.clear();
		this.wagons.addAll(behind);
	}

	/** Every cart of a train, lead first. */
	public List<UUID> train() {
		List<UUID> all = new ArrayList<>();
		all.add(this.boat);
		all.addAll(this.wagons);
		return all;
	}
}
