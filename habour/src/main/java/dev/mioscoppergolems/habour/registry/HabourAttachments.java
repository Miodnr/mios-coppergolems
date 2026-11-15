package dev.mioscoppergolems.habour.registry;

import dev.mioscoppergolems.habour.Habour;
import dev.mioscoppergolems.habour.captain.Voyage;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;

public final class HabourAttachments {
	/** The kontor a captain belongs to, linked by the player. */
	public static final AttachmentType<BlockPos> HARBOUR = AttachmentRegistry.create(Habour.id("harbour"), builder -> builder.persistent(BlockPos.CODEC));

	/** The voyage a captain is on, from loading the boat to coming home. */
	public static final AttachmentType<Voyage> VOYAGE = AttachmentRegistry.create(Habour.id("voyage"), builder -> builder.persistent(Voyage.CODEC));

	/** Game time until which a boat counts as taken by a captain; the captain renews it all the time. */
	public static final AttachmentType<Long> BOAT_TAKEN_UNTIL = AttachmentRegistry.create(Habour.id("boat_taken_until"), builder -> builder.persistent(com.mojang.serialization.Codec.LONG));

	private HabourAttachments() {
	}

	public static void init() {
	}
}
