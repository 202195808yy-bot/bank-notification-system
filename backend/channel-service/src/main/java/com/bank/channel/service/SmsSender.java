package com.bank.channel.service;

import com.bank.channel.messaging.StatusProducer;
import com.bank.channel.repository.SentLogRepository;
import com.bank.common.dto.NotificationStatus;
import com.bank.common.dto.SendCommand;
import com.bank.common.entity.SentLog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * 短信通道。两条路径（形状与 {@link EmailSender} 一致：显式开关 + 配置齐备，缺任一条都退回模拟）：
 * <ul>
 *   <li><b>真发</b>：阿里云短信服务 SendSms（RPC 签名 V1.0 / HMAC-SHA1），用 JDK 自带的
 *       HttpClient + javax.crypto 实现，<b>不引第三方 SDK</b>（离线构建拉不到新依赖，
 *       而且这个接口本身只是一个带签名的 GET）。</li>
 *   <li><b>模拟</b>：默认路径，按 {@code mock.failure-rate} 随机失败。</li>
 * </ul>
 *
 * ⚠️ 阿里云的硬约束（不是本类能绕过的）：短信正文必须走<b>已审核的模板 + 变量</b>，
 * 不能发任意文本。所以真发时把已渲染好的通知正文塞进模板的单个文本变量
 * （变量名由 {@code sms.content-variable} 配，默认 {@code content}，
 * 对应模板文案形如「您的账户通知：${content}」）。签名与模板都要先在阿里云控制台审核通过。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsSender implements ChannelSender {

    private static final String PROVIDER_MOCK = "aliyun-sms-mock";
    private static final String PROVIDER_REAL = "aliyun-sms";
    private static final String API_VERSION = "2017-05-25";
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private final SentLogRepository sentLogRepository;
    private final StatusProducer statusProducer;
    /** 读 {@code sms.event-template.<eventType>}：每个事件类型对应阿里云哪个已审核模板 */
    private final Environment environment;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${mock.failure-rate:0.1}")
    private double failureRate;

    /** 显式开关：配了 AK 也不一定真要发（演示/回归时故意不发） */
    @Value("${sms.real-send:false}")
    private boolean realSend;

    @Value("${sms.endpoint:https://dysmsapi.aliyuncs.com}")
    private String endpoint;

    @Value("${sms.access-key-id:}")
    private String accessKeyId;

    @Value("${sms.access-key-secret:}")
    private String accessKeySecret;

    /** 阿里云控制台审核通过的短信签名，如「某某银行」 */
    @Value("${sms.sign-name:}")
    private String signName;

    /** 阿里云控制台审核通过的模板 CODE，如 SMS_123456789 */
    @Value("${sms.template-code:}")
    private String templateCode;

    @Value("${sms.region-id:cn-hangzhou}")
    private String regionId;

    /** 模板里承载正文的变量名，必须与模板文案里的 ${...} 一致 */
    @Value("${sms.content-variable:content}")
    private String contentVariable;

    /**
     * 单个模板变量的长度上限，超了<b>直接不发</b>并给出 {@code SMS_CONTENT_TOO_LONG}。
     * <p>20 不是随手写的：实测该阿里云模板对 {@code ${content}} 返回
     * {@code isv.PARAM_LENGTH_LIMIT（参数超过长度限制:20）}。
     * <p>为什么不做截断：静默把「余额 8840.00」砍成「余额 884」是拿错的金额去通知客户，
     * 比一条明确的失败更糟；要么改短模板文案，要么换多变量模板。
     */
    @Value("${sms.max-variable-length:20}")
    private int maxVariableLength;

    @Value("${sms.timeout-ms:8000}")
    private int timeoutMs;

    @Override
    @CircuitBreaker(name = "smsSender", fallbackMethod = "sendFallback")
    public void send(SendCommand command) {
        if (!useReal()) {
            if (realSend) {
                log.warn("sms.real-send=true 但 AK/签名/模板未配齐，本条退回模拟发送: notificationId={}",
                        command.getNotificationId());
            }
            if (failureRate > 0 && Math.random() < failureRate) {
                throw new ProviderRejectException("SMS_MOCK_RANDOM_FAIL", "短信发送失败（模拟随机失败）");
            }
            // ⚠️ 这里的 SENT 是"模拟成功"，不是"投递成功"。不带原因码时，它与真发在界面上完全同形，
            // 于是"手机没收到但历史显示已发送"变成一个无法自证的 bug（本轮用户实测就是撞在这条上）。
            report(command.getNotificationId(), PROVIDER_MOCK, null, "SMS sent", "SENT", "SMS_MOCK_SEND");
            return;
        }
        sendReal(command);
    }

    private void sendReal(SendCommand command) {
        String phone = normalizePhone(command.getRecipient());
        if (phone == null) {
            // 号码非法时绝不假装 SENT：这是 PRD-13（库里存着 "1" 这类号码）在真发路径上的兜底
            throw new ProviderRejectException("SMS_RECIPIENT_INVALID",
                    "收件号码非法，无法发送短信: " + command.getRecipient());
        }
        String mapped = mappedTemplateCode(command.getEventType());
        String smsTemplateCode = mapped.isEmpty() ? templateCode : mapped;
        String templateParam = buildTemplateParam(command, !mapped.isEmpty());
        try {
            String url = signedUrl(phone, smsTemplateCode, templateParam);
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(timeoutMs))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode body = parse(response.body());
            String code = body.path("Code").asText("");
            String message = body.path("Message").asText("");
            if (!"OK".equals(code)) {
                // 不回显任何凭据；Code 进原因码，Message 原文进流水
                log.error("短信真实投递被网关拒绝: notificationId={}, http={}, code={}, message={}",
                        command.getNotificationId(), response.statusCode(), code, message);
                throw new ProviderRejectException(gatewayReason(code),
                        "短信网关拒绝: " + (code.isEmpty() ? "HTTP " + response.statusCode() : code)
                                + (message.isEmpty() ? "" : "（" + message + "）")).withRequest(templateParam);
            }
            log.info("短信已真实投递: notificationId={}, template={}, bizId={}",
                    command.getNotificationId(), smsTemplateCode, body.path("BizId").asText(""));
            report(command.getNotificationId(), PROVIDER_REAL, templateParam,
                    "SMS sent, bizId=" + body.path("BizId").asText(""), "SENT", null);
        } catch (IOException | InterruptedException | GeneralSecurityException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("短信真实投递失败: notificationId={}, cause={}", command.getNotificationId(), e.toString());
            throw new ProviderRejectException("SMS_GATEWAY_UNAVAILABLE",
                    "短信发送失败: " + ChannelSender.describe(e), e).withRequest(templateParam);
        }
    }

    /** {@code sms.event-template.<eventType>}（Spring 规范形是 kebab-case 小写）；未配置返回空串 */
    private String mappedTemplateCode(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return "";
        }
        // RISK_ALERT → risk-alert：yml 里的键与环境变量 SMS_EVENT_TEMPLATE_RISK_ALERT 都归一到这一形
        String key = eventType.trim().toLowerCase().replace('_', '-');
        return environment.getProperty("sms.event-template." + key, "").trim();
    }

    /**
     * 组装阿里云的 {@code TemplateParam}（必须是 JSON 对象字符串）。
     *
     * <p>两种形状：
     * <ul>
     *   <li><b>多变量</b>（该事件类型配了专属模板）：逐个传事件载荷的变量。这是唯一能发长通知的办法
     *       —— 阿里云单变量只有 20 字，整段正文塞一个变量必然被 {@code isv.PARAM_LENGTH_LIMIT} 拒。</li>
     *   <li><b>单变量</b>（只有全局 {@code sms.template-code}）：把渲染好的正文塞进
     *       {@code sms.content-variable}，超过上限就明确失败。</li>
     * </ul>
     */
    private String buildTemplateParam(SendCommand command, boolean perEventTemplate) {
        Map<String, String> param = new LinkedHashMap<>();
        if (!perEventTemplate) {
            param.put(contentVariable, checkLength(contentVariable, command.getContent()));
            return writeJson(param);
        }
        Map<String, Object> variables = command.getVariables();
        if (variables == null || variables.isEmpty()) {
            // 重投路径拿不到载荷（notifications 只存渲染后的正文），复用上一次的请求体
            String reused = lastRequest(command.getNotificationId());
            if (reused != null && !reused.isBlank()) {
                log.info("重投复用上次模板变量: notificationId={}", command.getNotificationId());
                return reused;
            }
            throw new ProviderRejectException("SMS_TEMPLATE_PARAM_MISSING",
                    "重投时既无载荷也无可复用的历史请求体: eventType=" + command.getEventType());
        }
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            if (entry.getValue() != null) {
                param.put(entry.getKey(), checkLength(entry.getKey(), entry.getValue()));
            }
        }
        return writeJson(param);
    }

    private String checkLength(String name, Object value) {
        String text = String.valueOf(value);
        if (maxVariableLength > 0 && text.length() > maxVariableLength) {
            throw new ProviderRejectException("SMS_CONTENT_TOO_LONG",
                    "短信模板变量 " + name + " 长 " + text.length() + " 字，超过模板上限 "
                            + maxVariableLength + " 字，未发送");
        }
        return text;
    }

    private String writeJson(Map<String, String> param) {
        try {
            return objectMapper.writeValueAsString(param);
        } catch (IOException e) {
            throw new ProviderRejectException("SMS_SEND_FAILED", "模板变量序列化失败: " + e.getMessage(), e);
        }
    }

    private String lastRequest(Long notificationId) {
        return sentLogRepository.findFirstByNotificationIdAndRequestIsNotNullOrderByIdDesc(notificationId)
                .map(SentLog::getRequest)
                .orElse(null);
    }

    /**
     * 阿里云 SendSms 的 {@code Code} → 稳定的原因码。
     *
     * <p>只按码匹配、不按 Message 匹配：Message 是英文描述、阿里云会改措辞，
     * 而码是契约。未识别的码归入 {@code SMS_GATEWAY_REJECTED}，原文仍在 {@code sent_logs.response}。
     */
    private String gatewayReason(String code) {
        if (code == null || code.isBlank()) {
            return "SMS_GATEWAY_UNAVAILABLE";
        }
        return switch (code) {
            case "isv.AMOUNT_NOT_ENOUGH" -> "SMS_AMOUNT_NOT_ENOUGH";
            case "isv.SMS_SIGNATURE_ILLEGAL", "isv.SIGN_NAME_ILLEGAL", "isv.SMS_SIGNATURE_SCENE_ILLEGAL" ->
                    "SMS_SIGNATURE_INVALID";
            case "isv.SMS_TEMPLATE_ILLEGAL" -> "SMS_TEMPLATE_INVALID";
            case "isv.TEMPLATE_MISSING_PARAMETERS" -> "SMS_TEMPLATE_PARAM_MISSING";
            case "isv.PARAM_LENGTH_LIMIT" -> "SMS_CONTENT_TOO_LONG";
            case "isv.MOBILE_NUMBER_ILLEGAL", "isv.MOBILE_COUNT_OVER_LIMIT" -> "SMS_RECIPIENT_INVALID";
            case "isv.BUSINESS_LIMIT_CONTROL", "isv.DAY_LIMIT_CONTROL", "Throttling.User" -> "SMS_RATE_LIMITED";
            case "SYSTEM_ERROR" -> "SMS_GATEWAY_UNAVAILABLE";
            default -> {
                // 凭据类（AK 不存在/被禁用/签名不对/账号异常）措辞分散，按关键字收口
                if (code.contains("AccessKey") || code.contains("Signature") || code.contains("Authentication")
                        || code.startsWith("isv.ACCOUNT") || code.startsWith("Forbidden")
                        || code.startsWith("Insecure")) {
                    yield "SMS_CREDENTIAL_INVALID";
                }
                if (code.startsWith("isv.SMS_SIGNATURE") || code.contains("SIGN_NAME")) {
                    yield "SMS_SIGNATURE_INVALID";
                }
                if (code.contains("TEMPLATE")) {
                    yield "SMS_TEMPLATE_INVALID";
                }
                // ⚠️ 这里不能写 code.contains("LIMIT")：实测 isv.PARAM_LENGTH_LIMIT 是"变量超长"，
                // 与限流无关，被它兜住会给出完全错误的提示。限流类一律走上面的精确分支。
                yield "SMS_GATEWAY_REJECTED";
            }
        };
    }

    /** 状态回调与流水一次写，避免两条路径各写一遍而漏掉其中一个 */
    private void report(Long notificationId, String provider, String request, String response,
                        String status, String reasonCode) {
        saveLog(notificationId, provider, request, response, status);
        NotificationStatus callback = "SENT".equals(status)
                ? NotificationStatus.success(notificationId, provider, response, reasonCode)
                : NotificationStatus.failure(notificationId, provider, response, reasonCode);
        statusProducer.send(callback);
    }

    private void sendFallback(SendCommand command, Throwable t) {
        String provider = useReal() ? PROVIDER_REAL : PROVIDER_MOCK;
        String reasonCode = t instanceof ProviderRejectException reject
                ? reject.getReasonCode()
                : "SMS_SEND_FAILED";
        String request = t instanceof ProviderRejectException reject ? reject.getRequest() : null;
        report(command.getNotificationId(), provider, request, t.getMessage(), "FAILED", reasonCode);
    }

    /** 真发 = 开关打开 且 AK/签名/模板四项都非空白；任一不满足都走模拟，绝不为配置缺失给客户留一条 FAILED */
    private boolean useReal() {
        return realSend && notBlank(accessKeyId) && notBlank(accessKeySecret)
                && notBlank(signName) && notBlank(templateCode);
    }

    /**
     * 阿里云要求 E.164（不带 + 的国内号码要补 86）。库里现在存着 "1"、"  " 这类脏值，
     * 所以这里既是规范化也是校验。
     */
    private String normalizePhone(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.trim().replaceAll("[\\s\\-()]", "");
        if (digits.isEmpty()) {
            return null;
        }
        if (digits.startsWith("00")) {
            digits = digits.substring(2);
        }
        String candidate = digits.startsWith("+") ? digits.substring(1) : digits;
        if (!candidate.matches("\\d{6,15}")) {
            return null;
        }
        // 11 位 1 开头按中国大陆号码补国家码；其余按调用方已带国家码处理
        return candidate.matches("1\\d{10}") ? "86" + candidate : candidate;
    }

    /** 组装阿里云 RPC 风格的签名请求 URL */
    private String signedUrl(String phone, String smsTemplateCode, String templateParam)
            throws IOException, GeneralSecurityException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("Action", "SendSms");
        params.put("Version", API_VERSION);
        params.put("RegionId", regionId);
        params.put("PhoneNumbers", phone);
        params.put("SignName", signName);
        params.put("TemplateCode", smsTemplateCode);
        params.put("TemplateParam", templateParam);
        // 公共参数
        params.put("Format", "JSON");
        params.put("SignatureMethod", "HMAC-SHA1");
        params.put("SignatureVersion", "1.0");
        params.put("SignatureNonce", UUID.randomUUID().toString());
        params.put("AccessKeyId", accessKeyId);
        params.put("Timestamp", TIMESTAMP.format(java.time.Instant.now()));

        String canonical = canonicalize(new TreeMap<>(params));
        String stringToSign = "GET&" + percentEncode("/") + "&" + percentEncode(canonical);
        String signature = hmacSha1(accessKeySecret + "&", stringToSign);
        // 签名本身也要 percent-encode 一次才能进 query
        return endpoint + "/?" + canonical + "&Signature=" + percentEncode(signature);
    }

    private JsonNode parse(String body) throws IOException {
        if (body == null || body.isBlank()) {
            throw new IOException("短信网关返回空响应体");
        }
        return objectMapper.readTree(body);
    }

    private String canonicalize(TreeMap<String, String> sorted) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(percentEncode(entry.getKey())).append('=').append(percentEncode(entry.getValue()));
        }
        return sb.toString();
    }

    /** 阿里云的 percent-encode 与 URLEncoder 有三处差异（空格、*、~），必须逐条修正 */
    private String percentEncode(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }

    private String hmacSha1(String key, String data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private void saveLog(Long notificationId, String provider, String request, String response, String status) {
        SentLog sentLog = new SentLog();
        sentLog.setNotificationId(notificationId);
        sentLog.setProvider(provider);
        // request 以前是死列（全库 0 行非空）。多变量模板下它是重投唯一能恢复变量的地方。
        sentLog.setRequest(request);
        sentLog.setResponse(response);
        sentLog.setStatus(status);
        sentLog.setSentAt(LocalDateTime.now());
        sentLogRepository.save(sentLog);
    }
}
