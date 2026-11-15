package dev.mioscoppergolems.habour.captain;

import dev.mioscoppergolems.api.GolemJob;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The conductor: a copper golem holding a clock in its off hand, linked to a station. When a rail route needs a train
 * driven, the station hands an idle conductor a {@link Voyage} (it uses the same voyage record as a captain's trip).
 */
public final class Conductors {
	public static final Item MARKER = Items.CLOCK;

	public static final GolemJob JOB = new GolemJob() {
		@Override
		public boolean has(CopperGolem golem) {
			return isConductor(golem);
		}

		@Override
		public Component name() {
			return Component.translatable("job.mioscoppergolems_habour.conductor");
		}

		@Override
		public @Nullable BlockPos workplace(CopperGolem golem) {
			return Captains.harbour(golem);
		}

		@Override
		public boolean busy(CopperGolem golem) {
			return Captains.voyage(golem) != null;
		}
	};

	private Conductors() {
	}

	public static boolean isConductor(CopperGolem golem) {
		return golem.getOffhandItem().is(MARKER);
	}
}
