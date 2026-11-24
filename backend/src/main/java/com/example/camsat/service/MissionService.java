package com.example.camsat.service;

import com.example.camsat.domain.Mission;
import com.example.camsat.dto.MissionRequest;
import com.example.camsat.dto.MissionResponse;
import com.example.camsat.repository.MissionRepository;
import com.example.camsat.repository.SimulationRunRepository;
import com.example.camsat.web.ApiException;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MissionService {
    private final MissionRepository missions;
    private final SimulationRunRepository runs;
    private final Clock clock;

    public MissionService(MissionRepository missions, SimulationRunRepository runs, Clock clock) {
        this.missions = missions; this.runs = runs; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<MissionResponse> list(long userId, String search) {
        List<Mission> found = (search == null || search.isBlank())
                ? missions.findByUserIdOrderByCreatedAtDescIdDesc(userId)
                : missions.search(userId, search.trim());
        return found.stream().map(MissionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public MissionResponse get(long userId, long id) {
        return MissionResponse.from(missions.findByIdAndUserId(id, userId).orElseThrow(() -> notFound(id)));
    }

    @Transactional
    public MissionResponse create(long userId, MissionRequest req) {
        Mission m = new Mission(userId, clock.instant());
        apply(m, req);
        return MissionResponse.from(missions.save(m));
    }

    @Transactional
    public MissionResponse update(long userId, long id, MissionRequest req) {
        Mission m = missions.lockByIdAndUserId(id, userId).orElseThrow(() -> notFound(id));
        if (runs.existsByMissionId(id)) {
            throw ApiException.conflict("This mission already has simulation runs and can no longer be edited.");
        }
        apply(m, req);
        m.setUpdatedAt(clock.instant());
        return MissionResponse.from(missions.save(m));
    }

    @Transactional
    public void delete(long userId, long id) {
        Mission m = missions.lockByIdAndUserId(id, userId).orElseThrow(() -> notFound(id));
        if (runs.existsByMissionId(id)) {
            throw ApiException.conflict("This mission already has simulation runs and cannot be deleted.");
        }
        missions.delete(m);
    }

    private static void apply(Mission m, MissionRequest r) {
        m.setName(r.name().trim());
        m.setPayloadMassKg(r.payloadMassKg());
        m.setReleaseAltitudeM(r.releaseAltitudeM());
        m.setParachuteDiameterM(r.parachuteDiameterM());
        m.setDragCoefficient(r.dragCoefficient());
        m.setWindSpeedMps(r.windSpeedMps());
        m.setWindDirectionDeg(r.windDirectionDeg());
        m.setInitialBatteryPercent(r.initialBatteryPercent());
    }

    private static ApiException notFound(long id) { return ApiException.notFound("Mission " + id + " was not found."); }
}
