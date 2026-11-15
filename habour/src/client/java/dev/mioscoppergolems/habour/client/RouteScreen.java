package dev.mioscoppergolems.habour.client;

import dev.mioscoppergolems.habour.menu.RouteMenu;
import dev.mioscoppergolems.habour.route.Route;
import dev.mioscoppergolems.habour.route.Status;
import dev.mioscoppergolems.habour.route.Trigger;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The route book: pick a route, its destination, how often it runs, and drop items on the cargo slots to say what is
 * shipped. Everything is a button or a slot; the values come from the menu's data slots.
 */
public class RouteScreen extends AbstractContainerScreen<RouteMenu> {
	private static final int PANEL = 0xFFC6C6C6;
	private static final int DARK = 0xFF373737;
	private static final int LIGHT = 0xFFFFFFFF;
	private static final int SLOT = 0xFF8B8B8B;
	private static final int TEXT = 0xFF404040;
	private static final int SELECTED = 0xFFFFC82E;

	private Button enabled;
	private Button trigger;
	private Button waitForFull;

	public RouteScreen(RouteMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, 262);
		this.inventoryLabelY = 1000;
	}

	@Override
	protected void init() {
		super.init();
		int x = this.leftPos;
		int y = this.topPos;
		this.addButton("<", x + 100, y + 14, 14, RouteMenu.PREV_ROUTE);
		this.addButton(">", x + 114, y + 14, 14, RouteMenu.NEXT_ROUTE);
		this.addButton("+", x + 132, y + 14, 14, RouteMenu.NEW_ROUTE);
		this.addButton("x", x + 148, y + 14, 14, RouteMenu.DELETE_ROUTE);
		this.addButton("<", x + 134, y + 30, 14, RouteMenu.DEST_PREV);
		this.addButton(">", x + 150, y + 30, 14, RouteMenu.DEST_NEXT);
		this.enabled = this.addButton("", x + 118, y + 46, 50, RouteMenu.TOGGLE_ENABLED);
		this.addButton("-", x + 100, y + 60, 14, RouteMenu.REPEAT_DEC);
		this.addButton("+", x + 114, y + 60, 14, RouteMenu.REPEAT_INC);
		this.addButton("", x + 130, y + 60, 38, RouteMenu.REPEAT_FOREVER).setMessage(Component.translatable("screen.mioscoppergolems_habour.loop"));
		this.addButton("<", x + 124, y + 74, 14, RouteMenu.INTERVAL_PREV);
		this.addButton(">", x + 138, y + 74, 14, RouteMenu.INTERVAL_NEXT);
		this.trigger = this.addButton("", x + 8, y + 88, 78, RouteMenu.TRIGGER_TOGGLE);
		this.waitForFull = this.addButton("", x + 90, y + 88, 78, RouteMenu.WAIT_TOGGLE);
		String[] amounts = {"-64", "-16", "-1", "+1", "+16", "+64"};
		int[] ids = {RouteMenu.AMOUNT_M64, RouteMenu.AMOUNT_M16, RouteMenu.AMOUNT_M1, RouteMenu.AMOUNT_P1, RouteMenu.AMOUNT_P16, RouteMenu.AMOUNT_P64};
		for (int i = 0; i < amounts.length; i++) {
			this.addButton(amounts[i], x + 8 + i * 26, y + 133, 24, ids[i]);
		}
		this.addButton("", x + 8, y + 147, 34, RouteMenu.AMOUNT_ALL).setMessage(Component.translatable("screen.mioscoppergolems_habour.all"));
		this.addButton("", x + 44, y + 147, 40, RouteMenu.CLEAR_SLOT).setMessage(Component.translatable("screen.mioscoppergolems_habour.remove"));
		this.addButton("", x + 88, y + 147, 44, RouteMenu.RUN_NOW).setMessage(Component.translatable("screen.mioscoppergolems_habour.run_now"));
		this.addButton("", x + 134, y + 147, 34, RouteMenu.STOP_RUN).setMessage(Component.translatable("screen.mioscoppergolems_habour.stop"));
		this.addButton("", x + 120, y + 165, 48, RouteMenu.LOCATE).setMessage(Component.translatable("screen.mioscoppergolems_habour.locate"));
	}

	private Button addButton(String label, int x, int y, int width, int id) {
		Button button = Button.builder(Component.literal(label), b -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id))
			.bounds(x, y, width, 12)
			.build();
		return this.addRenderableWidget(button);
	}

	private void updateButtons() {
		this.enabled.setMessage(Component.translatable(this.menu.isEnabled() ? "screen.mioscoppergolems_habour.active" : "screen.mioscoppergolems_habour.paused"));
		this.trigger.setMessage(Component.translatable("screen.mioscoppergolems_habour.trigger." + (this.menu.trigger() == Trigger.TIMER ? "timer" : "redstone")));
		this.waitForFull.setMessage(Component.translatable("screen.mioscoppergolems_habour.wait." + (this.menu.waitForFull() ? "yes" : "no")));
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
		for (Slot slot : this.menu.slots) {
			int sx = x + slot.x;
			int sy = y + slot.y;
			graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, DARK);
			graphics.fill(sx, sy, sx + 16, sy + 16, SLOT);
		}
		if (this.menu.routeCount() > 0) {
			int selected = this.menu.selectedSlot();
			if (selected >= 0 && selected < RouteMenu.CARGO_SLOTS) {
				int sx = x + 8 + selected * 18;
				int sy = y + 113;
				graphics.outline(sx - 2, sy - 2, 20, 20, SELECTED);
			}
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Font font = this.font;
		graphics.text(font, this.title, 8, 5, TEXT, false);
		if (this.menu.routeCount() == 0) {
			graphics.text(font, Component.translatable("screen.mioscoppergolems_habour.no_routes"), 8, 20, TEXT, false);
			graphics.text(font, Component.translatable("screen.mioscoppergolems_habour.no_routes_hint"), 8, 34, 0xFF707070, false);
			return;
		}
		graphics.text(font, Component.translatable("screen.mioscoppergolems_habour.route", this.menu.selectedRoute() + 1, this.menu.routeCount()), 8, 17, TEXT, false);
		String dest = font.plainSubstrByWidth(this.menu.destinationName(), 98);
		graphics.text(font, Component.translatable("screen.mioscoppergolems_habour.to"), 30, 28, 0xFF707070, false);
		graphics.text(font, dest.isEmpty() ? "-" : dest, 30, 38, TEXT, false);
		Status status = Status.byOrdinal(this.menu.statusOrdinal());
		graphics.text(font, Component.translatable("screen.mioscoppergolems_habour.status." + status.name().toLowerCase(java.util.Locale.ROOT)), 8, 49, TEXT, false);
		int repeats = this.menu.repeats();
		graphics.text(
			font,
			Component.translatable("screen.mioscoppergolems_habour.runs", repeats == Route.FOREVER ? Component.translatable("screen.mioscoppergolems_habour.forever") : Component.literal(String.valueOf(repeats))),
			8, 62, TEXT, false
		);
		graphics.text(font, Component.translatable("screen.mioscoppergolems_habour.every", Component.translatable("screen.mioscoppergolems_habour.interval." + this.menu.intervalIndex())), 8, 76, TEXT, false);
		int selected = this.menu.selectedSlot();
		Component cargoLine;
		if (selected >= 0 && selected < RouteMenu.CARGO_SLOTS && this.menu.hasCargo(selected)) {
			int amount = this.menu.amount(selected);
			Component amountText = amount <= 0 ? Component.translatable("screen.mioscoppergolems_habour.all") : Component.literal(String.valueOf(amount));
			String item = font.plainSubstrByWidth(this.menu.slots.get(selected).getItem().getHoverName().getString(), 78);
			cargoLine = Component.translatable("screen.mioscoppergolems_habour.cargo_line", item, amountText);
		} else {
			cargoLine = Component.translatable(this.menu.hasCargo(0) ? "screen.mioscoppergolems_habour.cargo_pick" : "screen.mioscoppergolems_habour.cargo_everything");
		}
		graphics.text(font, Component.translatable("screen.mioscoppergolems_habour.cargo"), 8, 103, 0xFF707070, false);
		graphics.text(font, cargoLine, 8 + font.width(Component.translatable("screen.mioscoppergolems_habour.cargo")) + 6, 103, TEXT, false);
		this.extractFleet(graphics, font);
		Component stats = Component.translatable("screen.mioscoppergolems_habour.stats", this.menu.trips(), this.menu.delivered());
		if (font.width(this.title) + font.width(stats) + 24 < this.imageWidth) {
			graphics.text(font, stats, this.imageWidth - 8 - font.width(stats), 5, 0xFF707070, false);
		}
	}

	/** Where the captains of the route are: the one seen last, how far and in which direction from this kontor. */
	private void extractFleet(GuiGraphicsExtractor graphics, Font font) {
		int count = this.menu.fleetCount();
		Component line1;
		Component line2 = Component.empty();
		if (count == 0) {
			line1 = Component.translatable("screen.mioscoppergolems_habour.fleet_none");
		} else {
			line1 = Component.translatable("screen.mioscoppergolems_habour.phase." + this.menu.fleetPhase());
			Component place = Component.translatable(
				"screen.mioscoppergolems_habour.fleet_place", this.menu.fleetDistance(), Component.translatable("screen.mioscoppergolems_habour.dir." + this.menu.fleetDirection())
			);
			int age = this.menu.fleetAge();
			line2 = age >= 15 ? Component.translatable("screen.mioscoppergolems_habour.fleet_old", place, age) : place;
			if (count > 1) {
				line2 = Component.translatable("screen.mioscoppergolems_habour.fleet_more", line2, count - 1);
			}
		}
		Component label = Component.translatable("screen.mioscoppergolems_habour.fleet");
		graphics.text(font, label, 8, 166, 0xFF707070, false);
		graphics.text(font, font.plainSubstrByWidth(line1.getString(), 100 - font.width(label)), 8 + font.width(label) + 4, 166, TEXT, false);
		graphics.text(font, font.plainSubstrByWidth(line2.getString(), 160), 8, 176, TEXT, false);
	}
}
