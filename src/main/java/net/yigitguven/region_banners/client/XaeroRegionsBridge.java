package net.yigitguven.region_banners.client;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.client.gui.screens.Screen;
import net.yigitguven.region_banners.RegionBanners;

public final class XaeroRegionsBridge {
    private static final String REGION_EDIT_SCREEN_CLASS = "com.example.xaeroregions.client.RegionEditScreen";
    private static final String CLIENT_ICON_CACHE_CLASS = "com.example.xaeroregions.client.ClientIconCache";
    private static final String REGION_NETWORK_CLASS = "com.example.xaeroregions.network.RegionNetwork";
    private static final String ICON_UPLOAD_PAYLOAD_CLASS = "com.example.xaeroregions.network.IconUploadPayload";
    private static final String REGION_ICON_REF_CLASS = "com.example.xaeroregions.common.region.RegionIconRef";
    private static final String REGION_ICON_KIND_CLASS = "com.example.xaeroregions.common.region.RegionIconKind";

    private XaeroRegionsBridge() {
    }

    public static boolean isRegionEditScreen(Screen screen) {
        return screen != null && REGION_EDIT_SCREEN_CLASS.equals(screen.getClass().getName());
    }

    public static boolean hasClientPermission(Screen screen) {
        try {
            Method hasPerm = screen.getClass().getDeclaredMethod("hasClientPermission");
            hasPerm.setAccessible(true);
            boolean allowed = (boolean) hasPerm.invoke(screen);
            if (!allowed) {
                try {
                    Method showDenied = screen.getClass().getDeclaredMethod("showPermissionDenied");
                    showDenied.setAccessible(true);
                    showDenied.invoke(screen);
                } catch (NoSuchMethodException ignored) {
                    // Fallback: screen might have changed private helper name
                }
            }
            return allowed;
        } catch (NoSuchMethodException e) {
            RegionBanners.LOGGER.warn("RegionEditScreen#hasClientPermission not found; proceeding", e);
            return true;
        } catch (ReflectiveOperationException e) {
            RegionBanners.LOGGER.warn("Failed to check RegionEditScreen permission", e);
            return false;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static UUID uploadAndApplyBannerIcon(Screen screen, byte[] pngBytes, String fileName)
            throws ReflectiveOperationException {
        UUID iconId = UUID.randomUUID();

        Class<?> payloadClass = Class.forName(ICON_UPLOAD_PAYLOAD_CLASS);
        Constructor<?> payloadCtor = payloadClass.getConstructor(UUID.class, String.class, byte[].class);
        Object payload = payloadCtor.newInstance(iconId, fileName, pngBytes);

        Class<?> networkClass = Class.forName(REGION_NETWORK_CLASS);
        Object networkInstance = networkClass.getField("INSTANCE").get(null);
        Method sendIconUpload = networkClass.getMethod("sendIconUpload", payloadClass);
        sendIconUpload.invoke(networkInstance, payload);

        Class<?> cacheClass = Class.forName(CLIENT_ICON_CACHE_CLASS);
        Object cacheInstance = cacheClass.getField("INSTANCE").get(null);
        Method putCustom = cacheClass.getMethod("putCustom", UUID.class, byte[].class);
        putCustom.invoke(cacheInstance, iconId, pngBytes);

        Class<?> iconKindClass = Class.forName(REGION_ICON_KIND_CLASS);
        Object customKind = Enum.valueOf((Class<Enum>) iconKindClass, "CUSTOM");

        Class<?> iconRefClass = Class.forName(REGION_ICON_REF_CLASS);
        Constructor<?> iconRefCtor = iconRefClass.getConstructor(iconKindClass, String.class);
        Object iconRef = iconRefCtor.newInstance(customKind, iconId.toString());

        Field iconRefField = screen.getClass().getDeclaredField("iconRef");
        iconRefField.setAccessible(true);
        iconRefField.set(screen, iconRef);

        return iconId;
    }
}
