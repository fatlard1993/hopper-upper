package justfatlard.hopper_upper.mixin;

import justfatlard.hopper_upper.Main;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public class HopperItemMixin {
	@Inject(method = "useOnBlock", at = @At("HEAD"), cancellable = true)
	private void onUseOnBlock(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
		BlockItem self = (BlockItem) (Object) this;

		// Only intercept vanilla hopper placement
		if (self.asItem() != Items.HOPPER) {
			return;
		}

		// Check if targeting the bottom face of a block (placing upward)
		Direction side = context.getSide();
		if (side != Direction.DOWN) {
			return;
		}

		World world = context.getWorld();
		if (world.isClient()) {
			cir.setReturnValue(ActionResult.SUCCESS);
			return;
		}

		// Create placement context and get the position
		ItemPlacementContext placementContext = new ItemPlacementContext(context);
		BlockPos pos = placementContext.getBlockPos();

		// Check if we can place here
		if (!placementContext.canPlace()) {
			cir.setReturnValue(ActionResult.FAIL);
			return;
		}

		// Get upward hopper placement state
		BlockState state = Main.UPWARD_HOPPER_BLOCK.getPlacementState(placementContext);
		if (state == null) {
			cir.setReturnValue(ActionResult.FAIL);
			return;
		}

		// Place the upward hopper
		if (world.setBlockState(pos, state, 11)) {
			if (context.getPlayer() != null && !context.getPlayer().getAbilities().creativeMode) {
				context.getStack().decrement(1);
			}

			if (world instanceof ServerWorld serverWorld) {
				state.getBlock().onPlaced(serverWorld, pos, state, context.getPlayer(), context.getStack());
			}

			BlockSoundGroup soundGroup = state.getSoundGroup();
			world.playSound(null, pos, soundGroup.getPlaceSound(), SoundCategory.BLOCKS,
				(soundGroup.getVolume() + 1.0F) / 2.0F, soundGroup.getPitch() * 0.8F);
			world.emitGameEvent(context.getPlayer(), GameEvent.BLOCK_PLACE, pos);

			cir.setReturnValue(ActionResult.SUCCESS);
		} else {
			cir.setReturnValue(ActionResult.FAIL);
		}
	}
}
