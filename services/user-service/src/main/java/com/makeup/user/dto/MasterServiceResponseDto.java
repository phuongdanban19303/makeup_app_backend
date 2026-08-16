package com.makeup.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MasterServiceResponseDto {
    private Long id;
    private String categoryName;
    private String serviceName;
    private String description;
    private ZonedDateTime createdAt;
}
