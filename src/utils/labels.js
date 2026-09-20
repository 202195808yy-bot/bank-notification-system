import { CHANNELS, EVENT_TYPES, REASONS, STATUS_MAP } from './constants';

const GROUPS = {
    eventType: EVENT_TYPES,
    channel: CHANNELS,
    status: STATUS_MAP,
    reason: REASONS,
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
