import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';

import LoginPage from './views/auth/LoginPage';
import SignUpPage from './views/auth/SignUpPage';
import VerificationPage from './views/auth/VerificationPage';
import HomePage from './views/home/HomePage';

const App = () => {
  return (
    <Router>
      <Routes>
        <Route path="/" element={<Navigate to="/login" />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignUpPage />} />
        <Route path="/verify" element={<VerificationPage />} />

        <Route path="/admin/home" element={<HomePage />} />
        <Route path="/student/home" element={<HomePage />} />
        <Route path="/teacher/home" element={<HomePage />} />
        <Route path="/staff/home" element={<HomePage />} />

        <Route path="*" element={<div>404 - Page Not Found</div>} />
      </Routes>
    </Router>
  );
};

export default App;
