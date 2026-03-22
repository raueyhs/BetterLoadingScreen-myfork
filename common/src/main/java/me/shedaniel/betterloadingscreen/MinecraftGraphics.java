package me.shedaniel.betterloadingscreen;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import me.shedaniel.betterloadingscreen.api.render.AbstractGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.function.Supplier;

public enum MinecraftGraphics implements AbstractGraphics {
    INSTANCE;

    public static final Logger LOGGER = LogManager.getLogger(MinecraftGraphics.class);
    private static final PoseStack stack = new PoseStack();
    public static Font font;
    private static volatile Method fontDrawMethod;
    private static volatile Method fontDrawShadowMethod;

    public static Font getFont() {
        Font cached = font;
        if (cached != null) {
            return cached;
        }

        font = Minecraft.getInstance().font;
        return font;
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int color) {
        if (x1 > x2) {
            int tmp = x1;
            x1 = x2;
            x2 = tmp;
        }

        if (y1 > y2) {
            int tmp = y1;
            y1 = y2;
            y2 = tmp;
        }

        float a = (color >> 24 & 255) / 255.0F;
        float r = (color >> 16 & 255) / 255.0F;
        float g = (color >> 8 & 255) / 255.0F;
        float b = (color & 255) / 255.0F;

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(r, g, b, a);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x2, y1);
        GL11.glVertex2f(x2, y2);
        GL11.glVertex2f(x1, y2);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
    }

    @Override
    public void bindTexture(String textureId) {
        ResourceLocation id = ResourceLocation.parse(textureId);
        RenderSystem.setShaderTexture(0, Minecraft.getInstance().getTextureManager().getTexture(id).getTextureView());
    }

    @Override
    public boolean bindTextureCustomStream(String textureId, Supplier<InputStream> supplier) {
        try {
            TextureManager manager = Minecraft.getInstance().getTextureManager();
            ResourceLocation location = ResourceLocation.parse(textureId);
            AbstractTexture existing = manager.getTexture(location);
            if (existing == null) {
                NativeImage image = NativeImage.read(supplier.get());
                DynamicTexture texture = new DynamicTexture(() -> textureId, image);
                manager.register(location, texture);
            }
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to load texture {}", textureId, e);
            return false;
        }
    }

    @Override
    public void drawString(String string, int x, int y, int color) {
        invokeFont(string, x, y, color, false);
    }

    @Override
    public void drawStringWithShadow(String string, int x, int y, int color) {
        invokeFont(string, x, y, color, true);
    }

    @Override
    public int width(String string) {
        return getFont().width(string);
    }

    @Override
    public int getScaledWidth() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    @Override
    public int getScaledHeight() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }

    private static Method findFontMethod(Font font, String methodName) {
        for (Method method : font.getClass().getMethods()) {
            if (!method.getName().equals(methodName)) continue;
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 5 && params[0].getName().equals(PoseStack.class.getName())) {
                return method;
            }
        }
        return null;
    }

    private void invokeFont(String string, int x, int y, int color, boolean shadow) {
        Font f = getFont();
        try {
            Method method = shadow
                    ? (fontDrawShadowMethod == null ? (fontDrawShadowMethod = findFontMethod(f, "drawShadow")) : fontDrawShadowMethod)
                    : (fontDrawMethod == null ? (fontDrawMethod = findFontMethod(f, "draw")) : fontDrawMethod);
            if (method == null) return;

            Class<?>[] params = method.getParameterTypes();
            Object xArg = params[2] == float.class ? (float) x : x;
            Object yArg = params[3] == float.class ? (float) y : y;
            Object colorArg = params[4] == float.class ? (float) color : color;

            method.invoke(f, stack, string, xArg, yArg, colorArg);
        } catch (Throwable t) {
            LOGGER.debug("Failed to draw font string '{}'(shadow={})", string, shadow, t);
        }
    }

    @Override
    public void innerBlit(int x1, int x2, int y1, int y2, int z, float u1, float u2, float v1, float v2, int color) {
        float a = (color >> 24 & 255) / 255.0F;
        float r = (color >> 16 & 255) / 255.0F;
        float g = (color >> 8 & 255) / 255.0F;
        float b = (color & 255) / 255.0F;

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(r, g, b, a);
        GL11.glTexCoord2f(u1, v1);
        GL11.glVertex3f(x1, y1, z);
        GL11.glTexCoord2f(u1, v2);
        GL11.glVertex3f(x1, y2, z);
        GL11.glTexCoord2f(u2, v2);
        GL11.glVertex3f(x2, y2, z);
        GL11.glTexCoord2f(u2, v1);
        GL11.glVertex3f(x2, y1, z);
        GL11.glEnd();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glDisable(GL11.GL_BLEND);
    }
}
