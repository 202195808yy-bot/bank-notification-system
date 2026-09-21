import messages from './src/i18n/messages.js';

const langs = Object.keys(messages);
const base = messages.ru;
let bad = 0;
for (const lang of langs) {
  const keys = Object.keys(messages[lang]);
  console.log(lang, 'keys =', keys.length);
  const missing = Object.keys(base).filter((k) => !(k in messages[lang]));
  const extra = keys.filter((k) => !(k in base));
  if (missing.length || extra.length) {
    bad = 1;
    console.log('  missing: ' + JSON.stringify(missing));
    console.log('  extra:   ' + JSON.stringify(extra));
  }
}
console.log(bad ? 'PARITY FAIL' : 'PARITY OK');
