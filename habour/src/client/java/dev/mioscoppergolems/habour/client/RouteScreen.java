package dev.mioscoppergolems.habour.client;

import dev.mioscoppergolems.habour.menu.RouteMenu;
import dev.mioscoppergolems.habour.route.Route;
import dev.mioscoppergolems.habour.route.Status;
import dev.mioscoppergolems.habour.route.Trigger;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The route book. Top to bottom: the route and its destination, how often it runs, the cargo, and where the captain
 * is. Everything is a button or a slot; the values come from the menu's data slots.
 */
public class RouteScreen extends AbstractContainerScreen<RouteMenu> {
	private static final String KEY = "screen.mioscoppergolems_habour.";
	private static final int PANEL = 0xFFC6C6C6;
	private static final int DARK = 0xFF373737;
	private static final int LIGHT = 0xFFFFFFFF;
	private static final int SHADE = 0xFF8B8B8B;
	private static final int SLOT = 0xFF8B8B8B;
	private static final int TEXT = 0xFF404040;
	private static final int DIM = 0xFF707070;
	private static final int SELECTED = 0xFFFFC82E;
	private static final int H = 14;
	private static final int LEFT = 8;
	private static final int RIGHT = RouteMenu.WIDTH - 8;

	// Rows (top edge of each row, relative to the screen).
	private static final int ROW_NAV = 15;
	private static final int ROW_DEST = RouteMenu.DEST_Y;
	private static final int ROW_STATUS = 50;
	private static final int ROW_RUNS = 62;
	private static final int ROW_START = 78;
	private static final int ROW_CARGO = 97;
	private static final int ROW_AMOUNT = 126;
	private static final int ROW_ACTION = 142;
	private static final int ROW_FLEET = 161;
	private static final int[] RULES = {60, 94, 158};

	private Button enabled;
	private Button trigger;
	private Button waitForFull;
	private Button runs;
	/** Everything that only makes sense once a route exists. */
	private final List<Button> routeButtons = new ArrayList<>();

	public RouteScreen(RouteMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, RouteMenu.WIDTH, RouteMenu.HEIGHT);
		this.inventoryLabelY = 1000;
	}

	@Override
	protected void init() {
		super.init();
		this.routeButtons.clear();
		int x = this.leftPos;
		int y = this.topPos;
		// Route: [Active] ... [<] [>]  [+] [x]
		this.enabled = this.route(x + 70, y + ROW_NAV, 58, RouteMenu.TOGGLE_ENABLED, "");
		this.route(x + 148, y + ROW_NAV, 16, RouteMenu.PREV_ROUTE, "<");
		this.route(x + 166, y + ROW_NAV, 16, RouteMenu.NEXT_ROUTE, ">");
		this.button(x + 190, y + ROW_NAV, 16, RouteMenu.NEW_ROUTE, "+");
		this.route(x + 208, y + ROW_NAV, 16, RouteMenu.DELETE_ROUTE, "x");
		// Destination
		this.route(x + 190, y + ROW_DEST + 1, 16, RouteMenu.DEST_PREV, "<");
		this.route(x + 208, y + ROW_DEST + 1, 16, RouteMenu.DEST_NEXT, ">");
		// Runs and interval
		this.route(x + 36, y + ROW_RUNS, 14, RouteMenu.REPEAT_DEC, "-");
		this.runs = this.route(x + 52, y + ROW_RUNS, 38, RouteMenu.REPEAT_FOREVER, "");
		this.route(x + 92, y + ROW_RUNS, 14, RouteMenu.REPEAT_INC, "+");
		this.route(x + 152, y + ROW_RUNS, 14, RouteMenu.INTERVAL_PREV, "<");
		this.route(x + 210, y + ROW_RUNS, 14, RouteMenu.INTERVAL_NEXT, ">");
		this.trigger = this.route(x + LEFT, y + ROW_START, 108, RouteMenu.TRIGGER_TOGGLE, "");
		this.waitForFull = this.route(x + 118, y + ROW_START, 106, RouteMenu.WAIT_TOGGLE, "");
		// Cargo
		this.route(x + 176, y + RouteMenu.CARGO_Y + 1, 48, RouteMenu.AMOUNT_ALL, Component.translatable(KEY + "all").getString());
		String[] amounts = {"-64", "-16", "-1", "+1", "+16", "+64"};
		int[] ids = {RouteMenu.AMOUNT_M64, RouteMenu.AMOUNT_M16, RouteMenu.AMOUNT_M1, RouteMenu.AMOUNT_P1, RouteMenu.AMOUNT_P16, RouteMenu.AMOUNT_P64};
		for (int i = 0; i < amounts.length; i++) {
			this.route(x + LEFT + i * 36, y + ROW_AMOUNT, 34, ids[i], amounts[i]);
		}
		this.route(x + LEFT, y + ROW_ACTION, 70, RouteMenu.CLEAR_SLOT, Component.translatable(KEY + "remove").getString());
		this.route(x + 80, y + ROW_ACTION, 70, RouteMenu.RUN_NOW, Component.translatable(KEY + "run_now").getString());
		this.route(x + 152, y + ROW_ACTION, 72, RouteMenu.STOP_RUN, Component.translatable(KEY + "stop").getString());
		this.route(x + 176, y + ROW_FLEET, 48, RouteMenu.LOCATE, Component.translatable(KEY + "locate").getString());
	}

	private Button button(int x, int y, int width, int id, String label) {
		Button button = Button.builder(Component.literal(label), b -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id))
			.bounds(x, y, width, H)
			.build();
		return this.addRenderableWidget(button);
	}

	private Button route(int x, int y, int width, int id, String label) {
		Button button = this.button(x, y, width, id, label);
		this.routeButtons.add(button);
		return button;
	}

	private void updateButtons() {
		boolean any = this.menu.routeCount() > 0;
		for (Button button : this.routeButtons) {
			button.visible = any;
			button.active = any;
		}
		if (!any) {
			return;
		}
		this.enabled.setMessage(Component.translatable(KEY + (this.menu.isEnabled() ? "active" : "paused")));
		this.trigger.setMessage(Component.translatable(KEY + "trigger." + (this.menu.trigger() == Trigger.TIMER ? "timer" : "redstone")));
		this.waitForFull.setMessage(Component.translatable(KEY + "wait." + (this.menu.waitForFull() ? "yes" : "no")));
		int repeats = this.menu.repeats();
		this.runs.setMessage(repeats == Route.FOREVER ? Component.translatable(KEY + "loop") : Component.literal(String.valueOf(repeats)));
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		this.updateButtons();
		int x = this.leftPos;
		int y = this.topPos;
		graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, DARK);
		graphics.fill(x + 1, y + 1, x + this.imageWidth - 1, y + this.imageHeight - 1, PANEL);
		graphics.fill(x + 1, y + 1, x + this.imageWidth - 1, y + 2, LIGHT);
		graphics.fill(x + 1, y + 1, x + 2, y + this.imageHeight - 1, LIGHT);
		boolean any = this.menu.routeCount() > 0;
		if (any) {
			for (int rule : RULES) {
				graphics.fill(x + LEFT, y + rule, x + RIGHT, y + rule + 1, SHADE);
				graphics.fill(x + LEFT, y + rule + 1, x + RIGHT, y + rule + 2, LIGHT);
			}
		}
		int first = any ? 0 : RouteMenu.CARGO_SLOTS + 1;
		for (int i = first; i < this.menu.slots.size(); i++) {
			Slot slot = this.menu.slots.get(i);
			int sx = x + slot.x;
			int sy = y + slot.y;
			graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, DARK);
			graphics.fill(sx, sy, sx + 16, sy + 16, SLOT);
		}
		if (any) {
			int selected = this.menu.selectedSlot();
			if (selected >= 0 && selected < RouteMenu.CARGO_SLOTS) {
				graphics.outline(x + RouteMenu.CARGO_X + selected * 18 - 2, y + RouteMenu.CARGO_Y - 2, 20, 20, SELECTED);
			}
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Font font = this.font;
		graphics.text(font, this.title, LEFT, 4, TEXT, false);
		if (this.menu.routeCount() == 0) {
			graphics.text(font, Component.translatable(KEY + "no_routes"), LEFT, 22, TEXT, false);
			graphics.text(font, Component.translatable(KEY + (this.menu.isStation() ? "no_routes_hint_station" : "no_routes_hint")), LEFT, 36, DIM, false);
			return;
		}
		Component stats = Component.translatable(KEY + "stats", this.menu.trips(), this.menu.delivered());
		if (font.width(this.title) + font.width(stats) + 24 < this.imageWidth) {
			graphics.text(font, stats, this.imageWidth - LEFT - font.width(stats), 4, DIM, false);
		}
		// Route
		graphics.text(font, Component.translatable(KEY + "route", this.menu.selectedRoute() + 1, this.menu.routeCount()), LEFT, ROW_NAV + 3, TEXT, false);
		// Destination
		String dest = font.plainSubstrByWidth(this.menu.destinationName(), 138);
		graphics.text(font, Component.translatable(KEY + "to"), 30, ROW_DEST + 1, DIM, false);
		graphics.text(font, dest.isEmpty() ? "-" : dest, 30, ROW_DEST + 11, TEXT, false);
		Status status = Status.byOrdinal(this.menu.statusOrdinal());
		Component statusText = Component.translatable(KEY + "status." + status.name().toLowerCase(java.util.Locale.ROOT));
		graphics.text(font, font.plainSubstrByWidth(statusText.getString(), RIGHT - LEFT), LEFT, ROW_STATUS, TEXT, false);
		// Schedule
		graphics.text(font, Component.translatable(KEY + "runs"), LEFT, ROW_RUNS + 3, DIM, false);
		graphics.text(font, Component.translatable(KEY + "every"), 114, ROW_RUNS + 3, DIM, false);
		String every = font.plainSubstrByWidth(Component.translatable(KEY + "interval." + this.menu.intervalIndex()).getString(), 40);
		graphics.text(font, every, 168 + (40 - font.width(every)) / 2, ROW_RUNS + 3, TEXT, false);
		// Cargo
		int selected = this.menu.selectedSlot();
		Component cargoLine;
		if (selected >= 0 && selected < RouteMenu.CARGO_SLOTS && this.menu.hasCargo(selected)) {
			int amount = this.menu.amount(selected);
			Component amountText = amount <= 0 ? Component.translatable(KEY + "all") : Component.literal(String.valueOf(amount));
			String item = font.plainSubstrByWidth(this.menu.slots.get(selected).getItem().getHoverName().getString(), 110);
			cargoLine = Component.translatable(KEY + "cargo_line", item, amountText);
		} else {
			cargoLine = Component.translatable(KEY + (this.menu.hasCargo(0) ? "cargo_pick" : "cargo_everything"));
		}
		Component cargo = Component.translatable(KEY + "cargo");
		graphics.text(font, cargo, LEFT, ROW_CARGO, DIM, false);
		graphics.text(font, font.plainSubstrByWidth(cargoLine.getString(), RIGHT - LEFT - font.width(cargo) - 6), LEFT + font.width(cargo) + 6, ROW_CARGO, TEXT, false);
		this.extractFleet(graphics, font);
	}

	/** Where the captains (or conductors) of the route are: the one seen last, how far and in which direction. */
	private void extractFleet(GuiGraphicsExtractor graphics, Font font) {
		String suffix = this.menu.isStation() ? "_station" : "";
		int count = this.menu.fleetCount();
		Component line;
		if (count == 0) {
			line = Component.translatable(KEY + "fleet_none" + suffix);
		} else {
			Component place = Component.translatable(
				KEY + "fleet_place" + suffix, this.menu.fleetDistance(), Component.translatable(KEY + "dir." + this.menu.fleetDirection())
			);
			int age = this.menu.fleetAge();
			if (age >= 15) {
				place = Component.translatable(KEY + "fleet_old", place, age);
			}
			if (count > 1) {
				place = Component.translatable(KEY + "fleet_more", place, count - 1);
			}
			line = Component.translatable(KEY + "fleet_line", Component.translatable(KEY + "phase." + this.menu.fleetPhase()), place);
		}
		Component label = Component.translatable(KEY + "fleet" + suffix);
		int textY = ROW_FLEET + 3;
		graphics.text(font, label, LEFT, textY, DIM, false);
		graphics.text(font, font.plainSubstrByWidth(line.getString(), 168 - font.width(label) - 12), LEFT + font.width(label) + 4, textY, TEXT, false);
	}
}
