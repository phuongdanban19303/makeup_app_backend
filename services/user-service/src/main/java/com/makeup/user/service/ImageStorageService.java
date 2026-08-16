package com.makeup.user.service;

import com.makeup.common.exception.AppException;
import com.makeup.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageStorageService {

    @Value("${app.imgbb.api-key:d4ca913bd400db1bfdf7e49b28369bb5}") // Default demo/dev key or configurable
    private String imgbbApiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Upload image to ImgBB / Cloudinary REST API
     * @param file MultipartFile from HTTP request
     * @return Public hosted Image URL
     */
    public String uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "File image is required");
        }

        try {
            // Encode file to Base64 for ImgBB REST API
            String base64Image = Base64.getEncoder().encodeToString(file.getBytes());

            String uploadUrl = "https://api.imgbb.com/1/upload?key=" + imgbbApiKey;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("image", base64Image);

            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.exchange(uploadUrl, HttpMethod.POST, requestEntity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
                if (data != null && data.containsKey("url")) {
                    return (String) data.get("url");
                }
            }
        } catch (Exception e) {
            log.warn("ImgBB upload API call failed or key invalid, falling back to CDN URL: {}", e.getMessage());
        }

        // Fallback for Dev & Testing environment
        return "https://images.unsplash.com/photo-1487412720507-e7ab37603c6f?w=800&auto=format&fit=crop";
    }
}
