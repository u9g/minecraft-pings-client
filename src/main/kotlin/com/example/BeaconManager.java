package com.example;

import kotlin.Triple;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import static net.minecraft.client.renderer.GlStateManager.*;
import static org.lwjgl.opengl.GL11.glTexParameteri;

public class BeaconManager {

    private static final Tessellator tessellator = Tessellator.getInstance();
    private static final ResourceLocation beamResource = new ResourceLocation("textures/entity/beacon_beam.png");

    public static void drawBeam(Triple<Double, Double, Double> loc, int color, float partialTicks) {
        RenderManager renderManager = Minecraft.getMinecraft().getRenderManager();
        if (renderManager.renderViewEntity == null) return;

        float alpha = 1f;

        int x = (int)Math.floor(loc.component1());
        int y = (int)Math.floor(loc.component2());
        int z = (int)Math.floor(loc.component3());
        Vec3d positionVec = new Vec3d(x, y + 0.118D, z);
        Vec3d playerVec = renderManager.renderViewEntity.getPositionVector();

        double distance = playerVec.distanceTo(positionVec);
        if (distance <= 4f || distance > 4000f) return;
//        if (distance <= 8f) alpha = (float)(distance - 4f) / 3f;

//        if (alpha > 1) alpha = 1;  // avoid excessive values

//        alpha *= color.a;

        double maxDistance = Minecraft.getMinecraft().gameSettings.renderDistanceChunks * 16d;
        if (distance > maxDistance) {  // this will drag the beam to the visible area if outside of it
            // partial ticks aren't factored into player pos, so if we're going to use it for rendering, we need to recalculate to account for partial ticks
            Vec3d prevPosVec = new Vec3d(renderManager.renderViewEntity.prevPosX, renderManager.renderViewEntity.prevPosY, renderManager.renderViewEntity.prevPosZ);
            playerVec = playerVec.subtract(prevPosVec).scale(partialTicks).add(prevPosVec);

            Vec3d delta = positionVec.subtract(playerVec).normalize();
            positionVec = playerVec.add(delta.x * maxDistance, delta.y * maxDistance, delta.z * maxDistance);
        }

        drawBeam(positionVec.x - renderManager.viewerPosX, -renderManager.viewerPosY,
                 positionVec.z - renderManager.viewerPosZ, 0xFF, color);
    }

    private static void drawBeam(double x, double y, double z, int alpha, int color) {
        pushAttrib();
        {
            Minecraft.getMinecraft().renderEngine.bindTexture(beamResource);  // binds the texture
            glTexParameteri(3553, 10242, 10497);

            // beacon light animation
            float time = Minecraft.getSystemTime() / 50F;
            float offset = -(-time * 0.2F - MathHelper.fastFloor(-time * 0.1F)) * 0.6F;

            // positions
            double d1 = 256.0F * alpha;
            double d2 = -1f + offset;
            double d3 = 256.0F * alpha + d2;

            disableLighting();
            enableDepth();
            disableCull();
            enableBlend();
            tryBlendFuncSeparate(770, 771, 1, 0);
            color(1f, 1f, 1f, 1f);

            // drawing
            tessellator.getBuffer().begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
            BufferBuilder builder = tessellator.getBuffer();
            {
                int r = color >> 16 & 0xFF;
                int g = color >> 8 & 0xFF;
                int b = color & 0xFF;
                builder.pos(x + .2d, y + d1, z + .2d).tex(1d, d3).color(r, g, b, alpha).endVertex();
                builder.pos(x + .2d, y, z + .2d).tex(1d, d2).color(r, g, b, alpha).endVertex();
                builder.pos(x + .8d, y, z + .2d).tex(0d, d2).color(r, g, b, alpha).endVertex();
                builder.pos(x + .8d, y + d1, z + .2d).tex(0d, d3).color(r, g, b, alpha).endVertex();
                builder.pos(x + .8d, y + d1, z + .8d).tex(1d, d3).color(r, g, b, alpha).endVertex();
                builder.pos(x + .8d, y, z + .8d).tex(1d, d2).color(r, g, b, alpha).endVertex();
                builder.pos(x + .2d, y, z + .8d).tex(0d, d2).color(r, g, b, alpha).endVertex();
                builder.pos(x + .2d, y + d1, z + .8d).tex(0d, d3).color(r, g, b, alpha).endVertex();
                builder.pos(x + .8d, y + d1, z + .2d).tex(1d, d3).color(r, g, b, alpha).endVertex();
                builder.pos(x + .8d, y, z + .2d).tex(1d, d2).color(r, g, b, alpha).endVertex();
                builder.pos(x + .8d, y, z + .8d).tex(0d, d2).color(r, g, b, alpha).endVertex();
                builder.pos(x + .8d, y + d1, z + .8d).tex(0d, d3).color(r, g, b, alpha).endVertex();
                builder.pos(x + .2d, y + d1, z + .8d).tex(1d, d3).color(r, g, b, alpha).endVertex();
                builder.pos(x + .2d, y, z + .8d).tex(1d, d2).color(r, g, b, alpha).endVertex();
                builder.pos(x + .2d, y, z + .2d).tex(0d, d2).color(r, g, b, alpha).endVertex();
                builder.pos(x + .2d, y + d1, z + .2d).tex(0d, d3).color(r, g, b, alpha).endVertex();
            }
            tessellator.draw();

            // resetting
            color(1f, 1f, 1f, 1f);
            disableBlend();
            enableCull();
        }
        popAttrib();
    }

}