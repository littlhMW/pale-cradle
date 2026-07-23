package com.pale_cradle.entity;

import com.pale_cradle.PaleCradleMod;
import com.pale_cradle.entity.minion.PaleMinionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.List;

public class KuxuezheEntity extends Monster implements GeoEntity {

    // ============ Synced Data Keys ============
    private static final EntityDataAccessor<String> DATA_STATE =
            SynchedEntityData.defineId(KuxuezheEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DATA_PHASE_TWO =
            SynchedEntityData.defineId(KuxuezheEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_STATE_TIMER =
            SynchedEntityData.defineId(KuxuezheEntity.class, EntityDataSerializers.INT);

    // ============ Boss States ============
    public enum BossState {
        // Phase 1 - Hanging
        HANGING_DORMANT,    // Inactive, eyes closed
        HANGING_WAKING,     // Wake up animation
        HANGING_IDLE,       // Idle breathing/swaying
        HANGING_SCREAM,     // Short scream attack
        HANGING_CURSE,      // Long curse attack
        HANGING_SUMMON,     // Summon minions

        // Transition
        FALLING,            // Fall from chains

        // Phase 2 - Ground
        GROUND_IDLE,        // Grounded idle
        CRAWLING,           // Fast crawl
        POUNCE,             // Leap attack
        JUMP_BACK,          // Jump backward
        GROUND_SCREAM       // Scream on ground
    }

    // ============ Animation Definitions ============
    private static final RawAnimation ANIM_INACTIVE = RawAnimation.begin().thenLoop("animation.boss.hang_inactive");
    private static final RawAnimation ANIM_WAKEUP = RawAnimation.begin().thenPlay("animation.boss.hang_wakeup");
    private static final RawAnimation ANIM_HANG_IDLE = RawAnimation.begin().thenLoop("animation.boss.hang_idle");
    private static final RawAnimation ANIM_HANG_SCREAM = RawAnimation.begin().thenPlay("animation.boss.hang_attack");
    private static final RawAnimation ANIM_HANG_CURSE = RawAnimation.begin().thenPlay("animation.boss.hang_curse1");
    private static final RawAnimation ANIM_HANG_SUMMON = RawAnimation.begin().thenPlay("animation.boss.hang_summon");
    private static final RawAnimation ANIM_FALL = RawAnimation.begin().thenPlay("animation.boss.fall");
    private static final RawAnimation ANIM_GROUND_IDLE = RawAnimation.begin().thenLoop("animation.boss.ground_idle");
    private static final RawAnimation ANIM_CRAWL = RawAnimation.begin().thenLoop("animation.boss.ground_crawl");
    private static final RawAnimation ANIM_POUNCE = RawAnimation.begin().thenPlay("animation.boss.ground_attack");
    private static final RawAnimation ANIM_JUMP_BACK = RawAnimation.begin().thenPlay("animation.boss.ground_jump_back");
    private static final RawAnimation ANIM_GROUND_SCREAM = RawAnimation.begin().thenPlay("animation.boss.ground_scream_attack");

    // ============ Combat Timings ============
    private BossState currentState = BossState.HANGING_DORMANT;
    private int stateTimer = 0;
    private int pounceCount = 0;
    private int attackCooldown = 0;
    private int curseCooldown = 0;
    private int currentCurseVariant = 0; // 0 = curse1, 1 = curse2
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // Detection range for waking up
    private static final double WAKE_RANGE = 12.0;
    // Range for close-combat trigger (summon + shockwave)
    private static final double CLOSE_RANGE = 4.0;
    // Max pounces in a row before jump-back
    private static final int MAX_POUNCES = 5;
    private static final int MIN_POUNCES = 3;

    public KuxuezheEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
        this.xpReward = 500;
        this.setNoGravity(true); // Phase 1: no gravity (hanging)
        this.noPhysics = false;  // Allow physics for collision
    }

    // ============ Attribute Setup ============
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 300.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D) // Phase 1: stationary
                .add(Attributes.ATTACK_DAMAGE, 10.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ARMOR, 4.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    // ============ Data Sync ============
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STATE, BossState.HANGING_DORMANT.name());
        builder.define(DATA_PHASE_TWO, false);
        builder.define(DATA_STATE_TIMER, 0);
    }

    // ============ GeckoLib Animation Controller ============
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main_controller", 0, this::mainController));
    }

    private PlayState mainController(AnimationState<KuxuezheEntity> state) {
        BossState bs = getCurrentState();

        return switch (bs) {
            case HANGING_DORMANT -> state.setAndContinue(ANIM_INACTIVE);
            case HANGING_WAKING -> state.setAndContinue(ANIM_WAKEUP);
            case HANGING_IDLE -> state.setAndContinue(ANIM_HANG_IDLE);
            case HANGING_SCREAM -> state.setAndContinue(ANIM_HANG_SCREAM);
            case HANGING_CURSE -> {
                RawAnimation curse = (currentCurseVariant == 0) ? ANIM_HANG_CURSE : RawAnimation.begin().thenPlay("animation.boss.hang_curse2");
                yield state.setAndContinue(curse);
            }
            case HANGING_SUMMON -> state.setAndContinue(ANIM_HANG_SUMMON);
            case FALLING -> state.setAndContinue(ANIM_FALL);
            case GROUND_IDLE -> state.setAndContinue(ANIM_GROUND_IDLE);
            case CRAWLING -> state.setAndContinue(ANIM_CRAWL);
            case POUNCE -> state.setAndContinue(ANIM_POUNCE);
            case JUMP_BACK -> state.setAndContinue(ANIM_JUMP_BACK);
            case GROUND_SCREAM -> state.setAndContinue(ANIM_GROUND_SCREAM);
        };
    }

    // ============ State Management ============
    public BossState getCurrentState() {
        return BossState.valueOf(this.entityData.get(DATA_STATE));
    }

    public boolean isPhaseTwo() {
        return this.entityData.get(DATA_PHASE_TWO);
    }

    public void setCurrentState(BossState newState) {
        BossState oldState = getCurrentState();
        this.entityData.set(DATA_STATE, newState.name());
        this.stateTimer = 0;
        PaleCradleMod.LOGGER.debug("Boss state: {} -> {}", oldState, newState);
    }

    public int getStateTimer() {
        return this.stateTimer;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // ============ Position Management ============
    private Vec3 getHangingPosition() {
        return this.position();
    }

    // ============ Tick ============
    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            return;
        }

        // Increment timers
        this.stateTimer++;
        if (this.attackCooldown > 0) this.attackCooldown--;
        if (this.curseCooldown > 0) this.curseCooldown--;

        BossState state = getCurrentState();
        boolean phaseTwo = isPhaseTwo();

        // Phase transition check
        if (!phaseTwo && this.getHealth() <= this.getMaxHealth() / 3.0f) {
            this.entityData.set(DATA_PHASE_TWO, true);
            setCurrentState(BossState.FALLING);
            this.setNoGravity(true);
        }

        // State machine tick
        tickState(state);

        // Keep the boss in position during Phase 1 hanging states
        if (!phaseTwo && state != BossState.FALLING) {
            Vec3 hangPos = getHangingPosition();
            this.setPos(hangPos.x, hangPos.y, hangPos.z);
            this.setDeltaMovement(Vec3.ZERO);
            this.setNoGravity(true);
        }
    }

    private void tickState(BossState state) {
        switch (state) {
            case HANGING_DORMANT -> tickHangingDormant();
            case HANGING_WAKING -> tickHangingWaking();
            case HANGING_IDLE -> tickHangingIdle();
            case HANGING_SCREAM -> tickHangingScream();
            case HANGING_CURSE -> tickHangingCurse();
            case HANGING_SUMMON -> tickHangingSummon();
            case FALLING -> tickFalling();
            case GROUND_IDLE -> tickGroundIdle();
            case CRAWLING -> tickCrawling();
            case POUNCE -> tickPounce();
            case JUMP_BACK -> tickJumpBack();
            case GROUND_SCREAM -> tickGroundScream();
        }
    }

    // ============ PHASE 1 STATE TICKS ============

    private void tickHangingDormant() {
        // Check for nearby players to wake up
        if (this.tickCount % 20 == 0) { // Check every second
            Player nearest = this.level().getNearestPlayer(this, WAKE_RANGE);
            if (nearest != null && !nearest.isCreative() && !nearest.isSpectator()) {
                setCurrentState(BossState.HANGING_WAKING);
            }
        }
    }

    private void tickHangingWaking() {
        // Wake-up animation runs ~8.5 seconds (170 ticks)
        if (stateTimer >= 170) {
            setCurrentState(BossState.HANGING_IDLE);
        }
        // eyes open at ~6s (120 ticks) → handled by animation, but we can play sound
        if (stateTimer == 120) {
            this.playSound(SoundEvents.WARDEN_ROAR, 1.5f, 0.5f);
        }

        if (this.tickCount % 10 == 0) {
            this.level().broadcastEntityEvent(this, (byte) 10); // wakeup particles
        }
    }

    private void tickHangingIdle() {
        LivingEntity target = this.getTarget();

        if (target == null || !target.isAlive() || target.distanceToSqr(this) > 32 * 32) {
            // Find target
            Player nearest = this.level().getNearestPlayer(this, 16.0);
            if (nearest != null && !nearest.isCreative() && !nearest.isSpectator()) {
                this.setTarget(nearest);
            } else {
                // No target, go dormant after a while
                if (stateTimer > 200) {
                    setCurrentState(BossState.HANGING_DORMANT);
                }
                return;
            }
        }

        target = this.getTarget();
        if (target == null) return;

        double dist = target.distanceToSqr(this);

        // Too close → summon minions + shockwave
        if (dist < CLOSE_RANGE * CLOSE_RANGE && attackCooldown <= 0) {
            setCurrentState(BossState.HANGING_SUMMON);
            attackCooldown = 160; // 8 second cooldown for summon
            return;
        }

        // Use curse attack (long animation) when curse cooldown is ready
        if (curseCooldown <= 0 && stateTimer > 100) {
            setCurrentState(BossState.HANGING_CURSE);
            curseCooldown = 300; // 15 second cooldown for curse
            currentCurseVariant = this.random.nextInt(2);
            return;
        }

        // Regular scream attack
        if (attackCooldown <= 0 && stateTimer > 60) {
            setCurrentState(BossState.HANGING_SCREAM);
            attackCooldown = 80; // 4 second cooldown
            return;
        }

        // Face the target
        this.lookAt(target, 30f, 30f);
    }

    private void tickHangingScream() {
        // hang_attack animation is ~1.5s (30 ticks)
        if (stateTimer == 5) {
            performScreamAttack();
        }
        if (stateTimer >= 30) {
            setCurrentState(BossState.HANGING_IDLE);
        }
    }

    private void tickHangingCurse() {
        int duration = (currentCurseVariant == 0) ? 130 : 160; // curse1: 6.5s, curse2: 8s

        if (stateTimer == 30) {
            performCurseAttack();
        }
        // Additional curse pulses
        if (currentCurseVariant == 1 && stateTimer == 70) {
            performCurseAttack();
        }

        if (stateTimer >= duration) {
            setCurrentState(BossState.HANGING_IDLE);
        }
    }

    private void tickHangingSummon() {
        // hang_summon animation is ~4s (80 ticks)
        if (stateTimer == 40) {
            // Summon minions
            summonMinions();
        }
        if (stateTimer == 50) {
            // Shockwave
            performShockwave();
        }
        if (stateTimer >= 80) {
            setCurrentState(BossState.HANGING_IDLE);
        }
    }

    // ============ PHASE 2 STATE TICKS ============

    private void tickFalling() {
        // fall animation is ~13s (260 ticks)
        this.setNoGravity(false);

        if (stateTimer == 10) {
            this.playSound(SoundEvents.CHAIN_BREAK, 1.5f, 0.5f);
        }
        if (stateTimer == 60) {
            this.playSound(SoundEvents.GENERIC_BIG_FALL, 2.0f, 0.5f);
            performShockwave();
        }

        if (stateTimer >= 260) {
            this.setNoGravity(false);
            this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.35D);
            setCurrentState(BossState.GROUND_IDLE);
        }
    }

    private void tickGroundIdle() {
        if (stateTimer > 40) {
            LivingEntity target = this.getTarget();
            if (target == null || !target.isAlive()) {
                Player nearest = this.level().getNearestPlayer(this, 32.0);
                if (nearest != null) {
                    this.setTarget(nearest);
                }
            }
            // Start crawling toward target
            setCurrentState(BossState.CRAWLING);
        }
    }

    private void tickCrawling() {
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) {
            setCurrentState(BossState.GROUND_IDLE);
            return;
        }

        double dist = target.distanceToSqr(this);

        // If close enough, start pounce sequence
        if (dist < 25.0 && attackCooldown <= 0) {
            this.pounceCount = 0;
            setCurrentState(BossState.POUNCE);
            return;
        }

        // Move toward target
        this.getNavigation().moveTo(target, 1.0D);
        this.lookAt(target, 30f, 30f);
    }

    private void tickPounce() {
        // ground_attack is ~1.5s (30 ticks)
        LivingEntity target = this.getTarget();

        if (stateTimer == 10 && target != null) {
            // Leap toward target
            Vec3 dir = target.position().subtract(this.position()).normalize();
            this.setDeltaMovement(dir.x * 1.5, 0.2, dir.z * 1.5);
            this.hurtMarked = true; // Mark for velocity update
        }
        if (stateTimer == 18 && target != null && this.distanceToSqr(target) < 4.0) {
            // Hit target if close
            target.hurt(this.damageSources().mobAttack(this), 12.0f);
            this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.5f);
        }

        // After pounce animation
        if (stateTimer >= 30) {
            pounceCount++;
            // Check if we should continue pouncing or jump back
            if (pounceCount >= MIN_POUNCES && (pounceCount >= MAX_POUNCES || this.random.nextFloat() < 0.4f)) {
                // Jump back!
                setCurrentState(BossState.JUMP_BACK);
                return;
            }
            // Continue pouncing
            attackCooldown = 15; // Short delay between pounces
            setCurrentState(BossState.CRAWLING);
        }
    }

    private void tickJumpBack() {
        // jump_back animation is ~1.5s (30 ticks)
        if (stateTimer == 5) {
            // Jump backward from target
            LivingEntity target = this.getTarget();
            if (target != null) {
                Vec3 dir = this.position().subtract(target.position()).normalize();
                this.setDeltaMovement(dir.x * 1.2, 0.35, dir.z * 1.2);
                this.hurtMarked = true;
            }
        }

        // After jump back, do scream attack
        if (stateTimer >= 30) {
            setCurrentState(BossState.GROUND_SCREAM);
        }
    }

    private void tickGroundScream() {
        // ground_scream_attack is ~2s (40 ticks)
        if (stateTimer == 10) {
            performScreamAttack();
        }
        if (stateTimer >= 40) {
            attackCooldown = 60; // Cooldown before next cycle
            setCurrentState(BossState.CRAWLING);
        }
    }

    // ============ COMBAT ACTIONS ============

    private void performScreamAttack() {
        this.playSound(SoundEvents.WARDEN_SONIC_BOOM, 2.0f, 1.0f);
        LivingEntity target = this.getTarget();

        // Sonic-boom style area damage
        Vec3 lookVec = this.getLookAngle();
        Vec3 start = this.getEyePosition();

        for (int i = 0; i < 15; i++) {
            Vec3 point = start.add(lookVec.scale(i * 0.8));
            // Damage entities in a line
            AABB box = new AABB(point.x - 0.8, point.y - 0.8, point.z - 0.8,
                    point.x + 0.8, point.y + 0.8, point.z + 0.8);

            for (LivingEntity entity : this.level().getEntitiesOfClass(LivingEntity.class, box)) {
                if (entity == this || entity instanceof PaleMinionEntity) continue;
                entity.hurt(this.damageSources().sonicBoom(this), 8.0f);
            }

            // Particle effect (handled client-side via entity event)
            this.level().broadcastEntityEvent(this, (byte) 20);
        }

        if (target != null && target.distanceToSqr(this) < 100.0) {
            target.hurt(this.damageSources().sonicBoom(this), 10.0f);
        }
    }

    private void performCurseAttack() {
        this.playSound(SoundEvents.EVOKER_CAST_SPELL, 2.0f, 0.3f);
        LivingEntity target = this.getTarget();

        if (target instanceof Player player) {
            // Apply negative effects
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0));

            // Damage
            player.hurt(this.damageSources().magic(), 6.0f);

            // Visual effect
            this.level().broadcastEntityEvent(this, (byte) 25);
        }

        // Area curse
        AABB area = this.getBoundingBox().inflate(16.0);
        for (LivingEntity entity : this.level().getEntitiesOfClass(LivingEntity.class, area)) {
            if (entity == this || entity instanceof PaleMinionEntity) continue;
            if (entity instanceof Player p && p == target) continue;
            if (entity.distanceToSqr(this) < 36.0) { // 6 block radius
                entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 0));
            }
        }
    }

    private void summonMinions() {
        this.playSound(SoundEvents.EVOKER_PREPARE_SUMMON, 2.0f, 0.5f);

        int count = 3 + this.random.nextInt(3); // 3-5 minions
        for (int i = 0; i < count; i++) {
            PaleMinionEntity minion = ModEntities.PALE_MINION.get().create(this.level());
            if (minion != null) {
                // Spawn around the boss
                double angle = (2 * Math.PI / count) * i;
                double dx = Math.cos(angle) * 4.0;
                double dz = Math.sin(angle) * 4.0;
                minion.setPos(this.getX() + dx, this.getY() - 2, this.getZ() + dz);
                minion.setTarget(this.getTarget());

                this.level().addFreshEntity(minion);
            }
        }
    }

    private void performShockwave() {
        this.playSound(SoundEvents.GENERIC_EXPLODE, 2.0f, 0.5f);

        // Damage + knockback in a radius
        AABB area = this.getBoundingBox().inflate(8.0, 4.0, 8.0);
        List<LivingEntity> entities = this.level().getEntitiesOfClass(LivingEntity.class, area,
                e -> e != this && !(e instanceof PaleMinionEntity));

        for (LivingEntity entity : entities) {
            Vec3 pushDir = entity.position().subtract(this.position()).normalize();
            entity.setDeltaMovement(pushDir.x * 2.5, 1.2, pushDir.z * 2.5);
            entity.hurtMarked = true;
            entity.hurt(this.damageSources().explosion(this, this), 8.0f);
        }

        this.level().broadcastEntityEvent(this, (byte) 30);
    }

    // ============ AI Goals ============
    @Override
    protected void registerGoals() {
        // Phase 2 movement goal (only active during ground phase)
        this.goalSelector.addGoal(1, new BossCombatGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true,
                p -> !p.isCreative() && !p.isSpectator()));
    }

    /**
     * Custom goal that handles boss movement during Phase 2 crawling
     */
    private static class BossCombatGoal extends Goal {
        private final KuxuezheEntity boss;

        public BossCombatGoal(KuxuezheEntity boss) {
            this.boss = boss;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return boss.isPhaseTwo() && (boss.getCurrentState() == BossState.CRAWLING || boss.getCurrentState() == BossState.GROUND_IDLE);
        }

        @Override
        public void tick() {
            LivingEntity target = boss.getTarget();
            if (target != null) {
                boss.getNavigation().moveTo(target, 1.1D);
                boss.getLookControl().setLookAt(target, 30f, 30f);
            }
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse() && boss.getTarget() != null;
        }
    }

    // ============ General Overrides ============

    @Override
    protected SoundEvent getAmbientSound() {
        BossState state = getCurrentState();
        if (state == BossState.HANGING_DORMANT) {
            return null; // Silent when dormant
        }
        return switch (state) {
            case HANGING_SCREAM -> SoundEvents.WARDEN_SONIC_BOOM;
            case HANGING_CURSE -> SoundEvents.EVOKER_CAST_SPELL;
            case GROUND_SCREAM -> SoundEvents.WARDEN_SONIC_BOOM;
            default -> SoundEvents.WARDEN_AMBIENT;
        };
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WARDEN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WARDEN_DEATH;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return this.isAlive();
    }

    @Override
    public void setTarget(LivingEntity target) {
        super.setTarget(target);
        if (target != null && getCurrentState() == BossState.HANGING_DORMANT) {
            setCurrentState(BossState.HANGING_WAKING);
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        // Client-side particle effects based on entity event ID
        switch (id) {
            case 10 -> spawnWakeupParticles();
            case 20 -> spawnScreamParticles();
            case 25 -> spawnCurseParticles();
            case 30 -> spawnShockwaveParticles();
            default -> super.handleEntityEvent(id);
        }
    }

    private void spawnWakeupParticles() {
        if (this.level().isClientSide) {
            for (int i = 0; i < 5; i++) {
                this.level().addParticle(
                        net.minecraft.core.particles.ParticleTypes.SOUL,
                        this.getX() + (this.random.nextDouble() - 0.5) * 2.0,
                        this.getEyeY() + this.random.nextDouble(),
                        this.getZ() + (this.random.nextDouble() - 0.5) * 2.0,
                        0, 0.05, 0);
            }
        }
    }

    private void spawnScreamParticles() {
        if (this.level().isClientSide) {
            Vec3 look = this.getLookAngle();
            for (int i = 0; i < 3; i++) {
                this.level().addParticle(
                        net.minecraft.core.particles.ParticleTypes.SONIC_BOOM,
                        this.getX() + look.x * i * 2,
                        this.getEyeY(),
                        this.getZ() + look.z * i * 2,
                        0, 0, 0);
            }
        }
    }

    private void spawnCurseParticles() {
        if (this.level().isClientSide) {
            for (int i = 0; i < 30; i++) {
                this.level().addParticle(
                        net.minecraft.core.particles.ParticleTypes.WITCH,
                        this.getX() + (this.random.nextDouble() - 0.5) * 10.0,
                        this.getY() + this.random.nextDouble() * 5.0,
                        this.getZ() + (this.random.nextDouble() - 0.5) * 10.0,
                        0, 0, 0);
            }
        }
    }

    private void spawnShockwaveParticles() {
        if (this.level().isClientSide) {
            for (int i = 0; i < 40; i++) {
                double angle = this.random.nextDouble() * Math.PI * 2;
                double radius = this.random.nextDouble() * 6.0;
                this.level().addParticle(
                        net.minecraft.core.particles.ParticleTypes.EXPLOSION,
                        this.getX() + Math.cos(angle) * radius,
                        this.getY() + 0.5,
                        this.getZ() + Math.sin(angle) * radius,
                        0, 0.1, 0);
            }
        }
    }

    @Override
    public MobType getMobType() {
        return MobType.UNDEAD;
    }

    @Override
    public boolean causeFallDamage(float pFallDistance, float pMultiplier, DamageSource pSource) {
        return false; // Boss is immune to fall damage
    }
}
