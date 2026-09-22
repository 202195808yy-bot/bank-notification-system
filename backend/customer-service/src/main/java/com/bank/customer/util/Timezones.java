package com.bank.customer.util;

import java.time.DateTimeException;
import java.time.ZoneId;

/**
 * 客户时区输入的归一与校验。写库前必须验：一个拼错的 Europe/Moskow 会让该客户的
 * 免打扰时段永久失效（派发端解析失败只能回退服务端默认时区，等于悄悄丢掉客户的选择）。
 */
public final class Timezones {

    private Timezones() {
    }

    /** null / 空白 → null（未设置，派发端回退 app.business-zone）；非法 IANA 名 → 抛错，由控制器映射成 400 */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        try {
            ZoneId.of(trimmed);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("TIMEZONE_INVALID");
        }
        return trimmed;
    }
}
