import React, { useEffect, useState } from 'react';
import { FaPlus, FaEdit, FaTrash } from 'react-icons/fa';

const ClassesScreen = () => {
  const [classes, setClasses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showAdd, setShowAdd] = useState(false);
  const [showEdit, setShowEdit] = useState(false);
  const [form, setForm] = useState({ className: '' });
  const [editForm, setEditForm] = useState({ id: '', className: '' });
  const [editingClass, setEditingClass] = useState(null);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  // Fetch all classes
  const fetchClasses = () => {
    setLoading(true);
    fetch('http://localhost:8080/api/v1/classes/all')
      .then(res => res.json())
      .then(setClasses)
      .catch(() => setError('Failed to load classes'))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    fetchClasses();
  }, []);

  const handleAdd = () => {
    setShowAdd(true);
    setForm({ className: '' });
    setError('');
  };

  const handleFormChange = (e) => {
    setForm(f => ({ ...f, [e.target.name]: e.target.value }));
  };

  const handleEditFormChange = (e) => {
    setEditForm(f => ({ ...f, [e.target.name]: e.target.value }));
  };

  const showTempMessage = (msg, isError = false) => {
    if (isError) setError(msg);
    else setSuccess(msg);
    setTimeout(() => {
      setError('');
      setSuccess('');
    }, 2000);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    try {
      const token = localStorage.getItem('jwt');
      const res = await fetch('http://localhost:8080/api/v1/classes', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
        body: JSON.stringify(form),
      });
      if (!res.ok) throw new Error('Failed to add class');
      setShowAdd(false);
      fetchClasses();
      showTempMessage('Class added successfully!');
    } catch (err) {
      showTempMessage(err.message, true);
    }
  };

  const handleEdit = (cls) => {
    setEditingClass(cls);
    setEditForm({ id: cls.id, className: cls.className });
    setShowEdit(true);
    setError('');
  };

  const handleEditSubmit = async (e) => {
    e.preventDefault();
    setError('');
    try {
      const token = localStorage.getItem('jwt');
      const res = await fetch(`http://localhost:8080/api/v1/classes/${editForm.id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
        body: JSON.stringify({ className: editForm.className }),
      });
      if (!res.ok) throw new Error('Failed to update class');
      setShowEdit(false);
      setEditingClass(null);
      fetchClasses();
      showTempMessage('Class updated successfully!');
    } catch (err) {
      showTempMessage(err.message, true);
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm('Are you sure you want to delete this class?')) return;
    setError('');
    try {
      const token = localStorage.getItem('jwt');
      const res = await fetch(`http://localhost:8080/api/v1/classes/${id}`, {
        method: 'DELETE',
        headers: { 'Authorization': `Bearer ${token}` },
      });
      if (!res.ok) throw new Error('Failed to delete class');
      fetchClasses();
      showTempMessage('Class deleted successfully!');
    } catch (err) {
      showTempMessage(err.message, true);
    }
  };

  return (
    <div style={{ padding: 36 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 32 }}>
        <h2 style={{ fontWeight: 700, fontSize: 28, letterSpacing: 1 }}>Classes</h2>
        <button onClick={handleAdd} style={{
          background: 'linear-gradient(90deg, #36d1c4 0%, #5b86e5 100%)',
          color: '#fff', border: 'none', borderRadius: 8, padding: '10px 22px', fontWeight: 600, fontSize: 16, cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 10, boxShadow: '0 2px 8px #0002'
        }}><FaPlus /> Add Class</button>
      </div>
      {success && <div style={{ color: '#43cea2', fontWeight: 600, marginBottom: 12 }}>{success}</div>}
      {error && <div style={{ color: '#ff5e62', fontWeight: 600, marginBottom: 12 }}>{error}</div>}
      {loading ? (
        <div>Loading...</div>
      ) : classes.length === 0 ? (
        <div style={{ color: '#fff', marginTop: 24 }}>No classes found.</div>
      ) : (
        <div style={{ background: 'rgba(35,36,58,0.95)', borderRadius: 18, boxShadow: '0 2px 12px #0002', padding: 24, overflowX: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', color: '#fff' }}>
            <thead>
              <tr style={{ background: 'rgba(90,90,150,0.15)' }}>
                <th style={thStyle}>ID</th>
                <th style={thStyle}>Class Name</th>
                <th style={thStyle}>Option</th>
              </tr>
            </thead>
            <tbody>
              {classes.map(cls => (
                <tr key={cls.id} style={{ borderBottom: '1px solid #444' }}>
                  <td style={tdStyle}>{cls.id}</td>
                  <td style={tdStyle}>{cls.className}</td>
                  <td style={{ ...tdStyle, display: 'flex', gap: 12 }}>
                    <span style={{ cursor: 'pointer', color: '#36d1c4' }} onClick={() => handleEdit(cls)}><FaEdit /></span>
                    <span style={{ cursor: 'pointer', color: '#ff5e62' }} onClick={() => handleDelete(cls.id)}><FaTrash /></span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {showAdd && (
        <div style={{ position: 'fixed', top: 0, left: 0, width: '100vw', height: '100vh', background: 'rgba(30,30,50,0.85)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
          <form onSubmit={handleSubmit} style={{ background: '#23243a', color: '#fff', borderRadius: 18, padding: 36, minWidth: 340, boxShadow: '0 2px 12px #0004', display: 'flex', flexDirection: 'column', gap: 16 }}>
            <input name="className" placeholder="Class Name" value={form.className} onChange={handleFormChange} required autoFocus disabled={loading}/>
            {error && <div style={{ color: '#ff5e62', fontWeight: 600 }}>{error}</div>}
            <div style={{ display: 'flex', gap: 16, marginTop: 12 }}>
              <button type="submit" style={{ background: 'linear-gradient(90deg, #36d1c4 0%, #5b86e5 100%)', color: '#fff', border: 'none', borderRadius: 8, padding: '10px 22px', fontWeight: 600, fontSize: 16, cursor: loading ? 'wait' : 'pointer', boxShadow: '0 2px 8px #0002' }} disabled={loading}>Add</button>
              <button type="button" onClick={() => setShowAdd(false)} style={{ background: 'rgba(90,90,150,0.15)', color: '#fff', border: 'none', borderRadius: 8, padding: '10px 22px', fontWeight: 600, fontSize: 16, cursor: 'pointer' }}>Cancel</button>
            </div>
          </form>
        </div>
      )}
      {showEdit && (
        <div style={{ position: 'fixed', top: 0, left: 0, width: '100vw', height: '100vh', background: 'rgba(30,30,50,0.85)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
          <form onSubmit={handleEditSubmit} style={{ background: '#23243a', color: '#fff', borderRadius: 18, padding: 36, minWidth: 340, boxShadow: '0 2px 12px #0004', display: 'flex', flexDirection: 'column', gap: 16 }}>
            <h3 style={{ textAlign: 'center', marginBottom: 12 }}>Edit Class</h3>
            <input name="className" placeholder="Class Name" value={editForm.className} onChange={handleEditFormChange} required autoFocus disabled={loading}/>
            {error && <div style={{ color: '#ff5e62', fontWeight: 600 }}>{error}</div>}
            <div style={{ display: 'flex', gap: 16, marginTop: 12 }}>
              <button type="submit" style={{ background: 'linear-gradient(90deg, #36d1c4 0%, #5b86e5 100%)', color: '#fff', border: 'none', borderRadius: 8, padding: '10px 22px', fontWeight: 600, fontSize: 16, cursor: loading ? 'wait' : 'pointer', boxShadow: '0 2px 8px #0002' }} disabled={loading}>Save</button>
              <button type="button" onClick={() => setShowEdit(false)} style={{ background: 'rgba(90,90,150,0.15)', color: '#fff', border: 'none', borderRadius: 8, padding: '10px 22px', fontWeight: 600, fontSize: 16, cursor: 'pointer' }}>Cancel</button>
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

export default ClassesScreen;
