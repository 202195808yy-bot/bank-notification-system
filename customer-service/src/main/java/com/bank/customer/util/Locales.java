package com.bank.customer.util;

import com.bank.common.constant.AppConstants;

import java.util.Locale;

/**
 * 通知正文语言的归一与校验。与 {@link Timezones} 同构：注册时采集一次、之后由客户自己改，
 * 派发端只读 customers.locale（null 才有"未设置"这一说，见 PRD-56）。
 */
public final class Locales {

    private Locales() {
    }

    /**
     * 客户在个人中心显式选的值。null = 不改（PATCH 语义由调用方处理）；
     * 空白 = 清除成"未设置"，交给派发端兜底；白名单外一律抛错，
     * 因为存成一个匹配不到模板的 locale 会让该客户所有渠道静默变成 SKIPPED/TEMPLATE_MISSING。
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (!AppConstants.SUPPORTED_LOCALES.contains(trimmed)) {
            throw new IllegalArgumentException("UNSUPPORTED_LOCALE");
        }
        return trimmed;
    }

    /**
     * 注册时从浏览器语言推断（{@code localStorage.locale} 或 {@code navigator.language}，
     * 形如 ru / ru-RU / zh-Hans-CN / en-US）。只认主标签：en_GB 落到 en_US，因为库里就只有这三本语料，
     * 选"最接近的一本"比"因为拼法不同而什么都不写"更接近客户当下的实际状态。
     * 认不出来返回 null —— 注册不该因为一个语言标签的写法而失败。
     */
    public static String fromBrowser(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (AppConstants.SUPPORTED_LOCALES.contains(trimmed)) {
            return trimmed;
        }
        String primary = trimmed.toLowerCase(Locale.ROOT).split("[-_]")[0];
        switch (primary) {
            case "ru":
                return "ru_RU";
            case "zh":
                return "zh_CN";
            case "en":
                return "en_US";
            default:
                return null;
        }
    }
}
