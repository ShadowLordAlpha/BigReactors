package erogenousbeef.bigreactors.common.multiblock.tileentity;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.IFluidBlock;
import erogenousbeef.bigreactors.api.IHeatEntity;
import erogenousbeef.bigreactors.api.IRadiationModerator;
import erogenousbeef.bigreactors.api.RadiationData;
import erogenousbeef.bigreactors.api.RadiationPacket;
import erogenousbeef.bigreactors.common.multiblock.MultiblockReactor;
import erogenousbeef.bigreactors.common.multiblock.helpers.RadiationHelper;
import erogenousbeef.bigreactors.utils.StaticUtils;
import erogenousbeef.core.multiblock.MultiblockValidationException;
import erogenousbeef.core.multiblock.rectangular.IMultiblockPartRectangular;

public class TileEntityReactorFuelRod extends TileEntityReactorPartBase implements IRadiationModerator, IHeatEntity {

	public TileEntityReactorFuelRod() {
		super();
	}
	
	// IRadiationModerator
	@Override
	public void moderateRadiation(RadiationData data, RadiationPacket radiation) {
		if(!isConnected()) { return; }

		// Grab control rod insertion and reactor heat
		MultiblockReactor reactor = getReactorController();
		float heat = reactor.getFuelHeat();
		
		int maxY = reactor.getMaximumCoord().y;
		TileEntity te = worldObj.getBlockTileEntity(xCoord, maxY, zCoord);
		if(!(te instanceof TileEntityReactorControlRod)) {
			return;
		}

		// Scale control rod insertion 0..1
		float controlRodInsertion = Math.min(1f, Math.max(0f, ((float)((TileEntityReactorControlRod)te).getControlRodInsertion())/100f));
		
		// Fuel absorptiveness is determined by control rod + a heat modifier.
		// Starts at 1 and decays towards 0.05, reaching 0.6 at 1000 and just under 0.2 at 2000. Inflection point at about 500-600.
		// Harder radiation makes absorption more difficult.
		float baseAbsorption = (float)(1.0 - (0.95 * Math.exp(-10 * Math.exp(-0.0022 * heat)))) * (1f - (radiation.hardness / getFuelHardnessDivisor()));

		// Some fuels are better at absorbing radiation than others
		float scaledAbsorption = Math.min(1f, baseAbsorption * getFuelAbsorptionCoefficient());
		scaledAbsorption += (1f - scaledAbsorption) * controlRodInsertion * 0.5f; // Absorb up to 50% better with control rods inserted.
		
		float radiationAbsorbed = scaledAbsorption * radiation.intensity;
		
		float fuelModerationFactor = getFuelModerationFactor();
		fuelModerationFactor += fuelModerationFactor * controlRodInsertion + controlRodInsertion; // Full insertion doubles the moderation factor of the fuel as well as adding its own level
		
		radiation.intensity = Math.max(0f, radiation.intensity - radiationAbsorbed);
		radiation.hardness /= fuelModerationFactor;
		
		// Being irradiated both heats up the fuel and also enhances its fertility
		data.fuelHeatChange += scaledAbsorption * RadiationHelper.heatPerRadiationUnit;
		data.fuelAbsorbedRadiation += scaledAbsorption;
	}

	// 1, upwards. How well does this fuel moderate, but not stop, radiation? Anything under 1.5 is "poor", 2-2.5 is "good", above 4 is "excellent".
	private float getFuelModerationFactor() {
		return 1.5f;
	}

	// 0..1. How well does this fuel absorb radiation?
	private float getFuelAbsorptionCoefficient() {
		// TODO: Lookup type of fuel and get data from there
		return 0.5f;
	}
	
	// Goes up from 1. How tolerant is this fuel of hard radiation?
	private float getFuelHardnessDivisor() {
		return 1.0f;
	}
	
	// IHeatEntity
	@Override
	public float getThermalConductivity() {
		return IHeatEntity.conductivityCopper;
	}

	// RectangularMultiblockTileEntityBase
	@Override
	public void isGoodForFrame() throws MultiblockValidationException {
		throw new MultiblockValidationException(String.format("%d, %d, %d - fuel rods may only be placed in the reactor interior", xCoord, yCoord, zCoord));
	}

	@Override
	public void isGoodForSides() throws MultiblockValidationException {
		throw new MultiblockValidationException(String.format("%d, %d, %d - fuel rods may only be placed in the reactor interior", xCoord, yCoord, zCoord));
	}

	@Override
	public void isGoodForTop() throws MultiblockValidationException {
		throw new MultiblockValidationException(String.format("%d, %d, %d - fuel rods may only be placed in the reactor interior", xCoord, yCoord, zCoord));
	}

	@Override
	public void isGoodForBottom() throws MultiblockValidationException {
		throw new MultiblockValidationException(String.format("%d, %d, %d - fuel rods may only be placed in the reactor interior", xCoord, yCoord, zCoord));
	}

	@Override
	public void isGoodForInterior() throws MultiblockValidationException {
		// Check above and below. Above must be fuel rod or control rod.
		TileEntity entityAbove = this.worldObj.getBlockTileEntity(xCoord, yCoord+1, zCoord);
		if(!(entityAbove instanceof TileEntityReactorFuelRod || entityAbove instanceof TileEntityReactorControlRod)) {
			throw new MultiblockValidationException(String.format("Fuel rod at %d, %d, %d must be part of a vertical column that reaches the entire height of the reactor, with a control rod on top.", xCoord, yCoord, zCoord));
		}

		// Below must be fuel rod or the base of the reactor.
		TileEntity entityBelow = this.worldObj.getBlockTileEntity(xCoord, yCoord-1, zCoord);
		if(entityBelow instanceof TileEntityReactorFuelRod) {
			return;
		}
		else if(entityBelow instanceof IMultiblockPartRectangular) {
			((IMultiblockPartRectangular)entityBelow).isGoodForBottom();
			return;
		}
		
		throw new MultiblockValidationException(String.format("Fuel rod at %d, %d, %d must be part of a vertical column that reaches the entire height of the reactor, with a control rod on top.", xCoord, yCoord, zCoord));
	}

	@Override
	public void onMachineActivated() {
	}

	@Override
	public void onMachineDeactivated() {
	}

	// Reactor information retrieval methods
	
	/**
	 * Returns the rate of heat transfer from this block to the reactor environment, based on this block's surrounding blocks.
	 * Note that this method queries the world, so use it sparingly.
	 * 
	 * @return Heat transfer rate from fuel rod to reactor environment, in Centigrade per tick.
	 */
	public float getHeatTransferRate() {
		float heatTransferRate = 0f;

		TileEntity te;
		for(ForgeDirection dir: StaticUtils.CardinalDirections) {
			te = worldObj.getBlockTileEntity(xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ);
			if(te instanceof TileEntityReactorFuelRod) {
				// We don't transfer to other fuel rods, due to heat pooling.
				continue;
			}
			else if(te instanceof IHeatEntity) {
				heatTransferRate += ((IHeatEntity)te).getThermalConductivity();
			}
			else if(worldObj.isAirBlock(xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ)) {
				heatTransferRate += IHeatEntity.conductivityAir;
			}
			else {
				
				int blockID;
				blockID = worldObj.getBlockId(xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ);
				heatTransferRate += getConductivityFromBlockID(blockID);
			}
		}

		return heatTransferRate;
	}
	
	private float getConductivityFromBlockID(int blockID) {
		if(blockID == Block.blockIron.blockID) {
			return IHeatEntity.conductivityIron;
		}
		else if(blockID == Block.blockGold.blockID) {
			return IHeatEntity.conductivityGold;
		}
		else if(blockID == Block.blockDiamond.blockID) {
			return IHeatEntity.conductivityDiamond;
		}
		else if(blockID == Block.blockEmerald.blockID) {
			return IHeatEntity.conductivityEmerald;
		}
		else {
			Block b = Block.blocksList[blockID];
			if(b instanceof IFluidBlock) {
				Fluid fluid = ((IFluidBlock)b).getFluid();
				if(fluid != null) {
					return getConductivityForFluid(fluid.getName());
				}
				else {
					return IHeatEntity.conductivityWater;
				}
			}
			else {
				// Screw it, just assume it's air.
				return IHeatEntity.conductivityAir;
			}
		}
	}
	
	private float getConductivityForFluid(String fluidName) {
		if(fluidName.equals("water")) {
			return IHeatEntity.conductivityWater;
		}
		else if(fluidName.equals("ender")) {
			return IHeatEntity.conductivityGold;
		}
		else if(fluidName.equals("redstone")) {
			return IHeatEntity.conductivityEmerald;
		}
		else if(fluidName.equals("cryotheum")) {
			return IHeatEntity.conductivityGold;
		}
		else if(fluidName.equals("pyrotheum")) {
			return IHeatEntity.conductivityGlass;
		}
		else if(fluidName.equals("glowstone")) {
			return IHeatEntity.conductivityStone;
		}
		
		return IHeatEntity.conductivityWater;
	}
}