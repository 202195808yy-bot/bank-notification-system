import { useEffect } from 'react';
import { IntlProvider } from 'react-intl';
import messages from './messages';

const LANG_ATTR = { ru: 'ru', zh: 'zh-CN', en: 'en' };

// localStorage 是用户可写的，而本项目的"语言"有两套拼法：界面用 ru/zh/en，
// 通知模板与后端枚举用 zh_CN/ru_RU/en_US。一旦把后者写进这个键（或个人中心改了它），
// IntlProvider 会对非法语言标记抛 RangeError，整页白屏；所以这里只认白名单，其余回落 ru。
export const resolveLocale = () => {
    const stored = localStorage.getItem('locale');
    return Object.prototype.hasOwnProperty.call(LANG_ATTR, stored) ? stored : 'ru';
};

const locale = resolveLocale();

/**
 * React 树之外取文案：axios 拦截器、登录/注册表单都拿不到 useIntl，
 * 而它们又都必须能把后端的错误码译成当前语言，所以这三处共用下面这一份实现
 * （以前拦截器自带一份 `t`，两个表单各写一份"读 data.message"，于是俄文界面弹出了中文报错）。
 */
export const messageText = (id) => {
    const bundle = messages[resolveLocale()] || messages.ru;
    return bundle[id];
};

/** 后端业务错误的 {code} 契约 → 当前语言的文案；没有对应键就返回 undefined 让调用方兜底 */
export const errorText = (code) => (code ? messageText(`error.code.${code}`) : undefined);

export default function I18nProvider({ children }) {
    const bundle = messages[locale] || messages.ru;

    // index.html 里的 <title>/<lang> 只是首屏兜底，真实值随语言在运行时覆写
    useEffect(() => {
        document.title = bundle['app.title'];
        document.documentElement.lang = LANG_ATTR[locale] || locale;
    }, [bundle, locale]);

    return (
        <IntlProvider locale={locale} messages={bundle}>
            {children}
        </IntlProvider>
    );
}