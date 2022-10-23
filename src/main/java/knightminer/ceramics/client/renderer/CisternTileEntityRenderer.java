package knightminer.ceramics.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Vector3f;
import knightminer.ceramics.blocks.CisternBlock;
import knightminer.ceramics.client.model.CisternModel;
import knightminer.ceramics.tileentity.CisternTileEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidAttributes;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.EmptyFluidHandler;
import slimeknights.mantle.client.model.fluid.FluidCuboid;
import slimeknights.mantle.client.model.util.ModelHelper;
import slimeknights.mantle.client.render.FluidRenderer;
import slimeknights.mantle.client.render.MantleRenderTypes;

/**
 * Renderer for cistern blocks
 */
public class CisternTileEntityRenderer implements BlockEntityRenderer<CisternTileEntity> {
  public CisternTileEntityRenderer(BlockEntityRendererProvider.Context context) {}

  @Override
  public void render(CisternTileEntity tileEntity, float partialTicks, PoseStack matrices, MultiBufferSource buffer, int light, int combinedOverlay) {
    FluidStack fluid = tileEntity.getPublicHandler().orElse(EmptyFluidHandler.INSTANCE).getFluidInTank(0);
    if (!fluid.isEmpty()) {
      int renderIndex = tileEntity.getRenderIndex();
      // capacity for gives us the minimum amount to start rendering in this segement
      // render nothing beyond the base capacity
      int amount = fluid.getAmount() - tileEntity.capacityFor(renderIndex);
      if (amount > 0) {
        // get the model pair, if the capacity is above the capacity per cistern, use the overfull model (no top face)
        BlockState state = tileEntity.getBlockState();
        CisternModel.Baked model = ModelHelper.getBakedModel(state, CisternModel.Baked.class);
        if (model != null) {
          // fetch textures and attributes
          FluidAttributes attributes = fluid.getFluid().getAttributes();
          TextureAtlasSprite still = FluidRenderer.getBlockSprite(attributes.getStillTexture(fluid));
          TextureAtlasSprite flowing = FluidRenderer.getBlockSprite(attributes.getFlowingTexture(fluid));
          VertexConsumer builder = buffer.getBuffer(MantleRenderTypes.FLUID);
          int color = attributes.getColor(fluid);
          light = FluidRenderer.withBlockLight(light, attributes.getLuminosity(fluid));

          // if full, just render all full sides
          int capacityPerLayer = tileEntity.capacityPerLayer();
          if (amount > capacityPerLayer) {
            for (Direction direction : Plane.HORIZONTAL) {
              // state and model must contain that direction
              FluidCuboid cuboid = model.getFluid(direction);
              if (cuboid != null && state.getValue(CisternBlock.CONNECTIONS.get(direction))) {
                FluidRenderer.renderCuboid(matrices, builder, cuboid, still, flowing, cuboid.getFromScaled(), cuboid.getToScaled(), color, light, false);
              }
            }
          } else {
            // determine the relevant height of the center
            FluidCuboid center = model.getCenterFluid(state.getValue(CisternBlock.EXTENSION));
            Vector3f from = center.getFromScaled();
            Vector3f to = center.getToScaled().copy();
            float minY = from.y();
            to.setY(minY + amount * (to.y() - minY) / (float)capacityPerLayer);
            // render the center using Mantle's logic
            FluidRenderer.renderCuboid(matrices, builder, center, still, still, from, to, color, light, false);

            // scale the sides based on the center
            for (Direction direction : Plane.HORIZONTAL) {
              // state and model must contain that direction
              FluidCuboid cuboid = model.getFluid(direction);
              if (cuboid != null && state.getValue(CisternBlock.CONNECTIONS.get(direction))) {
                // bottom of the side must be smaller than the height to consider
                Vector3f sFrom = cuboid.getFromScaled();
                if (sFrom.y() < to.y()) {
                  // if the side end is larger than the center, clamp it down
                  Vector3f sTo = cuboid.getToScaled();
                  if (sTo.y() > to.y()) {
                    sTo = sTo.copy();
                    sTo.setY(to.y());
                  }
                  FluidRenderer.renderCuboid(matrices, builder, cuboid, still, still, sFrom, sTo, color, light, false);
                }
              }
            }
          }
        }
      }
    }
  }
}
