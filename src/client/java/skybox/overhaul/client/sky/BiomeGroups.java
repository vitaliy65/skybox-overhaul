package skybox.overhaul.client.sky;

import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import skybox.overhaul.client.sky.BiomeSky.Group;

/** Biome -> sky tint group. First match wins; anything not listed keeps the plain palette. */
public final class BiomeGroups {
	private BiomeGroups() {}

	public static Group of(Holder<Biome> biome) {
		if (biome.value().getBaseTemperature() <= 0.05F) return Group.SNOW; // same cold test as the aurora
		if (biome.is(BiomeTags.IS_OCEAN)) return Group.OCEAN;
		if (biome.is(Biomes.DESERT)) return Group.DESERT;
		if (biome.is(BiomeTags.IS_BADLANDS)) return Group.BADLANDS;
		if (biome.is(BiomeTags.IS_SAVANNA)) return Group.SAVANNA;
		if (biome.is(BiomeTags.IS_JUNGLE)) return Group.JUNGLE;
		if (biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP)) return Group.SWAMP;
		if (biome.is(Biomes.CHERRY_GROVE)) return Group.CHERRY;
		return Group.NONE;
	}
}
