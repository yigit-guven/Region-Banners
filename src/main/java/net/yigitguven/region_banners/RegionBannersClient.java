package net.yigitguven.region_banners;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.yigitguven.region_banners.client.RegionEditScreenHandler;

@Mod(value = RegionBanners.MODID, dist = Dist.CLIENT)
public class RegionBannersClient {
    public RegionBannersClient(ModContainer container) {
        NeoForge.EVENT_BUS.addListener(RegionEditScreenHandler::onScreenInitPost);
    }
}
