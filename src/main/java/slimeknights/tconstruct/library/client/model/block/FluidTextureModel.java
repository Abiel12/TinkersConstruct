package slimeknights.tconstruct.library.client.model.block;

import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import lombok.AllArgsConstructor;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.ForgeModelBakery;
import net.minecraftforge.client.model.IModelConfiguration;
import net.minecraftforge.client.model.IModelLoader;
import net.minecraftforge.client.model.data.IModelData;
import net.minecraftforge.client.model.geometry.IModelGeometry;
import net.minecraftforge.fluids.FluidAttributes;
import net.minecraftforge.fluids.FluidStack;
import slimeknights.mantle.client.model.RetexturedModel;
import slimeknights.mantle.client.model.util.ColoredBlockModel;
import slimeknights.mantle.client.model.util.DynamicBakedWrapper;
import slimeknights.mantle.client.model.util.SimpleBlockModel;
import slimeknights.mantle.util.JsonHelper;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.smeltery.block.entity.tank.IDisplayFluidListener;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;

/**
 * Model that replaces fluid textures with the fluid from model data
 */
@AllArgsConstructor
public class FluidTextureModel implements IModelGeometry<FluidTextureModel> {
  public static final Loader LOADER = new Loader();

  private final SimpleBlockModel model;
  private final Set<String> fluids;

  @Override
  public Collection<Material> getTextures(IModelConfiguration owner, Function<ResourceLocation,UnbakedModel> modelGetter, Set<Pair<String,String>> missingTextureErrors) {
    return model.getTextures(owner, modelGetter, missingTextureErrors);
  }

  /** Trims the # character off the beginning of a texture name (if present) */
  private static String trimTextureName(String name) {
    if (name.charAt(0) == '#') {
      return name.substring(1);
    }
    return name;
  }

  @Override
  public BakedModel bake(IModelConfiguration owner, ModelBakery bakery, Function<Material,TextureAtlasSprite> spriteGetter, ModelState transform, ItemOverrides overrides, ResourceLocation modelLocation) {
    BakedModel baked = model.bakeModel(owner, transform, overrides, spriteGetter, modelLocation);
    Set<String> fluidTextures = RetexturedModel.getAllRetextured(owner, model, this.fluids);

    // determine which block parts are fluids
    List<BlockElement> elements = model.getElements();
    int size = elements.size();
    BitSet fluidParts = new BitSet(size);
    for (int i = 0; i < size; i++) {
      BlockElement part = elements.get(i);
      long fluidFaces = part.faces.values().stream()
                                     .filter(face -> fluidTextures.contains(trimTextureName(face.texture)))
                                     .count();
      // for simplicity, each part is either a fluid or not. If for some reason it contains both we mark it as a fluid, meaning it may get colored
      // if this is undesired, just use separate elements
      if (fluidFaces > 0) {
        if (fluidFaces < part.faces.size()) {
          TConstruct.LOG.warn("Mixed fluid and non-fluid elements in model {}, may cause unexpected results", modelLocation);
        }
        fluidParts.set(i);
      }
    }
    return new Baked(baked, elements, owner, transform, fluidTextures, fluidParts);
  }

  /** Baked wrapper class */
  private static class Baked extends DynamicBakedWrapper<BakedModel> {
    private final Map<FluidStack,BakedModel> cache = new HashMap<>();
    private final List<BlockElement> elements;
    private final IModelConfiguration owner;
    private final ModelState transform;
    private final Set<String> fluids;
    private final BitSet fluidParts;
    protected Baked(BakedModel originalModel, List<BlockElement> elements, IModelConfiguration owner, ModelState transform, Set<String> fluids, BitSet fluidParts) {
      super(originalModel);
      this.elements = elements;
      this.owner = owner;
      this.transform = transform;
      this.fluids = fluids;
      this.fluidParts = fluidParts;
    }

    /** Retextures a model for the given fluid */
    private BakedModel getRetexturedModel(FluidStack fluid) {
      // setup model baking
      Function<Material,TextureAtlasSprite> spriteGetter = ForgeModelBakery.defaultTextureGetter();
      TextureAtlasSprite particle = spriteGetter.apply(owner.resolveTexture("particle"));
      SimpleBakedModel.Builder builder = new SimpleBakedModel.Builder(owner, ItemOverrides.EMPTY).particle(particle);

      // get fluid details
      FluidAttributes attributes = fluid.getFluid().getAttributes();
      int color = attributes.getColor(fluid);
      int luminosity = attributes.getLuminosity(fluid);
      IModelConfiguration textured = new RetexturedModel.RetexturedConfiguration(this.owner, this.fluids, attributes.getStillTexture(fluid));

      // add in elements
      int size = elements.size();
      for (int i = 0; i < size; i++) {
        BlockElement element = elements.get(i);
        if (fluidParts.get(i)) {
          ColoredBlockModel.bakePart(builder, textured, element, color, luminosity, transform, spriteGetter, TankModel.BAKE_LOCATION);
        } else {
          SimpleBlockModel.bakePart(builder, owner, element, transform, spriteGetter, TankModel.BAKE_LOCATION);
        }
      }
      return builder.build();
    }

    /** Gets a retextured model for the given fluid, using the cached model if possible */
    private BakedModel getCachedModel(FluidStack fluid) {
      return this.cache.computeIfAbsent(fluid, this::getRetexturedModel);
    }

    @Nonnull
    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction direction, Random random, IModelData data) {
      FluidStack fluid = data.getData(IDisplayFluidListener.PROPERTY);
      if (fluid != null && !fluid.isEmpty()) {
        return getCachedModel(fluid).getQuads(state, direction, random, data);
      }
      return originalModel.getQuads(state, direction, random, data);
    }
  }

  /** Model loader class */
  private static class Loader implements IModelLoader<FluidTextureModel> {
    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {}

    @Override
    public FluidTextureModel read(JsonDeserializationContext context, JsonObject json) {
      SimpleBlockModel model = SimpleBlockModel.deserialize(context, json);
      Set<String> fluids = ImmutableSet.copyOf(JsonHelper.parseList(json, "fluids", GsonHelper::convertToString));
      return new FluidTextureModel(model, fluids);
    }
  }
}
