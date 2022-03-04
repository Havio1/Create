package com.simibubi.create.content.logistics.trains.management.signal;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.common.base.Objects;
import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.trains.TrackGraph;
import com.simibubi.create.content.logistics.trains.TrackNode;
import com.simibubi.create.content.logistics.trains.management.signal.SignalTileEntity.OverlayState;
import com.simibubi.create.content.logistics.trains.management.signal.SignalTileEntity.SignalState;
import com.simibubi.create.foundation.utility.Couple;
import com.simibubi.create.foundation.utility.Iterate;
import com.simibubi.create.foundation.utility.NBTHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.LevelAccessor;

public class SignalBoundary extends TrackEdgePoint {

	public Couple<Set<BlockPos>> signals;
	public Couple<UUID> groups;
	public Couple<Boolean> sidesToUpdate;

	public SignalBoundary() {
		signals = Couple.create(HashSet::new);
		groups = Couple.create(null, null);
		sidesToUpdate = Couple.create(true, true);
	}

	public void setGroup(TrackNode side, UUID groupId) {
		boolean primary = isPrimary(side);
		groups.set(primary, groupId);
		sidesToUpdate.set(primary, false);
	}

	@Override
	public boolean canMerge() {
		return true;
	}

	@Override
	public void invalidate(LevelAccessor level) {
		signals.forEach(s -> s.forEach(pos -> invalidateAt(level, pos)));
	}

	@Override
	public void tileAdded(BlockPos tilePos, boolean front) {
		signals.get(front)
			.add(tilePos);
	}

	@Override
	public void tileRemoved(BlockPos tilePos, boolean front) {
		signals.forEach(s -> s.remove(tilePos));
		if (signals.both(Set::isEmpty))
			removeFromAllGraphs();
	}

	@Override
	public void onRemoved(TrackGraph graph) {
		super.onRemoved(graph);
		SignalPropagator.onSignalRemoved(graph, this);
	}

	public void queueUpdate(TrackNode side) {
		sidesToUpdate.set(isPrimary(side), true);
	}

	public UUID getGroup(TrackNode side) {
		return groups.get(isPrimary(side));
	}

	@Override
	public boolean canNavigateVia(TrackNode side) {
		return !signals.get(isPrimary(side))
			.isEmpty();
	}

	public OverlayState getOverlayFor(BlockPos tile) {
		for (boolean first : Iterate.trueAndFalse) {
			Set<BlockPos> set = signals.get(first);
			for (BlockPos blockPos : set) {
				if (blockPos.equals(tile))
					return signals.get(!first)
						.isEmpty() ? OverlayState.RENDER : OverlayState.DUAL;
				return OverlayState.SKIP;
			}
		}
		return OverlayState.SKIP;
	}

	public SignalState getStateFor(BlockPos tile) {
		for (boolean first : Iterate.trueAndFalse) {
			Set<BlockPos> set = signals.get(first);
			if (!set.contains(tile))
				continue;
			UUID group = groups.get(first);
			if (Objects.equal(group, groups.get(!first)))
				return SignalState.INVALID;
			Map<UUID, SignalEdgeGroup> signalEdgeGroups = Create.RAILWAYS.signalEdgeGroups;
			SignalEdgeGroup signalEdgeGroup = signalEdgeGroups.get(group);
			if (signalEdgeGroup == null)
				return SignalState.INVALID;
			return signalEdgeGroup.isOccupiedUnless(this) ? SignalState.RED : SignalState.GREEN;
		}
		return SignalState.INVALID;
	}

	@Override
	public void tick(TrackGraph graph) {
		super.tick(graph);
		for (boolean front : Iterate.trueAndFalse) {
			if (!sidesToUpdate.get(front))
				continue;
			sidesToUpdate.set(front, false);
			SignalPropagator.propagateSignalGroup(graph, this, front);
		}
	}

	@Override
	public void read(CompoundTag nbt, boolean migration) {
		super.read(nbt, migration);
		
		if (migration)
			return;

		sidesToUpdate = Couple.create(true, true);
		signals = Couple.create(HashSet::new);
		groups = Couple.create(null, null);

		for (int i = 1; i <= 2; i++)
			if (nbt.contains("Tiles" + i)) {
				boolean first = i == 1;
				NBTHelper.iterateCompoundList(nbt.getList("Tiles" + i, Tag.TAG_COMPOUND), c -> signals.get(first)
					.add(NbtUtils.readBlockPos(c)));
			}
		for (int i = 1; i <= 2; i++)
			if (nbt.contains("Group" + i))
				groups.set(i == 1, nbt.getUUID("Group" + i));
		for (int i = 1; i <= 2; i++)
			sidesToUpdate.set(i == 1, nbt.contains("Update" + i));
	}
	
	@Override
	public void read(FriendlyByteBuf buffer) {
		super.read(buffer);
		for (int i = 1; i <= 2; i++) {
			if (buffer.readBoolean())
				groups.set(i == 1, buffer.readUUID());
		}
	}

	@Override
	public void write(CompoundTag nbt) {
		super.write(nbt);
		for (int i = 1; i <= 2; i++)
			if (!signals.get(i == 1)
				.isEmpty())
				nbt.put("Tiles" + i, NBTHelper.writeCompoundList(signals.get(i == 1), NbtUtils::writeBlockPos));
		for (int i = 1; i <= 2; i++)
			if (groups.get(i == 1) != null)
				nbt.putUUID("Group" + i, groups.get(i == 1));
		for (int i = 1; i <= 2; i++)
			if (sidesToUpdate.get(i == 1))
				nbt.putBoolean("Update" + i, true);
	}
	
	@Override
	public void write(FriendlyByteBuf buffer) {
		super.write(buffer);
		for (int i = 1; i <= 2; i++) {
			boolean hasGroup = groups.get(i == 1) != null;
			buffer.writeBoolean(hasGroup);
			if (hasGroup)
				buffer.writeUUID(groups.get(i == 1));
		}
	}

}
