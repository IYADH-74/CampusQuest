import React from 'react';
import { FaUserCog, FaChalkboardTeacher, FaTasks, FaChartBar, FaCalendarAlt, FaEnvelope, FaCog, FaLifeRing } from 'react-icons/fa';

const SideBar = ({ onNavigate }) => (
  <aside style={{
    width: 220,
    background: 'linear-gradient(180deg, #23243a 0%, #3a3456 100%)',
    height: '100vh',
    padding: '32px 0',
    color: '#fff',
    boxShadow: '2px 0 12px #0002',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    minWidth: 220
  }}>
    <div style={{ fontWeight: 'bold', fontSize: 24, marginBottom: 40, letterSpacing: 1 }}>DASHBOARD</div>
    <nav style={{ width: '100%' }}>
      <ul style={{ listStyle: 'none', padding: 0, width: '100%' }}>
        <li style={{ marginBottom: 18 }}><button onClick={() => onNavigate('dashboard')} style={navBtn}><FaChartBar style={iconStyle}/> Dashboard</button></li>
        <li style={{ marginBottom: 18 }}><button onClick={() => onNavigate('users')} style={navBtn}><FaUserCog style={iconStyle}/> Users</button></li>
        <li style={{ marginBottom: 18 }}><button onClick={() => onNavigate('classes')} style={navBtn}><FaChalkboardTeacher style={iconStyle}/> Classes</button></li>
        <li style={{ marginBottom: 18 }}><button onClick={() => onNavigate('tasks')} style={navBtn}><FaTasks style={iconStyle}/> Tasks</button></li>
        <li><button onClick={() => onNavigate('support')} style={navBtn}><FaLifeRing style={iconStyle}/> Support</button></li>
      </ul>
    </nav>
  </aside>
);

const navBtn = {
  background: 'none',
  border: 'none',
  color: '#fff',
  fontSize: 16,
  width: '100%',
  textAlign: 'left',
  padding: '10px 32px',
  borderRadius: 8,
  cursor: 'pointer',
  display: 'flex',
  alignItems: 'center',
  gap: 14,
  transition: 'background 0.2s',
};

const iconStyle = { fontSize: 20 };

export default SideBar;
