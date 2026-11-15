package dev.mioscoppergolems.habour.registry;

import dev.mioscoppergolems.habour.Habour;
import dev.mioscoppergolems.habour.menu.RouteMenu;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

public final class HabourMenus {
	public static final MenuType<RouteMenu> ROUTES = Registry.register(
		BuiltInRegistries.MENU, Habour.id("routes"), new MenuType<>(RouteMenu::new, FeatureFlags.VANILLA_SET)
	);

	private HabourMenus() {
	}

	public static void init() {
	}
}
