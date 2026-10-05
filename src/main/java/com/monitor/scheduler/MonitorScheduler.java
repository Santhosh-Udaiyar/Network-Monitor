package com.monitor.scheduler;

import com.monitor.model.Host;
import com.monitor.model.MonitorResult;
import com.monitor.monitor.HostMonitor;
import com.monitor.util.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages concurrent asynchronous periodic execution of network checks across configured hosts.
 * Utilizes a ScheduledExecutorService for pacing and a FixedThreadPool for parallel execution.
 */
public class MonitorScheduler {

    public interface SchedulerListener {
        void onHostChecked(Host host, MonitorResult result);
    }

    private final List<Host> hosts = new CopyOnWriteArrayList<>();
    private final HostMonitor hostMonitor;

    private final ScheduledExecutorService timerService;
    private final ExecutorService workerPool;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);

    private final List<SchedulerListener> listeners = new CopyOnWriteArrayList<>();

    private int checkIntervalSeconds = 5;

    public MonitorScheduler(List<Host> initialHosts, int workerPoolSize) {
        if (initialHosts != null) {
            this.hosts.addAll(initialHosts);
        }
        this.hostMonitor = new HostMonitor();

        int poolSize = Math.max(2, workerPoolSize);
        this.workerPool = Executors.newFixedThreadPool(poolSize, new NamedThreadFactory("net-worker"));
        this.timerService = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("net-scheduler"));
    }

    public MonitorScheduler() {
        this(Collections.emptyList(), Math.max(4, Runtime.getRuntime().availableProcessors()));
    }

    public void addListener(SchedulerListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(SchedulerListener listener) {
        listeners.remove(listener);
    }

    public void addAlertListener(HostMonitor.AlertListener listener) {
        hostMonitor.addAlertListener(listener);
    }

    public void removeAlertListener(HostMonitor.AlertListener listener) {
        hostMonitor.removeAlertListener(listener);
    }

    /**
     * Starts the periodic scheduling cycle.
     */
    public synchronized void start() {
        if (running.get()) {
            return;
        }
        running.set(true);
        paused.set(false);

        Logger.info("Starting Network Monitor Scheduler (interval: " + checkIntervalSeconds + "s)...");

        // Schedule periodic scanning
        timerService.scheduleWithFixedDelay(this::runPeriodicCheck, 1, checkIntervalSeconds, TimeUnit.SECONDS);
    }

    /**
     * Pauses checks without shutting down thread pools.
     */
    public void pause() {
        paused.set(true);
        Logger.info("Monitoring paused by user.");
    }

    /**
     * Resumes checks if previously paused.
     */
    public void resume() {
        paused.set(false);
        Logger.info("Monitoring resumed by user.");
    }

    public boolean isPaused() {
        return paused.get();
    }

    public boolean isRunning() {
        return running.get();
    }

    /**
     * Submits checks for all enabled hosts across the worker thread pool.
     */
    private void runPeriodicCheck() {
        if (paused.get() || !running.get()) {
            return;
        }

        for (Host host : hosts) {
            if (host.isEnabled()) {
                submitHostCheck(host);
            }
        }
    }

    /**
     * Immediately triggers check for all hosts.
     */
    public void checkAllNow() {
        Logger.info("Manual check triggered for all hosts.");
        for (Host host : hosts) {
            if (host.isEnabled()) {
                submitHostCheck(host);
            }
        }
    }

    /**
     * Submits a single host check to the thread pool immediately.
     */
    public void submitHostCheck(Host host) {
        if (workerPool.isShutdown() || workerPool.isTerminated()) {
            return;
        }

        workerPool.submit(() -> {
            try {
                MonitorResult result = hostMonitor.checkHost(host);
                notifyChecked(host, result);
            } catch (Exception e) {
                Logger.error("Unexpected error checking host " + host.getName() + ": " + e.getMessage());
            }
        });
    }

    private void notifyChecked(Host host, MonitorResult result) {
        for (SchedulerListener listener : listeners) {
            try {
                listener.onHostChecked(host, result);
            } catch (Exception e) {
                Logger.error("Error in scheduler listener: " + e.getMessage());
            }
        }
    }

    public void addHost(Host host) {
        if (host != null && !hosts.contains(host)) {
            hosts.add(host);
            Logger.info("Added new host to monitor: " + host.getName() + " (" + host.getAddress() + ")");
            // Trigger an immediate check on add
            submitHostCheck(host);
        }
    }

    public void removeHost(Host host) {
        if (host != null) {
            hosts.remove(host);
            Logger.info("Removed host from monitor: " + host.getName());
        }
    }

    public List<Host> getHosts() {
        return Collections.unmodifiableList(hosts);
    }

    public void setHosts(List<Host> newHosts) {
        hosts.clear();
        if (newHosts != null) {
            hosts.addAll(newHosts);
        }
    }

    public int getCheckIntervalSeconds() {
        return checkIntervalSeconds;
    }

    public void setCheckIntervalSeconds(int interval) {
        this.checkIntervalSeconds = Math.max(1, interval);
    }

    public HostMonitor getHostMonitor() {
        return hostMonitor;
    }

    /**
     * Gracefully stops and shuts down the thread pools.
     */
    public synchronized void shutdown() {
        Logger.info("Shutting down monitor scheduler and worker thread pools...");
        running.set(false);

        timerService.shutdown();
        workerPool.shutdown();

        try {
            if (!workerPool.awaitTermination(3, TimeUnit.SECONDS)) {
                workerPool.shutdownNow();
            }
            if (!timerService.awaitTermination(2, TimeUnit.SECONDS)) {
                timerService.shutdownNow();
            }
        } catch (InterruptedException e) {
            workerPool.shutdownNow();
            timerService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        Logger.info("Scheduler shutdown complete.");
    }

    /**
     * Custom ThreadFactory for identifiable daemon threads.
     */
    private static class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger count = new AtomicInteger(1);

        public NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + "-" + count.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }
}
