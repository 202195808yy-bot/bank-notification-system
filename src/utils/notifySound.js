const STORAGE_KEY = 'notification_sound_enabled';

export const isSoundEnabled = () => localStorage.getItem(STORAGE_KEY) !== 'off';

export const setSoundEnabled = (enabled) => {
  localStorage.setItem(STORAGE_KEY, enabled ? 'on' : 'off');
};

let audioContext = null;

const getContext = () => {
  const Ctor = window.AudioContext || window.webkitAudioContext;
  if (!Ctor) return null;
  if (!audioContext) audioContext = new Ctor();
  // 浏览器自动播放策略：上下文在用户首次交互前是 suspended，恢复失败就只能静音
  if (audioContext.state === 'suspended') audioContext.resume().catch(() => {});
  return audioContext;
};

/**
 * 两声上行短音，用振荡器合成而不是音频文件：部署时不用带资产，也不受 nginx 静态路径影响。
 */
export const playNotifySound = () => {
  if (!isSoundEnabled()) return;
  const ctx = getContext();
  if (!ctx) return;
  const start = ctx.currentTime;
  [
    { freq: 880, at: 0 },
    { freq: 1320, at: 0.12 },
  ].forEach(({ freq, at }) => {
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.type = 'sine';
    osc.frequency.value = freq;
    gain.gain.setValueAtTime(0.0001, start + at);
    gain.gain.exponentialRampToValueAtTime(0.08, start + at + 0.02);
    gain.gain.exponentialRampToValueAtTime(0.0001, start + at + 0.18);
    osc.connect(gain).connect(ctx.destination);
    osc.start(start + at);
    osc.stop(start + at + 0.2);
  });
};
