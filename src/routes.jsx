import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import Layout from './components/Layout/Layout';
import useAuthStore from './store/useAuthStore';

const LoginPage = React.lazy(() => import('./pages/LoginPage'));
const RegisterPage = React.lazy(() => import('./pages/RegisterPage'));
const DashboardPage = React.lazy(() => import('./pages/DashboardPage'));
const PreferencesPage = React.lazy(() => import('./pages/PreferencesPage'));
const NotificationHistoryPage = React.lazy(() => import('./pages/NotificationHistoryPage'));
const TemplatesPage = React.lazy(() => import('./pages/TemplatesPage'));
const EventSenderPage = React.lazy(() => import('./pages/EventSenderPage'));
const ProfilePage = React.lazy(() => import('./pages/ProfilePage'));

function PrivateRoute({ children, roles }) {
    const user = useAuthStore((state) => state.user);
    if (!user) return <Navigate to="/login" replace />;
    if (roles && !roles.includes(user.role)) return <Navigate to="/dashboard" replace />;
    return children;
}

export default function AppRoutes() {
    return (
        <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route path="/" element={<Layout />}>
                <Route index element={<Navigate to="/dashboard" replace />} />
                <Route path="dashboard" element={<PrivateRoute><DashboardPage /></PrivateRoute>} />
                <Route path="preferences" element={<PrivateRoute><PreferencesPage /></PrivateRoute>} />
                <Route path="notifications" element={<PrivateRoute><NotificationHistoryPage /></PrivateRoute>} />
                <Route path="profile" element={<PrivateRoute><ProfilePage /></PrivateRoute>} />
                <Route
                    path="admin/templates"
                    element={
                        <PrivateRoute roles={['ADMIN']}>
                            <TemplatesPage />
                        </PrivateRoute>
                    }
                />
                <Route
                    path="admin/events"
                    element={
                        <PrivateRoute roles={['ADMIN']}>
                            <EventSenderPage />
                        </PrivateRoute>
                    }
                />
            </Route>
        </Routes>
    );
}