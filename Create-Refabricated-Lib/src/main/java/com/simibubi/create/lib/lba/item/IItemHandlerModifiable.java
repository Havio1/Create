package com.simibubi.create.lib.lba.item;

import net.minecraft.item.ItemStack;

public interface IItemHandlerModifiable extends IItemHandler {
	void setStackInSlot(int slot, ItemStack stack);
}