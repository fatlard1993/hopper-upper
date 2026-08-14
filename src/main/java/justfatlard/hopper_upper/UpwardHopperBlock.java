package justfatlard.hopper_upper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class UpwardHopperBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
	public static final BooleanProperty ENABLED = BlockStateProperties.ENABLED;
	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

	// Inverted hopper shape - mathematically inverted from vanilla (y -> 16-y)
	// Bowl walls: y 0-5, rim: y 5-6, funnel: y 6-12, spout: y 12-16
	private static final VoxelShape BOWL_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 6.0, 16.0);
	private static final VoxelShape FUNNEL_SHAPE = Block.box(4.0, 6.0, 4.0, 12.0, 12.0, 12.0);
	private static final VoxelShape SPOUT_SHAPE = Block.box(6.0, 12.0, 6.0, 10.0, 16.0, 10.0);
	private static final VoxelShape OUTLINE_SHAPE = Shapes.or(BOWL_SHAPE, FUNNEL_SHAPE, SPOUT_SHAPE);

	// Raycast shape for interactions
	private static final VoxelShape RAYCAST_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);

	public UpwardHopperBlock(Properties settings) {
		super(settings);
		this.registerDefaultState(this.getStateDefinition().any()
			.setValue(ENABLED, true)
			.setValue(WATERLOGGED, false));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
		return OUTLINE_SHAPE;
	}

	@Override
	protected VoxelShape getInteractionShape(BlockState state, BlockGetter world, BlockPos pos) {
		return RAYCAST_SHAPE;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		boolean waterlogged = context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER;
		return this.defaultBlockState()
			.setValue(ENABLED, true)
			.setValue(WATERLOGGED, waterlogged);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new UpwardHopperBlockEntity(pos, state);
	}

	@Override
	@Nullable
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
		return world.isClientSide() ? null : createTickerHelper(type, Main.UPWARD_HOPPER_BLOCK_ENTITY, UpwardHopperBlockEntity::serverTick);
	}

	@Override
	protected void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (!oldState.is(state.getBlock())) {
			this.updateEnabled(world, pos, state);
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
		if (!world.isClientSide()) {
			BlockEntity blockEntity = world.getBlockEntity(pos);
			if (blockEntity instanceof UpwardHopperBlockEntity hopperEntity) {
				player.openMenu(hopperEntity);
				player.awardStat(Stats.INSPECT_HOPPER);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void neighborChanged(BlockState state, Level world, BlockPos pos, Block sourceBlock, @Nullable Orientation orientation, boolean notify) {
		this.updateEnabled(world, pos, state);
	}

	private void updateEnabled(Level world, BlockPos pos, BlockState state) {
		boolean powered = world.hasNeighborSignal(pos);
		if (powered != !state.getValue(ENABLED)) {
			world.setBlock(pos, state.setValue(ENABLED, !powered), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel world, BlockPos pos, boolean moved) {
		Containers.updateNeighboursAfterDestroy(state, world, pos);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level world, BlockPos pos, Direction direction) {
		return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(world.getBlockEntity(pos));
	}

	@Override
	public FluidState getFluidState(BlockState state) {
		return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader world, ScheduledTickAccess tickView, BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
		if (state.getValue(WATERLOGGED)) {
			tickView.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(world));
		}
		return super.updateShape(state, world, tickView, pos, direction, neighborPos, neighborState, random);
	}

	@Override
	protected void entityInside(BlockState state, Level world, BlockPos pos, Entity entity, net.minecraft.world.entity.InsideBlockEffectApplier effectApplier, boolean movedByPiston) {
		BlockEntity blockEntity = world.getBlockEntity(pos);
		if (blockEntity instanceof UpwardHopperBlockEntity hopperEntity) {
			UpwardHopperBlockEntity.onEntityCollided(world, pos, state, entity, hopperEntity);
		}
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(ENABLED, WATERLOGGED);
	}
}
