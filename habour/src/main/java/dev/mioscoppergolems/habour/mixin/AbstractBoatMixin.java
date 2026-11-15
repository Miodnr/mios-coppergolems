package dev.mioscoppergolems.habour.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla only shows the oars moving when the boat has a controlling passenger, and only a player is one. A copper golem
 * captain steers the boat itself, so for a boat with a golem in it the paddle flags the captain sets are used as they are
 * (on the server and on the client, which both read them here).
 */
@Mixin(AbstractBoat.class)
public abstract class AbstractBoatMixin {
	@Shadow
	private static EntityDataAccessor<Boolean> DATA_ID_PADDLE_LEFT;
	@Shadow
	private static EntityDataAccessor<Boolean> DATA_ID_PADDLE_RIGHT;

	@Inject(method = "getPaddleState", at = @At("RETURN"), cancellable = true)
	private void habour$golemRows(int side, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ()) {
			AbstractBoat self = (AbstractBoat) (Object) this;
			if (self.getFirstPassenger() instanceof CopperGolem) {
				cir.setReturnValue(self.getEntityData().get(side == 0 ? DATA_ID_PADDLE_LEFT : DATA_ID_PADDLE_RIGHT));
			}
		}
	}
}
