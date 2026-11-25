import React, { useEffect, useState } from 'react';

const TasksScreen = () => {
  const [tasks, setTasks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    setLoading(true);
    const token = localStorage.getItem('jwt');
    fetch('http://localhost:8080/api/v1/admin/tasks', {
      headers: { 'Authorization': `Bearer ${token}` }
    })
      .then(res => {
        if (!res.ok) throw new Error('Failed to fetch tasks');
        return res.json();
      })
      .then(setTasks)
      .catch(() => setError('Failed to load tasks'))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div style={{ padding: 36 }}>
      <h2 style={{ fontWeight: 700, fontSize: 28, letterSpacing: 1, marginBottom: 32 }}>Tasks</h2>
      {error && <div style={{ color: '#ff5e62', fontWeight: 600, marginBottom: 12 }}>{error}</div>}
      {loading ? (
        <div>Loading...</div>
      ) : tasks.length === 0 ? (
        <div style={{ color: '#fff', marginTop: 24 }}>No tasks found.</div>
      ) : (
        <div style={{ background: 'rgba(35,36,58,0.95)', borderRadius: 18, boxShadow: '0 2px 12px #0002', padding: 24, overflowX: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', color: '#fff' }}>
            <thead>
              <tr style={{ background: 'rgba(90,90,150,0.15)' }}>
                <th style={thStyle}>Title</th>
                <th style={thStyle}>Type</th>
                <th style={thStyle}>Tier</th>
                <th style={thStyle}>Assigner</th>
              </tr>
            </thead>
            <tbody>
              {tasks.map((task, idx) => (
                <tr key={idx} style={{ borderBottom: '1px solid #444' }}>
                  <td style={tdStyle}>{task.title}</td>
                  <td style={tdStyle}>{task.type}</td>
                  <td style={tdStyle}>{task.tier}</td>
                  <td style={tdStyle}>{task.assigner || '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};

const thStyle = {
  padding: '12px 18px', fontWeight: 700, fontSize: 16, letterSpacing: 0.5, textAlign: 'left', borderBottom: '2px solid #444',
};
const tdStyle = {
  padding: '10px 18px', fontSize: 15, letterSpacing: 0.2,
};

export default TasksScreen;
