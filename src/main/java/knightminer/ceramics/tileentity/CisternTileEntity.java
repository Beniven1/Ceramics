package knightminer.ceramics.tileentity;

import knightminer.ceramics.Ceramics;
import knightminer.ceramics.Registration;
import knightminer.ceramics.blocks.CisternBlock;
import knightminer.ceramics.items.BaseClayBucketItem;
import knightminer.ceramics.network.CeramicsNetwork;
import knightminer.ceramics.network.CisternUpdatePacket;
import knightminer.ceramics.tileentity.CrackableTileEntityHandler.ICrackableTileEntity;
import knightminer.ceramics.util.tank.CisternTank;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraftforge.client.model.data.IModelData;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.util.NonNullConsumer;
import net.minecraftforge.fluids.FluidAttributes;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.templates.EmptyFluidHandler;
import slimeknights.mantle.tileentity.MantleTileEntity;
import slimeknights.mantle.util.TileEntityHelper;
import slimeknights.mantle.util.WeakConsumerWrapper;

import javax.annotation.Nullable;

public class CisternTileEntity extends MantleTileEntity implements ICrackableTileEntity {
  /** Max capacity per cistern block */
  private static final String TAG_FLUID = "fluid";
  private static final String TAG_EXTENSIONS = "extensions";

  /* Base fields */
  /** Tank instance for this cistern */
  private final CisternTank tank = new CisternTank(this);
  /** Number of extensions on this tank */
  private int extensions = 0;

  /** Handler for internal use, passed between cisterns */
  @Nullable
  private LazyOptional<IFluidHandler> internalHandler;
  /** Handler exposed to Forge capabilities. Same as internal if and only if this is not an extension */
  @Nullable
  private LazyOptional<IFluidHandler> publicHandler;
  /** Index of the cistern for rendering */
  private int renderIndex = 0;

  /** Consumer for when the parent invalidates */
  private final NonNullConsumer<LazyOptional<IFluidHandler>> invalidationListener = new WeakConsumerWrapper<>(this, (te, handler) -> te.invalidateHandlers());

  /** Cracking logic */
  private final CrackableTileEntityHandler cracksHandler;

  public CisternTileEntity(boolean crackable) {
    super(Registration.CISTERN_TILE_ENTITY.get());
    cracksHandler = new CrackableTileEntityHandler(this, crackable);
  }

  public CisternTileEntity() {
    this(false);
  }

  /* Getters */

  /**
   * Gets the number of extensions on this cistern
   * @return  Number of extensions
   */
  public int getExtensions() {
    return extensions;
  }

  /**
   * Gets the index of this cistern in the structure for the sake of rendering
   * @return  Render index of cistern
   */
  public int getRenderIndex() {
    return renderIndex;
  }

  /**
   * Clears relevant fluid handlers
   */
  private void invalidateHandlers() {
    internalHandler = null;
    if (publicHandler != null) {
      publicHandler.invalidate();
      publicHandler = null;
    }
  }

  /**
   * Gets the capacity per layer of the tile entity
   * @return  Capacity per layer
   */
  public int capacityPerLayer() {
    Block block = getBlockState().getBlock();
    if (Registration.PORCELAIN_CISTERN.contains(block)) {
      return 6 * FluidAttributes.BUCKET_VOLUME;
    }
    return 4 * FluidAttributes.BUCKET_VOLUME;
  }

  /**
   * Gets the capacity for the given height of the tile entity
   * @param height  Cistern height
   * @return  Capacity
   */
  public final int capacityFor(int height) {
    return height * capacityPerLayer();
  }

  @Override
  public CrackableTileEntityHandler getCracksHandler() {
    return cracksHandler;
  }

  @Override
  public IModelData getModelData() {
    return cracksHandler.getModelData();
  }


  /* Parent behavior */

  /**
   * Transfers the relevant amount of fluid into a new base
   * @param index  Number of blocks the new base is above this block
   */
  private void transferBaseTo(Block block, int index) {
    assert level != null;
    // ensure the block above is the right type and is an extension
    BlockPos newBase = worldPosition.above(index);
    BlockState newState = level.getBlockState(newBase);
    if (newState.is(block) && newState.getValue(CisternBlock.EXTENSION)) {
      BlockEntity te = level.getBlockEntity(newBase);
      if (te instanceof CisternTileEntity) {
        // if we have fluid in the new base, extract that into that base
        FluidStack fluid = FluidStack.EMPTY;
        int amountAboveRemoved = tank.getFluidAmount() - capacityFor(index);
        if (amountAboveRemoved > 0) {
          fluid = new FluidStack(tank.getFluid(), amountAboveRemoved);
        }
        // subtract index, that is extensions below the index
        ((CisternTileEntity)te).makeBase(fluid, extensions - index);
      }
    }
  }

  /**
   * Called when a base block is broken to transfer relevant fluid to extensions
   * @param block  Block type, used since the block at the position may be air at the time of calling
   */
  public void onBroken(Block block) {
    if (extensions > 0) {
      transferBaseTo(block, 1);
    }
  }

  /**
   * Attempts to merge a cistern at the given position with this one
   * @param checkPos  Position to check
   */
  public void tryMerge(BlockPos checkPos) {
    assert level != null;
    // check if there is another cistern above the new one that should also be connected
    if (level.getBlockState(checkPos).is(getBlockState().getBlock())) {
      TileEntityHelper.getTile(CisternTileEntity.class, level, checkPos).ifPresent(te -> te.makeExtension(this));
    }
  }

  /**
   * Adds a new cistern as an extension to this one. Called by the new cistern when its placed in the world.
   * @param extPos  Position of extension
   */
  public void addExtension(BlockPos extPos) {
    assert level != null;

    // update extensions if the new position is outside the cistern (mostly safety)
    int newExtensions = extPos.getY() - worldPosition.getY();
    if (newExtensions > extensions) {
      extensions = newExtensions;

      // check if there is another cistern above the new one that should also be connected
      tryMerge(extPos.above());
    }
  }

  /**
   * Removes the extension at the given location. Called on the parent when a cistern extension above is removed
   * @param extPos  Extension to remove
   */
  public void removeExtension(BlockPos extPos) {
    assert extPos.getY() > worldPosition.getY();
    assert level != null;

    // index of the extension that was removed
    int removed = extPos.getY() - worldPosition.getY();
    // check if the removed position was within our current cistern
    if (removed <= extensions) {
      // if not the top, make a new base
      if (removed < extensions) {
        transferBaseTo(getBlockState().getBlock(), removed + 1);
      }

      // our new extensions simply one less than the removed index
      extensions = removed - 1;
      // shrink the fluid if needed
      tank.validateAmount();
    }
  }

  /** Called by the tank when fluid is added */
  public void onFluidAdded(int amountAdded) {
    onTankChanged(false);

    // if cracking is active and the fluid cracks the block, crack all blocks impacted by the fluid
    FluidStack fluid = tank.getFluid();
    if (cracksHandler.isActive() && level != null && !level.isClientSide() && BaseClayBucketItem.doesCrack(fluid.getFluid())) {
      int capacityPerLayer = capacityPerLayer();
      // first index needing an update
      int startIndex = (fluid.getAmount() - amountAdded + capacityPerLayer - 1) / capacityPerLayer;
      // last index needing an update
      int endIndex = (fluid.getAmount() - 1) / capacityPerLayer;
      // loop through indexes, updating them
      for (int i = startIndex; i <= endIndex; i++) {
        TileEntityHelper.getTile(ICrackableTileEntity.class, level, worldPosition.above(i)).ifPresent(te -> te.getCracksHandler().fluidAdded(fluid));
      }
    }
  }


  /* Extension behavior */

  /**
   * Gets the internal fluid handler for this cistern. This handler should only be used by other cisterns, prevents a long recursive chain every fluid interaction
   * @return  Fluid handler optional
   */
  protected LazyOptional<IFluidHandler> getInternalHandler() {
    // if we have a cached handler, return it
    if (internalHandler != null && publicHandler != null) {
      return internalHandler;
    }

    // if this is an extension, we want to find a parent handler
    if (getBlockState().getValue(CisternBlock.EXTENSION)) {
      if (level != null) {
        BlockEntity te = level.getBlockEntity(worldPosition.below());
        if (te instanceof CisternTileEntity) {
          CisternTileEntity parent = (CisternTileEntity) te;

          // invalidate existing handlers
          invalidateHandlers();
          internalHandler = parent.getInternalHandler();
          // add the invalidation listener to the parent's public handler, means it will properly chain both if the parent is broken and if anything along the chain is broken
          parent.getPublicHandler().addListener(invalidationListener);

          // create a public handler to proxy to the parent, so we can invalidate separately
          publicHandler = LazyOptional.of(() -> internalHandler.orElse(EmptyFluidHandler.INSTANCE));
          // render index is the render index of the tank below plus 1, nifty trick
          renderIndex = parent.getRenderIndex() + 1;

          // return the internal handler
          return internalHandler;
        } else if (!level.isClientSide()) {
          // render thread often has a few ticks with no cistern for an extension the base is broken
          Ceramics.LOG.error("Missing cistern tile entity below cistern extension");
        }
      } else {
        Ceramics.LOG.error("No world when trying to get cistern's fluid handler");
      }
    }

    // return our internal tank as a fluid handler
    internalHandler = publicHandler = LazyOptional.of(() -> tank);
    renderIndex = 0;
    return internalHandler;
  }

  /**
   * Gets the public fluid handler associated with this cistern
   * @return  Fluid handler optional
   */
  public LazyOptional<IFluidHandler> getPublicHandler() {
    getInternalHandler();
    assert publicHandler != null;
    return publicHandler;
  }

  /** Called on random block tick to update the heat */
  public void randomTick() {
    if (cracksHandler.isActive()) {
      if (getBlockState().getValue(CisternBlock.EXTENSION)) {
        // extensions: if fluid does not reach here, treat as empty
        FluidStack fluid = getInternalHandler().orElse(EmptyFluidHandler.INSTANCE).getFluidInTank(0);
        int localAmount = fluid.getAmount() - capacityFor(renderIndex);
        if (localAmount > 0) {
          cracksHandler.updateCracks(fluid.getFluid(), localAmount);
        } else {
          cracksHandler.updateCracks(Fluids.EMPTY, 0);
        }
      } else {
        FluidStack fluid = tank.getFluid();
        cracksHandler.updateCracks(fluid.getFluid(), Math.min(fluid.getAmount(), capacityPerLayer()));
      }
    }
  }


  /* State updating */

  /**
   * Makes the barrel a base
   * @param fluid       Fluid to contain
   * @param extensions  Number of extensions
   */
  protected void makeBase(FluidStack fluid, int extensions) {
    assert level != null;
    // extra extra data from parent
    this.extensions = extensions;
    tank.setFluid(fluid);
    // notify client and any listening blocks of changes
    onTankChanged(true);
    invalidateHandlers();
    renderIndex = 0;
    // update block state in world
    level.setBlockAndUpdate(worldPosition, getBlockState().setValue(CisternBlock.EXTENSION, false));
  }

  /**
   * Checks if the tank contains the given fluid
   * @param fluid  Fluid to check
   * @return  True if the fluid is contained, false otherwise
   */
  public boolean containsFluid(FluidStack fluid) {
    return tank.getFluidAmount() == 0 || tank.getFluid().isFluidEqual(fluid);
  }

  /**
   * Merges the contents of another cistern into this
   * @param fluid          Fluid contents to merge in
   * @param newExtensions  New extensions to add
   */
  protected void mergeCisterns(FluidStack fluid, int newExtensions) {
    extensions += newExtensions;
    if (!fluid.isEmpty()) {
      tank.fill(fluid, FluidAction.EXECUTE);
    }
  }

  /**
   * Converts a given base cistern into an extension. Called by the base when a new cistern is added below an existing one
   * @param parent  New parent to the extension
   */
  protected void makeExtension(CisternTileEntity parent) {
    assert level != null;

    // ensure the fluid is valid for the parent
    FluidStack fluid = tank.getFluid();
    if (fluid.isEmpty() || parent.containsFluid(fluid)) {
      // update block to extension block
      level.setBlockAndUpdate(worldPosition, getBlockState().setValue(CisternBlock.EXTENSION, true));
      // merge contents into the parent, add an extra 1 for ourself
      parent.mergeCisterns(fluid, extensions + 1);

      // clear our data
      invalidateHandlers();
      extensions = 0;
      tank.setFluid(FluidStack.EMPTY);
    }
  }


  /* Capabilities */

  @Override
  public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
    if (cap == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
      return getPublicHandler().cast();
    }
    return super.getCapability(cap, side);
  }

  @Override
  protected void invalidateCaps() {
    super.invalidateCaps();
    invalidateHandlers();
  }


  /* Networking */

  /**
   * Called when the tank contents change
   */
  public void onTankChanged(boolean shouldRefreshCapabilities) {
    if(level != null && !level.isClientSide) {
      CeramicsNetwork.getInstance().sendToClientsAround(new CisternUpdatePacket(worldPosition, tank.getFluid(), shouldRefreshCapabilities), level, worldPosition);
    }
  }

  /**
   * Called client side to update the tank fluid
   * @param fluid                      New fluid
   * @param shouldRefreshCapabilities  If true, capability handlers should be invalidated. Typically means the block was changed from extension to base
   */
  public void updateFluidTo(FluidStack fluid, boolean shouldRefreshCapabilities) {
    tank.setFluid(fluid);
    if (shouldRefreshCapabilities) {
      invalidateHandlers();
    }
  }

  @Override
  public void handleUpdateTag(BlockState state, CompoundTag tag) {
    load(state, tag);
  }

  @Override
  public CompoundTag getUpdateTag() {
    // new tag instead of super since default implementation calls the super of writeToNBT
    return this.save(new CompoundTag());
  }


  /* Serialization */

  @Override
  public void load(BlockState state, CompoundTag tags) {
    super.load(state, tags);
    extensions = tags.getInt(TAG_EXTENSIONS);
    if (tags.contains(TAG_FLUID, NBT.TAG_COMPOUND)) {
      tank.readFromNBT(tags.getCompound(TAG_FLUID));
    }
    cracksHandler.readNBT(state, tags);
  }

  @Override
  public CompoundTag save(CompoundTag compound) {
    compound = super.save(compound);
    if (tank.getFluidAmount() > 0) {
      compound.put(TAG_FLUID, tank.writeToNBT());
    }
    compound.putInt(TAG_EXTENSIONS, extensions);
    cracksHandler.writeNBT(compound);
    return compound;
  }
}
