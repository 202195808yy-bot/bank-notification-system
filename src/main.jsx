import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { ConfigProvider } from 'antd'
import ruRU from 'antd/locale/ru_RU'
import zhCN from 'antd/locale/zh_CN'
import I18nProvider from './i18n'
import App from './App'
import 'antd'
import './index.css'

const root = ReactDOM.createRoot(document.getElementById('root'))
const locale = localStorage.getItem('locale') || 'ru'

root.render(
  <BrowserRouter
    future={{
      v7_startTransition: true,
      v7_relativeSplatPath: true,
    }}
  >
    <I18nProvider>
      <ConfigProvider locale={locale === 'ru' ? ruRU : zhCN}>
        <App />
      </ConfigProvider>
    </I18nProvider>
  </BrowserRouter>
)