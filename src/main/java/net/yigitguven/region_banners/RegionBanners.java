package net.yigitguven.region_banners;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(RegionBanners.MODID)
public class RegionBanners {
    public static final String MODID = "region_banners";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RegionBanners(IEventBus modEventBus, ModContainer modContainer) {
    }
}
