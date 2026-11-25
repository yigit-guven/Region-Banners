package net.yigitguven.region_banners.client;

import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.yigitguven.region_banners.RegionBanners;

public final class RegionEditScreenHandler {
    private RegionEditScreenHandler() {
    }

    public static void onScreenInitPost(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!XaeroRegionsBridge.isRegionEditScreen(screen)) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        ItemStack heldBanner = findHeldBanner(mc.player);
        if (heldBanner.isEmpty()) {
            return;
        }

        Button uploadButton = null;
        Button iconButton = null;
        Component uploadLabel = Component.translatable("xaeroregions.button.upload_icon");

        for (GuiEventListener listener : event.getListenersList()) {
            if (!(listener instanceof Button button)) {
                continue;
            }
            Component message = button.getMessage();
            if (uploadButton == null && (uploadLabel.equals(message) || (button.getWidth() == 244 && button.getHeight() == 20))) {
                uploadButton = button;
            }
            if (iconButton == null && message.getString().startsWith("Icon:")) {
                iconButton = button;
            }
        }

        int x;
        int y;
        int w;
        if (uploadButton != null) {
            uploadButton.setWidth(118);
            x = uploadButton.getX() + 126;
            y = uploadButton.getY();
            w = 118;
        } else {
            x = screen.width / 2 - 122;
            y = Math.max(18, (screen.height - 252) / 2) + 196;
            w = 244;
        }

        final Button iconButtonRef = iconButton;
        Button importButton = Button.builder(Component.translatable("region_banners.button.import_banner_icon"), button -> {
            ItemStack currentBanner = findHeldBanner(mc.player);
            if (currentBanner.isEmpty()) {
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.translatable("region_banners.message.no_banner"), true);
                }
                return;
            }
            if (!XaeroRegionsBridge.hasClientPermission(screen)) {
                return;
            }

            try {
                byte[] pngBytes = BannerIconRenderer.renderBannerPng(currentBanner);
                String fileName = BannerIconRenderer.buildFileName(currentBanner);
                UUID iconId = XaeroRegionsBridge.uploadAndApplyBannerIcon(screen, pngBytes, fileName);
                RegionBanners.LOGGER.debug("Imported held banner icon {} ({})", fileName, iconId);

                button.setMessage(Component.translatable("region_banners.button.banner_imported"));
                if (iconButtonRef != null) {
                    iconButtonRef.setMessage(Component.literal("Icon: Banner"));
                }
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.translatable("region_banners.message.banner_imported"), true);
                }
            } catch (Exception e) {
                RegionBanners.LOGGER.error("Failed to import banner icon into Xaero Regions", e);
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.translatable("region_banners.message.import_failed"), true);
                }
            }
        }).bounds(x, y, w, 20).build();

        event.addListener(importButton);
    }

    private static ItemStack findHeldBanner(Player player) {
        if (player == null) {
            return ItemStack.EMPTY;
        }
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() instanceof BannerItem) {
            return mainHand;
        }
        ItemStack offHand = player.getOffhandItem();
        if (offHand.getItem() instanceof BannerItem) {
            return offHand;
        }
        return ItemStack.EMPTY;
    }
}
