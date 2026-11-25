package net.yigitguven.region_banners.client;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
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
    private record PendingBannerUpload(UUID iconId, byte[] pngBytes, String fileName) {
    }

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
        Button saveButton = null;
        Component uploadLabel = Component.translatable("xaeroregions.button.upload_icon");
        Component saveLabel = Component.translatable("xaeroregions.button.save");

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
            if (saveButton == null && saveLabel.equals(message)) {
                saveButton = button;
            }
        }

        final AtomicReference<PendingBannerUpload> pendingUpload = new AtomicReference<>();

        if (saveButton != null) {
            final Button originalSave = saveButton;
            event.removeListener(originalSave);
            Button wrappedSave = Button.builder(originalSave.getMessage(), button -> {
                PendingBannerUpload pending = pendingUpload.getAndSet(null);
                if (pending != null
                        && pending.iconId().equals(XaeroRegionsBridge.getCurrentCustomIconId(screen))
                        && !XaeroRegionsBridge.hasCachedIcon(pending.iconId())) {
                    if (!XaeroRegionsBridge.hasClientPermission(screen)) {
                        return;
                    }
                    try {
                        XaeroRegionsBridge.uploadIcon(pending.iconId(), pending.pngBytes(), pending.fileName());
                        RegionBanners.LOGGER.debug("Uploaded deferred banner icon {} ({})", pending.fileName(), pending.iconId());
                    } catch (Exception e) {
                        RegionBanners.LOGGER.error("Failed to upload banner icon to Xaero Regions", e);
                        if (mc.player != null) {
                            mc.player.displayClientMessage(Component.translatable("region_banners.message.import_failed"), true);
                        }
                        return;
                    }
                }
                originalSave.onPress();
            }).bounds(originalSave.getX(), originalSave.getY(), originalSave.getWidth(), originalSave.getHeight()).build();
            event.addListener(wrappedSave);
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
        final boolean hasWrappedSave = saveButton != null;
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
                UUID iconId = BannerIconRenderer.computeBannerUuid(currentBanner);
                if (XaeroRegionsBridge.hasCachedIcon(iconId)) {
                    pendingUpload.set(null);
                } else {
                    byte[] pngBytes = BannerIconRenderer.renderBannerPng(currentBanner);
                    String fileName = BannerIconRenderer.buildFileName(currentBanner);
                    if (hasWrappedSave) {
                        pendingUpload.set(new PendingBannerUpload(iconId, pngBytes, fileName));
                    } else {
                        XaeroRegionsBridge.uploadIcon(iconId, pngBytes, fileName);
                    }
                }

                XaeroRegionsBridge.applyIconRef(screen, iconId);
                RegionBanners.LOGGER.debug("Selected held banner icon {}", iconId);

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
