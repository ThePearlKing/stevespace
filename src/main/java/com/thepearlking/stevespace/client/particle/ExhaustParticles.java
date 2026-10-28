package com.thepearlking.stevespace.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** Rocket exhaust: a white-hot flame that turns orange as it cools, and smoke that spreads out. */
public final class ExhaustParticles {

    private ExhaustParticles() {
    }

    public static final class Flame extends TextureSheetParticle {
        private final float startSize;

        Flame(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
            super(level, x, y, z, vx, vy, vz);
            xd = vx;
            yd = vy;
            zd = vz;
            lifetime = 5 + random.nextInt(4);
            startSize = 0.4f + random.nextFloat() * 0.25f;
            quadSize = startSize;
            gravity = 0;
            friction = 0.9f;
            hasPhysics = true; // fire splashes on the ground instead of sinking into it
            pickSprite(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            float t = (float) age / lifetime;
            // white → yellow → orange as the gas expands and cools
            rCol = 1.0f;
            gCol = 0.95f - 0.45f * t;
            bCol = 0.85f - 0.75f * t;
            alpha = 1 - t * t;
            quadSize = startSize * (1 + t * 1.6f);
        }

        @Override
        public int getLightColor(float partialTick) {
            return 0xF000F0; // glows
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    public static final class Smoke extends TextureSheetParticle {
        private final float startSize;

        Smoke(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
            super(level, x, y, z, vx, vy, vz);
            xd = vx;
            yd = vy;
            zd = vz;
            lifetime = 60 + random.nextInt(80);
            startSize = 0.5f + random.nextFloat() * 0.5f;
            quadSize = startSize;
            gravity = -0.004f; // warm smoke drifts up slowly
            friction = 0.9f;
            hasPhysics = true;
            float grey = 0.72f + random.nextFloat() * 0.15f;
            rCol = grey;
            gCol = grey;
            bCol = grey * 1.02f;
            alpha = 0.55f;
            pickSprite(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            float t = (float) age / lifetime;
            quadSize = startSize * (1 + t * 3.5f);
            alpha = 0.7f * (1 - t) * Math.min(1, age / 3f);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    public record FlameProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz) {
            return new Flame(level, x, y, z, vx, vy, vz, sprites);
        }
    }

    public record SmokeProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz) {
            return new Smoke(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
