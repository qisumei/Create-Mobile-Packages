package de.theidler.create_mobile_packages;

import de.theidler.create_mobile_packages.entities.robo_entity.RoboEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RoboManager {

    public Map<UUID, RoboEntity> robos;
    public Map<UUID, RoboEntity> clientRobos;
    public List<RoboEntity> robosToAdd;

    private RoboManagerSavedData savedData;
    private Level level;

    private static final int MAX_TICKS_PER_TICK = 10; // 每 tick 最多处理多少个机器人
    private int roboTickIndex = 0; // 分批处理索引

    public RoboManager() {
        cleanUp();
    }

    public void markDirty() {
        if (savedData != null)
            savedData.setDirty();
    }

    public void tick(Level level) {
        if (level.dimension() != Level.OVERWORLD)
            return;

        tickRobos(level);
    }

    private void tickRobos(Level level) {
        addPendingRobos();
        tickExistingRobos(level);
        removeMarkedRobos();
    }

    private void addPendingRobos() {
        if (robosToAdd.isEmpty()) return;

        List<RoboEntity> newRobos = new ArrayList<>(robosToAdd);
        for (RoboEntity robo : newRobos) {
            if (robo.level().isClientSide()) {
                clientRobos.put(robo.getUUID(), robo);
            } else {
                robos.put(robo.getUUID(), robo);
            }
        }
        robosToAdd.removeAll(newRobos);
    }

    private void tickExistingRobos(Level level) {
        int size = robos.size();
        if (size == 0 && clientRobos.isEmpty()) return;

        List<RoboEntity> roboList = new ArrayList<>(robos.values());
        int processed = 0;
        for (int i = 0; i < size && processed < MAX_TICKS_PER_TICK; i++) {
            if (roboList.isEmpty()) break;
            int index = (roboTickIndex + i) % roboList.size();
            RoboEntity robo = roboList.get(index);
            if (robo == null || robo.isRemoved()) continue;
            level.guardEntityTick(entity -> {}, robo);
            robo.roboMangerTick();
            processed++;
        }
        roboTickIndex = (roboTickIndex + processed) % (size == 0 ? 1 : size);

        List<RoboEntity> clientList = new ArrayList<>(clientRobos.values());
        for (RoboEntity robo : clientList) {
            if (robo == null || robo.isRemoved()) continue;
            level.guardEntityTick(entity -> {}, robo);
            robo.roboMangerTick();
        }
    }

    private void removeMarkedRobos() {
        if (robos.isEmpty() && clientRobos.isEmpty()) return;
        robos.entrySet().removeIf(entry -> entry.getValue().isRemoved());
        clientRobos.entrySet().removeIf(entry -> entry.getValue().isRemoved());
    }

    public void addRobo(RoboEntity robo) {
        robosToAdd.add(robo);
    }

    public Level getLevel() {
        return level;
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    public void levelLoaded(LevelAccessor level) {
        this.level = (Level) level;
        MinecraftServer server = level.getServer();
        if (server == null || server.overworld() != level)
            return;
        cleanUp();
        savedData = null;
        loadRoboData(server);
    }

    private void loadRoboData(MinecraftServer server) {
        if (savedData != null)
            return;
        savedData = RoboManagerSavedData.load(server);
        robos = savedData.getRobos();
    }

    private void cleanUp() {
        this.robos = new ConcurrentHashMap<>();
        this.robosToAdd = new ArrayList<>();
        this.clientRobos = new ConcurrentHashMap<>();
    }
}
