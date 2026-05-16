package justfatlard.hopper_upper.mixin;

import justfatlard.hopper_upper.Main;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public class HopperItemMixin {
	@Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
	private void onUseOn(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
		BlockItem self = (BlockItem) (Object) this;

		// Only intercept vanilla hopper placement
		if (self.asItem() != Items.HOPPER) {
			return;
		}

		// Check if targeting the bottom face of a block (placing upward)
		Direction side = context.getClickedFace();
		if (side != Direction.DOWN) {
			return;
		}

		Level world = context.getLevel();
		if (world.isClientSide()) {
			cir.setReturnValue(InteractionResult.SUCCESS);
			return;
		}

		// Create placement context and get the position
		BlockPlaceContext placementContext = new BlockPlaceContext(context);
		BlockPos pos = placementContext.getClickedPos();

		// Check if we can place here
		if (!placementContext.canPlace()) {
			cir.setReturnValue(InteractionResult.FAIL);
			return;
		}

		// Get upward hopper placement state
		BlockState state = Main.UPWARD_HOPPER_BLOCK.getStateForPlacement(placementContext);
		if (state == null) {
			cir.setReturnValue(InteractionResult.FAIL);
			return;
		}

		// Place the upward hopper
		if (world.setBlock(pos, state, 11)) {
			if (context.getPlayer() != null && !context.getPlayer().getAbilities().instabuild) {
				context.getItemInHand().shrink(1);
			}

			if (world instanceof ServerLevel serverWorld) {
				state.getBlock().setPlacedBy(serverWorld, pos, state, context.getPlayer(), context.getItemInHand());
			}

			SoundType soundGroup = state.getSoundType();
			world.playSound(null, pos, soundGroup.getPlaceSound(), SoundSource.BLOCKS,
				(soundGroup.getVolume() + 1.0F) / 2.0F, soundGroup.getPitch() * 0.8F);
			world.gameEvent(context.getPlayer(), GameEvent.BLOCK_PLACE, pos);

			cir.setReturnValue(InteractionResult.SUCCESS);
		} else {
			cir.setReturnValue(InteractionResult.FAIL);
		}
	}
}
