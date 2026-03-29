package it.patric.cittaexp.custommobs;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class CustomMobRuntimeScheduler {

    public interface TaskHandle {
        void cancel();

        boolean cancelled();
    }

    private record BukkitTaskHandle(BukkitTask task) implements TaskHandle {
        @Override
        public void cancel() {
            task.cancel();
        }

        @Override
        public boolean cancelled() {
            return task.isCancelled();
        }
    }

    private final Plugin plugin;

    public CustomMobRuntimeScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    public TaskHandle scheduleRepeating(long delayTicks, long periodTicks, Runnable runnable) {
        return new BukkitTaskHandle(Bukkit.getScheduler().runTaskTimer(plugin, runnable, delayTicks, periodTicks));
    }

    public TaskHandle scheduleLater(long delayTicks, Runnable runnable) {
        return new BukkitTaskHandle(Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks));
    }
}
