package com.makeup.location.client;

import com.makeup.common.response.ApiResponse;
import com.makeup.location.dto.MuaSummaryDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "user-service", url = "${services.user-service.url:http://localhost:8081}")
public interface UserClient {

    @PostMapping("/api/v1/mua/summaries")
    ApiResponse<List<MuaSummaryDto>> getMuaSummaries(@RequestBody List<Long> muaIds);
}
