package dev.mioscoppergolems.habour.kontor;

import dev.mioscoppergolems.api.MiosCoppergolemsApi;
import dev.mioscoppergolems.habour.Habour;
import dev.mioscoppergolems.habour.captain.Captains;
import dev.mioscoppergolems.habour.captain.Conductors;
import dev.mioscoppergolems.habour.captain.Dockhands;
import dev.mioscoppergolems.habour.captain.Voyage;
import dev.mioscoppergolems.habour.harbour.Berths;
import dev.mioscoppergolems.habour.harbour.HarbourRegistry;
import dev.mioscoppergolems.habour.harbour.Harbours;
import dev.mioscoppergolems.habour.registry.HabourAttachments;
import dev.mioscoppergolems.habour.registry.HabourBlockEntities;
import dev.mioscoppergolems.habour.route.Cargo;
import dev.mioscoppergolems.habour.route.Route;
import dev.mioscoppergolems.habour.route.Status;
import dev.mioscoppergolems.habour.route.Trigger;
import dev.mioscoppergolems.entity.CopperChestMinecart;
import dev.mioscoppergolems.habour.rail.Trains;
import dev.mioscoppergolems.upkeep.Upkeep;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.vehicle.boat.AbstractChestBoat;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The warehouse and the office of a harbour: 54 slots of goods, and the routes that send them to other harbours. Every
 * second it looks at its routes; one that is due starts a run, and a run sends off one boat at a time with a captain
 * and a manifest, as long as there are cargo, an idle captain and a free boat.
 */
public class KontorBlockEntity extends BaseContainerBlockEntity {
	/** Switched on by the game test to log why captains are or are not sent. */
	public static boolean DEBUG;
	public static final int SIZE = 54;
	public static final int MAX_ROUTES = 8;
	/** A chest boat has 27 slots. */
	public static final int BOAT_SLOTS = 27;
	/** How far from the kontor captains of this harbour are looked for. */
	public static final int CAPTAIN_RANGE = 48;
	private static final int CHECK_INTERVAL = 20;
	private static final int ANCHOR_CACHE_TICKS = 100;
	/** A run that has found nothing to send for this long is called off. */
	private static final long RUN_PATIENCE = 24000L;
	/** A boat that has not reported back after this long is counted as lost. */
	private static final long IN_FLIGHT_PATIENCE = 72000L;

	private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
	private final List<Route> routes = new ArrayList<>();
	private int nextRouteId = 1;
	private boolean armed;
	private boolean powered;
	private final Map<Integer, Status> status = new HashMap<>();
	private List<BlockPos> anchors = List.of();
	private long anchorsAt = Long.MIN_VALUE / 2;
	private long runStalledSince = -1L;
	/** Boats on their way (or home again and not yet reported) per route id, so the route book can show it. */
	private final Map<Integer, Integer> inFlight = new HashMap<>();

	public KontorBlockEntity(BlockPos pos, BlockState state) {
		super(HabourBlockEntities.KONTOR, pos, state);
	}

	// --- container ---

	@Override
	protected Component getDefaultName() {
		return Component.translatable("container.mioscoppergolems_habour.kontor");
	}

	@Override
	public int getContainerSize() {
		return SIZE;
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return this.items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> items) {
		this.items = items;
	}

	@Override
	protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
		return ChestMenu.sixRows(containerId, inventory, this);
	}

	/** The harbour's name: the kontor's custom name, or its coordinates. */
	/** Opens the route book for a player (the block does this on sneak + right-click). */
	public void openRoutes(net.minecraft.world.entity.player.Player player) {
		player.openMenu(new net.minecraft.world.SimpleMenuProvider(
			(containerId, inventory, p) -> new dev.mioscoppergolems.habour.menu.RouteMenu(containerId, inventory, this),
			Component.translatable("container.mioscoppergolems_habour.routes", this.harbourName())
		));
	}

	public String harbourName() {
		Component custom = this.getCustomName();
		if (custom != null && !custom.getString().isBlank()) {
			return custom.getString();
		}
		return defaultName(this.worldPosition);
	}

	public static String defaultName(BlockPos pos) {
		return "Harbour " + pos.getX() + " " + pos.getZ();
	}

	public int count(Item item) {
		int total = 0;
		for (ItemStack stack : this.items) {
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	// --- saving ---

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, this.items);
		this.routes.clear();
		input.read("routes", Route.CODEC.listOf()).ifPresent(this.routes::addAll);
		this.nextRouteId = input.getIntOr("next_route_id", 1);
		this.armed = input.getBooleanOr("armed", false);
		this.powered = input.getBooleanOr("powered", false);
		this.inFlight.clear();
		int[] flight = input.getIntArray("in_flight").orElse(new int[0]);
		for (int i = 0; i + 1 < flight.length; i += 2) {
			this.inFlight.put(flight[i], flight[i + 1]);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, this.items);
		output.store("routes", Route.CODEC.listOf(), List.copyOf(this.routes));
		output.putInt("next_route_id", this.nextRouteId);
		output.putBoolean("armed", this.armed);
		output.putBoolean("powered", this.powered);
		int[] flight = new int[this.inFlight.size() * 2];
		int at = 0;
		for (Map.Entry<Integer, Integer> entry : this.inFlight.entrySet()) {
			flight[at++] = entry.getKey();
			flight[at++] = entry.getValue();
		}
		output.putIntArray("in_flight", flight);
	}

	// --- routes (edited by the route screen and the commands) ---

	public List<Route> routes() {
		return this.routes;
	}

	public Status statusOf(int routeId) {
		return this.status.getOrDefault(routeId, Status.PAUSED);
	}

	public int indexOf(int routeId) {
		for (int i = 0; i < this.routes.size(); i++) {
			if (this.routes.get(i).id() == routeId) {
				return i;
			}
		}
		return -1;
	}

	public @Nullable Route addRoute(BlockPos dest) {
		if (this.routes.size() >= MAX_ROUTES) {
			return null;
		}
		Route route = Route.create(this.nextRouteId++, dest);
		this.routes.add(route);
		this.changed();
		return route;
	}

	public void setRoute(int index, Route route) {
		if (index >= 0 && index < this.routes.size()) {
			this.routes.set(index, route);
			this.changed();
		}
	}

	public void removeRoute(int index) {
		if (index >= 0 && index < this.routes.size()) {
			this.status.remove(this.routes.get(index).id());
			this.routes.remove(index);
			this.changed();
		}
	}

	/** Starts a run now, whatever the timer says. */
	public void runNow(int index, long now) {
		if (index < 0 || index >= this.routes.size()) {
			return;
		}
		Route route = this.routes.get(index);
		if (route.running()) {
			return;
		}
		if (!route.hasRunsLeft()) {
			route = route.withRepeats(1);
		}
		this.routes.set(index, route.withEnabled(true).startRun(now));
		this.runStalledSince = -1L;
		this.changed();
	}

	public void changed() {
		this.setChanged();
		if (this.level instanceof ServerLevel serverLevel) {
			this.updateRegistry(serverLevel);
		}
	}

	private void updateRegistry(ServerLevel level) {
		boolean active = false;
		for (Route route : this.routes) {
			active |= route.enabled() && (route.hasRunsLeft() || route.running());
		}
		HarbourRegistry.get(level).put(level, this.worldPosition, this.harbourName(), active, this.isStation());
	}

	/** Redstone: a rising edge arms the routes that wait for a pulse. */
	public void setPowered(ServerLevel level, boolean powered) {
		if (powered && !this.powered) {
			this.armed = true;
			Harbours.wake(level, this.worldPosition);
		}
		if (powered != this.powered) {
			this.powered = powered;
			this.setChanged();
		}
	}

	// --- dispatching ---

	public static void serverTick(Level level, BlockPos pos, BlockState state, KontorBlockEntity kontor) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		long now = level.getGameTime();
		if ((now + pos.hashCode()) % CHECK_INTERVAL != 0) {
			return;
		}
		kontor.updateRegistry(serverLevel);
		if (!kontor.routes.isEmpty()) {
			kontor.dispatch(serverLevel, now);
		}
	}

	private List<BlockPos> anchors(ServerLevel level, long now) {
		if (now - this.anchorsAt >= ANCHOR_CACHE_TICKS) {
			this.anchors = Berths.anchors(level, this.worldPosition);
			this.anchorsAt = now;
		}
		return this.anchors;
	}

	/** A station sends trains with conductors; a kontor sends boats with captains. */
	public boolean isStation() {
		return this.getBlockState().getBlock() instanceof StationBlock;
	}

	/** The captains (or conductors, at a station) of this harbour that are loaded. */
	public List<CopperGolem> captains(ServerLevel level) {
		AABB area = new AABB(this.worldPosition).inflate(CAPTAIN_RANGE);
		boolean rail = this.isStation();
		return level.getEntitiesOfClass(CopperGolem.class, area, golem -> (rail ? Conductors.isConductor(golem) : Captains.isCaptain(golem)) && this.worldPosition.equals(Captains.harbour(golem)));
	}

	private int countChains() {
		int sum = 0;
		for (ItemStack stack : this.items) {
			if (CopperChestMinecart.isChain(stack)) {
				sum += stack.getCount();
			}
		}
		return sum;
	}

	/** Captains do not carry: a voyage needs a dockhand of this harbour that can work. */
	private boolean hasDockhand(ServerLevel level) {
		for (CopperGolem hand : Dockhands.linked(level, this.worldPosition)) {
			if (!hand.isPanicking() && Upkeep.canWork(level, hand)) {
				return true;
			}
		}
		return false;
	}

	private void dispatch(ServerLevel level, long now) {
		if (DEBUG && now % 100 == 0) {
			for (CopperGolem hand : Dockhands.linked(level, this.worldPosition)) {
				Habour.LOGGER.info("[HABOUR-DBG] dockhand {} claim={} hand={} canWork={}", hand.blockPosition().toShortString(), hand.getAttached(Dockhands.CLAIM) != null, hand.getMainHandItem(), Upkeep.canWork(level, hand));
			}
		}
		List<CopperGolem> captains = this.captains(level);
		List<CopperGolem> idle = new ArrayList<>();
		Map<Integer, Voyage.Phase> out = new HashMap<>();
		Map<Item, Integer> reserved = new HashMap<>();
		int[] chainsReserved = {0};
		for (CopperGolem captain : captains) {
			Voyage voyage = Captains.voyage(captain);
			if (DEBUG && now % 100 == 0) {
				Habour.LOGGER.info("[HABOUR-DBG] captain {} voyage={} leashed={} panicking={} canWork={} energy={}", captain.blockPosition(), voyage != null, captain.isLeashed(),
					captain.isPanicking(), Upkeep.canWork(level, captain), Upkeep.get(captain).energy());
			}
			if (voyage == null) {
				if (!captain.isLeashed() && !captain.isPanicking() && Upkeep.canWork(level, captain)) {
					idle.add(captain);
				}
				continue;
			}
			out.put(voyage.routeId, voyage.phase);
			chainsReserved[0] += voyage.couple.size();
			if (voyage.phase == Voyage.Phase.LOAD || voyage.phase == Voyage.Phase.COUPLE) {
				for (Cargo cargo : voyage.manifest) {
					reserved.merge(cargo.item(), cargo.amount(), Integer::sum);
				}
			}
		}
		List<AbstractChestBoat> boats = null;
		List<Trains.Train> trains = null;
		for (int i = 0; i < this.routes.size(); i++) {
			Route route = this.routes.get(i);
			Status shown = this.statusOf(route.id());
			if (!route.enabled()) {
				shown = Status.PAUSED;
			} else if (HarbourRegistry.get(level).find(level, route.dest()) == null || route.dest().equals(this.worldPosition)) {
				shown = Status.NO_DESTINATION;
			} else {
				if (!route.running()) {
					if (!route.hasRunsLeft()) {
						shown = Status.FINISHED;
					} else if (route.trigger() == Trigger.REDSTONE ? !this.armed : now - route.lastRun() < route.intervalTicks()) {
						shown = route.trigger() == Trigger.REDSTONE ? Status.WAITING_REDSTONE : Status.WAITING_TIMER;
					} else if (!this.hasStock(route, reserved)) {
						shown = Status.NO_STOCK;
					} else {
						route = route.startRun(now);
						this.routes.set(i, route);
						if (route.trigger() == Trigger.REDSTONE) {
							this.armed = false;
						}
						this.runStalledSince = -1L;
						this.setChanged();
					}
				}
				if (route.running()) {
					if (boats == null) {
						boats = this.isStation() ? new ArrayList<>() : Berths.freeBoats(level, this.worldPosition, this.anchors(level, now));
						trains = this.isStation() ? Trains.free(level, this.worldPosition) : new ArrayList<>();
					}
					shown = this.send(level, i, route, idle, boats, trains, reserved, chainsReserved, now);
					if (DEBUG && now % 100 == 0) {
						Habour.LOGGER.info("[HABOUR-DBG] route {} -> {} idle={} boats={} pending={}", route.id(), shown, idle.size(), this.isStation() ? trains.size() : boats.size(), route.pending());
					}
				}
			}
			if (!route.running() && (shown == Status.WAITING_TIMER || shown == Status.WAITING_REDSTONE || shown == Status.FINISHED || shown == Status.NO_STOCK)) {
				Voyage.Phase phase = out.get(route.id());
				if (phase != null) {
					shown = phase == Voyage.Phase.LOAD ? Status.LOADING : Status.SAILING;
				} else if (this.inFlight.getOrDefault(route.id(), 0) > 0) {
					if (now - route.lastRun() > IN_FLIGHT_PATIENCE) {
						// A captain that was lost at sea never reported back.
						this.inFlight.remove(route.id());
					} else {
						shown = Status.SAILING;
					}
				}
			}
			this.status.put(route.id(), shown);
		}
	}

	/** Whether the kontor has what a run of this route wants (free of what captains are already loading). */
	private boolean hasStock(Route route, Map<Item, Integer> reserved) {
		if (route.cargo().isEmpty()) {
			return !this.isEmpty();
		}
		boolean any = false;
		for (Cargo cargo : route.cargo()) {
			int available = this.count(cargo.item()) - reserved.getOrDefault(cargo.item(), 0);
			if (route.waitForFull() && available < (cargo.isAll() ? 1 : cargo.amount())) {
				return false;
			}
			any |= available >= 1;
		}
		return any;
	}

	/** Tries to send one boat off for a running route. Returns what the route is waiting for. */
	private Status send(ServerLevel level, int index, Route route, List<CopperGolem> idle, List<AbstractChestBoat> boats, List<Trains.Train> trains, Map<Item, Integer> reserved, int[] chainsReserved, long now) {
		boolean rail = this.isStation();
		List<Cargo> manifest = this.plan(route, reserved, rail ? 1000 : BOAT_SLOTS);
		if (manifest.isEmpty()) {
			// Nothing more to send. A run that waits for exact amounts is patient; the others are over.
			if (this.runStalledSince < 0) {
				this.runStalledSince = now;
			}
			boolean takesAll = route.cargo().isEmpty() || route.pending().stream().anyMatch(Cargo::isAll);
			if (!route.waitForFull() || takesAll || now - this.runStalledSince > RUN_PATIENCE) {
				this.finishRun(index, route);
			}
			return Status.NO_STOCK;
		}
		this.runStalledSince = -1L;
		if (idle.isEmpty()) {
			return rail ? Status.NO_CONDUCTOR : Status.NO_CAPTAIN;
		}
		if (rail ? trains.isEmpty() : boats.isEmpty()) {
			return rail ? Status.NO_TRAIN : Status.NO_BOAT;
		}
		if (!this.hasDockhand(level)) {
			return Status.NO_DOCKHAND;
		}
		CopperGolem captain = idle.remove(0);
		Voyage voyage;
		if (rail) {
			Trains.Train train = trains.get(0);
			for (Trains.Train candidate : trains) {
				if (candidate.slots() > train.slots()) {
					train = candidate;
				}
			}
			trains.remove(train);
			// Couple more carts to the train if the cargo needs the room and the station has chains for it.
			int needed = slotsOf(manifest);
			int slots = train.slots();
			int chains = this.countChains() - chainsReserved[0];
			List<CopperChestMinecart> couple = new ArrayList<>();
			for (CopperChestMinecart spare : Trains.spares(trains, train)) {
				if (slots >= needed || couple.size() >= chains || train.wagons().size() + couple.size() + 1 >= Trains.MAX_CARTS) {
					break;
				}
				couple.add(spare);
				slots += spare.getContainerSize();
			}
			trains.removeIf(other -> other.wagons().isEmpty() && couple.contains(other.lead()));
			manifest = this.plan(route, reserved, slots);
			List<java.util.UUID> wagons = new ArrayList<>();
			for (CopperChestMinecart wagon : train.wagons()) {
				wagons.add(wagon.getUUID());
			}
			List<java.util.UUID> coupling = new ArrayList<>();
			for (CopperChestMinecart cart : couple) {
				coupling.add(cart.getUUID());
				Berths.take(level, cart);
			}
			chainsReserved[0] += coupling.size();
			voyage = new Voyage(this.worldPosition, route.dest(), route.id(), train.lead().getUUID(), coupling.isEmpty() ? Voyage.Phase.LOAD : Voyage.Phase.COUPLE, manifest, 0, false, wagons, coupling);
			for (CopperChestMinecart cart : train.all()) {
				Berths.take(level, cart);
			}
		} else {
			AbstractChestBoat boat = this.nearestBoat(boats, captain);
			boats.remove(boat);
			voyage = new Voyage(this.worldPosition, route.dest(), route.id(), boat.getUUID(), Voyage.Phase.LOAD, manifest, 0);
			Berths.take(level, boat);
		}
		captain.setAttached(HabourAttachments.VOYAGE, voyage);
		this.inFlight.merge(route.id(), 1, Integer::sum);
		for (Cargo cargo : manifest) {
			reserved.merge(cargo.item(), cargo.amount(), Integer::sum);
		}
		level.playSound(null, this.worldPosition, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 0.8F, 1.3F);
		MiosCoppergolemsApi.awardGuideNearby(level, Vec3.atCenterOf(this.worldPosition), Habour.id("guide/route"));

		// What the manifest took off the run's list.
		Route updated = route.withPending(this.reduce(route, manifest));
		this.routes.set(index, updated);
		if (!route.cargo().isEmpty() && updated.pending().isEmpty()) {
			this.finishRun(index, updated);
		}
		this.setChanged();
		return Status.LOADING;
	}

	/** How many slots a manifest takes up. */
	private static int slotsOf(List<Cargo> manifest) {
		int slots = 0;
		for (Cargo cargo : manifest) {
			int stack = Math.max(1, cargo.item().getDefaultMaxStackSize());
			slots += (cargo.amount() + stack - 1) / stack;
		}
		return slots;
	}

	private AbstractChestBoat nearestBoat(List<AbstractChestBoat> boats, CopperGolem captain) {
		AbstractChestBoat best = boats.get(0);
		for (AbstractChestBoat boat : boats) {
			if (boat.distanceToSqr(captain) < best.distanceToSqr(captain)) {
				best = boat;
			}
		}
		return best;
	}

	private void finishRun(int index, Route route) {
		this.routes.set(index, route.endRun());
		this.runStalledSince = -1L;
		this.changed();
	}

	/**
	 * What one boat takes next: the route's pending cargo (or everything in the kontor) limited by what is in stock and
	 * not already promised to a captain, and by the 27 slots of a chest boat.
	 */
	private List<Cargo> plan(Route route, Map<Item, Integer> reserved, int boatSlots) {
		Map<Item, Integer> available = new LinkedHashMap<>();
		for (ItemStack stack : this.items) {
			if (!stack.isEmpty()) {
				available.merge(stack.getItem(), stack.getCount(), Integer::sum);
			}
		}
		available.replaceAll((item, count) -> count - reserved.getOrDefault(item, 0));
		List<Cargo> wanted = new ArrayList<>();
		if (route.cargo().isEmpty()) {
			for (Map.Entry<Item, Integer> entry : available.entrySet()) {
				wanted.add(new Cargo(entry.getKey(), Cargo.ALL));
			}
		} else {
			wanted.addAll(route.pending());
		}
		List<Cargo> manifest = new ArrayList<>();
		int slots = boatSlots;
		for (Cargo cargo : wanted) {
			int have = available.getOrDefault(cargo.item(), 0);
			int want = cargo.isAll() ? have : Math.min(cargo.amount(), have);
			if (want <= 0 || slots <= 0) {
				continue;
			}
			int stack = Math.max(1, cargo.item().getDefaultMaxStackSize());
			int fits = slots * stack;
			want = Math.min(want, fits);
			slots -= (want + stack - 1) / stack;
			manifest.add(new Cargo(cargo.item(), want));
			available.merge(cargo.item(), -want, Integer::sum);
		}
		return manifest;
	}

	/** The run's pending list after a boat takes {@code manifest}: quotas shrink, "all" entries stay until stock runs out. */
	private List<Cargo> reduce(Route route, List<Cargo> manifest) {
		if (route.cargo().isEmpty()) {
			return List.of();
		}
		List<Cargo> left = new ArrayList<>();
		for (Cargo pending : route.pending()) {
			if (pending.isAll()) {
				left.add(pending);
				continue;
			}
			int sent = 0;
			for (Cargo cargo : manifest) {
				if (cargo.item() == pending.item()) {
					sent += cargo.amount();
				}
			}
			if (pending.amount() - sent > 0) {
				left.add(pending.withAmount(pending.amount() - sent));
			}
		}
		return left;
	}

	/** A captain is back (or gave up): counts the trip and what it delivered. */
	public void voyageDone(int routeId, int delivered) {
		this.inFlight.computeIfPresent(routeId, (id, count) -> count > 1 ? count - 1 : null);
		int index = this.indexOf(routeId);
		if (index >= 0) {
			this.routes.set(index, this.routes.get(index).withTrip().withDelivered(delivered));
			this.setChanged();
		}
	}
}
