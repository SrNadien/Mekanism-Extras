package com.jerry.mekanism_extras.common.tile.transmitter;

import com.jerry.mekanism_extras.api.tier.AdvancedTier;
import com.jerry.mekanism_extras.common.content.network.transmitter.ExtraUniversalCable;
import com.jerry.mekanism_extras.common.registry.ExtraBlock;
import mekanism.api.NBTConstants;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.math.FloatingLong;
import mekanism.api.providers.IBlockProvider;
import mekanism.common.block.states.BlockStateHelper;
import mekanism.common.block.states.TransmitterType;
import mekanism.common.capabilities.energy.DynamicStrictEnergyHandler;
import mekanism.common.capabilities.resolver.manager.EnergyHandlerManager;
import mekanism.common.content.network.EnergyNetwork;
import mekanism.common.integration.computer.ComputerCapabilityHelper;
import mekanism.common.integration.computer.IComputerTile;
import mekanism.common.integration.computer.annotation.ComputerMethod;
import mekanism.common.integration.energy.EnergyCompatUtils;
import mekanism.common.lib.transmitter.ConnectionType;
import mekanism.common.util.EnumUtils;
import mekanism.common.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class ExtraTileEntityUniversalCable extends ExtraTileEntityTransmitter implements IComputerTile {
    private final EnergyHandlerManager energyHandlerManager;

    public ExtraTileEntityUniversalCable(IBlockProvider blockProvider, BlockPos pos, BlockState state) {
        super(blockProvider, pos, state);
        addCapabilityResolver(energyHandlerManager = new EnergyHandlerManager(direction -> {
            ExtraUniversalCable cable = getTransmitter();
            if (direction != null && (cable.getConnectionTypeRaw(direction) == ConnectionType.NONE) || cable.isRedstoneActivated()) {
                //If we actually have a side, and our connection type on that side is none, or we are currently activated by redstone,
                // then return that we have no containers
                return Collections.emptyList();
            }
            return cable.getEnergyContainers(direction);
        }, new DynamicStrictEnergyHandler(this::getEnergyContainers, getExtractPredicate(), getInsertPredicate(), null)));
        ComputerCapabilityHelper.addComputerCapabilities(this, this::addCapabilityResolver);
    }

    @Override
    protected ExtraUniversalCable createTransmitter(IBlockProvider blockProvider) {
        return new ExtraUniversalCable(blockProvider, this);
    }

    @Override
    public ExtraUniversalCable getTransmitter() {
        return (ExtraUniversalCable) super.getTransmitter();
    }

    @Override
    protected void onUpdateServer() {
        getTransmitter().pullFromAcceptors();
        super.onUpdateServer();
    }

    @Override
    public TransmitterType getTransmitterType() {
        return TransmitterType.UNIVERSAL_CABLE;
    }

    @NotNull
    @Override
    protected BlockState upgradeResult(@NotNull BlockState current, @NotNull AdvancedTier tier) {
        return BlockStateHelper.copyStateData(current, switch (tier) {
            case ABSOLUTE -> ExtraBlock.ABSOLUTE_UNIVERSAL_CABLE;
            case SUPREME -> ExtraBlock.SUPREME_UNIVERSAL_CABLE;
            case COSMIC -> ExtraBlock.COSMIC_UNIVERSAL_CABLE;
            case INFINITE -> ExtraBlock.INFINITE_UNIVERSAL_CABLE;
        });
    }

    @NotNull
    @Override
    public CompoundTag getUpdateTag() {
        //Note: We add the stored information to the initial update tag and not to the one we sync on side changes which uses getReducedUpdateTag
        CompoundTag updateTag = super.getUpdateTag();
        if (getTransmitter().hasTransmitterNetwork()) {
            EnergyNetwork network = getTransmitter().getTransmitterNetwork();
            updateTag.putString(NBTConstants.ENERGY_STORED, network.energyContainer.getEnergy().toString());
            updateTag.putFloat(NBTConstants.SCALE, network.currentScale);
        }
        return updateTag;
    }

    private List<IEnergyContainer> getEnergyContainers(@Nullable Direction side) {
        return energyHandlerManager.getContainers(side);
    }

    @Override
    public void sideChanged(@NotNull Direction side, @NotNull ConnectionType old, @NotNull ConnectionType type) {
        super.sideChanged(side, old, type);
        if (type == ConnectionType.NONE) {
            invalidateCapabilities(EnergyCompatUtils.getEnabledEnergyCapabilities(), side);
            //Notify the neighbor on that side our state changed and we no longer have a capability
            WorldUtils.notifyNeighborOfChange(level, side, worldPosition);
        } else if (old == ConnectionType.NONE) {
            //Notify the neighbor on that side our state changed, and we now do have a capability
            WorldUtils.notifyNeighborOfChange(level, side, worldPosition);
        }
    }

    @Override
    public void redstoneChanged(boolean powered) {
        super.redstoneChanged(powered);
        if (powered) {
            //The transmitter now is powered by redstone and previously was not
            //Note: While at first glance the below invalidation may seem over aggressive, it is not actually that aggressive as
            // if a cap has not been initialized yet on a side then invalidating it will just NO-OP
            invalidateCapabilities(EnergyCompatUtils.getEnabledEnergyCapabilities(), EnumUtils.DIRECTIONS);
        }
        //Note: We do not have to invalidate any caps if we are going from powered to unpowered as all the caps would already be "empty"
    }

    //Methods relating to IComputerTile
    @Override
    public String getComputerName() {
        return getTransmitter().getTier().getBaseTier().getLowerName() + "UniversalCable";
    }

    @ComputerMethod
    FloatingLong getBuffer() {
        return getTransmitter().getBufferWithFallback();
    }

    @ComputerMethod
    FloatingLong getCapacity() {
        ExtraUniversalCable cable = getTransmitter();
        return cable.hasTransmitterNetwork() ? cable.getTransmitterNetwork().getCapacityAsFloatingLong() : cable.getCapacityAsFloatingLong();
    }

    @ComputerMethod
    FloatingLong getNeeded() {
        return getCapacity().subtract(getBuffer());
    }

    @ComputerMethod
    double getFilledPercentage() {
        return getBuffer().divideToLevel(getCapacity());
    }
    //End methods IComputerTile
}
