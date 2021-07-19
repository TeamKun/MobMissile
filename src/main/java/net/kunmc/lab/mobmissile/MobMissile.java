package net.kunmc.lab.mobmissile;

import com.destroystokyo.paper.ParticleBuilder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MobMissile extends JavaPlugin {
    public static JavaPlugin INSTANCE;
    private static final String metadataKey = "firedMob";

    @Override
    public void onEnable() {
        INSTANCE = this;

        new BukkitRunnable() {
            @Override
            public void run() {
                List<Player> playerList = new ArrayList<>(Bukkit.getOnlinePlayers());
                Collections.shuffle(playerList);
                playerList.forEach(p -> {
                    if (p.getGameMode().equals(GameMode.CREATIVE) || p.getGameMode().equals(GameMode.SPECTATOR)) {
                        return;
                    }

                    Bukkit.selectEntities(p, "@e[type=!player,distance=1..50]").forEach(entity -> {
                        if (entity.hasMetadata(metadataKey)) {
                            return;
                        }

                        if (!(entity instanceof LivingEntity)) {
                            return;
                        }

                        if (((LivingEntity) entity).hasLineOfSight(p)) {
                            entity.setMetadata(metadataKey, new FixedMetadataValue(INSTANCE, null));
                            new FiringTask(((LivingEntity) entity), p).runTaskTimerAsynchronously(INSTANCE, 0, 1);
                        }
                    });
                });
            }
        }.runTaskTimer(INSTANCE, 0, 10);
    }

    @Override
    public void onDisable() {
        Bukkit.selectEntities(Bukkit.getConsoleSender(), "@e").forEach(e -> e.removeMetadata(metadataKey, INSTANCE));
    }

    private static class FiringTask extends BukkitRunnable {
        LivingEntity missile;
        LivingEntity target;
        Location launchPoint;
        int count;

        public FiringTask(LivingEntity missileEntity, LivingEntity targetEntity) {
            this.missile = missileEntity;
            this.target = targetEntity;
            this.launchPoint = missile.getLocation().clone();
        }

        @Override
        public void run() {
            if (missile.isDead()) {
                this.cancel();
                return;
            }

            if (target instanceof Player) {
                Player p = ((Player) target);
                if (p.getGameMode().equals(GameMode.CREATIVE) || p.getGameMode().equals(GameMode.SPECTATOR)) {
                    missile.removeMetadata(metadataKey, INSTANCE);
                    this.cancel();
                    return;
                }
            }

            Location currentLocation = missile.getLocation();
            Location targetLocation = target.getEyeLocation();

            if (count < 25) {
                missile.setVelocity(new Vector(0.0, 1.0, 0.0));
                count++;
            } else {
                Vector vector = targetLocation.clone().subtract(currentLocation).toVector();
                vector = vector.multiply(1.0 / vector.length());
                missile.setVelocity(vector);
            }

            new BukkitRunnable() {
                @Override
                public void run() {
                    new ParticleBuilder(Particle.SMOKE_LARGE)
                            .count(4)
                            .extra(0.03)
                            .location(currentLocation)
                            .spawn();
                }
            }.runTaskLater(INSTANCE, 1);

            if (currentLocation.distance(targetLocation) < 0.5) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        currentLocation.createExplosion(1.0F);
                    }
                }.runTask(INSTANCE);
                missile.remove();
                this.cancel();
            }
        }
    }
}