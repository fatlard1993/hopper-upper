package justfatlard.hopper_upper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class UpwardHopperBlockEntity extends BaseContainerBlockEntity {
	public static final int TRANSFER_COOLDOWN = 8;
	public static final int INVENTORY_SIZE = 5;

	private NonNullList<ItemStack> inventory = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
	private int transferCooldown = -1;

	public UpwardHopperBlockEntity(BlockPos pos, BlockState state) {
		super(Main.UPWARD_HOPPER_BLOCK_ENTITY, pos, state);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, this.inventory);
		output.putInt("TransferCooldown", this.transferCooldown);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.inventory = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, this.inventory);
		this.transferCooldown = input.getIntOr("TransferCooldown", -1);
	}

	@Override
	public int getContainerSize() {
		return this.inventory.size();
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return this.inventory;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> inventory) {
		this.inventory = inventory;
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("container.hopper");
	}

	@Override
	protected AbstractContainerMenu createMenu(int syncId, Inventory playerInventory) {
		return new HopperMenu(syncId, playerInventory, this);
	}

	public static void serverTick(Level world, BlockPos pos, BlockState state, UpwardHopperBlockEntity blockEntity) {
		--blockEntity.transferCooldown;

		if (!blockEntity.needsCooldown()) {
			blockEntity.setTransferCooldown(0);
			insertAndExtract(world, pos, state, blockEntity);
		}
	}

	private static void insertAndExtract(Level world, BlockPos pos, BlockState state, UpwardHopperBlockEntity blockEntity) {
		if (!world.isClientSide()) {
			if (!blockEntity.needsCooldown() && state.getValue(UpwardHopperBlock.ENABLED)) {
				boolean changed = false;

				if (!blockEntity.isEmpty()) {
					changed = insert(world, pos, blockEntity);
				}

				if (!blockEntity.isFull()) {
					changed |= extract(world, pos, blockEntity);
				}

				if (changed) {
					blockEntity.setTransferCooldown(TRANSFER_COOLDOWN);
					setChanged(world, pos, state);
				}
			}
		}
	}

	private boolean isFull() {
		for (ItemStack stack : this.inventory) {
			if (stack.isEmpty() || stack.getCount() < stack.getMaxStackSize()) {
				return false;
			}
		}
		return true;
	}

	// Insert items INTO the block ABOVE (push up)
	private static boolean insert(Level world, BlockPos pos, UpwardHopperBlockEntity blockEntity) {
		Container targetInventory = getInventoryAbove(world, pos);
		if (targetInventory == null) {
			return false;
		}

		Direction insertDirection = Direction.DOWN; // We're inserting from below into the target

		if (isInventoryFull(targetInventory, insertDirection)) {
			return false;
		}

		for (int i = 0; i < blockEntity.getContainerSize(); i++) {
			ItemStack stack = blockEntity.getItem(i);
			if (!stack.isEmpty()) {
				ItemStack copyStack = stack.copy();
				ItemStack remaining = transfer(blockEntity, targetInventory, blockEntity.removeItem(i, 1), insertDirection);

				if (remaining.isEmpty()) {
					targetInventory.setChanged();
					return true;
				}

				blockEntity.setItem(i, copyStack);
			}
		}

		return false;
	}

	// Extract items FROM the block BELOW (pull from below)
	private static boolean extract(Level world, BlockPos pos, UpwardHopperBlockEntity blockEntity) {
		Container sourceInventory = getInventoryBelow(world, pos);
		if (sourceInventory != null) {
			Direction extractDirection = Direction.UP; // We're extracting from above the source

			if (!isInventoryEmpty(sourceInventory, extractDirection)) {
				return extractFromInventory(blockEntity, sourceInventory, extractDirection);
			}
		}

		// Also collect item entities from below
		return extractFromItemEntities(world, pos, blockEntity);
	}

	private static boolean extractFromInventory(UpwardHopperBlockEntity hopper, Container inventory, Direction direction) {
		if (inventory instanceof WorldlyContainer worldlyContainer) {
			int[] slots = worldlyContainer.getSlotsForFace(direction);
			for (int slot : slots) {
				if (extractFromSlot(hopper, inventory, slot, direction)) {
					return true;
				}
			}
		} else {
			int size = inventory.getContainerSize();
			for (int slot = 0; slot < size; slot++) {
				if (extractFromSlot(hopper, inventory, slot, direction)) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean extractFromSlot(UpwardHopperBlockEntity hopper, Container inventory, int slot, Direction direction) {
		ItemStack stack = inventory.getItem(slot);
		if (!stack.isEmpty() && canExtract(hopper, inventory, stack, slot, direction)) {
			ItemStack copyStack = stack.copy();
			ItemStack remaining = transfer(inventory, hopper, inventory.removeItem(slot, 1), Direction.DOWN);

			if (remaining.isEmpty()) {
				inventory.setChanged();
				return true;
			}

			inventory.setItem(slot, copyStack);
		}
		return false;
	}

	private static boolean extractFromItemEntities(Level world, BlockPos pos, UpwardHopperBlockEntity hopper) {
		// Get item entities from below the hopper
		AABB belowBox = new AABB(
			pos.getX(), pos.getY() - 1, pos.getZ(),
			pos.getX() + 1, pos.getY(), pos.getZ() + 1
		);

		List<ItemEntity> items = world.getEntitiesOfClass(ItemEntity.class, belowBox, EntitySelector.ENTITY_STILL_ALIVE);

		for (ItemEntity itemEntity : items) {
			if (collectItemEntity(hopper, itemEntity)) {
				return true;
			}
		}

		return false;
	}

	public static void onEntityCollided(Level world, BlockPos pos, BlockState state, Entity entity, UpwardHopperBlockEntity hopper) {
		if (!state.getValue(UpwardHopperBlock.ENABLED)) {
			return;
		}
		if (entity instanceof ItemEntity itemEntity && !itemEntity.getItem().isEmpty()) {
			// Only collect items from below
			if (itemEntity.getY() < pos.getY()) {
				if (collectItemEntity(hopper, itemEntity)) {
					hopper.setTransferCooldown(TRANSFER_COOLDOWN);
				}
			}
		}
	}

	private static boolean collectItemEntity(UpwardHopperBlockEntity hopper, ItemEntity itemEntity) {
		ItemStack stack = itemEntity.getItem().copy();
		ItemStack remaining = transfer(null, hopper, stack, null);

		if (remaining.isEmpty()) {
			itemEntity.discard();
			return true;
		} else {
			itemEntity.setItem(remaining);
			return false;
		}
	}

	private static ItemStack transfer(@Nullable Container from, Container to, ItemStack stack, @Nullable Direction direction) {
		if (to instanceof WorldlyContainer worldlyContainer && direction != null) {
			int[] slots = worldlyContainer.getSlotsForFace(direction);
			for (int i = 0; i < slots.length && !stack.isEmpty(); i++) {
				stack = transferToSlot(from, to, stack, slots[i], direction);
			}
		} else {
			int size = to.getContainerSize();
			for (int i = 0; i < size && !stack.isEmpty(); i++) {
				stack = transferToSlot(from, to, stack, i, direction);
			}
		}
		return stack;
	}

	private static ItemStack transferToSlot(@Nullable Container from, Container to, ItemStack stack, int slot, @Nullable Direction direction) {
		ItemStack existingStack = to.getItem(slot);

		if (canInsert(to, stack, slot, direction)) {
			if (existingStack.isEmpty()) {
				int maxCount = Math.min(stack.getMaxStackSize(), to.getMaxStackSize());
				int transferCount = Math.min(stack.getCount(), maxCount);
				to.setItem(slot, stack.split(transferCount));
			} else if (canMerge(existingStack, stack)) {
				int space = Math.min(to.getMaxStackSize(), existingStack.getMaxStackSize()) - existingStack.getCount();
				int transferCount = Math.min(stack.getCount(), space);
				stack.shrink(transferCount);
				existingStack.grow(transferCount);
			}
		}

		return stack;
	}

	private static boolean canInsert(Container inventory, ItemStack stack, int slot, @Nullable Direction direction) {
		if (!inventory.canPlaceItem(slot, stack)) {
			return false;
		}
		if (inventory instanceof WorldlyContainer worldlyContainer) {
			return worldlyContainer.canPlaceItemThroughFace(slot, stack, direction);
		}
		return true;
	}

	private static boolean canExtract(Container hopper, Container inventory, ItemStack stack, int slot, Direction direction) {
		if (inventory instanceof WorldlyContainer worldlyContainer) {
			return worldlyContainer.canTakeItemThroughFace(slot, stack, direction);
		}
		return true;
	}

	private static boolean canMerge(ItemStack first, ItemStack second) {
		return first.getCount() < first.getMaxStackSize() && ItemStack.isSameItemSameComponents(first, second);
	}

	@Nullable
	private static Container getInventoryAbove(Level world, BlockPos pos) {
		return getInventoryAt(world, pos.above());
	}

	@Nullable
	private static Container getInventoryBelow(Level world, BlockPos pos) {
		return getInventoryAt(world, pos.below());
	}

	@Nullable
	private static Container getInventoryAt(Level world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		Block block = state.getBlock();

		if (block instanceof WorldlyContainerHolder containerHolder) {
			return containerHolder.getContainer(state, world, pos);
		}

		if (state.hasBlockEntity()) {
			BlockEntity blockEntity = world.getBlockEntity(pos);
			if (blockEntity instanceof Container inventory) {
				if (inventory instanceof ChestBlockEntity && block instanceof ChestBlock) {
					return ChestBlock.getContainer((ChestBlock) block, state, world, pos, true);
				}
				return inventory;
			}
		}

		return null;
	}

	private static boolean isInventoryFull(Container inventory, Direction direction) {
		int maxPerSlot = inventory.getMaxStackSize();
		if (inventory instanceof WorldlyContainer worldlyContainer) {
			int[] slots = worldlyContainer.getSlotsForFace(direction);
			for (int slot : slots) {
				ItemStack stack = inventory.getItem(slot);
				if (stack.isEmpty() || stack.getCount() < Math.min(stack.getMaxStackSize(), maxPerSlot)) {
					return false;
				}
			}
			return true;
		}

		int size = inventory.getContainerSize();
		for (int i = 0; i < size; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty() || stack.getCount() < Math.min(stack.getMaxStackSize(), maxPerSlot)) {
				return false;
			}
		}
		return true;
	}

	private static boolean isInventoryEmpty(Container inventory, Direction direction) {
		if (inventory instanceof WorldlyContainer worldlyContainer) {
			int[] slots = worldlyContainer.getSlotsForFace(direction);
			for (int slot : slots) {
				if (!inventory.getItem(slot).isEmpty()) {
					return false;
				}
			}
			return true;
		}

		int size = inventory.getContainerSize();
		for (int i = 0; i < size; i++) {
			if (!inventory.getItem(i).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	private void setTransferCooldown(int cooldown) {
		this.transferCooldown = cooldown;
	}

	private boolean needsCooldown() {
		return this.transferCooldown > 0;
	}
}
