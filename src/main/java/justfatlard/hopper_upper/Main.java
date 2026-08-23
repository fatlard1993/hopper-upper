package justfatlard.hopper_upper;

import justfatlard.pandorical.api.BlockRegistration;
import justfatlard.pandorical.api.ItemRegistration;
import justfatlard.pandorical.api.PandoricalApi;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main implements ModInitializer {
	public static final String MOD_ID = "hopper-upper-justfatlard";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final Identifier UPWARD_HOPPER_ID = Identifier.fromNamespaceAndPath(MOD_ID, "upward_hopper");

	public static final ResourceKey<net.minecraft.world.level.block.Block> UPWARD_HOPPER_BLOCK_KEY = ResourceKey.create(Registries.BLOCK, UPWARD_HOPPER_ID);
	public static final ResourceKey<Item> UPWARD_HOPPER_ITEM_KEY = ResourceKey.create(Registries.ITEM, UPWARD_HOPPER_ID);

	public static final UpwardHopperBlock UPWARD_HOPPER_BLOCK = new UpwardHopperBlock(
		BlockBehaviour.Properties.of()
			.sound(SoundType.METAL)
			.strength(3.0F, 4.8F)
			.setId(UPWARD_HOPPER_BLOCK_KEY)
			.noOcclusion()
	);

	public static final UpwardHopperItem UPWARD_HOPPER_ITEM = new UpwardHopperItem(
		UPWARD_HOPPER_BLOCK,
		new Item.Properties().setId(UPWARD_HOPPER_ITEM_KEY).useBlockDescriptionPrefix()
	);

	public static BlockEntityType<UpwardHopperBlockEntity> UPWARD_HOPPER_BLOCK_ENTITY;

	@Override
	public void onInitialize() {
		// Guarded class load: HopperQuestRegistration names village-quests types.
		if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("village-quests-justfatlard")) {
			justfatlard.hopper_upper.integration.HopperQuestRegistration.register();
		}

		// Register with Pandorical if available
		if (PandoricalApi.isAvailable()) {
			PandoricalApi.content().registerBlock(MOD_ID + ":upward_hopper", new BlockRegistration()
				.baseBlock("minecraft:hopper")
				.interactive()
				.model(MOD_ID + ":block/upward_hopper"));
			PandoricalApi.content().registerItem(MOD_ID + ":upward_hopper", new ItemRegistration()
				.model(MOD_ID + ":item/upward_hopper"));
			PandoricalApi.content().registerModAssets(MOD_ID);
		}

		// Register block
		Registry.register(BuiltInRegistries.BLOCK, UPWARD_HOPPER_ID, UPWARD_HOPPER_BLOCK);

		// Register block entity
		UPWARD_HOPPER_BLOCK_ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			UPWARD_HOPPER_ID,
			FabricBlockEntityTypeBuilder.create(UpwardHopperBlockEntity::new, UPWARD_HOPPER_BLOCK).build()
		);

		// Register item
		Registry.register(BuiltInRegistries.ITEM, UPWARD_HOPPER_ID, UPWARD_HOPPER_ITEM);

		LOGGER.info("Loaded (server-side with Pandorical)");
	}
}
