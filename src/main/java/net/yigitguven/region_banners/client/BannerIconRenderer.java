package net.yigitguven.region_banners.client;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.yigitguven.region_banners.RegionBanners;

public final class BannerIconRenderer {
    private static final int SRC_W = 20;
    private static final int SRC_H = 40;
    private static final int DST_W = 6;
    private static final int DST_H = 10;
    private static final int OUT_SIZE = 12;
    private static final int BORDER_COLOR = 0xCC181818;
    private static final ResourceLocation BANNER_BASE_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/banner_base.png");

    private BannerIconRenderer() {
    }

    public static String buildFileName(ItemStack bannerStack) {
        if (bannerStack.getItem() instanceof BannerItem bannerItem) {
            return "rb_" + bannerItem.getColor().getName() + "_banner.png";
        }
        return "rb_banner.png";
    }

    public static UUID computeBannerUuid(ItemStack bannerStack) {
        if (!(bannerStack.getItem() instanceof BannerItem bannerItem)) {
            throw new IllegalArgumentException("ItemStack is not a BannerItem");
        }
        StringBuilder canonical = new StringBuilder("region_banners:v1:")
                .append(bannerItem.getColor().getSerializedName());
        BannerPatternLayers layers = bannerStack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
        for (BannerPatternLayers.Layer layer : layers.layers()) {
            ResourceLocation assetId = layer.pattern().value().assetId();
            canonical.append('|')
                    .append(assetId)
                    .append('=')
                    .append(layer.color().getSerializedName());
        }
        return UUID.nameUUIDFromBytes(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] renderBannerPng(ItemStack bannerStack) throws IOException {
        if (!(bannerStack.getItem() instanceof BannerItem bannerItem)) {
            throw new IllegalArgumentException("ItemStack is not a BannerItem");
        }

        DyeColor baseColor = bannerItem.getColor();
        BannerPatternLayers layers = bannerStack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
        int[] canvas = new int[SRC_W * SRC_H];

        BufferedImage baseImg = loadTexture(BANNER_BASE_TEXTURE);
        int baseDye = baseColor.getTextureDiffuseColor();
        if (baseImg != null) {
            fillTintedBase(canvas, baseImg, baseDye);
        } else {
            int opaque = 0xFF000000 | (baseDye & 0xFFFFFF);
            for (int i = 0; i < canvas.length; i++) {
                canvas[i] = opaque;
            }
        }

        for (BannerPatternLayers.Layer layer : layers.layers()) {
            ResourceLocation assetId = layer.pattern().value().assetId();
            ResourceLocation textureLoc = ResourceLocation.fromNamespaceAndPath(
                    assetId.getNamespace(),
                    "textures/entity/banner/" + assetId.getPath() + ".png"
            );
            BufferedImage patternImg = loadTexture(textureLoc);
            if (patternImg == null) {
                RegionBanners.LOGGER.warn("Missing banner pattern texture: {}", textureLoc);
                continue;
            }
            blendPatternLayer(canvas, patternImg, layer.color().getTextureDiffuseColor());
        }

        BufferedImage out = new BufferedImage(OUT_SIZE, OUT_SIZE, BufferedImage.TYPE_INT_ARGB);
        drawDownsampledBanner(out, canvas);
        drawBorder(out);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (!ImageIO.write(out, "PNG", baos)) {
            throw new IOException("No PNG ImageIO writer available");
        }
        return baos.toByteArray();
    }

    private static BufferedImage loadTexture(ResourceLocation location) throws IOException {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(location);
        if (resource.isEmpty()) {
            return null;
        }
        try (InputStream in = resource.get().open()) {
            return ImageIO.read(in);
        }
    }

    private static void fillTintedBase(int[] canvas, BufferedImage img, int dyeArgb) {
        int dyeR = (dyeArgb >> 16) & 0xFF;
        int dyeG = (dyeArgb >> 8) & 0xFF;
        int dyeB = dyeArgb & 0xFF;
        int imgW = img.getWidth();
        int imgH = img.getHeight();

        for (int py = 0; py < SRC_H; py++) {
            int sy = clamp((int) Math.floor((1 + py) * (imgH / 64.0)), 0, imgH - 1);
            for (int px = 0; px < SRC_W; px++) {
                int sx = clamp((int) Math.floor((1 + px) * (imgW / 64.0)), 0, imgW - 1);
                int tex = img.getRGB(sx, sy);
                int r = (((tex >> 16) & 0xFF) * dyeR) / 255;
                int g = (((tex >> 8) & 0xFF) * dyeG) / 255;
                int b = ((tex & 0xFF) * dyeB) / 255;
                canvas[py * SRC_W + px] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
    }

    private static void blendPatternLayer(int[] canvas, BufferedImage img, int dyeArgb) {
        int dyeR = (dyeArgb >> 16) & 0xFF;
        int dyeG = (dyeArgb >> 8) & 0xFF;
        int dyeB = dyeArgb & 0xFF;
        int imgW = img.getWidth();
        int imgH = img.getHeight();

        for (int py = 0; py < SRC_H; py++) {
            int sy = clamp((int) Math.floor((1 + py) * (imgH / 64.0)), 0, imgH - 1);
            for (int px = 0; px < SRC_W; px++) {
                int sx = clamp((int) Math.floor((1 + px) * (imgW / 64.0)), 0, imgW - 1);
                int pat = img.getRGB(sx, sy);
                int pA = (pat >> 24) & 0xFF;
                if (pA == 0) {
                    continue;
                }

                int srcR = (((pat >> 16) & 0xFF) * dyeR) / 255;
                int srcG = (((pat >> 8) & 0xFF) * dyeG) / 255;
                int srcB = ((pat & 0xFF) * dyeB) / 255;

                int idx = py * SRC_W + px;
                int dst = canvas[idx];
                int dstR = (dst >> 16) & 0xFF;
                int dstG = (dst >> 8) & 0xFF;
                int dstB = dst & 0xFF;

                int invA = 255 - pA;
                int outR = (srcR * pA + dstR * invA) / 255;
                int outG = (srcG * pA + dstG * invA) / 255;
                int outB = (srcB * pA + dstB * invA) / 255;
                canvas[idx] = 0xFF000000 | (outR << 16) | (outG << 8) | outB;
            }
        }
    }

    private static void drawDownsampledBanner(BufferedImage out, int[] canvas) {
        for (int dy = 0; dy < DST_H; dy++) {
            double y0 = dy * (double) SRC_H / DST_H;
            double y1 = (dy + 1) * (double) SRC_H / DST_H;
            int iy0 = (int) Math.floor(y0);
            int iy1 = Math.min(SRC_H - 1, (int) Math.floor(y1 - 1e-9));

            for (int dx = 0; dx < DST_W; dx++) {
                double x0 = dx * (double) SRC_W / DST_W;
                double x1 = (dx + 1) * (double) SRC_W / DST_W;
                int ix0 = (int) Math.floor(x0);
                int ix1 = Math.min(SRC_W - 1, (int) Math.floor(x1 - 1e-9));

                double totalWeight = 0.0;
                double sumR = 0.0;
                double sumG = 0.0;
                double sumB = 0.0;

                for (int sy = iy0; sy <= iy1; sy++) {
                    double wy = Math.min(y1, sy + 1.0) - Math.max(y0, sy);
                    for (int sx = ix0; sx <= ix1; sx++) {
                        double wx = Math.min(x1, sx + 1.0) - Math.max(x0, sx);
                        double w = wx * wy;
                        int rgb = canvas[sy * SRC_W + sx];
                        sumR += ((rgb >> 16) & 0xFF) * w;
                        sumG += ((rgb >> 8) & 0xFF) * w;
                        sumB += (rgb & 0xFF) * w;
                        totalWeight += w;
                    }
                }

                int r = clamp((int) Math.round(sumR / totalWeight), 0, 255);
                int g = clamp((int) Math.round(sumG / totalWeight), 0, 255);
                int b = clamp((int) Math.round(sumB / totalWeight), 0, 255);
                out.setRGB(dx + 3, dy + 1, 0xFF000000 | (r << 16) | (g << 8) | b);
            }
        }
    }

    private static void drawBorder(BufferedImage out) {
        for (int x = 2; x <= 9; x++) {
            out.setRGB(x, 0, BORDER_COLOR);
            out.setRGB(x, 11, BORDER_COLOR);
        }
        for (int y = 1; y <= 10; y++) {
            out.setRGB(2, y, BORDER_COLOR);
            out.setRGB(9, y, BORDER_COLOR);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
