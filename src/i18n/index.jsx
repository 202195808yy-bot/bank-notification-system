import React, { useEffect } from 'react';
import { IntlProvider } from 'react-intl';
import messages from './messages';

const LANG_ATTR = { ru: 'ru', zh: 'zh-CN', en: 'en' };

const locale = localStorage.getItem('locale') || 'ru';

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