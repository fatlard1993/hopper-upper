package justfatlard.hopper_upper;

import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main implements ModInitializer {
	public static final String MOD_ID = "hopper-upper-justfatlard";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final Identifier UPWARD_HOPPER_ID = Identifier.of(MOD_ID, "upward_hopper");

	public static final RegistryKey<net.minecraft.block.Block> UPWARD_HOPPER_BLOCK_KEY = RegistryKey.of(RegistryKeys.BLOCK, UPWARD_HOPPER_ID);
	public static final RegistryKey<Item> UPWARD_HOPPER_ITEM_KEY = RegistryKey.of(RegistryKeys.ITEM, UPWARD_HOPPER_ID);

	public static final UpwardHopperBlock UPWARD_HOPPER_BLOCK = new UpwardHopperBlock(
		AbstractBlock.Settings.create()
			.sounds(BlockSoundGroup.METAL)
			.strength(3.0F, 4.8F)
			.registryKey(UPWARD_HOPPER_BLOCK_KEY)
			.nonOpaque()
	);

	public static final UpwardHopperItem UPWARD_HOPPER_ITEM = new UpwardHopperItem(
		UPWARD_HOPPER_BLOCK,
		new Item.Settings().registryKey(UPWARD_HOPPER_ITEM_KEY).useBlockPrefixedTranslationKey()
	);

	public static BlockEntityType<UpwardHopperBlockEntity> UPWARD_HOPPER_BLOCK_ENTITY;

	@Override
	public void onInitialize() {
		// Register mod assets with Polymer resource pack system
		PolymerResourcePackUtils.addModAssets(MOD_ID);
		// Not marking as required so vanilla clients can connect

		// Register block
		Registry.register(Registries.BLOCK, UPWARD_HOPPER_ID, UPWARD_HOPPER_BLOCK);

		// Register block entity
		UPWARD_HOPPER_BLOCK_ENTITY = Registry.register(
			Registries.BLOCK_ENTITY_TYPE,
			UPWARD_HOPPER_ID,
			FabricBlockEntityTypeBuilder.create(UpwardHopperBlockEntity::new, UPWARD_HOPPER_BLOCK).build()
		);

		// Register block entity with Polymer to prevent sync to vanilla clients
		PolymerBlockUtils.registerBlockEntity(UPWARD_HOPPER_BLOCK_ENTITY);

		// Register item
		Registry.register(Registries.ITEM, UPWARD_HOPPER_ID, UPWARD_HOPPER_ITEM);

		// Setup Polymer model - use TRANSPARENT_BLOCK to avoid face culling on adjacent blocks
		Identifier modelId = Identifier.of(MOD_ID, "block/upward_hopper");
		BlockState polymerState = PolymerBlockResourceUtils.requestBlock(
			BlockModelType.TRANSPARENT_BLOCK,
			PolymerBlockModel.of(modelId)
		);

		if (polymerState != null) {
			UPWARD_HOPPER_BLOCK.setPolymerBlockState(polymerState);
		} else {
			LOGGER.error("Failed to request polymer model");
		}

		LOGGER.info("Loaded (server-side with Polymer)");
	}
}
