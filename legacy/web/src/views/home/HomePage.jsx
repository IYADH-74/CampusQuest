// src/Views/HomePage.jsx
import React, { useEffect, useState, Suspense } from 'react';
import { useNavigate } from 'react-router-dom';
import HeaderBar from '../components/HeaderBar';
import SideBar from '../components/SideBar';
import MainDashboard from '../components/MainDashboard';
import StaffMainDashboard from '../components/StaffMainDashboard';
import StaffSideBar from '../components/StaffSideBar';
import DummyScreen from '../components/DummyScreen';
import StaffClassesScreen from '../screens/staff/StaffClassesScreen';
import StaffTeachersScreen from '../screens/staff/StaffTeachersScreen';
import StaffTasksScreen from '../screens/staff/StaffTasksScreen';

const UsersScreen = React.lazy(() => import('../screens/admin/UsersScreen'));
const ClassesScreen = React.lazy(() => import('../screens/admin/ClassesScreen'));
const TasksScreen = React.lazy(() => import('../screens/admin/TasksScreen'));
const SupportScreen = React.lazy(() => import('../screens/admin/SupportScreen'));

const HomePage = () => {
  const [role, setRole] = useState('');
  const [activeScreen, setActiveScreen] = useState('dashboard');
  const navigate = useNavigate();

  useEffect(() => {
    const storedRole = localStorage.getItem('role');
    if (storedRole) {
      setRole(storedRole.replace('ROLE_', '').toLowerCase());
    }
  }, []);

  useEffect(() => {
    // Session validation: redirect if no token or after 1 hour
    const token = localStorage.getItem('jwt');
    const loginTime = localStorage.getItem('loginTime');
    if (!token) {
      alert('Unauthorized: Please log in.');
      navigate('/login');
      return;
    }
    if (!loginTime) {
      localStorage.setItem('loginTime', Date.now());
    } else {
      const now = Date.now();
      if (now - Number(loginTime) > 60 * 60 * 1000) { // 1 hour
        alert('Session expired. Please log in again.');
        localStorage.clear();
        navigate('/login');
      }
    }
  }, [navigate]);

  const handleNavigate = (screen) => {
    setActiveScreen(screen);
  };

  const comingSoonStyle = {
    background: 'rgba(35,36,58,0.95)',
    borderRadius: 18,
    padding: '48px',
    margin: '48px',
    color: '#fff',
    fontSize: 28,
    textAlign: 'center',
    boxShadow: '0 2px 12px #0002',
  };

  return (
    <div style={{
      minHeight: '100vh',
      background: 'linear-gradient(135deg, #23243a 0%, #3a3456 100%)',
      color: '#fff',
      fontFamily: 'Segoe UI, Arial, sans-serif',
      letterSpacing: 0.2,
      width: '100vw',
      minWidth: 0,
      boxSizing: 'border-box',
      overflowX: 'hidden',
    }}>
      <h1 style={{ fontWeight: 700, fontSize: 'clamp(1.5rem, 4vw, 2.5rem)', margin: '0 0 24px 0', letterSpacing: 1, textShadow: '0 2px 8px #0004', textAlign: 'center' }}>
        Welcome {role ? role.toUpperCase() : 'User'}
      </h1>

      {role === 'admin' && (
        <div style={{ display: 'flex', flexDirection: 'column', minHeight: '100vh', background: 'none', width: '100vw', minWidth: 0 }}>
          <HeaderBar />
          <div style={{ display: 'flex', flex: 1, background: 'none', width: '100vw', minWidth: 0 }}>
            <SideBar onNavigate={handleNavigate} />
            <div style={{ flex: 1, background: 'none', minWidth: 0, width: '100%', overflowX: 'auto' }}>
              {activeScreen === 'dashboard' && <MainDashboard />}
              {activeScreen === 'users' && <div><Suspense fallback={<div>Loading...</div>}><UsersScreen /></Suspense></div>}
              {activeScreen === 'classes' && <div><Suspense fallback={<div>Loading...</div>}><ClassesScreen /></Suspense></div>}
              {activeScreen === 'tasks' && <div><React.Suspense fallback={<div>Loading...</div>}><TasksScreen /></React.Suspense></div>}
              {activeScreen === 'support' && <SupportScreen />}
            </div>
          </div>
        </div>
      )}
      {role === 'teacher' && <div>📚 Teacher panel placeholder</div>}
      {role === 'student' && <div>🎓 Student interface placeholder</div>}
      {role === 'staff' && (
        <div style={{ display: 'flex', flexDirection: 'column', minHeight: '100vh', background: 'none', width: '100vw', minWidth: 0 }}>
          <HeaderBar showLogout />
          <div style={{ display: 'flex', flex: 1, background: 'none', width: '100vw', minWidth: 0 }}>
            <StaffSideBar onNavigate={handleNavigate} />
            <div style={{ flex: 1, background: 'none', minWidth: 0, width: '100%', overflowX: 'auto' }}>
              {activeScreen === 'dashboard' && <StaffMainDashboard />}
              {activeScreen === 'tasks' && <StaffTasksScreen />}
              {activeScreen === 'classes' && <StaffClassesScreen />}
              {activeScreen === 'teachers' && <StaffTeachersScreen />}
            </div>
          </div>
        </div>
      )}
      {!role && <div>HA nice try dude</div>}
    </div>
  );
};

export default HomePage;
