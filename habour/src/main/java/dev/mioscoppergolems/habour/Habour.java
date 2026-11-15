package dev.mioscoppergolems.habour;

import dev.mioscoppergolems.api.MiosCoppergolemsApi;
import dev.mioscoppergolems.habour.captain.CaptainBehavior;
import dev.mioscoppergolems.habour.captain.Captains;
import dev.mioscoppergolems.habour.captain.ConductorBehavior;
import dev.mioscoppergolems.habour.captain.Conductors;
import dev.mioscoppergolems.habour.captain.DockhandBehavior;
import dev.mioscoppergolems.habour.captain.Dockhands;
import dev.mioscoppergolems.habour.command.HabourCommand;
import dev.mioscoppergolems.habour.harbour.Fleet;
import dev.mioscoppergolems.habour.harbour.Harbours;
import dev.mioscoppergolems.habour.registry.HabourAttachments;
import dev.mioscoppergolems.habour.registry.HabourBlockEntities;
import dev.mioscoppergolems.habour.registry.HabourBlocks;
import dev.mioscoppergolems.habour.registry.HabourMenus;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mios Coppergolems Habour: harbours with a kontor (the warehouse) and mooring posts. Routes between harbours send
 * copper golem captains off in chest boats: they fetch the cargo from the kontor, load the boat, sail it to the other
 * harbour and unload it there, in plain sight.
 */
public class Habour implements ModInitializer {
	public static final String MOD_ID = "mioscoppergolems_habour";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Golem Handbook chapters, in order; their texts are in the lang files. */
	private static final String[] HANDBOOK_CHAPTERS = {"harbour", "routes", "captains", "dockhands", "sailing", "railways"};

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		HabourBlocks.init();
		HabourBlockEntities.init();
		HabourMenus.init();
		HabourAttachments.init();
		MiosCoppergolemsApi.registerJob(Captains.JOB);
		MiosCoppergolemsApi.registerJob(Dockhands.JOB);
		MiosCoppergolemsApi.registerJob(Conductors.JOB);
		MiosCoppergolemsApi.registerBehavior(CaptainBehavior::new);
		MiosCoppergolemsApi.registerBehavior(DockhandBehavior::new);
		MiosCoppergolemsApi.registerBehavior(ConductorBehavior::new);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			Fleet.clear();
			dev.mioscoppergolems.habour.captain.BoatPilot.forgetAll();
		});
		ServerTickEvents.END_LEVEL_TICK.register(dev.mioscoppergolems.habour.captain.BoatPilot::tickPaddles);
		MiosCoppergolemsApi.registerLinkTarget(Captains.LINK);
		UseEntityCallback.EVENT.register(Captains::onUseEntity);
		ServerTickEvents.END_LEVEL_TICK.register(Harbours::tick);
		CommandRegistrationCallback.EVENT.register(HabourCommand::register);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			output.insertAfter(Items.BARREL, HabourBlocks.KONTOR);
			output.insertAfter(HabourBlocks.KONTOR, HabourBlocks.MOORING_POST);
			output.insertAfter(HabourBlocks.MOORING_POST, HabourBlocks.STATION);
		});
		for (String chapter : HANDBOOK_CHAPTERS) {
			MiosCoppergolemsApi.registerHandbookChapter(id(chapter));
		}
		LOGGER.info("Mios Coppergolems Habour initialised");
	}
}
