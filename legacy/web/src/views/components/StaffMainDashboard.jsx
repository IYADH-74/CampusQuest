import React, { useEffect, useState } from 'react';

const StaffMainDashboard = () => {
  const [leaderboard, setLeaderboard] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const token = localStorage.getItem('jwt');
    fetch('http://localhost:8080/api/v1/users/leaderboard', {
      headers: { 'Authorization': `Bearer ${token}` }
    })
      .then(res => res.json())
      .then(setLeaderboard)
      .catch(() => setError('Failed to load leaderboard'))
      .finally(() => setLoading(false));
  }, []);

  return (
    <main style={{
      flex: 1,
      padding: '2vw',
      background: 'linear-gradient(135deg, #23243a 0%, #3a3456 100%)',
      minHeight: '100vh',
      color: '#fff',
      overflow: 'auto',
      width: '100%',
      boxSizing: 'border-box',
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
    }}>
      <div style={{ width: '100%', maxWidth: 700, margin: '0 auto' }}>
        <h2 style={{ fontWeight: 700, fontSize: 28, letterSpacing: 1, marginBottom: 32, textAlign: 'center' }}>Students Leaderboard</h2>
        {loading ? (
          <div>Loading...</div>
        ) : error ? (
          <div style={{ color: '#ff5e62' }}>{error}</div>
        ) : (
          <div style={{
            background: 'rgba(35,36,58,0.95)',
            borderRadius: 18,
            boxShadow: '0 2px 12px #0002',
            padding: '2vw',
            width: '100%',
            overflowX: 'auto',
          }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', color: '#fff', fontSize: '1.1em' }}>
              <thead>
                <tr style={{ background: 'rgba(90,90,150,0.15)' }}>
                  <th style={thStyle}>#</th>
                  <th style={thStyle}>Full Name</th>
                  <th style={thStyle}>Level</th>
                  <th style={thStyle}>XP</th>
                </tr>
              </thead>
              <tbody>
                {leaderboard.map((s, i) => (
                  <tr key={s.fullName} style={{ borderBottom: '1px solid #444' }}>
                    <td style={tdStyle}>{i + 1}</td>
                    <td style={tdStyle}>{s.fullName}</td>
                    <td style={tdStyle}>{s.level}</td>
                    <td style={tdStyle}>{s.xp}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </main>
  );
};

const thStyle = {
  padding: '12px 8px',
  fontWeight: 700,
  fontSize: 16,
  letterSpacing: 0.5,
  textAlign: 'left',
  borderBottom: '2px solid #444',
};
const tdStyle = {
  padding: '10px 8px',
  fontSize: 15,
  letterSpacing: 0.2,
};

export default StaffMainDashboard;
