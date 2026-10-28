package com.thepearlking.stevespace.client.gui.config;

import com.thepearlking.stevespace.config.ClientConfig;
import com.thepearlking.stevespace.config.SpaceConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The mod's settings screen (Mods → Steve's Space Program → Config). Two tabs: gameplay
 * (shared with the server when hosting) and visuals (this player only). Done saves; Cancel
 * puts every value back the way it was.
 */
public final class SpaceConfigScreen extends Screen {

    private enum Tab { GAMEPLAY, VISUALS }

    private final Screen parent;
    private final Map<ConfigOption<?>, Object> original = new HashMap<>();
    private final List<ConfigOption<?>> gameplay = new ArrayList<>();
    private final List<ConfigOption<?>> visuals = new ArrayList<>();
    private HeaderAndFooterLayout layout;
    private Tab tab = Tab.GAMEPLAY;
    private ConfigOptionList list;

    public SpaceConfigScreen(Screen parent) {
        super(Component.translatable("config.stevespace.title"));
        this.parent = parent;
        defineOptions();
        for (ConfigOption<?> option : gameplay) {
            original.put(option, option.get());
        }
        for (ConfigOption<?> option : visuals) {
            original.put(option, option.get());
        }
    }

    private void defineOptions() {
        gameplay.add(ConfigOption.number("time.earthDayMinutes", SpaceConfig.EARTH_DAY_MINUTES, 1, 120, 1,
                v -> (int) v + " min"));
        gameplay.add(ConfigOption.choice("time.timeWarpPermission", SpaceConfig.WARP_PERMISSION, SpaceConfig.WarpPermission.values()));
        gameplay.add(ConfigOption.toggle("rockets.creativeInfiniteFuel", SpaceConfig.CREATIVE_INFINITE_FUEL));
        gameplay.add(ConfigOption.integer("rockets.maxVesselBlocks", SpaceConfig.MAX_VESSEL_BLOCKS, 50, 20000, 50));
        gameplay.add(ConfigOption.toggle("rockets.shipPower", SpaceConfig.SHIP_POWER));
        gameplay.add(ConfigOption.toggle("nukes.nukes", SpaceConfig.NUKES));
        gameplay.add(ConfigOption.toggle("nukes.destruction", SpaceConfig.NUKE_DESTRUCTION));
        gameplay.add(ConfigOption.toggle("nukes.emp", SpaceConfig.NUKE_EMP));
        gameplay.add(ConfigOption.number("physics.gravityScale", SpaceConfig.GRAVITY_SCALE, 0.1, 2, 0.05,
                v -> String.format("%.2f×", v)));
        gameplay.add(ConfigOption.number("physics.thrustScale", SpaceConfig.THRUST_SCALE, 0.25, 5, 0.05,
                v -> String.format("%.2f×", v)));
        gameplay.add(ConfigOption.number("physics.fuelEfficiency", SpaceConfig.FUEL_EFFICIENCY, 0.25, 5, 0.05,
                v -> String.format("%.2f×", v)));
        gameplay.add(ConfigOption.number("physics.atmosphericDrag", SpaceConfig.ATMOSPHERIC_DRAG, 0, 3, 0.05,
                v -> v < 0.001 ? "Off" : String.format("%.2f×", v)));
        gameplay.add(ConfigOption.number("physics.turnRate", SpaceConfig.TURN_RATE, 5, 120, 1,
                v -> (int) v + "°/s"));
        gameplay.add(ConfigOption.number("physics.crashSpeed", SpaceConfig.CRASH_SPEED, 3, 100, 1,
                v -> (int) v + " m/s"));
        gameplay.add(ConfigOption.number("newWorlds.continentSize", SpaceConfig.CONTINENT_SIZE, 0.25, 4, 0.05,
                v -> String.format("%.2f×", v)));
        gameplay.add(ConfigOption.number("newWorlds.landFraction", SpaceConfig.LAND_FRACTION, 0.05, 0.9, 0.01,
                v -> Math.round(v * 100) + "%"));
        gameplay.add(ConfigOption.number("newWorlds.iceCapLatitude", SpaceConfig.ICE_CAP_LATITUDE, 45, 85, 1,
                v -> (int) v + "°"));

        visuals.add(ConfigOption.number("sky.sunMoonScale", ClientConfig.SUN_MOON_SCALE, 1, 10, 0.5,
                v -> String.format("%.1f×", v)));
        visuals.add(ConfigOption.number("sky.starBrightness", ClientConfig.STAR_BRIGHTNESS, 0, 2, 0.05,
                v -> Math.round(v * 100) + "%"));
        visuals.add(ConfigOption.toggle("planet.planetView", ClientConfig.PLANET_VIEW));
        visuals.add(ConfigOption.choice("planet.textureQuality", ClientConfig.PLANET_TEXTURE_QUALITY, ClientConfig.TextureQuality.values()));
    }

    @Override
    protected void init() {
        layout = new HeaderAndFooterLayout(this, 58, 33);
        LinearLayout header = layout.addToHeader(LinearLayout.vertical().spacing(6));
        header.defaultCellSetting().alignHorizontallyCenter();
        header.addChild(new net.minecraft.client.gui.components.StringWidget(title, font));
        LinearLayout tabs = header.addChild(LinearLayout.horizontal().spacing(4));
        Button gameplayTab = tabs.addChild(Button.builder(Component.translatable("config.stevespace.tab.gameplay"), b -> switchTab(Tab.GAMEPLAY)).width(120).build());
        Button visualsTab = tabs.addChild(Button.builder(Component.translatable("config.stevespace.tab.visuals"), b -> switchTab(Tab.VISUALS)).width(120).build());
        gameplayTab.active = tab != Tab.GAMEPLAY;
        visualsTab.active = tab != Tab.VISUALS;

        list = layout.addToContents(new ConfigOptionList(minecraft, width, layout.getContentHeight(), layout.getHeaderHeight()));
        String lastSection = "";
        for (ConfigOption<?> option : tab == Tab.GAMEPLAY ? gameplay : visuals) {
            String section = option.key.substring(0, option.key.indexOf('.'));
            if (!section.equals(lastSection)) {
                list.addSection(Component.translatable("config.stevespace.section." + section));
                lastSection = section;
            }
            list.addOption(option);
        }

        LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, b -> cancel()).width(150).build());
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).width(150).build());
        layout.visitWidgets(this::addRenderableWidget);
        layout.arrangeElements();
    }

    @Override
    protected void repositionElements() {
        if (layout == null) {
            return;
        }
        layout.arrangeElements();
        if (list != null) {
            list.updateSize(width, layout);
        }
    }

    private void switchTab(Tab newTab) {
        tab = newTab;
        rebuildWidgets();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void cancel() {
        for (Map.Entry<ConfigOption<?>, Object> entry : original.entrySet()) {
            ((ConfigOption) entry.getKey()).set(entry.getValue());
        }
        save();
        minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        save();
        minecraft.setScreen(parent);
    }

    private static void save() {
        SpaceConfig.SPEC.save();
        ClientConfig.SPEC.save();
    }
}
