package dev.mioscoppergolems.habour.menu;

import dev.mioscoppergolems.habour.harbour.Fleet;
import dev.mioscoppergolems.habour.harbour.HarbourRegistry;
import dev.mioscoppergolems.home.HomeDisplay;
import dev.mioscoppergolems.habour.kontor.KontorBlockEntity;
import dev.mioscoppergolems.habour.registry.HabourBlocks;
import dev.mioscoppergolems.habour.registry.HabourMenus;
import dev.mioscoppergolems.habour.route.Cargo;
import dev.mioscoppergolems.habour.route.Route;
import dev.mioscoppergolems.habour.route.Trigger;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The route book of a kontor. The cargo list is nine "ghost" slots: click one with an item on the cursor to set what is
 * shipped (nothing is taken from you). All other settings are buttons, and their values travel to the screen as data
 * slots, like in the survey table. The destination's name travels as the name of the item in the destination slot.
 */
public class RouteMenu extends AbstractContainerMenu {
	public static final int CARGO_SLOTS = Route.MAX_CARGO;
	/** The layout of the route book (the screen draws around these). */
	public static final int WIDTH = 232;
	public static final int HEIGHT = 262;
	public static final int CARGO_X = 8;
	public static final int CARGO_Y = 107;
	public static final int DEST_X = 8;
	public static final int DEST_Y = 31;
	public static final int INV_X = 35;
	public static final int INV_Y = 181;
	public static final int DEST_SLOT = CARGO_SLOTS;
	private static final int FIRST_PLAYER_SLOT = CARGO_SLOTS + 1;

	public static final int PREV_ROUTE = 0;
	public static final int NEXT_ROUTE = 1;
	public static final int NEW_ROUTE = 2;
	public static final int DELETE_ROUTE = 3;
	public static final int TOGGLE_ENABLED = 4;
	public static final int DEST_PREV = 5;
	public static final int DEST_NEXT = 6;
	public static final int REPEAT_DEC = 7;
	public static final int REPEAT_INC = 8;
	public static final int REPEAT_FOREVER = 9;
	public static final int INTERVAL_PREV = 10;
	public static final int INTERVAL_NEXT = 11;
	public static final int TRIGGER_TOGGLE = 12;
	public static final int WAIT_TOGGLE = 13;
	public static final int AMOUNT_M64 = 14;
	public static final int AMOUNT_M16 = 15;
	public static final int AMOUNT_M1 = 16;
	public static final int AMOUNT_P1 = 17;
	public static final int AMOUNT_P16 = 18;
	public static final int AMOUNT_P64 = 19;
	public static final int AMOUNT_ALL = 20;
	public static final int CLEAR_SLOT = 21;
	public static final int RUN_NOW = 22;
	public static final int STOP_RUN = 23;
	public static final int LOCATE = 24;

	private final @Nullable KontorBlockEntity kontor;
	private final ContainerLevelAccess access;
	private final Container ghosts = new SimpleContainer(CARGO_SLOTS);
	private final Container destination = new SimpleContainer(1);

	private final DataSlot routeCount = DataSlot.standalone();
	private final DataSlot selected = DataSlot.standalone();
	private final DataSlot enabled = DataSlot.standalone();
	private final DataSlot repeats = DataSlot.standalone();
	private final DataSlot interval = DataSlot.standalone();
	private final DataSlot trigger = DataSlot.standalone();
	private final DataSlot waitForFull = DataSlot.standalone();
	private final DataSlot status = DataSlot.standalone();
	private final DataSlot destCount = DataSlot.standalone();
	private final DataSlot destIndex = DataSlot.standalone();
	private final DataSlot selectedSlot = DataSlot.standalone();
	private final DataSlot running = DataSlot.standalone();
	private final DataSlot trips = DataSlot.standalone();
	private final DataSlot delivered = DataSlot.standalone();
	/** The captains of the selected route that are out: how many, and where the most recently seen one is. */
	private final DataSlot fleetCount = DataSlot.standalone();
	private final DataSlot fleetPhase = DataSlot.standalone();
	private final DataSlot fleetDistance = DataSlot.standalone();
	private final DataSlot fleetDirection = DataSlot.standalone();
	private final DataSlot fleetAge = DataSlot.standalone();
	/** 1 when the book belongs to a railway station, 0 for a kontor. */
	private final DataSlot station = DataSlot.standalone();
	private final DataSlot[] amounts = new DataSlot[CARGO_SLOTS];

	private int route;
	private int refreshTimer;

	public RouteMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, null);
	}

	public RouteMenu(int containerId, Inventory inventory, @Nullable KontorBlockEntity kontor) {
		super(HabourMenus.ROUTES, containerId);
		this.kontor = kontor;
		this.access = kontor != null && kontor.getLevel() != null ? ContainerLevelAccess.create(kontor.getLevel(), kontor.getBlockPos()) : ContainerLevelAccess.NULL;
		for (int i = 0; i < CARGO_SLOTS; i++) {
			this.addSlot(new GhostSlot(this.ghosts, i, CARGO_X + i * 18, CARGO_Y));
		}
		this.addSlot(new GhostSlot(this.destination, 0, DEST_X, DEST_Y));
		this.addStandardInventorySlots(inventory, INV_X, INV_Y);
		for (DataSlot slot : new DataSlot[] {
			this.routeCount, this.selected, this.enabled, this.repeats, this.interval, this.trigger, this.waitForFull, this.status,
			this.destCount, this.destIndex, this.selectedSlot, this.running, this.trips, this.delivered,
				this.fleetCount, this.fleetPhase, this.fleetDistance, this.fleetDirection, this.fleetAge, this.station
		}) {
			this.addDataSlot(slot);
		}
		for (int i = 0; i < CARGO_SLOTS; i++) {
			this.amounts[i] = DataSlot.standalone();
			this.addDataSlot(this.amounts[i]);
		}
		this.selected.set(-1);
		this.destIndex.set(-1);
		this.refresh();
	}

	/** A slot that only shows something: nothing can be put in or taken out. */
	private static final class GhostSlot extends Slot {
		GhostSlot(Container container, int index, int x, int y) {
			super(container, index, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}

		@Override
		public boolean mayPickup(Player player) {
			return false;
		}
	}

	// --- what the screen reads ---

	public int routeCount() {
		return this.routeCount.get();
	}

	public int selectedRoute() {
		return this.selected.get();
	}

	public boolean isEnabled() {
		return this.enabled.get() != 0;
	}

	public int repeats() {
		return this.repeats.get();
	}

	public int intervalIndex() {
		return this.interval.get();
	}

	public Trigger trigger() {
		return Trigger.values()[Math.max(0, Math.min(Trigger.values().length - 1, this.trigger.get()))];
	}

	public boolean waitForFull() {
		return this.waitForFull.get() != 0;
	}

	public int statusOrdinal() {
		return this.status.get();
	}

	public int destCount() {
		return this.destCount.get();
	}

	public int destIndex() {
		return this.destIndex.get();
	}

	public int selectedSlot() {
		return this.selectedSlot.get();
	}

	public boolean isRunning() {
		return this.running.get() != 0;
	}

	public int trips() {
		return this.trips.get();
	}

	public int delivered() {
		return this.delivered.get();
	}

	public int fleetCount() {
		return this.fleetCount.get();
	}

	/** Voyage phase ordinal of the captain that was seen last. */
	public int fleetPhase() {
		return this.fleetPhase.get();
	}

	/** Blocks between the kontor and that captain. */
	public int fleetDistance() {
		return this.fleetDistance.get();
	}

	/** Compass direction from the kontor to that captain, 0 = north, clockwise in steps of 45 degrees. */
	public int fleetDirection() {
		return this.fleetDirection.get();
	}

	/** Seconds since that captain last reported. */
	public boolean isStation() {
		return this.station.get() != 0;
	}

	public int fleetAge() {
		return this.fleetAge.get();
	}

	public int amount(int slot) {
		return this.amounts[slot].get();
	}

	public boolean hasCargo(int slot) {
		return !this.ghosts.getItem(slot).isEmpty();
	}

	public String destinationName() {
		ItemStack stack = this.destination.getItem(0);
		return stack.isEmpty() ? "" : stack.getHoverName().getString();
	}

	// --- server side ---

	private @Nullable Route current() {
		if (this.kontor == null || this.route < 0 || this.route >= this.kontor.routes().size()) {
			return null;
		}
		return this.kontor.routes().get(this.route);
	}

	private List<HarbourRegistry.Entry> destinations() {
		if (this.kontor == null || !(this.kontor.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) {
			return List.of();
		}
		List<HarbourRegistry.Entry> list = new ArrayList<>();
		for (HarbourRegistry.Entry entry : HarbourRegistry.get(level).inDimension(level)) {
			if (!entry.pos().equals(this.kontor.getBlockPos()) && entry.rail() == this.kontor.isStation()) {
				list.add(entry);
			}
		}
		return list;
	}

	/** Copies the selected route into the data slots and ghost slots. */
	private void refresh() {
		if (this.kontor == null) {
			return;
		}
		this.station.set(this.kontor.isStation() ? 1 : 0);
		int count = this.kontor.routes().size();
		this.routeCount.set(count);
		if (count == 0) {
			this.route = -1;
		} else {
			this.route = Math.max(0, Math.min(count - 1, this.route));
		}
		this.selected.set(this.route);
		Route route = this.current();
		if (route == null) {
			this.enabled.set(0);
			this.repeats.set(0);
			this.interval.set(0);
			this.trigger.set(0);
			this.waitForFull.set(0);
			this.status.set(0);
			this.destCount.set(this.destinations().size());
			this.destIndex.set(-1);
			this.running.set(0);
			this.trips.set(0);
			this.delivered.set(0);
			this.fleetCount.set(0);
			for (int i = 0; i < CARGO_SLOTS; i++) {
				this.ghosts.setItem(i, ItemStack.EMPTY);
				this.amounts[i].set(0);
			}
			this.destination.setItem(0, ItemStack.EMPTY);
			return;
		}
		this.enabled.set(route.enabled() ? 1 : 0);
		this.repeats.set(route.repeats());
		this.interval.set(route.interval());
		this.trigger.set(route.trigger().ordinal());
		this.waitForFull.set(route.waitForFull() ? 1 : 0);
		this.status.set(this.kontor.statusOf(route.id()).ordinal());
		this.running.set(route.running() ? 1 : 0);
		this.trips.set(Math.min(route.trips(), Short.MAX_VALUE));
		this.delivered.set((int) Math.min(route.delivered(), Short.MAX_VALUE));
		this.refreshFleet(route);
		List<HarbourRegistry.Entry> destinations = this.destinations();
		this.destCount.set(destinations.size());
		int destIndex = -1;
		String destName = "-";
		for (int i = 0; i < destinations.size(); i++) {
			if (destinations.get(i).pos().equals(route.dest())) {
				destIndex = i;
				destName = destinations.get(i).name();
			}
		}
		this.destIndex.set(destIndex);
		ItemStack marker = new ItemStack(Items.COMPASS);
		marker.set(DataComponents.CUSTOM_NAME, Component.literal(destName));
		if (!ItemStack.matches(this.destination.getItem(0), marker)) {
			this.destination.setItem(0, marker);
		}
		for (int i = 0; i < CARGO_SLOTS; i++) {
			if (i < route.cargo().size()) {
				Cargo cargo = route.cargo().get(i);
				ItemStack shown = new ItemStack(cargo.item());
				if (!ItemStack.matches(this.ghosts.getItem(i), shown)) {
					this.ghosts.setItem(i, shown);
				}
				this.amounts[i].set(cargo.amount());
			} else {
				if (!this.ghosts.getItem(i).isEmpty()) {
					this.ghosts.setItem(i, ItemStack.EMPTY);
				}
				this.amounts[i].set(0);
			}
		}
		if (this.selectedSlot.get() >= Math.max(1, route.cargo().size() + 1)) {
			this.selectedSlot.set(Math.min(CARGO_SLOTS - 1, route.cargo().size()));
		}
	}

	private void refreshFleet(Route route) {
		if (this.kontor == null || !(this.kontor.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) {
			return;
		}
		List<Fleet.Report> fleet = Fleet.of(level, this.kontor.getBlockPos(), route.id());
		this.fleetCount.set(fleet.size());
		if (fleet.isEmpty()) {
			return;
		}
		Fleet.Report seen = fleet.get(0);
		BlockPos from = this.kontor.getBlockPos();
		int dx = seen.pos().getX() - from.getX();
		int dz = seen.pos().getZ() - from.getZ();
		this.fleetPhase.set(seen.phase().ordinal());
		this.fleetDistance.set((int) Math.min(Short.MAX_VALUE, Math.round(Math.sqrt((double) dx * dx + (double) dz * dz))));
		this.fleetDirection.set(compass(dx, dz));
		this.fleetAge.set((int) Math.min(Short.MAX_VALUE, (level.getGameTime() - seen.time()) / 20L));
	}

	/** 0 = north (-z), 1 = north-east, 2 = east (+x) ... */
	public static int compass(int dx, int dz) {
		double angle = Math.atan2(dx, -dz);
		return Math.floorMod((int) Math.round(angle / (Math.PI / 4.0)), 8);
	}

	/** The Locate button: the route captains glow for half a minute, and the player is told where the latest one is. */
	private void locate(net.minecraft.server.level.ServerLevel level, Player player, Route route) {
		List<Fleet.Report> fleet = Fleet.of(level, this.kontor.getBlockPos(), route.id());
		if (fleet.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.mioscoppergolems_habour.locate_none"));
			return;
		}
		for (Fleet.Report report : fleet) {
			if (level.getEntity(report.captain()) instanceof net.minecraft.world.entity.animal.golem.CopperGolem golem) {
				golem.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, 600, 0, false, false));
				if (report == fleet.get(0) && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
					HomeDisplay.drawLine(level, golem, player.blockPosition(), serverPlayer);
				}
			}
		}
		Fleet.Report first = fleet.get(0);
		BlockPos at = first.pos();
		int dx = at.getX() - player.blockPosition().getX();
		int dz = at.getZ() - player.blockPosition().getZ();
		player.sendOverlayMessage(Component.translatable(
			"message.mioscoppergolems_habour.locate", at.getX(), at.getY(), at.getZ(), (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz)),
			Component.translatable("screen.mioscoppergolems_habour.dir." + compass(dx, dz)),
			Component.translatable("screen.mioscoppergolems_habour.phase." + first.phase().ordinal()),
			Math.max(0, (level.getGameTime() - first.time()) / 20L)
		));
	}

	@Override
	public void broadcastChanges() {
		if (this.kontor != null && ++this.refreshTimer >= 5) {
			this.refreshTimer = 0;
			this.refresh();
		}
		super.broadcastChanges();
	}

	@Override
	public boolean clickMenuButton(Player player, int buttonId) {
		if (this.kontor == null || !(this.kontor.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) {
			return false;
		}
		Route route = this.current();
		boolean handled = true;
		switch (buttonId) {
			case PREV_ROUTE -> this.route = Math.max(0, this.route - 1);
			case NEXT_ROUTE -> this.route = Math.min(this.kontor.routes().size() - 1, this.route + 1);
			case NEW_ROUTE -> {
				List<HarbourRegistry.Entry> destinations = this.destinations();
				BlockPos dest = destinations.isEmpty() ? this.kontor.getBlockPos() : destinations.get(0).pos();
				if (this.kontor.addRoute(dest) != null) {
					this.route = this.kontor.routes().size() - 1;
					this.selectedSlot.set(0);
				}
			}
			case DELETE_ROUTE -> {
				if (route != null) {
					this.kontor.removeRoute(this.route);
				}
			}
			case LOCATE -> {
				if (route != null) {
					this.locate(level, player, route);
				}
			}
			default -> handled = route != null && this.editRoute(level, route, buttonId);
		}
		this.refresh();
		return handled;
	}

	private boolean editRoute(net.minecraft.server.level.ServerLevel level, Route route, int buttonId) {
		Route edited = route;
		switch (buttonId) {
			case TOGGLE_ENABLED -> edited = route.withEnabled(!route.enabled());
			case DEST_PREV, DEST_NEXT -> {
				List<HarbourRegistry.Entry> destinations = this.destinations();
				if (destinations.isEmpty()) {
					return false;
				}
				int index = -1;
				for (int i = 0; i < destinations.size(); i++) {
					if (destinations.get(i).pos().equals(route.dest())) {
						index = i;
					}
				}
				int step = buttonId == DEST_NEXT ? 1 : -1;
				index = Math.floorMod(index + step, destinations.size());
				edited = route.withDest(destinations.get(index).pos());
			}
			case REPEAT_DEC -> edited = route.withRepeats(route.repeats() == Route.FOREVER ? Route.MAX_REPEATS : Math.max(1, route.repeats() - 1));
			case REPEAT_INC -> edited = route.withRepeats(route.repeats() == Route.FOREVER ? Route.FOREVER : Math.min(Route.MAX_REPEATS, route.repeats() + 1));
			case REPEAT_FOREVER -> edited = route.withRepeats(route.repeats() == Route.FOREVER ? 1 : Route.FOREVER);
			case INTERVAL_PREV -> edited = route.withInterval(Math.max(0, route.interval() - 1));
			case INTERVAL_NEXT -> edited = route.withInterval(Math.min(Route.INTERVALS.length - 1, route.interval() + 1));
			case TRIGGER_TOGGLE -> edited = route.withTrigger(route.trigger().next());
			case WAIT_TOGGLE -> edited = route.withWaitForFull(!route.waitForFull());
			case AMOUNT_M64, AMOUNT_M16, AMOUNT_M1, AMOUNT_P1, AMOUNT_P16, AMOUNT_P64, AMOUNT_ALL, CLEAR_SLOT -> {
				return this.editCargo(route, buttonId);
			}
			case RUN_NOW -> {
				this.kontor.runNow(this.route, level.getGameTime());
				return true;
			}
			case STOP_RUN -> edited = route.cancelRun();
			default -> {
				return false;
			}
		}
		this.kontor.setRoute(this.route, edited);
		return true;
	}

	private boolean editCargo(Route route, int buttonId) {
		int slot = this.selectedSlot.get();
		if (slot < 0 || slot >= route.cargo().size()) {
			return false;
		}
		List<Cargo> cargo = new ArrayList<>(route.cargo());
		Cargo line = cargo.get(slot);
		switch (buttonId) {
			case AMOUNT_M64 -> cargo.set(slot, line.withAmount(Math.max(1, line.amount() - 64)));
			case AMOUNT_M16 -> cargo.set(slot, line.withAmount(Math.max(1, line.amount() - 16)));
			case AMOUNT_M1 -> cargo.set(slot, line.withAmount(Math.max(1, line.amount() - 1)));
			case AMOUNT_P1 -> cargo.set(slot, line.withAmount(line.amount() + 1));
			case AMOUNT_P16 -> cargo.set(slot, line.withAmount(line.amount() + 16));
			case AMOUNT_P64 -> cargo.set(slot, line.withAmount(line.amount() + 64));
			case AMOUNT_ALL -> cargo.set(slot, line.withAmount(Cargo.ALL));
			case CLEAR_SLOT -> cargo.remove(slot);
			default -> {
				return false;
			}
		}
		this.kontor.setRoute(this.route, route.withCargo(cargo));
		return true;
	}

	@Override
	public void clicked(int slotId, int button, ContainerInput input, Player player) {
		if (slotId >= 0 && slotId < CARGO_SLOTS) {
			this.clickCargoSlot(slotId, button);
			return;
		}
		if (slotId == DEST_SLOT) {
			return;
		}
		super.clicked(slotId, button, input, player);
	}

	private void clickCargoSlot(int slot, int button) {
		Route route = this.current();
		if (this.kontor == null || route == null) {
			return;
		}
		ItemStack carried = this.getCarried();
		List<Cargo> cargo = new ArrayList<>(route.cargo());
		if (!carried.isEmpty()) {
			Cargo line = new Cargo(carried.getItem(), Math.max(1, carried.getItem().getDefaultMaxStackSize()));
			if (slot < cargo.size()) {
				cargo.set(slot, line);
			} else if (cargo.size() < CARGO_SLOTS) {
				cargo.add(line);
				slot = cargo.size() - 1;
			}
			this.selectedSlot.set(slot);
		} else if (slot < cargo.size()) {
			if (button == 1) {
				cargo.remove(slot);
			}
			this.selectedSlot.set(Math.min(slot, Math.max(0, cargo.size() - 1)));
			if (button == 1) {
				this.kontor.setRoute(this.route, route.withCargo(cargo));
			}
			this.refresh();
			return;
		} else {
			this.selectedSlot.set(cargo.size());
			return;
		}
		this.kontor.setRoute(this.route, route.withCargo(cargo));
		this.refresh();
	}

	/** Shift-clicking an item in your inventory adds it to the cargo list. */
	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		Route route = this.current();
		if (this.kontor != null && route != null && slotIndex >= FIRST_PLAYER_SLOT && slotIndex < this.slots.size()) {
			ItemStack stack = this.slots.get(slotIndex).getItem();
			if (!stack.isEmpty() && route.cargo().size() < CARGO_SLOTS && route.cargo().stream().noneMatch(line -> line.item() == stack.getItem())) {
				List<Cargo> cargo = new ArrayList<>(route.cargo());
				cargo.add(new Cargo(stack.getItem(), Math.max(1, stack.getItem().getDefaultMaxStackSize())));
				this.kontor.setRoute(this.route, route.withCargo(cargo));
				this.selectedSlot.set(cargo.size() - 1);
				this.refresh();
			}
		}
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		// A kontor or a railway station (a subclass of the kontor block): the vanilla check wants one exact block.
		return this.access.evaluate(
			(level, pos) -> level.getBlockState(pos).getBlock() instanceof dev.mioscoppergolems.habour.kontor.KontorBlock
				&& player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0,
			true
		);
	}
}
