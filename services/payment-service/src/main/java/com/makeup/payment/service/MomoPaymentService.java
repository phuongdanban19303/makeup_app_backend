package com.makeup.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service tích hợp Cổng thanh toán MoMo THẬT (MoMo Payment Gateway v2 API).
 * Thực hiện gửi HTTP POST Request trực tiếp sang máy chủ MoMo để nhận `payUrl`, `deeplink`, `qrCodeUrl`.
 * Nhận và kiểm tra tính hợp lệ của chữ ký HMAC-SHA256 khi MoMo gọi Webhook IPN Callback về.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MomoPaymentService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${payment.momo.partner-code:MOMO}")
    private String partnerCode;

    @Value("${payment.momo.access-key:F8BBA842ECF85}")
    private String accessKey;

    @Value("${payment.momo.secret-key:K951B6PE1wa8ngfBWja1mi1jnmWbmPDg}")
    private String secretKey;

    @Value("${payment.momo.endpoint:https://test-payment.momo.vn/v2/gateway/api/create}")
    private String endpoint;

    @Value("${payment.momo.redirect-url:http://localhost:3000/payment/momo/callback}")
    private String redirectUrl;

    @Value("${payment.momo.ipn-url:http://localhost:8085/api/v1/wallets/webhook/momo}")
    private String ipnUrl;

    /**
     * GỬI REQUEST THẬT sang máy chủ MoMo Gateway API v2 (https://test-payment.momo.vn/v2/gateway/api/create).
     * 
     * @param customerId ID Khách hàng
     * @param amount Số tiền nạp (VNĐ)
     * @return Map chứa kết quả trả về THẬT từ MoMo (bao gồm payUrl, deeplink, qrCodeUrl, resultCode)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> createMoMoTopUpRequest(String customerId, long amount) {
        String orderId = "TOPUP_MOMO_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 6);
        String requestId = UUID.randomUUID().toString();
        String orderInfo = "Nap tien vi khach hang " + customerId;
        String requestType = "captureWallet";
        String extraData = "customerId=" + customerId;

        // BƯỚC 1: Tạo chuỗi Raw Signature theo định dạng bắt buộc của MoMo v2
        String rawSignature = "accessKey=" + accessKey +
                "&amount=" + amount +
                "&extraData=" + extraData +
                "&ipnUrl=" + ipnUrl +
                "&orderId=" + orderId +
                "&orderInfo=" + orderInfo +
                "&partnerCode=" + partnerCode +
                "&redirectUrl=" + redirectUrl +
                "&requestId=" + requestId +
                "&requestType=" + requestType;

        // BƯỚC 2: Ký số chữ ký điện tử HMAC-SHA256
        String signature = hmacSha256(rawSignature, secretKey);

        // BƯỚC 3: Đóng gói JSON Payload gửi sang MoMo
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("partnerCode", partnerCode);
        requestBody.put("partnerName", "MakeupApp Platform");
        requestBody.put("storeId", "MakeupApp");
        requestBody.put("requestId", requestId);
        requestBody.put("amount", amount);
        requestBody.put("orderId", orderId);
        requestBody.put("orderInfo", orderInfo);
        requestBody.put("redirectUrl", redirectUrl);
        requestBody.put("ipnUrl", ipnUrl);
        requestBody.put("lang", "vi");
        requestBody.put("extraData", extraData);
        requestBody.put("requestType", requestType);
        requestBody.put("signature", signature);

        log.info(">>> Gửi HTTP POST Request THẬT tới MoMo Endpoint [{}] cho OrderId [{}]", endpoint, orderId);

        try {
            // BƯỚC 4: Gửi HTTP POST Request thực tế sang server MoMo
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> responseEntity = restTemplate.postForEntity(endpoint, entity, Map.class);
            Map<String, Object> responseMap = responseEntity.getBody();

            log.info(">>> Nhận phản hồi THẬT từ MoMo Gateway: {}", responseMap);

            if (responseMap != null) {
                Object resultCodeObj = responseMap.get("resultCode");
                if (resultCodeObj != null && !Integer.valueOf(0).equals(resultCodeObj) && !"0".equals(String.valueOf(resultCodeObj))) {
                    String momoErrMsg = String.valueOf(responseMap.getOrDefault("message", "Lỗi chữ ký hoặc tham số MoMo"));
                    log.error(">>> [ERROR] MoMo Gateway trả về lỗi resultCode [{}]: {}", resultCodeObj, momoErrMsg);
                    throw new RuntimeException("Cổng MoMo trả về lỗi (code " + resultCodeObj + "): " + momoErrMsg);
                }
                return responseMap;
            } else {
                throw new RuntimeException("Phản hồi từ cổng thanh toán MoMo rỗng!");
            }
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.error(">>> [ERROR] MoMo API Gateway HTTP Status: {}, Response Body: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Cổng thanh toán MoMo trả về lỗi (HTTP " + e.getStatusCode() + "): " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error(">>> [ERROR] Lỗi kết nối HTTP POST tới MoMo Payment Gateway!", e);
            throw new RuntimeException("Lỗi khởi tạo giao dịch MoMo: " + e.getMessage(), e);
        }
    }

    /**
     * BƯỚC XÁC THỰC THẬT: Kiểm tra chữ ký HMAC-SHA256 khi MoMo gọi Webhook IPN Callback về Backend.
     */
    public boolean verifyMoMoSignature(Map<String, String> webhookPayload) {
        try {
            String receivedSignature = webhookPayload.get("signature");
            if (receivedSignature == null || receivedSignature.isBlank()) {
                return false;
            }

            String amount = webhookPayload.getOrDefault("amount", "");
            String extraData = webhookPayload.getOrDefault("extraData", "");
            String message = webhookPayload.getOrDefault("message", "");
            String orderId = webhookPayload.getOrDefault("orderId", "");
            String orderInfo = webhookPayload.getOrDefault("orderInfo", "");
            String orderType = webhookPayload.getOrDefault("orderType", "");
            String partnerCodePayload = webhookPayload.getOrDefault("partnerCode", "");
            String requestId = webhookPayload.getOrDefault("requestId", "");
            String responseTime = webhookPayload.getOrDefault("responseTime", "");
            String resultCode = webhookPayload.getOrDefault("resultCode", "");
            String transId = webhookPayload.getOrDefault("transId", "");

            // Chuỗi Raw Signature của MoMo IPN Callback
            String rawSignature = "accessKey=" + accessKey +
                    "&amount=" + amount +
                    "&extraData=" + extraData +
                    "&message=" + message +
                    "&orderId=" + orderId +
                    "&orderInfo=" + orderInfo +
                    "&orderType=" + orderType +
                    "&partnerCode=" + partnerCodePayload +
                    "&requestId=" + requestId +
                    "&responseTime=" + responseTime +
                    "&resultCode=" + resultCode +
                    "&transId=" + transId;

            String expectedSignature = hmacSha256(rawSignature, secretKey);
            log.info(">>> Kiểm tra Chữ ký Webhook MoMo: Kỳ vọng [{}], Nhận được [{}]", expectedSignature, receivedSignature);

            return expectedSignature.equalsIgnoreCase(receivedSignature);
        } catch (Exception e) {
            log.error("Lỗi khi kiểm tra chữ ký Webhook MoMo", e);
            return false;
        }
    }

    /**
     * Hàm tính mã mã hóa HMAC-SHA256 tiêu chuẩn.
     */
    private String hmacSha256(String data, String key) {
        try {
            Mac hmacSha256 = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hmacSha256.init(secretKeySpec);
            byte[] hash = hmacSha256.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi tính mã HMAC SHA256", e);
        }
    }
}
