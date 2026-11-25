import React, { useEffect, useState } from 'react';
import { FaPlus, FaChevronDown, FaChevronUp } from 'react-icons/fa';

const TASK_TYPES = [
  'Class_Mandetory',
  'Global_Certification',
  'Global_Challenge',
  'Global_Extra_Course',
  'Global_Envent_Attendance',
];

const TIERS = [1, 2, 3, 4, 5];

const StaffTasksScreen = () => {
  const [dropdown, setDropdown] = useState(false);
  const [activeTab, setActiveTab] = useState('global');
  const [globalTasks, setGlobalTasks] = useState([]);
  const [myTasks, setMyTasks] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({
    title: '',
    description: '',
    tier: 1,
    mandatory: false,
    deadline: '',
    taskType: 'Global_Challenge',
  });
  const [formLoading, setFormLoading] = useState(false);
  const [formError, setFormError] = useState('');
  const [formSuccess, setFormSuccess] = useState('');

  const userId = localStorage.getItem('userId');

  // Fetch tasks
  useEffect(() => {
    const token = localStorage.getItem('jwt');
    setLoading(true);
    setError('');
    Promise.all([
      fetch('http://localhost:8080/api/v1/tasks/global', { headers: { 'Authorization': `Bearer ${token}` } })
        .then(res => res.json())
        .then(data => setGlobalTasks(data.tasks || []))
        .catch(() => setError('Failed to load global tasks')),
      fetch(`http://localhost:8080/api/v1/tasks/global?publisherId=${userId}`, { headers: { 'Authorization': `Bearer ${token}` } })
        .then(res => res.json())
        .then(data => setMyTasks(data.tasks || []))
        .catch(() => setError('Failed to load my tasks')),
    ]).finally(() => setLoading(false));
  }, [showForm, userId]);

  // Animation for dropdown
  const dropdownStyle = {
    maxHeight: dropdown ? 120 : 0,
    opacity: dropdown ? 1 : 0,
    overflow: 'hidden',
    transition: 'max-height 0.3s cubic-bezier(0.4,0,0.2,1), opacity 0.2s',
    background: 'rgba(35,36,58,0.95)',
    borderRadius: 10,
    marginBottom: dropdown ? 18 : 0,
    boxShadow: dropdown ? '0 2px 12px #0002' : 'none',
  };

  const handleTab = (tab) => {
    setActiveTab(tab);
    setError('');
  };

  const handleFormChange = (e) => {
    const { name, value } = e.target;
    setForm(f => ({ ...f, [name]: value }));
  };

  const handleFormSubmit = async (e) => {
    e.preventDefault();
    setFormLoading(true);
    setFormError('');
    setFormSuccess('');
    try {
      const token = localStorage.getItem('jwt');
      const res = await fetch(`http://localhost:8080/api/v1/tasks/global?publisherId=${userId}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
        body: JSON.stringify({
          ...form,
          tier: Number(form.tier),
          mandatory: false,
          baseXP: 0, // will be set by backend
          bonusXP: 0,
          classes: [],
        }),
      });
      if (!res.ok) throw new Error('Failed to publish task');
      setFormSuccess('Task published successfully!');
      setTimeout(() => setShowForm(false), 1200);
    } catch (err) {
      setFormError(err.message);
    } finally {
      setFormLoading(false);
    }
  };

  return (
    <div style={{ padding: 36 }}>
      <h2 style={{ fontWeight: 700, fontSize: 28, letterSpacing: 1, marginBottom: 32 }}>Tasks</h2>
      <div style={{ marginBottom: 24 }}>
        <button
          onClick={() => setDropdown(d => !d)}
          style={{
            background: 'linear-gradient(90deg, #36d1c4 0%, #5b86e5 100%)',
            color: '#fff',
            border: 'none',
            borderRadius: 8,
            padding: '10px 22px',
            fontWeight: 600,
            fontSize: 16,
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            gap: 10,
            boxShadow: '0 2px 8px #0002',
            marginBottom: 8
          }}
        >
          Tasks {dropdown ? <FaChevronUp /> : <FaChevronDown />}
        </button>
        <div style={dropdownStyle}>
          <button onClick={() => handleTab('global')} style={{ ...tabBtn, background: activeTab === 'global' ? '#36d1c4' : 'none', color: activeTab === 'global' ? '#fff' : '#36d1c4' }}>Global Tasks</button>
          <button onClick={() => handleTab('my')} style={{ ...tabBtn, background: activeTab === 'my' ? '#36d1c4' : 'none', color: activeTab === 'my' ? '#fff' : '#36d1c4' }}>My Tasks</button>
        </div>
      </div>
      <div style={{ marginBottom: 24 }}>
        <button
          onClick={() => setShowForm(true)}
          style={{
            background: 'linear-gradient(90deg, #43cea2 0%, #36d1c4 100%)',
            color: '#fff',
            border: 'none',
            borderRadius: 8,
            padding: '10px 22px',
            fontWeight: 600,
            fontSize: 16,
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            gap: 10,
            boxShadow: '0 2px 8px #0002',
          }}
        >
          <FaPlus /> Publish New Global Task
        </button>
      </div>
      {error && <div style={{ color: '#ff5e62', fontWeight: 600, marginBottom: 12 }}>{error}</div>}
      {loading ? (
        <div>Loading...</div>
      ) : (
        <div style={{ background: 'rgba(35,36,58,0.95)', borderRadius: 18, boxShadow: '0 2px 12px #0002', padding: 24, overflowX: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', color: '#fff' }}>
            <thead>
              <tr style={{ background: 'rgba(90,90,150,0.15)' }}>
                <th style={thStyle}>ID</th>
                <th style={thStyle}>Title</th>
                <th style={thStyle}>Description</th>
                <th style={thStyle}>Tier</th>
                <th style={thStyle}>Mandatory</th>
                <th style={thStyle}>Base XP</th>
                <th style={thStyle}>Bonus XP</th>
                <th style={thStyle}>Deadline</th>
                <th style={thStyle}>Task Type</th>
              </tr>
            </thead>
            <tbody>
              {(activeTab === 'global' ? globalTasks : myTasks).map(task => (
                <tr key={task.id} style={{ borderBottom: '1px solid #444' }}>
                  <td style={tdStyle}>{task.id}</td>
                  <td style={tdStyle}>{task.title}</td>
                  <td style={tdStyle}>{task.description}</td>
                  <td style={tdStyle}>{task.tier}</td>
                  <td style={tdStyle}>{task.mandatory ? 'Yes' : 'No'}</td>
                  <td style={tdStyle}>{task.baseXP}</td>
                  <td style={tdStyle}>{task.bonusXP}</td>
                  <td style={tdStyle}>{task.deadline}</td>
                  <td style={tdStyle}>{task.taskType}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {showForm && (
        <div style={{ position: 'fixed', top: 0, left: 0, width: '100vw', height: '100vh', background: 'rgba(30,30,50,0.85)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
          <form onSubmit={handleFormSubmit} style={{ background: '#23243a', color: '#fff', borderRadius: 18, padding: 36, minWidth: 340, boxShadow: '0 2px 12px #0004', display: 'flex', flexDirection: 'column', gap: 16 }}>
            <h3 style={{ textAlign: 'center', marginBottom: 12 }}>Publish New Global Task</h3>
            <input name="title" placeholder="Title" value={form.title} onChange={handleFormChange} required disabled={formLoading} />
            <textarea name="description" placeholder="Description" value={form.description} onChange={handleFormChange} required disabled={formLoading} style={{ minHeight: 60 }} />
            <label>Tier:
              <select name="tier" value={form.tier} onChange={handleFormChange} required disabled={formLoading}>
                {TIERS.map(t => <option key={t} value={t}>{t}</option>)}
              </select>
            </label>
            <label>Task Type:
              <select name="taskType" value={form.taskType} onChange={handleFormChange} required disabled={formLoading}>
                {TASK_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
              </select>
            </label>
            <label>Deadline:
              <input name="deadline" type="date" value={form.deadline} onChange={handleFormChange} required disabled={formLoading} />
            </label>
            {formError && <div style={{ color: '#ff5e62', fontWeight: 600 }}>{formError}</div>}
            {formSuccess && <div style={{ color: '#43cea2', fontWeight: 600 }}>{formSuccess}</div>}
            <div style={{ display: 'flex', gap: 16, marginTop: 12 }}>
              <button type="submit" style={{ background: 'linear-gradient(90deg, #43cea2 0%, #36d1c4 100%)', color: '#fff', border: 'none', borderRadius: 8, padding: '10px 22px', fontWeight: 600, fontSize: 16, cursor: formLoading ? 'wait' : 'pointer', boxShadow: '0 2px 8px #0002' }} disabled={formLoading}>Publish</button>
              <button type="button" onClick={() => setShowForm(false)} style={{ background: 'rgba(90,90,150,0.15)', color: '#fff', border: 'none', borderRadius: 8, padding: '10px 22px', fontWeight: 600, fontSize: 16, cursor: 'pointer' }}>Cancel</button>
            </div>
          </form>
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
const tabBtn = {
  width: '100%',
  background: 'none',
  border: 'none',
  color: '#36d1c4',
  fontWeight: 600,
  fontSize: 16,
  padding: '12px 0',
  borderRadius: 8,
  cursor: 'pointer',
  textAlign: 'left',
  transition: 'background 0.2s',
};

export default StaffTasksScreen;
