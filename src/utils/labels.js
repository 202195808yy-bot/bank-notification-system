import { CHANNELS, EVENT_TYPES, NOTIFICATION_LOCALES, REASONS, STATUS_MAP } from './constants';

const GROUPS = {
    eventType: EVENT_TYPES,
    channel: CHANNELS,
    status: STATUS_MAP,
    reason: REASONS,
    // 模板语言：语料里是 enum.locale.<code>，展示名带语言后缀
    locale: Object.fromEntries(NOTIFICATION_LOCALES.map((code) => [code, code])),
};

export function enumLabel(intl, group, key) {
    if (!key) return '-';
    return intl.formatMessage({
        id: `enum.${group}.${key}`,
        defaultMessage: GROUPS[group]?.[key] ?? key,
    });
}

export function enumOptions(intl, group) {
    return Object.keys(GROUPS[group] ?? {}).map((value) => ({
        value,
        label: enumLabel(intl, group, value),
    }));
}
