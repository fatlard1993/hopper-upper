package justfatlard.hopper_upper;

import com.mojang.serialization.MapCodec;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCollisionHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stats;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.tick.ScheduledTickView;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.packettweaker.PacketContext;

public class UpwardHopperBlock extends BlockWithEntity implements Waterloggable, PolymerTexturedBlock {
	public static final MapCodec<UpwardHopperBlock> CODEC = createCodec(UpwardHopperBlock::new);
	public static final BooleanProperty ENABLED = Properties.ENABLED;
	public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;

	// Inverted hopper shape - mathematically inverted from vanilla (y → 16-y)
	// Bowl walls: y 0-5, rim: y 5-6, funnel: y 6-12, spout: y 12-16
	private static final VoxelShape BOWL_SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 6.0, 16.0);
	private static final VoxelShape FUNNEL_SHAPE = Block.createCuboidShape(4.0, 6.0, 4.0, 12.0, 12.0, 12.0);
	private static final VoxelShape SPOUT_SHAPE = Block.createCuboidShape(6.0, 12.0, 6.0, 10.0, 16.0, 10.0);
	private static final VoxelShape OUTLINE_SHAPE = VoxelShapes.union(BOWL_SHAPE, FUNNEL_SHAPE, SPOUT_SHAPE);

	// Raycast shape for interactions
	private static final VoxelShape RAYCAST_SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);

	// Polymer block state for rendering
	private BlockState polymerBlockState;

	public UpwardHopperBlock(Settings settings) {
		super(settings);
		this.setDefaultState(this.stateManager.getDefaultState()
			.with(ENABLED, true)
			.with(WATERLOGGED, false));
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	public void setPolymerBlockState(BlockState state) {
		this.polymerBlockState = state;
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
		return polymerBlockState != null ? polymerBlockState : state;
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return OUTLINE_SHAPE;
	}

	@Override
	protected VoxelShape getRaycastShape(BlockState state, BlockView world, BlockPos pos) {
		return RAYCAST_SHAPE;
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext context) {
		boolean waterlogged = context.getWorld().getFluidState(context.getBlockPos()).getFluid() == Fluids.WATER;
		return this.getDefaultState()
			.with(ENABLED, true)
			.with(WATERLOGGED, waterlogged);
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new UpwardHopperBlockEntity(pos, state);
	}

	@Override
	@Nullable
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return world.isClient() ? null : validateTicker(type, Main.UPWARD_HOPPER_BLOCK_ENTITY, UpwardHopperBlockEntity::serverTick);
	}

	@Override
	protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
		if (!oldState.isOf(state.getBlock())) {
			this.updateEnabled(world, pos, state);
		}
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!world.isClient()) {
			BlockEntity blockEntity = world.getBlockEntity(pos);
			if (blockEntity instanceof UpwardHopperBlockEntity hopperEntity) {
				player.openHandledScreen(hopperEntity);
				player.incrementStat(Stats.INSPECT_HOPPER);
			}
		}
		return ActionResult.SUCCESS;
	}

	// Called by onBlockAdded and can be triggered by redstone
	private void checkRedstoneUpdate(World world, BlockPos pos, BlockState state) {
		this.updateEnabled(world, pos, state);
	}

	private void updateEnabled(World world, BlockPos pos, BlockState state) {
		boolean powered = world.isReceivingRedstonePower(pos);
		if (powered != !state.get(ENABLED)) {
			world.setBlockState(pos, state.with(ENABLED, !powered), Block.NOTIFY_LISTENERS);
		}
	}

	@Override
	protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
		ItemScatterer.onStateReplaced(state, world, pos);
		super.onStateReplaced(state, world, pos, moved);
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Override
	protected boolean hasComparatorOutput(BlockState state) {
		return true;
	}

	// Comparator output is handled by the base class when hasComparatorOutput returns true

	@Override
	public FluidState getFluidState(BlockState state) {
		return state.get(WATERLOGGED) ? Fluids.WATER.getStill(false) : super.getFluidState(state);
	}

	@Override
	protected BlockState getStateForNeighborUpdate(BlockState state, WorldView world, ScheduledTickView tickView, BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, Random random) {
		if (state.get(WATERLOGGED)) {
			tickView.scheduleFluidTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(world));
		}
		return super.getStateForNeighborUpdate(state, world, tickView, pos, direction, neighborPos, neighborState, random);
	}

	@Override
	protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity, EntityCollisionHandler handler, boolean collidedWithFluid) {
		BlockEntity blockEntity = world.getBlockEntity(pos);
		if (blockEntity instanceof UpwardHopperBlockEntity hopperEntity) {
			UpwardHopperBlockEntity.onEntityCollided(world, pos, state, entity, hopperEntity);
		}
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(ENABLED, WATERLOGGED);
	}
}
