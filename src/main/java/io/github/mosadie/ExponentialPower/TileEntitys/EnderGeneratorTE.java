package io.github.mosadie.ExponentialPower.TileEntitys;

import javax.annotation.Nullable;

import io.github.mosadie.ExponentialPower.ExponentialPower;
import io.github.mosadie.ExponentialPower.Items.ItemManager;
import io.github.mosadie.ExponentialPower.energy.generator.*;
import net.darkhax.tesla.api.ITeslaConsumer;
import net.darkhax.tesla.capability.TeslaCapabilities;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.energy.*;
import net.minecraftforge.fml.common.Loader;


public class EnderGeneratorTE extends TileEntity implements ITickable, IInventory, ICapabilityProvider {


	public long currentOutput = 0;
	public long energy = 0;
	public NonNullList<ItemStack> Inventory = NonNullList.withSize(1, ItemStack.EMPTY);
	public String customName;
	
	private final long base;
	private int maxStack;

	private ForgeEnergyConnection fec;
	private TeslaEnergyConnection tec;

	public EnderGeneratorTE() {
		base = ExponentialPower.getConfigProp(ExponentialPower.CONFIG_ENDER_GENERATOR, "Base", "Controls the rate of change of the power output. Remember Base^MaxStack must be less than Long.MAX_VALUE for things to work correctly.", Long.toString(2)).getLong();
		maxStack = ExponentialPower.getConfigProp(ExponentialPower.CONFIG_ENDER_GENERATOR, "MaxStack", "Controls the number of Ender Cells required to reach the maximum power output. Min: 1 Max: 64 (inclusive)", Integer.toString(64)).getInt();
		if (maxStack > 64) maxStack = 64;
		else if (maxStack <= 0) maxStack = 1;
		fec = new ForgeEnergyConnection(this, true, false);
		if (Loader.isModLoaded("tesla"))
			tec = new TeslaEnergyConnection(this);
	}

	@Override
	public boolean hasCapability(Capability<?> cap, @Nullable EnumFacing f) {
		if (tec != null)
			return cap == CapabilityEnergy.ENERGY || cap == TeslaCapabilities.CAPABILITY_PRODUCER;
		else
			return cap == CapabilityEnergy.ENERGY;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T getCapability(Capability<T> cap, @Nullable EnumFacing f) {
		if (cap == CapabilityEnergy.ENERGY) return (T) fec;
		if (tec != null) if (cap == TeslaCapabilities.CAPABILITY_PRODUCER) return (T) tec;
		return null;
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);

		NBTTagList list = new NBTTagList();
		for (int i = 0; i < this.getSizeInventory(); ++i) {
			if (this.getStackInSlot(i) != null) {
				NBTTagCompound stackTag = new NBTTagCompound();
				stackTag.setByte("Slot", (byte) i);
				this.getStackInSlot(i).writeToNBT(stackTag);
				list.appendTag(stackTag);
			}
		}
		nbt.setTag("Items", list);

		if (this.hasCustomName()) {
			nbt.setString("CustomName", this.getCustomName());
		}

		return nbt;
	}


	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		NBTTagList list = nbt.getTagList("Items", 10);
		for (int i = 0; i < list.tagCount(); ++i) {
			NBTTagCompound stackTag = list.getCompoundTagAt(i);
			int slot = stackTag.getByte("Slot") & 255;
			this.setInventorySlotContents(slot, new ItemStack(stackTag));
		}

		if (nbt.hasKey("CustomName", 8)) {
			this.setCustomName(nbt.getString("CustomName"));
		}
	}

	@Override
	public void update() {
		if (Inventory != null) {
			if (Inventory.get(0).getItem() == ItemManager.enderCell) {
				energy = currentOutput;
				currentOutput = longPow(base,(63*((Inventory.get(0).getCount())/(double)maxStack)))-1L;
				if (currentOutput == 0) currentOutput = 1;
			}
			else {
				currentOutput = 0;
				energy = 0;
			}
		}
		handleSendingEnergy();
	}

	@Override
	public String getName() {
		return this.hasCustomName() ? this.customName : "container.exponentialpower_endergenerator_tile_entity";
	}

	@Override
	public boolean hasCustomName() {
		return this.customName != null && !this.customName.equals("");
	}

	@Override
	public int getSizeInventory() {
		return 1;
	}

	@Override
	public ItemStack getStackInSlot(int index) {
		if (index < 0 || index >= this.getSizeInventory())
			return ItemStack.EMPTY;
		return this.Inventory.get(0);
	}

	@Override
	public ItemStack decrStackSize(int index, int count) {
		if (this.getStackInSlot(index) != null) {
			ItemStack itemstack;

			if (this.getStackInSlot(index).getCount() <= count) { //Inventory has less or same as amount asked for
				itemstack = this.getStackInSlot(index);
				//itemstack.shrink(1);
				this.setInventorySlotContents(index, ItemStack.EMPTY);//new ItemStack(ItemManager.enderCell,1));
				this.markDirty();
				if (itemstack.getCount() > 0) return itemstack;
				else return ItemStack.EMPTY;
			} else { //More in slot then asked for
				itemstack = this.getStackInSlot(index).splitStack(count);

				if (this.getStackInSlot(index).getCount() <= 0) {
					this.setInventorySlotContents(index, ItemStack.EMPTY);//new ItemStack(ItemManager.enderCell,1));
				} else {
					//Just to show that changes happened
					this.setInventorySlotContents(index, this.getStackInSlot(index));
				}

				this.markDirty();
				return itemstack;
			}
		} else {
			return ItemStack.EMPTY;
		}
	}


	@Override
	public void setInventorySlotContents(int index, ItemStack stack) {
		if (index < 0 || index >= this.getSizeInventory())
			return;

		if (stack != null && stack.getCount() > this.getInventoryStackLimit())
			stack.setCount(this.getInventoryStackLimit());

		if (stack != null && stack.getCount() == 0)
			stack = ItemStack.EMPTY;

		this.Inventory.set(0, stack);
		this.markDirty();
	}

	@Override
	public ItemStack removeStackFromSlot(int index) {
		if (index < 0 || index >= this.getSizeInventory())
			return ItemStack.EMPTY;
		ItemStack whatWasThere = this.Inventory.get(0);
		this.Inventory.set(0,ItemStack.EMPTY);
		this.markDirty();
		return whatWasThere;
	}

	@Override
	public int getInventoryStackLimit() {
		return maxStack;
	}

	@Override
	public boolean isUsableByPlayer(EntityPlayer player) {
		return this.world.getTileEntity(this.getPos()) == this && player.getDistanceSq(this.pos.add(0.5, 0.5, 0.5)) <= 64;
	}

	@Override
	public void openInventory(EntityPlayer player) {
	}

	@Override
	public void closeInventory(EntityPlayer player) {
	}

	@Override
	public boolean isItemValidForSlot(int index, ItemStack stack) {
		return true;
	}

	@Override
	public int getField(int id) {
		return 0;
	}

	@Override
	public void setField(int id, int value) {		
	}

	@Override
	public int getFieldCount() {
		return 0;
	}

	@Override
	public void clear() {
		for (int i = 0; i < this.getSizeInventory(); i++)
			this.setInventorySlotContents(i, ItemStack.EMPTY);
	}

	public String getCustomName() {
		return customName;
	}

	public void setCustomName(String customName) {
		this.customName = customName;
	}

	private void handleSendingEnergy() {
		if (!world.isRemote) {
			if (energy <= 0) {
				return;
			}
			for (EnumFacing dir : EnumFacing.values()) {
				BlockPos targetBlock = getPos().add(dir.getDirectionVec());

				TileEntity tile = world.getTileEntity(targetBlock);
				if (tile != null) {
					if (tile instanceof EnderStorageTE) {
						EnderStorageTE storage = (EnderStorageTE) tile;
						if (storage.energy == Long.MAX_VALUE) continue;
						else if (storage.energy + energy < storage.energy) {
							energy -= Long.MAX_VALUE-storage.energy;
							storage.energy = Long.MAX_VALUE;
						}
						else {
							storage.energy += energy;
							energy = 0;
						}
					}
					else if (Loader.isModLoaded("tesla")) {
						if (tile.hasCapability(TeslaCapabilities.CAPABILITY_CONSUMER, dir.getOpposite())) {
							ITeslaConsumer other = tile.getCapability(TeslaCapabilities.CAPABILITY_CONSUMER, dir.getOpposite());
							energy -= other.givePower(energy, false);
						}
						else if (tile.hasCapability(CapabilityEnergy.ENERGY, dir.getOpposite())) {
							IEnergyStorage other = tile.getCapability(CapabilityEnergy.ENERGY, dir.getOpposite());
							if (other.canReceive()) {
								energy -= other.receiveEnergy((int) (energy > Integer.MAX_VALUE ? Integer.MAX_VALUE : energy), false);
							}
						}
					} 
					else {
						if (tile.hasCapability(CapabilityEnergy.ENERGY, dir.getOpposite())) {
							IEnergyStorage other = tile.getCapability(CapabilityEnergy.ENERGY, dir.getOpposite());
							if (other.canReceive()) {
								energy -= other.receiveEnergy((int) (energy > Integer.MAX_VALUE ? Integer.MAX_VALUE : energy), false);
							}
						}
					}
				}
			}
		}
	}

	@Override
	public boolean isEmpty() {
		return Inventory.get(0).getCount() == 0;
	}

	private long longPow (long a, double b) {
		if (b == 0) return 1L;
		if (b == 1) return a;
		return (long)Math.pow(a, b);
	}
}
