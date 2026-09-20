import React, { Suspense } from 'react';
import { Spin } from 'antd';
import AppRoutes from './routes';

function App() {
    return (
        <Suspense fallback={<Spin style={{ display: 'block', margin: '200px auto' }} />}>
            <AppRoutes />
        </Suspense>
    );
}

export default App;