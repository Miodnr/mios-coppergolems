package dev.mioscoppergolems.habour.kontor;

/**
 * The railway's counterpart of the kontor: the warehouse and office of a station. Trains of chest minecarts stop on the
 * track next to it, conductors drive them, dockhands load and unload them. Everything else works like the kontor, so
 * it uses the same block entity: the block decides whether boats or trains are sent.
 */
public class StationBlock extends KontorBlock {
	public StationBlock(Properties properties) {
		super(properties);
	}

	@Override
	public void setPlacedBy(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state,
		net.minecraft.world.entity.@org.jspecify.annotations.Nullable LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (placer instanceof net.minecraft.world.entity.player.Player player) {
			dev.mioscoppergolems.api.MiosCoppergolemsApi.awardGuide(player, dev.mioscoppergolems.habour.Habour.id("guide/station"));
		}
	}
}
