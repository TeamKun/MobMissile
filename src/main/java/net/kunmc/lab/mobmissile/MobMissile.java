package net.kunmc.lab.mobmissile;

import com.destroystokyo.paper.ParticleBuilder;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class MobMissile extends JavaPlugin implements Listener {
    public static JavaPlugin INSTANCE;
    private final String metadataKey = "firedMob";
    private BukkitTask mainTask = null;
    private double range = 50.0;
    private double speed = 1.0;
    private float power = 1.0F;

    @Override
    public void onEnable() {
        INSTANCE = this;
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        Bukkit.selectEntities(Bukkit.getConsoleSender(), "@e").forEach(e -> e.removeMetadata(metadataKey, INSTANCE));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length < 1) {
            return false;
        }

        switch (args[0]) {
            case "start":
                if (mainTask != null) {
                    sender.sendMessage(ChatColor.RED + "MobMissileは既に実行されています.");
                    break;
                }

                mainTask = new MainTask().runTaskTimer(INSTANCE, 0, 10);
                sender.sendMessage(ChatColor.GREEN + "MobMissileを開始しました.");
                break;
            case "stop":
                if (mainTask == null) {
                    sender.sendMessage(ChatColor.RED + "MobMissileは実行されていません.");
                    break;
                }

                mainTask.cancel();
                mainTask = null;
                sender.sendMessage(ChatColor.GREEN + "MobMissileを停止しました.");
                break;
            case "config":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "usage:\n" +
                            "/mobmissile config show \n" +
                            "/mobmissile config set <item> <value>");
                    break;
                }

                switch (args[1]) {
                    case "show":
                        sender.sendMessage(String.format(ChatColor.GREEN + "range=%f, speed=%f, power=%f", range, speed, power));
                        break;
                    case "set":
                        if (args.length < 4) {
                            sender.sendMessage(ChatColor.RED + "usage: /mobmissile config set <item> <value>");
                            break;
                        }

                        switch (args[2]) {
                            case "range":
                                try {
                                    range = Double.parseDouble(args[3]);
                                    sender.sendMessage(ChatColor.GREEN + "rangeの値を" + range + "に設定しました.");
                                } catch (NumberFormatException e) {
                                    sender.sendMessage(ChatColor.RED + "不正な値です.double値を入力してください.");
                                }
                                break;
                            case "speed":
                                try {
                                    speed = Double.parseDouble(args[3]);
                                    sender.sendMessage(ChatColor.GREEN + "speedの値を" + speed + "に設定しました.");
                                } catch (NumberFormatException e) {
                                    sender.sendMessage(ChatColor.RED + "不正な値です.double値を入力してください.");
                                }
                                break;
                            case "power":
                                try {
                                    power = Float.parseFloat(args[3]);
                                    sender.sendMessage(ChatColor.GREEN + "powerの値を" + power + "に設定しました.");
                                } catch (NumberFormatException e) {
                                    sender.sendMessage(ChatColor.RED + "不正な値です.double値を入力してください.");
                                }
                                break;
                            default:
                                sender.sendMessage(ChatColor.RED + "不明な設定項目です.");
                        }
                        break;
                    default:
                        sender.sendMessage(ChatColor.RED + "不明なコマンドです.");
                }
                break;
            default:
                sender.sendMessage(ChatColor.RED + "不明なコマンドです.");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> list = Collections.emptyList();
        if (args.length == 1) {
            list = Arrays.asList("start", "stop", "config");
        }

        if (args.length == 2 && args[0].equals("config")) {
            list = Arrays.asList("show", "set");
        }

        if (args.length == 3 && args[1].equals("set")) {
            list = Arrays.asList("range", "speed", "power");
        }

        if (args.length == 4 && args[1].equals("set")) {
            list = Collections.singletonList("<DoubleValue>");
        }

        return list.stream().filter(x -> x.startsWith(args[args.length - 1])).collect(Collectors.toList());
    }

    private class MainTask extends BukkitRunnable {
        @Override
        public void run() {
            List<Player> playerList = new ArrayList<>(Bukkit.getOnlinePlayers());
            Collections.shuffle(playerList);
            playerList.forEach(p -> {
                if (p.getGameMode().equals(GameMode.CREATIVE) || p.getGameMode().equals(GameMode.SPECTATOR)) {
                    return;
                }

                Bukkit.selectEntities(p, String.format("@e[type=!player,distance=1..%f]", range)).forEach(entity -> {
                    if (entity.hasMetadata(metadataKey)) {
                        return;
                    }

                    if (!(entity instanceof LivingEntity)) {
                        return;
                    }

                    if (((LivingEntity) entity).hasLineOfSight(p)) {
                        entity.setMetadata(metadataKey, new FixedMetadataValue(INSTANCE, null));
                        new FiringTask(((LivingEntity) entity), p).runTaskTimerAsynchronously(INSTANCE, 0, 0);
                    }
                });
            });
        }
    }

    private class FiringTask extends BukkitRunnable {
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

            if (!currentLocation.getWorld().equals(targetLocation.getWorld())) {
                missile.removeMetadata(metadataKey, INSTANCE);
                this.cancel();
                return;
            }

            if (count < 25 / speed) {
                missile.setVelocity(new Vector(0.0, speed, 0.0));
                count++;
            } else {
                Vector vector = targetLocation.clone().subtract(currentLocation).toVector();
                vector = vector.multiply(speed / vector.length());
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

            if (currentLocation.distance(targetLocation) < speed * 1) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        currentLocation.createExplosion(power);
                    }
                }.runTask(INSTANCE);

                if (!(missile instanceof Wither)) {
                    missile.remove();
                    this.cancel();
                }
            }
        }
    }

    @EventHandler
    public void onEntityDamaged(EntityDamageEvent e) {
        if (!e.getEntity().hasMetadata(metadataKey)) {
            return;
        }

        if (e.getCause().equals(EntityDamageEvent.DamageCause.FALL)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onSnowballHit(ProjectileHitEvent e) {
        Entity entity = e.getHitEntity();
        if (entity == null) {
            return;
        }

        if (!entity.hasMetadata(metadataKey)) {
            return;
        }

        entity.getLocation().createExplosion(power);
        entity.remove();
    }
}