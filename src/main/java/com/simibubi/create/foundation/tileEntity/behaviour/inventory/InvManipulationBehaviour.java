package com.simibubi.create.foundation.tileEntity.behaviour.inventory;

import java.util.function.Function;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.google.common.base.Predicates;
import com.simibubi.create.foundation.item.ItemHelper;
import com.simibubi.create.foundation.tileEntity.SmartTileEntity;
import com.simibubi.create.foundation.tileEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.tileEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.utility.BlockFace;

import io.github.fabricators_of_create.porting_lib.transfer.StorageProvider;
import io.github.fabricators_of_create.porting_lib.transfer.TransferUtil;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class InvManipulationBehaviour extends CapManipulationBehaviourBase<ItemVariant, InvManipulationBehaviour> {

	// Extra types available for multibehaviour
	public static final BehaviourType<InvManipulationBehaviour>

	TYPE = new BehaviourType<>(), EXTRACT = new BehaviourType<>(), INSERT = new BehaviourType<>();

	private BehaviourType<InvManipulationBehaviour> behaviourType;

	public static InvManipulationBehaviour forExtraction(SmartTileEntity te, InterfaceProvider target) {
		return new InvManipulationBehaviour(EXTRACT, te, target);
	}

	public static InvManipulationBehaviour forInsertion(SmartTileEntity te, InterfaceProvider target) {
		return new InvManipulationBehaviour(INSERT, te, target);
	}

	public InvManipulationBehaviour(SmartTileEntity te, InterfaceProvider target) {
		this(TYPE, te, target);
	}

	private InvManipulationBehaviour(BehaviourType<InvManipulationBehaviour> type, SmartTileEntity te,
		InterfaceProvider target) {
		super(te, target);
		behaviourType = type;
	}

	@Override
	protected StorageProvider<ItemVariant> getProvider(BlockFace face) {
		return StorageProvider.createForItems(getWorld(), face.getPos());
	}

	@Override
	protected UnsidedStorageProvider<ItemVariant> getUnsidedProvider(BlockPos pos) {
		return new UnsidedItemStorageProvider(ItemStorage.SIDED, getWorld(), pos);
	}

	public ItemStack extract() {
		return extract(getAmountFromFilter());
	}

	public ItemStack extract(int amount) {
		return extract(amount, Predicates.alwaysTrue());
	}

	public ItemStack extract(int amount, Predicate<ItemStack> filter) {
		return extract(amount, filter, ItemStack::getMaxStackSize);
	}

	public ItemStack extract(int amount, Predicate<ItemStack> filter, Function<ItemStack, Integer> amountThreshold) {
		boolean shouldSimulate = simulateNext;
		simulateNext = false;

		if (getWorld().isClientSide || !hasInventory())
			return ItemStack.EMPTY;
		Storage<ItemVariant> inventory = getInventory();
		if (inventory == null)
			return ItemStack.EMPTY;

		Predicate<ItemStack> test = getFilterTest(filter);

		ItemStack simulatedItems = extractAmountOrThresh(inventory, test, amount, amountThreshold, true);
		if (shouldSimulate || simulatedItems.isEmpty())
			return simulatedItems;

		return extractAmountOrThresh(inventory, test, amount, amountThreshold, false);
	}

	private static ItemStack extractAmountOrThresh(Storage<ItemVariant> inventory, Predicate<ItemStack> test, int amount,
		Function<ItemStack, Integer> amountThreshold, boolean shouldSimulate) {
		if (amount == -1)
			return ItemHelper.extract(inventory, test, amountThreshold, shouldSimulate);
		return ItemHelper.extract(inventory, test, amount, shouldSimulate);
	}

	public ItemStack insert(ItemStack stack) {
		boolean shouldSimulate = simulateNext;
		simulateNext = false;
		Storage<ItemVariant> inventory = hasInventory() ? getInventory() : null;
		if (inventory == null)
			return stack;
		try (Transaction t = TransferUtil.getTransaction()) {
			long inserted = inventory.insert(ItemVariant.of(stack), stack.getCount(), t);
			if (!shouldSimulate) t.commit();
			long remainder = stack.getCount() - inserted;
			if (remainder == 0)
				return ItemStack.EMPTY;
			stack = stack.copy();
			stack.setCount((int) remainder);
			return stack;
		}
	}

	protected Predicate<ItemStack> getFilterTest(Predicate<ItemStack> customFilter) {
		Predicate<ItemStack> test = customFilter;
		FilteringBehaviour filter = tileEntity.getBehaviour(FilteringBehaviour.TYPE);
		if (filter != null)
			test = customFilter.and(filter::test);
		return test;
	}

	@Override
	public BehaviourType<?> getType() {
		return behaviourType;
	}

	public static class UnsidedItemStorageProvider extends UnsidedStorageProvider<ItemVariant> {
		protected UnsidedItemStorageProvider(BlockApiLookup<Storage<ItemVariant>, Direction> lookup, Level level, BlockPos pos) {
			super(lookup, level, pos);
		}

		@Nullable
		@Override
		public Storage<ItemVariant> get() {
			return TransferUtil.getItemStorage(level, pos);
		}
	}
}
