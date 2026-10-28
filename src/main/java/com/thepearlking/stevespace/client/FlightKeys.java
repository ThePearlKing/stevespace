package com.thepearlking.stevespace.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** Flight controls. Pitch and roll use the normal movement keys (W/S, A/D). */
public final class FlightKeys {

    private static final String CATEGORY = "key.categories.stevespace";

    /** Keys that only mean something while flying, so they may share keys with walking controls. */
    private static final IKeyConflictContext PILOTING = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            return FlightInput.isPiloting();
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return other == this;
        }
    };

    public static final KeyMapping THROTTLE_UP = key("throttle_up", GLFW.GLFW_KEY_LEFT_SHIFT);
    public static final KeyMapping THROTTLE_DOWN = key("throttle_down", GLFW.GLFW_KEY_LEFT_CONTROL);
    public static final KeyMapping THROTTLE_FULL = key("throttle_full", GLFW.GLFW_KEY_Z);
    public static final KeyMapping THROTTLE_CUT = key("throttle_cut", GLFW.GLFW_KEY_X);
    // Not Q/E: those are vanilla's drop and inventory keys.
    public static final KeyMapping YAW_LEFT = key("yaw_left", GLFW.GLFW_KEY_LEFT);
    public static final KeyMapping YAW_RIGHT = key("yaw_right", GLFW.GLFW_KEY_RIGHT);
    public static final KeyMapping PITCH_UP = key("pitch_up", GLFW.GLFW_KEY_UP);
    public static final KeyMapping PITCH_DOWN = key("pitch_down", GLFW.GLFW_KEY_DOWN);
    /** Switches the movement keys between steering (main engines) and RCS (pushing the ship about). */
    public static final KeyMapping RCS_MODE = key("rcs_mode", GLFW.GLFW_KEY_B);
    public static final KeyMapping AUTOPILOT = key("autopilot", GLFW.GLFW_KEY_C);
    public static final KeyMapping WARP_FASTER = new KeyMapping("key.stevespace.warp_faster", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_PERIOD, CATEGORY);
    public static final KeyMapping WARP_SLOWER = new KeyMapping("key.stevespace.warp_slower", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_COMMA, CATEGORY);
    public static final KeyMapping MAP = new KeyMapping("key.stevespace.map", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, CATEGORY);
    public static final KeyMapping TELESCOPE_TARGETS = new KeyMapping("key.stevespace.telescope_targets", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_TAB, CATEGORY);
    public static final KeyMapping LEAVE_SEAT = new KeyMapping("key.stevespace.leave_seat", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);

    public static final KeyMapping RADIO = new KeyMapping("key.stevespace.radio", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);

    private FlightKeys() {
    }

    private static KeyMapping key(String name, int defaultKey) {
        return new KeyMapping("key.stevespace." + name, PILOTING, InputConstants.Type.KEYSYM, defaultKey, CATEGORY);
    }

    /** Mod bus. */
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(RADIO);
        event.register(THROTTLE_UP);
        event.register(THROTTLE_DOWN);
        event.register(THROTTLE_FULL);
        event.register(THROTTLE_CUT);
        event.register(YAW_LEFT);
        event.register(YAW_RIGHT);
        event.register(PITCH_UP);
        event.register(PITCH_DOWN);
        event.register(RCS_MODE);
        event.register(AUTOPILOT);
        event.register(LEAVE_SEAT);
        event.register(TELESCOPE_TARGETS);
        event.register(WARP_FASTER);
        event.register(WARP_SLOWER);
        event.register(MAP);
    }
}
