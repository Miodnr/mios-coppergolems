package dev.mioscoppergolems.habour.captain;

import dev.mioscoppergolems.api.GolemJob;
import dev.mioscoppergolems.api.GolemLinkTarget;
import dev.mioscoppergolems.api.MiosCoppergolemsApi;
import dev.mioscoppergolems.habour.Habour;
import dev.mioscoppergolems.habour.kontor.KontorBlock;
import dev.mioscoppergolems.habour.kontor.KontorBlockEntity;
import dev.mioscoppergolems.habour.registry.HabourAttachments;
import dev.mioscoppergolems.home.HomeDisplay;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The captain: a copper golem holding a spyglass (in its off hand, since the main hand carries the cargo), linked to the
 * kontor of its harbour (sneak + right-click the golem, then the kontor). When a route needs a boat sailed, the kontor
 * hands an idle captain a {@link Voyage}.
 */
public final class Captains {
	private static final double MAX_LINK_DISTANCE = 48.0;

	public static final GolemJob JOB = new GolemJob() {
		@Override
		public boolean has(CopperGolem golem) {
			return isCaptain(golem);
		}

		@Override
		public Component name() {
			return Component.translatable("job.mioscoppergolems_habour.captain");
		}

		@Override
		public @Nullable BlockPos workplace(CopperGolem golem) {
			return harbour(golem);
		}

		@Override
		public boolean busy(CopperGolem golem) {
			return voyage(golem) != null;
		}
	};

	public static final GolemLinkTarget LINK = new GolemLinkTarget() {
		@Override
		public boolean accepts(BlockState state) {
			return state.getBlock() instanceof KontorBlock;
		}

		@Override
		public InteractionResult link(ServerPlayer player, ServerLevel level, CopperGolem golem, BlockPos pos) {
			if (!isCaptain(golem) && !Dockhands.isDockhand(golem) && !Conductors.isConductor(golem)) {
				player.sendOverlayMessage(Component.translatable("message.mioscoppergolems_habour.needs_spyglass"));
				return InteractionResult.FAIL;
			}
			if (voyage(golem) != null || golem.getAttached(Dockhands.CLAIM) != null) {
				player.sendOverlayMessage(Component.translatable("message.mioscoppergolems_habour.captain_at_sea"));
				return InteractionResult.FAIL;
			}
			boolean station = level.getBlockState(pos).getBlock() instanceof dev.mioscoppergolems.habour.kontor.StationBlock;
			if (isCaptain(golem) && station || Conductors.isConductor(golem) && !station) {
				player.sendOverlayMessage(Component.translatable(station ? "message.mioscoppergolems_habour.captain_not_station" : "message.mioscoppergolems_habour.conductor_not_harbour"));
				return InteractionResult.FAIL;
			}
			if (golem.position().distanceTo(Vec3.atCenterOf(pos)) > MAX_LINK_DISTANCE) {
				player.sendOverlayMessage(Component.translatable("message.mioscoppergolems.home_too_far"));
				return InteractionResult.FAIL;
			}
			golem.setAttached(HabourAttachments.HARBOUR, pos.immutable());
			level.playSound(null, pos, SoundEvents.COPPER_GOLEM_ITEM_DROP, SoundSource.NEUTRAL, 1.0F, 1.2F);
			HomeDisplay.drawLine(level, golem, pos, null);
			String name = level.getBlockEntity(pos) instanceof KontorBlockEntity kontor ? kontor.harbourName() : "?";
			String role = roleOf(golem);
			player.sendOverlayMessage(Component.translatable("message.mioscoppergolems_habour." + role + "_linked", name));
			MiosCoppergolemsApi.awardGuide(player, Habour.id("guide/" + role));
			return InteractionResult.SUCCESS;
		}
	};

	private Captains() {
	}

	/** "captain", "conductor" or "dockhand": what the golem's marker item makes it. */
	public static String roleOf(CopperGolem golem) {
		return isCaptain(golem) ? "captain" : Conductors.isConductor(golem) ? "conductor" : "dockhand";
	}

	public static boolean isCaptain(CopperGolem golem) {
		return golem.getOffhandItem().is(Items.SPYGLASS);
	}

	public static @Nullable BlockPos harbour(CopperGolem golem) {
		return golem.getAttached(HabourAttachments.HARBOUR);
	}

	public static @Nullable Voyage voyage(CopperGolem golem) {
		return golem.getAttached(HabourAttachments.VOYAGE);
	}

	/**
	 * Right-clicking a golem with a spyglass makes it a captain, with a piece of string a dockhand. With an empty hand, a
	 * captain that is not at sea (or a dockhand that is not at work) and carries nothing hands the item back.
	 */
	public static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity entity, @Nullable EntityHitResult hitResult) {
		if (!(entity instanceof CopperGolem golem) || player.isSpectator() || hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown()) {
			return InteractionResult.PASS;
		}
		ItemStack held = player.getItemInHand(hand);
		boolean marker = held.is(Items.SPYGLASS) || held.is(Items.STRING) || held.is(Conductors.MARKER);
		if (marker && !isCaptain(golem) && !Dockhands.isDockhand(golem) && !Conductors.isConductor(golem) && golem.getOffhandItem().isEmpty()) {
			if (level instanceof ServerLevel) {
				golem.setItemSlot(EquipmentSlot.OFFHAND, held.copyWithCount(1));
				golem.setGuaranteedDrop(EquipmentSlot.OFFHAND);
				String role = held.is(Items.SPYGLASS) ? "captain" : held.is(Conductors.MARKER) ? "conductor" : "dockhand";
				held.consume(1, player);
				golem.playSound(SoundEvents.SPYGLASS_USE);
				player.sendOverlayMessage(Component.translatable(
					harbour(golem) == null ? "message.mioscoppergolems_habour." + role + "_needs_harbour" : "message.mioscoppergolems_habour." + role + "_ready"
				));
			}
			return InteractionResult.SUCCESS;
		}
		boolean working = voyage(golem) != null || golem.getAttached(Dockhands.CLAIM) != null;
		if (held.isEmpty() && (isCaptain(golem) || Dockhands.isDockhand(golem) || Conductors.isConductor(golem)) && !working && golem.getMainHandItem().isEmpty()) {
			if (level instanceof ServerLevel serverLevel) {
				ItemStack taken = golem.getOffhandItem();
				golem.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
				golem.removeAttached(HabourAttachments.HARBOUR);
				if (!player.getInventory().add(taken)) {
					golem.spawnAtLocation(serverLevel, taken);
				}
				golem.playSound(SoundEvents.SPYGLASS_STOP_USING);
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}
}
