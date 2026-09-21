import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { ConfigProvider } from 'antd'
import ruRU from 'antd/locale/ru_RU'
import zhCN from 'antd/locale/zh_CN'
import enUS from 'antd/locale/en_US'
import I18nProvider from './i18n'
import App from './App'
import './index.css'

const root = ReactDOM.createRoot(document.getElementById('root'))
const locale = localStorage.getItem('locale') || 'ru'

// 缺 en_US 时选 English 会让 react-intl 文案是英文、antd 组件（分页、表格筛选、日期选择）仍是中文
const ANTD_LOCALES = { ru: ruRU, zh: zhCN, en: enUS }

root.render(
  <BrowserRouter
    future={{
      v7_startTransition: true,
      v7_relativeSplatPath: true,
    }}
  >
    <I18nProvider>
      <ConfigProvider locale={ANTD_LOCALES[locale] ?? ruRU}>
        <App />
      </ConfigProvider>
    </I18nProvider>
  </BrowserRouter>
)