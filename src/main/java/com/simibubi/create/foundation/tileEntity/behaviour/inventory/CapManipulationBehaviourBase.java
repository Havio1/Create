package com.simibubi.create.foundation.tileEntity.behaviour.inventory;

import javax.annotation.Nullable;

import com.simibubi.create.foundation.tileEntity.SmartTileEntity;
import com.simibubi.create.foundation.tileEntity.TileEntityBehaviour;
import com.simibubi.create.foundation.tileEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.utility.BlockFace;

import io.github.fabricators_of_create.porting_lib.util.StorageProvider;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public abstract class CapManipulationBehaviourBase<T, S extends CapManipulationBehaviourBase<?, ?>>
	extends TileEntityBehaviour {

	// fabric: move to StorageProvider, big changes

	protected InterfaceProvider target;
	private StorageProvider<T> targetStorageProvider;
	protected boolean simulateNext;
	protected boolean bypassSided;
	protected Direction side;

	public CapManipulationBehaviourBase(SmartTileEntity te, InterfaceProvider target) {
		super(te);
		setLazyTickRate(5);
		this.target = target;
		targetStorageProvider = null;
		simulateNext = false;
		bypassSided = false;
	}

	protected abstract StorageProvider<T> getProvider(BlockPos pos, boolean bypassSided);

	@SuppressWarnings("unchecked")
	public S bypassSidedness() {
		bypassSided = true;
		return (S) this;
	}

	/**
	 * Only simulate the upcoming operation
	 */
	@SuppressWarnings("unchecked")
	public S simulate() {
		simulateNext = true;
		return (S) this;
	}

	public boolean hasInventory() {
		return getInventory() != null;
	}

	@Nullable
	public Storage<T> getInventory() {
		if (targetStorageProvider == null || side == null)
			return null;
		return targetStorageProvider.get(side);
	}

	@Override
	public void lazyTick() {
		super.lazyTick();
		if (targetStorageProvider == null) {
			BlockFace targetBlockFace = target.getTarget(getWorld(), tileEntity.getBlockPos(), tileEntity.getBlockState())
					.getOpposite();
			BlockPos pos = targetBlockFace.getPos();

			targetStorageProvider = getProvider(pos, bypassSided);
			this.side = targetBlockFace.getFace();
		}
	}

	public int getAmountFromFilter() {
		int amount = -1;
		FilteringBehaviour filter = tileEntity.getBehaviour(FilteringBehaviour.TYPE);
		if (filter != null && !filter.anyAmount())
			amount = filter.getAmount();
		return amount;
	}

	@FunctionalInterface
	public interface InterfaceProvider {

		public static InterfaceProvider towardBlockFacing() {
			return (w, p, s) -> new BlockFace(p,
				s.hasProperty(BlockStateProperties.FACING) ? s.getValue(BlockStateProperties.FACING)
					: s.getValue(BlockStateProperties.HORIZONTAL_FACING));
		}

		public static InterfaceProvider oppositeOfBlockFacing() {
			return (w, p, s) -> new BlockFace(p,
				(s.hasProperty(BlockStateProperties.FACING) ? s.getValue(BlockStateProperties.FACING)
					: s.getValue(BlockStateProperties.HORIZONTAL_FACING)).getOpposite());
		}

		public BlockFace getTarget(Level world, BlockPos pos, BlockState blockState);
	}

	public abstract static class UnsidedStorageProvider<T> extends StorageProvider<T> {
		protected UnsidedStorageProvider(BlockApiLookup<Storage<T>, Direction> lookup, Level level, BlockPos pos) {
			super(lookup, level, pos);
		}

		@Override
		@Nullable
		public Storage<T> get(Direction direction) {
			return get();
		}

		@Nullable
		public abstract Storage<T> get();
	}

}
