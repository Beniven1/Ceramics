package knightminer.ceramics.client.renderer;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import knightminer.ceramics.blocks.ChannelBlock;
import knightminer.ceramics.blocks.ChannelBlock.ChannelConnection;
import knightminer.ceramics.client.model.ChannelModel;
import knightminer.ceramics.tileentity.ChannelTileEntity;
import net.minecraft.block.BlockState;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.util.Direction;
import net.minecraft.util.Direction.Plane;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidAttributes;
import net.minecraftforge.fluids.FluidStack;
import slimeknights.mantle.client.model.FaucetFluidLoader;
import slimeknights.mantle.client.model.fluid.FluidCuboid;
import slimeknights.mantle.client.model.util.ModelHelper;
import slimeknights.mantle.client.render.FluidRenderer;
import slimeknights.mantle.client.render.RenderingHelper;

public class ChannelTileEntityRenderer extends TileEntityRenderer<ChannelTileEntity> {
	public ChannelTileEntityRenderer(TileEntityRendererDispatcher rendererDispatcherIn) {
		super(rendererDispatcherIn);
	}

	@Override
	public void render(ChannelTileEntity te, float partialTicks, MatrixStack matrices, IRenderTypeBuffer buffer, int light, int combinedOverlayIn)  {
		FluidStack fluid = te.getFluid();
		if (fluid.isEmpty()) {
			return;
		}

		// fetch model properties
		World world = te.getLevel();
		if (world == null) {
			return;
		}
		BlockPos pos = te.getBlockPos();
		BlockState state = te.getBlockState();
		ChannelModel.BakedModel model = ModelHelper.getBakedModel(state, ChannelModel.BakedModel.class);
		if (model == null) {
			return;
		}

		// fluid attributes
		FluidAttributes attributes = fluid.getFluid().getAttributes();
		TextureAtlasSprite still = FluidRenderer.getBlockSprite(attributes.getStillTexture(fluid));
		TextureAtlasSprite flowing = FluidRenderer.getBlockSprite(attributes.getFlowingTexture(fluid));
		IVertexBuilder builder = buffer.getBuffer(FluidRenderer.RENDER_TYPE);
		int color = attributes.getColor(fluid);
		light = FluidRenderer.withBlockLight(light, attributes.getLuminosity(fluid));

		// render sides first, while doing so we will determine center "flow"
		FluidCuboid cube;
		boolean isRotated;
		Direction centerFlow = Direction.UP;
		for (Direction direction : Plane.HORIZONTAL) {
			// check if we have that side on the block
			ChannelConnection connection = state.getValue(ChannelBlock.DIRECTION_MAP.get(direction));
			if (connection.canFlow()) {
				// apply rotation for the side
				isRotated = RenderingHelper.applyRotation(matrices, direction);
				// get the relevant fluid model, render it
				if (te.isFlowing(direction)) {
					cube = model.getSideFlow(connection == ChannelConnection.OUT);

					// add to center direction
					if (connection == ChannelConnection.OUT) {
						// if unset (up), use this direction
						if (centerFlow == Direction.UP) {
							centerFlow = direction;
							// if set and it disagrees, set the fail state (down)
						} else if (centerFlow != direction) {
							centerFlow = Direction.DOWN;
						}
					}
					// render the extra edge against other blocks
					if (!world.getBlockState(pos.relative(direction)).is(state.getBlock())) {
						FluidRenderer.renderCuboid(matrices, builder, model.getSideEdge(), 0, still, flowing, color, light, false);
					}
				} else {
					cube = model.getSideStill();
				}
				FluidRenderer.renderCuboid(matrices, builder, cube, 0, still, flowing, color, light, false);
				// undo rotation
				if (isRotated) {
					matrices.popPose();
				}
			}
		}

		// render center
		isRotated = false;
		if (centerFlow.getAxis().isVertical()) {
			cube = model.getCenterFluid(false);
		} else {
			cube = model.getCenterFluid(true);
			isRotated = RenderingHelper.applyRotation(matrices, centerFlow);
		}
		// render the cube and pop back
		FluidRenderer.renderCuboid(matrices, builder, cube, 0, still, flowing, color, light, false);
		if (isRotated) {
			matrices.popPose();
		}

		// render flow downwards
		if (state.getValue(ChannelBlock.DOWN) && te.isFlowing(Direction.DOWN)) {
			cube = model.getDownFluid();
			FluidRenderer.renderCuboid(matrices, builder, cube, 0, still, flowing, color, light, false);

			// render into the block(s) below
			FaucetFluidLoader.renderFaucetFluids(world, pos, Direction.DOWN, matrices, builder, still, flowing, color, light);
		}
	}
}