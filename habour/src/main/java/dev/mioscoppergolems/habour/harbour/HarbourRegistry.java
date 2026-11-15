package dev.mioscoppergolems.habour.harbour;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.mioscoppergolems.habour.Habour;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * Every kontor in the world: where it is, what it is called and whether it has routes that could run. Kontors tell the
 * registry when they load or change; it lives on after their chunk unloads, so routes can still point at a harbour
 * nobody is near and the scheduler can wake harbours up.
 */
public class HarbourRegistry extends SavedData {
	public record Entry(String dimension, BlockPos pos, String name, boolean active, boolean rail) {
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
				Codec.STRING.fieldOf("dimension").forGetter(Entry::dimension),
				BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
				Codec.STRING.fieldOf("name").forGetter(Entry::name),
				Codec.BOOL.fieldOf("active").forGetter(Entry::active),
				Codec.BOOL.optionalFieldOf("rail", false).forGetter(Entry::rail)
			).apply(instance, Entry::new)
		);
	}

	public static final Codec<HarbourRegistry> CODEC = Entry.CODEC.listOf().xmap(HarbourRegistry::new, registry -> new ArrayList<>(registry.entries.values()));
	public static final SavedDataType<HarbourRegistry> TYPE = new SavedDataType<>(Habour.id("harbours"), HarbourRegistry::new, CODEC, DataFixTypes.LEVEL);

	private final Map<String, Entry> entries = new LinkedHashMap<>();

	public HarbourRegistry() {
	}

	private HarbourRegistry(List<Entry> list) {
		for (Entry entry : list) {
			this.entries.put(key(entry.dimension(), entry.pos()), entry);
		}
	}

	public static HarbourRegistry get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	private static String key(String dimension, BlockPos pos) {
		return dimension + "|" + pos.asLong();
	}

	public static String dimensionOf(ServerLevel level) {
		return level.dimension().toString();
	}

	/** Adds or updates a harbour. */
	public void put(ServerLevel level, BlockPos pos, String name, boolean active, boolean rail) {
		String dimension = dimensionOf(level);
		Entry entry = new Entry(dimension, pos.immutable(), name, active, rail);
		Entry old = this.entries.put(key(dimension, pos), entry);
		if (!entry.equals(old)) {
			this.setDirty();
		}
	}

	public void remove(ServerLevel level, BlockPos pos) {
		if (this.entries.remove(key(dimensionOf(level), pos)) != null) {
			this.setDirty();
		}
	}

	public @Nullable Entry find(ServerLevel level, BlockPos pos) {
		return this.entries.get(key(dimensionOf(level), pos));
	}

	/** All harbours in this dimension, in the order they were built. */
	public List<Entry> inDimension(ServerLevel level) {
		String dimension = dimensionOf(level);
		List<Entry> result = new ArrayList<>();
		for (Entry entry : this.entries.values()) {
			if (entry.dimension().equals(dimension)) {
				result.add(entry);
			}
		}
		return result;
	}
}
