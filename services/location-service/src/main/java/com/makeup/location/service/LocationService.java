package com.makeup.location.service;

import com.makeup.common.response.ApiResponse;
import com.makeup.location.client.UserClient;
import com.makeup.location.dto.MuaSummaryDto;
import com.makeup.location.dto.NearbyWorkerDto;
import com.makeup.location.dto.WorkerLocationStreamDto;
import com.makeup.location.entity.MuaLocationHistoryEntity;
import com.makeup.location.repository.LocationHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.data.redis.domain.geo.GeoShape;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationService {

    public static final String REDIS_GEO_KEY = "mua_realtime_locations";

    private final StringRedisTemplate redisTemplate;
    private final LocationHistoryRepository locationHistoryRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserClient userClient;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    /**
     * Xóa thông tin vị trí thợ khỏi Redis GEO khi thợ ấn "Tắt hoạt động" (Go
     * Offline).
     */
    public void removeWorkerLocation(Long workerId) {
        if (workerId == null) {
            log.warn("Cannot remove null workerId from Redis GEO");
            return;
        }
        redisTemplate.opsForZSet().remove(REDIS_GEO_KEY, workerId.toString());
        log.info("Successfully removed workerId [{}] from Redis GEO key '{}'", workerId, REDIS_GEO_KEY);
    }

    /**
     * Tiếp nhận stream tọa độ GPS từ Thợ (MUA) ứng dụng ONLINE.
     * 1. Cập nhật vị trí realtime vào Redis GEO key mua_realtime_locations bằng
     * GEOADD.
     * 2. Đẩy tọa độ GPS thời gian thực qua WebSocket tới Customer App để vẽ đường
     * đi trên bản đồ.
     * 3. Gọi phương thức bất đồng bộ (@Async) để ghi nhật ký di chuyển vào
     * PostgreSQL.
     */
    public void processLocationStream(WorkerLocationStreamDto dto) {
        if (dto == null || dto.getWorkerId() == null) {
            log.warn("Received invalid location stream data");
            return;
        }

        // 1. Cập nhật vị trí thời gian thực vào Redis GEO bằng GEOADD
        redisTemplate.opsForGeo().add(
                REDIS_GEO_KEY,
                new Point(dto.getLongitude(), dto.getLatitude()),
                dto.getWorkerId().toString());
        log.debug("GEOADD updated Redis key '{}' for workerId: {}, lat: {}, lng: {}",
                REDIS_GEO_KEY, dto.getWorkerId(), dto.getLatitude(), dto.getLongitude());

        // 2. Đẩy stream tọa độ GPS thời gian thực tới WebSocket Destination cho Khách
        // theo dõi trên bản đồ
        if (dto.getBookingId() != null && !dto.getBookingId().isBlank()) {
            String bookingTopic = "/topic/booking/" + dto.getBookingId() + "/location";
            messagingTemplate.convertAndSend(bookingTopic, Map.of(
                    "type", "MUA_LOCATION_UPDATE",
                    "workerId", dto.getWorkerId(),
                    "bookingId", dto.getBookingId(),
                    "latitude", dto.getLatitude(),
                    "longitude", dto.getLongitude(),
                    "timestamp", dto.getTimestamp() != null ? dto.getTimestamp() : System.currentTimeMillis()));
            log.debug("Broadcast live GPS tracking to WebSocket topic [{}]", bookingTopic);
        }

        // 3. Lưu bản ghi nhật ký bất đồng bộ vào PostgreSQL
        saveLocationHistoryAsync(dto);
    }

    /**
     * Ghi nhận vết lịch sử di chuyển vào cơ sở dữ liệu PostgreSQL
     * (mua_location_history)
     * Bất đồng bộ (@Async) để không gây nghẽn luồng xử lý WebSocket.
     */
    @Async
    public void saveLocationHistoryAsync(WorkerLocationStreamDto dto) {
        try {
            org.locationtech.jts.geom.Point jtsPoint = geometryFactory.createPoint(
                    new Coordinate(dto.getLongitude(), dto.getLatitude()));

            MuaLocationHistoryEntity historyEntity = MuaLocationHistoryEntity.builder()
                    .muaId(dto.getWorkerId())
                    .bookingId(dto.getBookingId())
                    .location(jtsPoint)
                    .recordedAt(ZonedDateTime.now())
                    .build();

            locationHistoryRepository.save(historyEntity);
            log.debug("Async location track history saved to PostgreSQL for workerId: {}", dto.getWorkerId());
        } catch (Exception e) {
            log.error("Error saving async location history for workerId: {}", dto.getWorkerId(), e);
        }
    }

    /**
     * Tìm kiếm danh sách thợ trang điểm gần nhất trong bán kính radiusKm (km) bằng
     * GEOSEARCH của Redis GEO.
     * Tích hợp gọi Batch Query từ user-service để bổ sung thông tin profile (tên,
     * rating, tổng số đơn, trạng thái).
     */
    public List<NearbyWorkerDto> findNearbyWorkers(double latitude, double longitude, double radiusKm) {
        RedisGeoCommands.GeoSearchCommandArgs args = RedisGeoCommands.GeoSearchCommandArgs
                .newGeoSearchArgs()
                .includeDistance()
                .includeCoordinates()
                .sortAscending();

        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redisTemplate.opsForGeo().search(
                REDIS_GEO_KEY,
                GeoReference.fromCoordinate(longitude, latitude),
                GeoShape.byRadius(new Distance(radiusKm, Metrics.KILOMETERS)),
                args);

        if (results == null || results.getContent().isEmpty()) {
            return List.of();
        }

        List<Long> workerIds = new ArrayList<>();
        List<NearbyWorkerDto> nearbyWorkers = new ArrayList<>();

        for (var result : results.getContent()) {
            RedisGeoCommands.GeoLocation<String> geoLocation = result.getContent();
            Double distanceVal = result.getDistance() != null ? result.getDistance().getValue() : 0.0;

            Long workerId = Long.parseLong(geoLocation.getName());
            Point point = geoLocation.getPoint();
            workerIds.add(workerId);

            nearbyWorkers.add(NearbyWorkerDto.builder()
                    .workerId(workerId)
                    .latitude(point != null ? point.getY() : 0.0)
                    .longitude(point != null ? point.getX() : 0.0)
                    .distanceKm(Math.round(distanceVal * 100.0) / 100.0)
                    .build());
        }

        // Bổ sung thông tin chi tiết thợ từ user-service (Batch Query)
        Map<Long, MuaSummaryDto> summaryMap = fetchWorkerSummariesMap(workerIds);

        String defaultAvatar = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=300&q=80";
        for (NearbyWorkerDto dto : nearbyWorkers) {
            MuaSummaryDto summary = summaryMap.get(dto.getWorkerId());
            if (summary != null) {
                dto.setFullName(
                        summary.getFullName() != null && !summary.getFullName().isBlank() ? summary.getFullName()
                                : "Chuyên Gia MUA #" + dto.getWorkerId());
                dto.setAvatarUrl(
                        summary.getAvatarUrl() != null && !summary.getAvatarUrl().isBlank() ? summary.getAvatarUrl()
                                : defaultAvatar);
                dto.setRating(summary.getRating() != null ? summary.getRating() : 5.0);
                dto.setTotalCompletedJobs(
                        summary.getTotalCompletedJobs() != null ? summary.getTotalCompletedJobs() : 0);
                dto.setCurrentStatus(summary.getCurrentStatus() != null ? summary.getCurrentStatus() : "ONLINE");
                dto.setServices(summary.getServices() != null ? summary.getServices() : List.of());
            } else {
                dto.setFullName("Chuyên Gia MUA #" + dto.getWorkerId());
                dto.setAvatarUrl(defaultAvatar);
                dto.setRating(5.0);
                dto.setTotalCompletedJobs(0);
                dto.setCurrentStatus("ONLINE");
                dto.setServices(List.of());
            }
        }

        return nearbyWorkers;
    }

    /**
     * Tìm kiếm danh sách thợ gần nhất có lọc theo Danh mục yêu cầu (category) và Kỹ
     * năng/Sub-services cần thiết.
     */
    public List<NearbyWorkerDto> findNearbyWorkers(double latitude, double longitude, double radiusKm,
            String category, List<String> requiredSubServices) {
        List<NearbyWorkerDto> candidates = findNearbyWorkers(latitude, longitude, radiusKm);
        if (candidates.isEmpty()) {
            return candidates;
        }

        if ((category == null || category.isBlank())
                && (requiredSubServices == null || requiredSubServices.isEmpty())) {
            return candidates;
        }

        return candidates.stream().filter(worker -> {
            if (worker.getServices() == null || worker.getServices().isEmpty()) {
                return false;
            }
            return worker.getServices().stream().anyMatch(service -> {
                boolean matchCat = (category == null || category.isBlank())
                        || category.equalsIgnoreCase(service.getCategory());

                boolean matchSub = true;
                if (requiredSubServices != null && !requiredSubServices.isEmpty()) {
                    List<String> workerSubs = service.getSubServices() != null ? service.getSubServices() : List.of();
                    matchSub = requiredSubServices.stream()
                            .allMatch(req -> workerSubs.stream().anyMatch(ws -> ws.equalsIgnoreCase(req)));
                }
                return matchCat && matchSub;
            });
        }).toList();
    }

    private Map<Long, MuaSummaryDto> fetchWorkerSummariesMap(List<Long> workerIds) {
        if (workerIds == null || workerIds.isEmpty()) {
            return Map.of();
        }
        try {
            ApiResponse<List<MuaSummaryDto>> response = userClient.getMuaSummaries(workerIds);
            if (response != null && response.getData() != null) {
                return response.getData().stream()
                        .collect(Collectors.toMap(MuaSummaryDto::getUserId, s -> s, (s1, s2) -> s1));
            }
        } catch (Exception e) {
            log.error("Failed to fetch worker summaries from user-service via Feign Client: {}", e.getMessage());
        }
        return Map.of();
    }
}
