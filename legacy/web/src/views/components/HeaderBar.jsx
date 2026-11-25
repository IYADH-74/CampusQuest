import React, { useEffect, useState, useRef } from 'react';
import { FaBell, FaEnvelope, FaUserCircle, FaCog, FaSignOutAlt } from 'react-icons/fa';

const HeaderBar = () => {
  const [user, setUser] = useState(null);
  const [dropdown, setDropdown] = useState(false);
  const dropdownRef = useRef();

  useEffect(() => {
    const token = localStorage.getItem('jwt');
    if (!token) return;
    fetch('http://localhost:8080/api/v1/users/me', {
      headers: { 'Authorization': `Bearer ${token}` }
    })
      .then(res => res.ok ? res.json() : null)
      .then(setUser)
      .catch(() => setUser(null));
  }, []);

  // Close dropdown on outside click
  useEffect(() => {
    function handleClick(e) {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target)) setDropdown(false);
    }
    if (dropdown) document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, [dropdown]);

  const handleLogout = async () => {
    const token = localStorage.getItem('jwt');
    if (token) {
      await fetch('http://localhost:8080/api/v1/auth/logout', {
        method: 'GET',
        headers: { 'Authorization': `Bearer ${token}` }
      });
    }
    localStorage.clear();
    window.location.href = '/login';
  };

  return (
    <header style={{
      display: 'flex',
      justifyContent: 'space-between',
      alignItems: 'center',
      padding: '18px 36px',
      background: 'linear-gradient(90deg, #2d2e4a 0%, #3a3456 100%)',
      color: '#fff',
      boxShadow: '0 2px 8px #0002',
      minHeight: 70,
      width: '100%',
      boxSizing: 'border-box',
      position: 'relative',
      zIndex: 10
    }}>
      <div />
      <div style={{ display: 'flex', alignItems: 'center', gap: 24 }}>
        <FaEnvelope style={iconStyle} />
        <FaBell style={iconStyle} />
        <FaCog style={iconStyle} />
        <span style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', position: 'relative' }} onClick={() => setDropdown(d => !d)} ref={dropdownRef}>
          <FaUserCircle style={{ fontSize: 28 }} />
          <span style={{ fontWeight: 500 }}>
            {user ? `${user.firstName} ${user.lastName}` : 'Profile'}
          </span>
          {dropdown && user && (
            <div style={{
              position: 'absolute',
              top: 40,
              right: 0,
              background: '#23243a',
              color: '#fff',
              borderRadius: 12,
              boxShadow: '0 2px 12px #0006',
              minWidth: 220,
              padding: 18,
              zIndex: 100,
              fontSize: 15
            }}>
              <div style={{ marginBottom: 10, fontWeight: 700, fontSize: 17 }}>{user.firstName} {user.lastName}</div>
              <div style={{ marginBottom: 6 }}><b>Email:</b> {user.email}</div>
              <div style={{ marginBottom: 6 }}><b>Role:</b> {user.role}</div>
              {user.departement && <div style={{ marginBottom: 6 }}><b>Department:</b> {user.departement}</div>}
              {user.material && <div style={{ marginBottom: 6 }}><b>Material:</b> {user.material}</div>}
              {user.classeId && <div style={{ marginBottom: 6 }}><b>Class ID:</b> {user.classeId}</div>}
              <div style={{ marginTop: 16, borderTop: '1px solid #444', paddingTop: 10 }}>
                <button onClick={handleLogout} style={{
                  background: 'linear-gradient(90deg, #ff5e62 0%, #ff9966 100%)',
                  color: '#fff',
                  border: 'none',
                  borderRadius: 8,
                  padding: '8px 18px',
                  fontWeight: 600,
                  fontSize: 15,
                  cursor: 'pointer',
                  width: '100%',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 10,
                  marginTop: 4
                }}><FaSignOutAlt /> Logout</button>
              </div>
            </div>
          )}
        </span>
      </div>
    </header>
  );
};

const iconStyle = { fontSize: 22, cursor: 'pointer' };

export default HeaderBar;
