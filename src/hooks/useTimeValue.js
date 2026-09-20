import dayjs from 'dayjs';

export function timeToString(time) {
  if (!time) return null;
  if (typeof time === 'string') return time;
  return time.format('HH:mm');
}

export function stringToTime(str) {
  if (!str) return null;
  return dayjs(str, 'HH:mm');
}