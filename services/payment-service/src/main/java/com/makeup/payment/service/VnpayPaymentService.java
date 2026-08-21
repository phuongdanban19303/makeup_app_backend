package com.makeup.payment.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Service tích hợp Cổng thanh toán VNPay Sandbox v2.1.0.
 * Tạo URL thanh toán chứa chữ ký bảo mật HMAC-SHA512 và xác thực Webhook IPN Callback từ VNPay.
 */
@Slf4j
@Service
public class VnpayPaymentService {

    @Value("${payment.vnpay.tmn-code:2QX72L2E}")
    private String tmnCode;

    @Value("${payment.vnpay.hash-secret:RAK88456789012345678901234567890}")
    private String hashSecret;

    @Value("${payment.vnpay.url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}")
    private String vnpPayUrl;

    @Value("${payment.vnpay.return-url:https://muamake.duckdns.org/payment/vnpay/callback}")
    private String returnUrl;

    /**
     * Khởi tạo URL Thanh toán VNPay Sandbox v2.1.0
     * 
     * @param customerId ID khách hàng nạp tiền
     * @param amount Số tiền nạp (VNĐ)
     * @param ipAddress IP người dùng
     * @return URL điều hướng sang cổng VNPay
     */
    public String createVnpayPaymentUrl(String customerId, long amount, String ipAddress) {
        String pTmnCode = tmnCode != null ? tmnCode.trim() : "2QX72L2E";
        String pSecret = hashSecret != null ? hashSecret.trim() : "RAK88456789012345678901234567890";
        String pUrl = vnpPayUrl != null ? vnpPayUrl.trim() : "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
        String rUrl = returnUrl != null ? returnUrl.trim() : "https://muamake.duckdns.org/payment/vnpay/callback";

        String orderId = "TOPUP_VNPAY_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 6);
        long vnpAmount = amount * 100L; // VNPay quy định nhân 100 số tiền

        Map<String, String> vnpParams = new HashMap<>();
        vnpParams.put("vnp_Version", "2.1.0");
        vnpParams.put("vnp_Command", "pay");
        vnpParams.put("vnp_TmnCode", pTmnCode);
        vnpParams.put("vnp_Amount", String.valueOf(vnpAmount));
        vnpParams.put("vnp_CurrCode", "VND");
        vnpParams.put("vnp_TxnRef", orderId);
        vnpParams.put("vnp_OrderInfo", "Nap tien vi khach hang " + customerId);
        vnpParams.put("vnp_OrderType", "other");
        vnpParams.put("vnp_Locale", "vn");
        vnpParams.put("vnp_ReturnUrl", rUrl);
        vnpParams.put("vnp_IpAddr", (ipAddress != null && !ipAddress.isBlank()) ? ipAddress : "127.0.0.1");

        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        String vnpCreateDate = formatter.format(cld.getTime());
        vnpParams.put("vnp_CreateDate", vnpCreateDate);

        cld.add(Calendar.MINUTE, 15);
        String vnpExpireDate = formatter.format(cld.getTime());
        vnpParams.put("vnp_ExpireDate", vnpExpireDate);

        // Sắp xếp các tham số theo bảng chữ cái
        List<String> fieldNames = new ArrayList<>(vnpParams.keySet());
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = vnpParams.get(fieldName);
            if ((fieldValue != null) && (fieldValue.length() > 0)) {
                // Thêm vào hashData
                hashData.append(fieldName);
                hashData.append('=');
                hashData.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
                // Thêm vào query
                query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII));
                query.append('=');
                query.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
                if (itr.hasNext()) {
                    query.append('&');
                    hashData.append('&');
                }
            }
        }

        String queryUrl = query.toString();
        String vnpSecureHash = hmacSHA512(pSecret, hashData.toString());
        queryUrl += "&vnp_SecureHash=" + vnpSecureHash;

        log.info(">>> Tạo URL thanh toán VNPay Sandbox thành công cho OrderId [{}]: {}", orderId, pUrl + "?" + queryUrl);
        return pUrl + "?" + queryUrl;
    }

    /**
     * Xác thực chữ ký mã hóa HMAC-SHA512 khi VNPay gọi IPN Webhook hoặc ReturnUrl về.
     */
    public boolean verifyVnpaySignature(Map<String, String> fields) {
        String vnpSecureHash = fields.get("vnp_SecureHash");
        if (vnpSecureHash == null || vnpSecureHash.isBlank()) {
            return false;
        }

        String pSecret = hashSecret != null ? hashSecret.trim() : "RAK88456789012345678901234567890";

        Map<String, String> cleanFields = new HashMap<>(fields);
        cleanFields.remove("vnp_SecureHash");
        cleanFields.remove("vnp_SecureHashType");

        List<String> fieldNames = new ArrayList<>(cleanFields.keySet());
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = cleanFields.get(fieldName);
            if ((fieldValue != null) && (fieldValue.length() > 0)) {
                hashData.append(fieldName);
                hashData.append('=');
                hashData.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
                if (itr.hasNext()) {
                    hashData.append('&');
                }
            }
        }

        String expectedHash = hmacSHA512(pSecret, hashData.toString());
        log.info(">>> Kiểm tra chữ ký VNPay: Kỳ vọng [{}], Nhận được [{}]", expectedHash, vnpSecureHash);
        return expectedHash.equalsIgnoreCase(vnpSecureHash);
    }

    /**
     * Mã hóa HMAC-SHA512 tiêu chuẩn của VNPay v2.1.0
     */
    public static String hmacSHA512(final String key, final String data) {
        try {
            if (key == null || data == null) {
                return null;
            }
            final Mac hmac512 = Mac.getInstance("HmacSHA512");
            byte[] hmacKeyBytes = key.getBytes(StandardCharsets.UTF_8);
            final SecretKeySpec secretKey = new SecretKeySpec(hmacKeyBytes, "HmacSHA512");
            hmac512.init(secretKey);
            byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
            byte[] result = hmac512.doFinal(dataBytes);
            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception ex) {
            log.error("Lỗi mã hóa HMAC-SHA512 VNPay", ex);
            return "";
        }
    }
}
