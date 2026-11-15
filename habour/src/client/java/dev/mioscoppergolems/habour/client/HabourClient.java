package dev.mioscoppergolems.habour.client;

import dev.mioscoppergolems.habour.registry.HabourMenus;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

public class HabourClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MenuScreens.register(HabourMenus.ROUTES, RouteScreen::new);
	}
}
