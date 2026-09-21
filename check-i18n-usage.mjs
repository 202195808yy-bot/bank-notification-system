import fs from 'fs'
import path from 'path'

const SRC = 'src'
const msg = fs.readFileSync(path.join(SRC, 'i18n/messages.js'), 'utf8')

// 语料里出现过的全部 key
const corpusKeys = new Set([...msg.matchAll(/^\s{4}'([^']+)':/gm)].map((m) => m[1]))

// 源码里引用过的全部 id
const used = new Map()
const walk = (dir) => {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name)
    if (e.isDirectory()) { if (p !== path.join(SRC, 'i18n')) walk(p); continue }
    if (!/\.jsx?$/.test(p)) continue
    const text = fs.readFileSync(p, 'utf8')
    for (const m of text.matchAll(/id:\s*'([^']+)'/g)) used.set(m[1], p)
    for (const m of text.matchAll(/\{\s*id:\s*`([^`]+)`/g)) used.set(m[1].replace(/\$\{[^}]*\}/g, '*'), p)
    for (const m of text.matchAll(/formatMessage\(\s*'([^']+)'/g)) used.set(m[1], p)
    // 模板串拼接：`enum.${group}.${key}` 这类交给 defaultMessage 兜底，只记录前缀
  }
}
walk(SRC)

const missing = [...used].filter(([id]) => !id.includes('*') && !corpusKeys.has(id))
console.log('corpus keys =', corpusKeys.size, '| referenced ids =', used.size)
console.log('--- 源码引用但语料缺失 ---')
for (const [id, file] of missing) console.log('MISSING', id, '←', file)
if (!missing.length) console.log('none')

// 语料里从未被引用的 key（只统计明确的静态 id，跳过动态前缀）
const prefixes = new Set([...used.keys()].filter((k) => k.includes('*')))
const orphans = [...corpusKeys].filter((k) => !used.has(k) && ![...prefixes].some((p) => k.startsWith(p.split('*')[0])))
console.log('--- 语料存在但源码未静态引用（可能是死文案/动态拼接）---', orphans.length)
console.log(orphans.slice(0, 40).join('\n'))
