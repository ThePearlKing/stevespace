package com.thepearlking.stevespace.client.gui.config;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.function.DoubleFunction;

/**
 * One editable setting on the config screen: its label, its control, and how to read, write and
 * reset the underlying config value. Label and tooltip text come from the language file
 * ({@code config.stevespace.<key>} and {@code <key>.tooltip}).
 */
public abstract class ConfigOption<T> {

    protected final String key;
    protected final ForgeConfigSpec.ConfigValue<T> value;

    protected ConfigOption(String key, ForgeConfigSpec.ConfigValue<T> value) {
        this.key = key;
        this.value = value;
    }

    public Component label() {
        return Component.translatable("config.stevespace." + key);
    }

    public Component tooltip() {
        return Component.translatable("config.stevespace." + key + ".tooltip");
    }

    public T get() {
        return value.get();
    }

    public void set(T newValue) {
        value.set(newValue);
    }

    public T defaultValue() {
        return value.getDefault();
    }

    public boolean isDefault() {
        return get().equals(defaultValue());
    }

    /** Creates the control for this option; called again after a reset to refresh it. */
    public abstract AbstractWidget createWidget(int width, Runnable onChange);

    // ---------------------------------------------------------------------------------------

    public static ConfigOption<Boolean> toggle(String key, ForgeConfigSpec.BooleanValue value) {
        return new ConfigOption<>(key, value) {
            @Override
            public AbstractWidget createWidget(int width, Runnable onChange) {
                return CycleButton.onOffBuilder(get())
                        .displayOnlyValue()
                        .create(0, 0, width, 20, label(), (button, on) -> {
                            set(on);
                            onChange.run();
                        });
            }
        };
    }

    public static <E extends Enum<E>> ConfigOption<E> choice(String key, ForgeConfigSpec.EnumValue<E> value, E[] values) {
        return new ConfigOption<>(key, value) {
            @Override
            public AbstractWidget createWidget(int width, Runnable onChange) {
                return CycleButton.<E>builder(e -> Component.translatable("config.stevespace." + key + "." + e.name().toLowerCase()))
                        .withValues(values)
                        .withInitialValue(get())
                        .displayOnlyValue()
                        .create(0, 0, width, 20, label(), (button, choice) -> {
                            set(choice);
                            onChange.run();
                        });
            }
        };
    }

    /** A whole number on a slider, rounded to {@code step}. */
    public static ConfigOption<Integer> integer(String key, ForgeConfigSpec.IntValue value, int min, int max, int step) {
        return new ConfigOption<>(key, value) {
            @Override
            public AbstractWidget createWidget(int width, Runnable onChange) {
                double start = (get() - min) / (double) (max - min);
                return new AbstractSliderButton(0, 0, width, 20, Component.literal(String.valueOf(get())), start) {
                    @Override
                    protected void updateMessage() {
                        setMessage(Component.literal(String.valueOf(current())));
                    }

                    @Override
                    protected void applyValue() {
                        set(current());
                        onChange.run();
                    }

                    private int current() {
                        double raw = min + this.value * (max - min);
                        return (int) Math.max(min, Math.min(max, Math.round(raw / step) * step));
                    }
                };
            }
        };
    }

    /**
     * A number on a slider. {@code step} rounds the value (e.g. 0.05); {@code format} turns it
     * into the text shown on the slider.
     */
    public static ConfigOption<Double> number(String key, ForgeConfigSpec.DoubleValue value, double min, double max,
                                              double step, DoubleFunction<String> format) {
        return new ConfigOption<>(key, value) {
            @Override
            public AbstractWidget createWidget(int width, Runnable onChange) {
                double start = (get() - min) / (max - min);
                return new AbstractSliderButton(0, 0, width, 20, Component.literal(format.apply(get())), start) {
                    @Override
                    protected void updateMessage() {
                        setMessage(Component.literal(format.apply(current())));
                    }

                    @Override
                    protected void applyValue() {
                        set(current());
                        onChange.run();
                    }

                    private double current() {
                        double raw = min + this.value * (max - min);
                        return Math.max(min, Math.min(max, Math.round(raw / step) * step));
                    }
                };
            }
        };
    }
}
