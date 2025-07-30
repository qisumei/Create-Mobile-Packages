package de.theidler.create_mobile_packages;

import de.theidler.create_mobile_packages.entities.robo_entity.RoboEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class RoboManager {
    private final Map<UUID, RoboEntity> robos = new ConcurrentHashMap<>();
    private final Map<UUID, RoboEntity> clientRobos = new ConcurrentHashMap<>();
    private final List<RoboEntity> robosToAdd = Collections.synchronizedList(new ArrayList<>());
    
    private RoboManagerSavedData savedData;
    private Level level;
    
    // Reusable consumer for tick operations
    private final Consumer<RoboEntity> roboTickConsumer = robo -> {
        if (robo != null && !robo.isRemoved()) {
            level.guardEntityTick(entity -> {}, robo);
            robo.roboMangerTick();
        }
    };

    public void markDirty() {
        if (savedData != null) {
            savedData.setDirty();
        }
    }

    public void tick(Level level) {
        if (level.dimension() != Level.OVERWORLD) return;

        this.level = level; // Update level reference
        processRoboTicks();
    }

    private void processRoboTicks() {
        // Process pending additions first
        addPendingRobos();
        
        // Process ticks for all active robos
        tickActiveRobos();
        
        // Clean up removed robos
        removeMarkedRobos();
    }

    private void addPendingRobos() {
        if (robosToAdd.isEmpty()) return;
        
        // Minimize synchronization by working on a copy
        List<RoboEntity> newRobos;
        synchronized (robosToAdd) {
            newRobos = new ArrayList<>(robosToAdd);
            robosToAdd.clear();
        }
        
        for (RoboEntity robo : newRobos) {
            if (robo != null) {
                (robo.level().isClientSide() ? clientRobos : robos).put(robo.getUUID(), robo);
            }
        }
    }

    private void tickActiveRobos() {
        // Process server-side robos
        robos.values().forEach(roboTickConsumer);
        
        // Process client-side robos if needed
        if (!clientRobos.isEmpty()) {
            clientRobos.values().forEach(roboTickConsumer);
        }
    }

    private void removeMarkedRobos() {
        robos.values().removeIf(robo -> robo == null || robo.isRemoved());
        clientRobos.values().removeIf(robo -> robo == null || robo.isRemoved());
    }

    public void addRobo(RoboEntity robo) {
        if (robo != null) {
            robosToAdd.add(robo);
        }
    }

    public Level getLevel() {
        return level;
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    public void levelLoaded(LevelAccessor level) {
        if (!(level instanceof Level)) return;
        
        this.level = (Level) level;
        MinecraftServer server = level.getServer();
        
        if (server == null || server.overworld() != level) {
            savedData = null;
            return;
        }
        
        // Only load data if we don't have it already
        if (savedData == null) {
            savedData = RoboManagerSavedData.load(server);
            robos.putAll(savedData.getRobos());
        }
    }
}
