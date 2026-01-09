package justfatlard.hopper_upper;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.InventoryProvider;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.screen.HopperScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class UpwardHopperBlockEntity extends LootableContainerBlockEntity {
	public static final int TRANSFER_COOLDOWN = 8;
	public static final int INVENTORY_SIZE = 5;

	// Record for storing slot index with item
	private record SlottedItem(int slot, ItemStack stack) {
		public static final Codec<SlottedItem> CODEC = RecordCodecBuilder.create(instance ->
			instance.group(
				Codec.INT.fieldOf("Slot").forGetter(SlottedItem::slot),
				ItemStack.CODEC.fieldOf("Item").forGetter(SlottedItem::stack)
			).apply(instance, SlottedItem::new)
		);
	}

	private static final Codec<List<SlottedItem>> SLOTTED_ITEMS_CODEC = SlottedItem.CODEC.listOf();

	private DefaultedList<ItemStack> inventory = DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY);
	private int transferCooldown = -1;
	private long lastTickTime;

	public UpwardHopperBlockEntity(BlockPos pos, BlockState state) {
		super(Main.UPWARD_HOPPER_BLOCK_ENTITY, pos, state);
	}

	@Override
	protected void writeData(WriteView writeView) {
		super.writeData(writeView);

		// Only serialize non-empty slots with their indices
		List<SlottedItem> nonEmptyItems = new ArrayList<>();
		for (int i = 0; i < this.inventory.size(); i++) {
			ItemStack stack = this.inventory.get(i);
			if (!stack.isEmpty()) {
				nonEmptyItems.add(new SlottedItem(i, stack));
			}
		}
		if (!nonEmptyItems.isEmpty()) {
			writeView.put("Items", SLOTTED_ITEMS_CODEC, nonEmptyItems);
		}

		writeView.putInt("TransferCooldown", this.transferCooldown);
	}

	@Override
	protected void readData(ReadView readView) {
		super.readData(readView);

		// Clear inventory first
		this.inventory = DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY);

		// Load items into their correct slots
		readView.read("Items", SLOTTED_ITEMS_CODEC).ifPresent(items -> {
			for (SlottedItem slottedItem : items) {
				if (slottedItem.slot >= 0 && slottedItem.slot < INVENTORY_SIZE) {
					this.inventory.set(slottedItem.slot, slottedItem.stack);
				}
			}
		});

		this.transferCooldown = readView.getInt("TransferCooldown", -1);
	}

	@Override
	public int size() {
		return this.inventory.size();
	}

	@Override
	protected DefaultedList<ItemStack> getHeldStacks() {
		return this.inventory;
	}

	@Override
	protected void setHeldStacks(DefaultedList<ItemStack> inventory) {
		this.inventory = inventory;
	}

	@Override
	protected Text getContainerName() {
		return Text.translatable("container.hopper");
	}

	@Override
	protected ScreenHandler createScreenHandler(int syncId, PlayerInventory playerInventory) {
		return new HopperScreenHandler(syncId, playerInventory, this);
	}

	public static void serverTick(World world, BlockPos pos, BlockState state, UpwardHopperBlockEntity blockEntity) {
		--blockEntity.transferCooldown;
		blockEntity.lastTickTime = world.getTime();

		if (!blockEntity.needsCooldown()) {
			blockEntity.setTransferCooldown(0);
			insertAndExtract(world, pos, state, blockEntity);
		}
	}

	private static void insertAndExtract(World world, BlockPos pos, BlockState state, UpwardHopperBlockEntity blockEntity) {
		if (!world.isClient()) {
			if (!blockEntity.needsCooldown() && state.get(UpwardHopperBlock.ENABLED)) {
				boolean changed = false;

				if (!blockEntity.isEmpty()) {
					changed = insert(world, pos, blockEntity);
				}

				if (!blockEntity.isFull()) {
					changed |= extract(world, pos, blockEntity);
				}

				if (changed) {
					blockEntity.setTransferCooldown(TRANSFER_COOLDOWN);
					markDirty(world, pos, state);
				}
			}
		}
	}

	private boolean isFull() {
		for (ItemStack stack : this.inventory) {
			if (stack.isEmpty() || stack.getCount() < stack.getMaxCount()) {
				return false;
			}
		}
		return true;
	}

	// Insert items INTO the block ABOVE (push up)
	private static boolean insert(World world, BlockPos pos, UpwardHopperBlockEntity blockEntity) {
		Inventory targetInventory = getInventoryAbove(world, pos);
		if (targetInventory == null) {
			return false;
		}

		Direction insertDirection = Direction.DOWN; // We're inserting from below into the target

		if (isInventoryFull(targetInventory, insertDirection)) {
			return false;
		}

		for (int i = 0; i < blockEntity.size(); i++) {
			ItemStack stack = blockEntity.getStack(i);
			if (!stack.isEmpty()) {
				ItemStack copyStack = stack.copy();
				ItemStack remaining = transfer(blockEntity, targetInventory, blockEntity.removeStack(i, 1), insertDirection);

				if (remaining.isEmpty()) {
					targetInventory.markDirty();
					return true;
				}

				blockEntity.setStack(i, copyStack);
			}
		}

		return false;
	}

	// Extract items FROM the block BELOW (pull from below)
	private static boolean extract(World world, BlockPos pos, UpwardHopperBlockEntity blockEntity) {
		Inventory sourceInventory = getInventoryBelow(world, pos);
		if (sourceInventory != null) {
			Direction extractDirection = Direction.UP; // We're extracting from above the source

			if (!isInventoryEmpty(sourceInventory, extractDirection)) {
				return extractFromInventory(blockEntity, sourceInventory, extractDirection);
			}
		}

		// Also collect item entities from below
		return extractFromItemEntities(world, pos, blockEntity);
	}

	private static boolean extractFromInventory(UpwardHopperBlockEntity hopper, Inventory inventory, Direction direction) {
		if (inventory instanceof SidedInventory sidedInventory) {
			int[] slots = sidedInventory.getAvailableSlots(direction);
			for (int slot : slots) {
				if (extractFromSlot(hopper, inventory, slot, direction)) {
					return true;
				}
			}
		} else {
			int size = inventory.size();
			for (int slot = 0; slot < size; slot++) {
				if (extractFromSlot(hopper, inventory, slot, direction)) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean extractFromSlot(UpwardHopperBlockEntity hopper, Inventory inventory, int slot, Direction direction) {
		ItemStack stack = inventory.getStack(slot);
		if (!stack.isEmpty() && canExtract(hopper, inventory, stack, slot, direction)) {
			ItemStack copyStack = stack.copy();
			ItemStack remaining = transfer(inventory, hopper, inventory.removeStack(slot, 1), null);

			if (remaining.isEmpty()) {
				inventory.markDirty();
				return true;
			}

			inventory.setStack(slot, copyStack);
		}
		return false;
	}

	private static boolean extractFromItemEntities(World world, BlockPos pos, UpwardHopperBlockEntity hopper) {
		// Get item entities from below the hopper
		Box belowBox = new Box(
			pos.getX(), pos.getY() - 1, pos.getZ(),
			pos.getX() + 1, pos.getY(), pos.getZ() + 1
		);

		List<ItemEntity> items = world.getEntitiesByClass(ItemEntity.class, belowBox, EntityPredicates.VALID_ENTITY);

		for (ItemEntity itemEntity : items) {
			if (collectItemEntity(hopper, itemEntity)) {
				return true;
			}
		}

		return false;
	}

	public static void onEntityCollided(World world, BlockPos pos, BlockState state, Entity entity, UpwardHopperBlockEntity hopper) {
		if (entity instanceof ItemEntity itemEntity && !itemEntity.getStack().isEmpty()) {
			// Only collect items from below
			if (itemEntity.getY() < pos.getY()) {
				if (collectItemEntity(hopper, itemEntity)) {
					hopper.setTransferCooldown(TRANSFER_COOLDOWN);
				}
			}
		}
	}

	private static boolean collectItemEntity(UpwardHopperBlockEntity hopper, ItemEntity itemEntity) {
		ItemStack stack = itemEntity.getStack().copy();
		ItemStack remaining = transfer(null, hopper, stack, null);

		if (remaining.isEmpty()) {
			itemEntity.discard();
			return true;
		} else {
			itemEntity.setStack(remaining);
			return false;
		}
	}

	private static ItemStack transfer(@Nullable Inventory from, Inventory to, ItemStack stack, @Nullable Direction direction) {
		if (to instanceof SidedInventory sidedInventory && direction != null) {
			int[] slots = sidedInventory.getAvailableSlots(direction);
			for (int i = 0; i < slots.length && !stack.isEmpty(); i++) {
				stack = transferToSlot(from, to, stack, slots[i], direction);
			}
		} else {
			int size = to.size();
			for (int i = 0; i < size && !stack.isEmpty(); i++) {
				stack = transferToSlot(from, to, stack, i, direction);
			}
		}
		return stack;
	}

	private static ItemStack transferToSlot(@Nullable Inventory from, Inventory to, ItemStack stack, int slot, @Nullable Direction direction) {
		ItemStack existingStack = to.getStack(slot);

		if (canInsert(to, stack, slot, direction)) {
			if (existingStack.isEmpty()) {
				int maxCount = Math.min(stack.getMaxCount(), to.getMaxCountPerStack());
				int transferCount = Math.min(stack.getCount(), maxCount);
				to.setStack(slot, stack.split(transferCount));
			} else if (canMerge(existingStack, stack)) {
				int space = Math.min(to.getMaxCountPerStack(), existingStack.getMaxCount()) - existingStack.getCount();
				int transferCount = Math.min(stack.getCount(), space);
				stack.decrement(transferCount);
				existingStack.increment(transferCount);
			}
		}

		return stack;
	}

	private static boolean canInsert(Inventory inventory, ItemStack stack, int slot, @Nullable Direction direction) {
		if (!inventory.isValid(slot, stack)) {
			return false;
		}
		if (inventory instanceof SidedInventory sidedInventory) {
			return sidedInventory.canInsert(slot, stack, direction);
		}
		return true;
	}

	private static boolean canExtract(Inventory hopper, Inventory inventory, ItemStack stack, int slot, Direction direction) {
		if (inventory instanceof SidedInventory sidedInventory) {
			return sidedInventory.canExtract(slot, stack, direction);
		}
		return true;
	}

	private static boolean canMerge(ItemStack first, ItemStack second) {
		return first.getCount() < first.getMaxCount() && ItemStack.areItemsAndComponentsEqual(first, second);
	}

	@Nullable
	private static Inventory getInventoryAbove(World world, BlockPos pos) {
		return getInventoryAt(world, pos.up());
	}

	@Nullable
	private static Inventory getInventoryBelow(World world, BlockPos pos) {
		return getInventoryAt(world, pos.down());
	}

	@Nullable
	private static Inventory getInventoryAt(World world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		Block block = state.getBlock();

		if (block instanceof InventoryProvider inventoryProvider) {
			return inventoryProvider.getInventory(state, world, pos);
		}

		if (state.hasBlockEntity()) {
			BlockEntity blockEntity = world.getBlockEntity(pos);
			if (blockEntity instanceof Inventory inventory) {
				if (inventory instanceof ChestBlockEntity && block instanceof ChestBlock) {
					return ChestBlock.getInventory((ChestBlock) block, state, world, pos, true);
				}
				return inventory;
			}
		}

		return null;
	}

	private static boolean isInventoryFull(Inventory inventory, Direction direction) {
		if (inventory instanceof SidedInventory sidedInventory) {
			int[] slots = sidedInventory.getAvailableSlots(direction);
			for (int slot : slots) {
				ItemStack stack = inventory.getStack(slot);
				if (stack.isEmpty() || stack.getCount() < stack.getMaxCount()) {
					return false;
				}
			}
			return true;
		}

		int size = inventory.size();
		for (int i = 0; i < size; i++) {
			ItemStack stack = inventory.getStack(i);
			if (stack.isEmpty() || stack.getCount() < stack.getMaxCount()) {
				return false;
			}
		}
		return true;
	}

	private static boolean isInventoryEmpty(Inventory inventory, Direction direction) {
		if (inventory instanceof SidedInventory sidedInventory) {
			int[] slots = sidedInventory.getAvailableSlots(direction);
			for (int slot : slots) {
				if (!inventory.getStack(slot).isEmpty()) {
					return false;
				}
			}
			return true;
		}

		int size = inventory.size();
		for (int i = 0; i < size; i++) {
			if (!inventory.getStack(i).isEmpty()) {
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
