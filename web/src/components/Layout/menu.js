import {
    BellOutlined,
    DashboardOutlined,
    FileTextOutlined,
    SendOutlined,
    SettingOutlined,
    UserOutlined,
} from '@ant-design/icons';

/**
 * 全站唯一的导航来源：Sidebar、Header 的横向导航、移动端抽屉都从这里派生。
 * 原先三处各写一份，抽屉只有 3 项 ⇒ 手机上的管理员从菜单里进不去模板/事件页。
 *
 * roles 必须与 routes.jsx 的 PrivateRoute 一致，否则菜单会把用户导向一个跳回 dashboard 的路由。
 * inHeader 只给「日常三件事」：桌面端管理员另有 Sidebar 承载 6 项，横向导航再放 6 项只会挤到横向滚动。
 */
export const MENU_ITEMS = [
    { key: '/dashboard', icon: DashboardOutlined, id: 'menu.dashboard', roles: ['ADMIN', 'USER'], inHeader: true },
    { key: '/notifications', icon: BellOutlined, id: 'menu.history', roles: ['ADMIN', 'USER'], inHeader: true },
    { key: '/preferences', icon: SettingOutlined, id: 'menu.preferences', roles: ['ADMIN', 'USER'], inHeader: true },
    { key: '/profile', icon: UserOutlined, id: 'menu.profile', roles: ['ADMIN', 'USER'] },
    { key: '/admin/templates', icon: FileTextOutlined, id: 'menu.templates', roles: ['ADMIN'] },
    { key: '/admin/events', icon: SendOutlined, id: 'menu.events', roles: ['ADMIN'] },
];

export const visibleMenu = (role) => MENU_ITEMS.filter((item) => item.roles.includes(role));

export const headerMenu = (role) => visibleMenu(role).filter((item) => item.inHeader);
