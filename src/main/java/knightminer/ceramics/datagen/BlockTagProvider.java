package knightminer.ceramics.datagen;

import knightminer.ceramics.Ceramics;
import knightminer.ceramics.Registration;
import knightminer.ceramics.recipe.CeramicsTags;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.TagsProvider;
import net.minecraft.item.DyeColor;
import net.minecraft.tags.BlockTags;
import net.minecraftforge.common.data.ExistingFileHelper;

public class BlockTagProvider extends net.minecraft.data.BlockTagsProvider {
  public BlockTagProvider(DataGenerator gen, ExistingFileHelper helper) {
    super(gen, Ceramics.MOD_ID, helper);
  }

  @Override
  public String getName() {
    return "Ceramics Block Tags";
  }

  @Override
  protected void addTags() {
    // vanilla colored terracotta
    TagsProvider.Builder<Block> coloredTerracotta = this.tag(CeramicsTags.Blocks.COLORED_TERRACOTTA);
    Registration.TERRACOTTA.values().forEach(coloredTerracotta::add);

    // porcelain
    this.tag(BlockTags.ENDERMAN_HOLDABLE).add(Registration.UNFIRED_PORCELAIN_BLOCK.get());
    TagsProvider.Builder<Block> coloredPorcelain = this.tag(CeramicsTags.Blocks.COLORED_PORCELAIN);
    Registration.PORCELAIN_BLOCK.forEach((color, block) -> {
      if (color != DyeColor.WHITE) {
        coloredPorcelain.add(block);
      }
    });
    this.tag(CeramicsTags.Blocks.PORCELAIN)
        .add(Registration.PORCELAIN_BLOCK.get(DyeColor.WHITE))
        .addTag(CeramicsTags.Blocks.COLORED_PORCELAIN);

    // rainbow porcelain
    TagsProvider.Builder<Block> rainbow = this.tag(CeramicsTags.Blocks.RAINBOW_PORCELAIN);
    Registration.RAINBOW_PORCELAIN.values().forEach(rainbow::add);

    // bricks
    this.tag(CeramicsTags.Blocks.BRICKS).add(
        // clay
        Blocks.BRICKS,
        Registration.DARK_BRICKS.get(),
        Registration.DRAGON_BRICKS.get(),
        Registration.LAVA_BRICKS.get(),
        // porcelain
        Registration.PORCELAIN_BRICKS.get(),
        Registration.GOLDEN_BRICKS.get(),
        Registration.MARINE_BRICKS.get(),
        Registration.MONOCHROME_BRICKS.get(),
        Registration.RAINBOW_BRICKS.get()
    );
    this.tag(BlockTags.WALLS).add(
        // clay
        Registration.DARK_BRICKS.getWall(),
        Registration.DRAGON_BRICKS.getWall(),
        Registration.LAVA_BRICKS.getWall(),
        // porcelain
        Registration.PORCELAIN_BRICKS.getWall(),
        Registration.GOLDEN_BRICKS.getWall(),
        Registration.MARINE_BRICKS.getWall(),
        Registration.MONOCHROME_BRICKS.getWall(),
        Registration.RAINBOW_BRICKS.getWall()
    );
    // blocks that cisterns connect to
    this.tag(CeramicsTags.Blocks.CISTERN_CONNECTIONS)
        .add(Registration.TERRACOTTA_GAUGE.get(), Registration.PORCELAIN_GAUGE.get(),
             Registration.CLAY_FAUCET.get(), Registration.UNFIRED_FAUCET.get(), Registration.TERRACOTTA_FAUCET.get(), Registration.PORCELAIN_FAUCET.get(),
             Registration.CLAY_CHANNEL.get(), Registration.UNFIRED_CHANNEL.get(), Registration.TERRACOTTA_CHANNEL.get(), Registration.PORCELAIN_CHANNEL.get());
    // list of all terracotta cisterns
    TagsProvider.Builder<Block> terracottaCisterns = this.tag(CeramicsTags.Blocks.TERRACOTTA_CISTERNS)
                                                         .add(Registration.TERRACOTTA_CISTERN.get());
    //noinspection Convert2MethodRef
    Registration.COLORED_CISTERN.forEach(block -> terracottaCisterns.add(block));
    TagsProvider.Builder<Block> porcelainCisterns = this.tag(CeramicsTags.Blocks.PORCELAIN_CISTERNS);
    //noinspection Convert2MethodRef
    Registration.PORCELAIN_CISTERN.forEach(block -> porcelainCisterns.add(block));
  }
}
