package me.shedaniel.betterloadingscreen.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import me.shedaniel.betterloadingscreen.BetterLoadingScreen;
import me.shedaniel.betterloadingscreen.BetterLoadingScreenClient;
import me.shedaniel.betterloadingscreen.BetterLoadingScreenConfig;
import me.shedaniel.betterloadingscreen.MinecraftGraphics;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

@Mixin(LoadingOverlay.class)
public abstract class MixinLoadingOverlay {
    @Shadow private long fadeOutStart;
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "render", at = @At("RETURN"))
    private void bls$renderOverlay(GuiGraphics guiGraphics, int i, int j, float f, CallbackInfo ci) {
        float g = this.fadeOutStart > -1L ? (float) (Util.getMillis() - this.fadeOutStart) / 1000.0F : -1.0F;
        if (g < 1.0F) {
            BetterLoadingScreenClient.renderOverlay(MinecraftGraphics.INSTANCE, i, j, f, 1.0F - Mth.clamp(g, 0.0F, 1.0F));
        }
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/LoadingOverlay;drawProgressBar(Lnet/minecraft/client/gui/GuiGraphics;IIIIF)V"))
    private void bls$hideVanillaProgress(LoadingOverlay instance, GuiGraphics guiGraphics, int i, int j, int k, int l, float f) {
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Ljava/util/function/IntSupplier;getAsInt()I"))
    private int bls$overrideBrandColor(java.util.function.IntSupplier supplier) {
        return BetterLoadingScreenClient.renderer.getBackgroundColor() | 0xFF000000;
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/ResourceLocation;IIFFIIIIIII)V"))
    private void bls$blitLogo(GuiGraphics guiGraphics, com.mojang.blaze3d.pipeline.RenderPipeline renderPipeline, ResourceLocation resourceLocation, int x, int y, float u, float v, int width, int height, int uWidth, int vHeight, int texWidth, int texHeight, int color) {
        boolean isMojangLogo = LoadingOverlay.MOJANG_STUDIOS_LOGO_LOCATION.equals(resourceLocation);
        if (isMojangLogo && BetterLoadingScreen.CONFIG.rendersLogo) {
            int logoColor = (BetterLoadingScreenConfig.getColor(BetterLoadingScreen.CONFIG.logoColor, 0xFFFFFF) & 0x00FFFFFF) | (color & 0xFF000000);
            guiGraphics.blit(renderPipeline, resourceLocation, x, y - 20, u, v, width, height, uWidth, vHeight, texWidth, texHeight, logoColor);
            return;
        }
        guiGraphics.blit(renderPipeline, resourceLocation, x, y, u, v, width, height, uWidth, vHeight, texWidth, texHeight, color);
    }

    @Unique private static final ResourceLocation BACKGROUND_PATH = ResourceLocation.fromNamespaceAndPath(BetterLoadingScreen.MOD_ID, "background.png");
    @Unique private static Boolean hasCustomBackground;

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;guiWidth()I",
                    ordinal = 1
            ),
            require = 0
    )
    private void bls$renderBackground(GuiGraphics guiGraphics, int i, int j, float f, CallbackInfo ci) {
        if (hasCustomBackground == null) {
            hasCustomBackground = false;
            if (Files.exists(BetterLoadingScreen.BACKGROUND_PATH)) {
                TextureManager manager = Minecraft.getInstance().getTextureManager();
                AbstractTexture texture = manager.getTexture(BACKGROUND_PATH);
                if (texture == null) {
                    try (InputStream inputStream = Files.newInputStream(BetterLoadingScreen.BACKGROUND_PATH)) {
                        byte[] bytes = inputStream.readAllBytes();
                        texture = new DynamicTexture(() -> BACKGROUND_PATH.toString(), NativeImage.read(new ByteArrayInputStream(bytes)));
                        manager.register(BACKGROUND_PATH, texture);
                        hasCustomBackground = true;
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    hasCustomBackground = true;
                }
            }
        }
        if (Boolean.TRUE.equals(hasCustomBackground)) {
            TextureManager manager = Minecraft.getInstance().getTextureManager();
            RenderSystem.setShaderTexture(0, manager.getTexture(BACKGROUND_PATH).getTextureView());
            MinecraftGraphics.INSTANCE.innerBlit(0, minecraft.getWindow().getGuiScaledWidth(), 0, minecraft.getWindow().getGuiScaledHeight(),
                    0, 0, 1, 0, 1, 0xFFFFFFFF);
        }
    }
}
