import React, { useEffect, useState } from 'react';
import { FaPlus, FaUser, FaChalkboardTeacher, FaUserShield, FaUserTie, FaEdit, FaTrash } from 'react-icons/fa';

const userTypeOptions = [
  { label: 'Student', value: 'students', icon: <FaUser /> },
  { label: 'Staff', value: 'staff', icon: <FaUserTie /> },
  { label: 'Teacher', value: 'teachers', icon: <FaChalkboardTeacher /> },
  { label: 'Admin', value: 'admins', icon: <FaUserShield /> },
];

const UsersScreen = () => {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showAdd, setShowAdd] = useState(false);
  const [userType, setUserType] = useState('students');
  const [form, setForm] = useState({});
  const [error, setError] = useState('');
  const [editingUser, setEditingUser] = useState(null);
  const [showEdit, setShowEdit] = useState(false);
  const [editForm, setEditForm] = useState({});
  const [boostActive, setBoostActive] = useState(false);
  const [boostLoading, setBoostLoading] = useState(false);
  const [classes, setClasses] = useState([]);

  useEffect(() => {
    const token = localStorage.getItem('jwt');
    fetch('http://localhost:8080/api/v1/admin/users/summary', {
      headers: { 'Authorization': `Bearer ${token}` }
    })
      .then(res => res.json())
      .then(setUsers)
      .catch(() => setError('Failed to load users'))
      .finally(() => setLoading(false));
  }, []);

  // Fetch classes for student add/edit
  useEffect(() => {
    if (showAdd || showEdit) {
      fetch('http://localhost:8080/api/v1/classes/all')
        .then(res => res.json())
        .then(setClasses)
        .catch(() => setClasses([]));
    }
  }, [showAdd, showEdit]);

  // Fetch global boost status
  useEffect(() => {
    const token = localStorage.getItem('jwt');
    fetch('http://localhost:8080/api/v1/admin/global-boost-status', {
      headers: { 'Authorization': `Bearer ${token}` }
    })
      .then(res => res.json())
      .then(setBoostActive)
      .catch(() => setBoostActive(false));
  }, []);

  const handleAddUser = () => {
    setShowAdd(true);
    setUserType('students');
    setForm({});
    setError('');
  };

  const handleFormChange = (e) => {
    const { name, value } = e.target;
    setForm(f => ({ ...f, [name]: value }));
  };

  const handleUserTypeChange = (e) => {
    setUserType(e.target.value);
    setForm({});
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    let url = `http://localhost:8080/api/v1/admin/${userType}`;
    let body = { ...form };
    if (userType === 'students') body.classeId = Number(body.classeId);
    if (userType === 'teachers' && body.classIds) body.classIds = body.classIds.split(',').map(Number);
    try {
      const token = localStorage.getItem('jwt');
      const res = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
        body: JSON.stringify(body),
      });
      if (!res.ok) throw new Error('Failed to add user');
      setShowAdd(false);
      setLoading(true);
      fetch('http://localhost:8080/api/v1/admin/users/summary', {
        headers: { 'Authorization': `Bearer ${token}` }
      })
        .then(res => res.json())
        .then(setUsers)
        .finally(() => setLoading(false));
    } catch (err) {
      setError(err.message);
    }
  };

  const handleEditUser = (user) => {
    setEditingUser(user);
    setEditForm(user);
    setShowEdit(true);
    setError('');
  };

  const handleEditFormChange = (e) => {
    const { name, value } = e.target;
    setEditForm(f => ({ ...f, [name]: value }));
  };

  const handleEditSubmit = async (e) => {
    e.preventDefault();
    setError('');
    if (!editingUser) return;
    const { id, role } = editingUser;
    let url = `http://localhost:8080/api/v1/admin/${role.toLowerCase()}s/${id}`;
    let body = { ...editForm };
    if (role === 'STUDENT') body.classeId = Number(body.classeId);
    if (role === 'TEACHER' && body.classIds) body.classIds = body.classIds.split(',').map(Number);
    try {
      const token = localStorage.getItem('jwt');
      const res = await fetch(url, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
        body: JSON.stringify(body),
      });
      if (!res.ok) throw new Error('Failed to update user');
      setShowEdit(false);
      setEditingUser(null);
      setLoading(true);
      fetch('http://localhost:8080/api/v1/admin/users/summary', {
        headers: { 'Authorization': `Bearer ${token}` }
      })
        .then(res => res.json())
        .then(setUsers)
        .finally(() => setLoading(false));
    } catch (err) {
      setError(err.message);
    }
  };

  const handleDeleteUser = async (id) => {
    if (!window.confirm('Are you sure you want to delete this user?')) return;
    setError('');
    try {
      const token = localStorage.getItem('jwt');
      const res = await fetch(`http://localhost:8080/api/v1/admin/${id}`, {
        method: 'DELETE',
        headers: { 'Authorization': `Bearer ${token}` },
      });
      if (!res.ok) throw new Error('Failed to delete user');
      setLoading(true);
      fetch('http://localhost:8080/api/v1/admin/users/summary', {
        headers: { 'Authorization': `Bearer ${token}` }
      })
        .then(res => res.json())
        .then(setUsers)
        .finally(() => setLoading(false));
    } catch (err) {
      setError(err.message);
    }
  };

  const handleGlobalBoost = async () => {
    setBoostLoading(true);
    setError('');
    try {
      const token = localStorage.getItem('jwt');
      const res = await fetch('http://localhost:8080/api/v1/admin/toggle-global-boost?enable=' + (!boostActive), {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${token}` },
      });
      if (!res.ok) throw new Error('Failed to toggle global boost');
      setBoostActive(!boostActive);
    } catch (err) {
      setError(err.message);
    } finally {
      setTimeout(() => setBoostLoading(false), 800); // Animation duration
    }
  };

  const renderFormFields = (isEdit = false) => {
    const currentType = isEdit ? (editingUser?.role?.toLowerCase() + 's') : userType;
    switch (currentType) {
      case 'students':
        return (
          <>
            <input name="firstName" placeholder="First Name" value={isEdit ? editForm.firstName : undefined} onChange={isEdit ? handleEditFormChange : handleFormChange} required />
            <input name="lastName" placeholder="Last Name" value={isEdit ? editForm.lastName : undefined} onChange={isEdit ? handleEditFormChange : handleFormChange} required />
            <input name="email" placeholder="Email" value={isEdit ? editForm.email : undefined} onChange={isEdit ? handleEditFormChange : handleFormChange} required />
            <input name="password" placeholder="Password" type="password" value={isEdit ? editForm.password : undefined} onChange={isEdit ? handleEditFormChange : handleFormChange} required />
            <select name="classeId" value={isEdit ? editForm.classeId : undefined} onChange={isEdit ? handleEditFormChange : handleFormChange} required>
              <option value="">Select Class</option>
              {classes.map(cls => (
                <option key={cls.id} value={cls.id}>{cls.className}</option>
              ))}
            </select>
          </>
        );
      case 'staff':
        return (
          <>
            <input name="firstName" placeholder="First Name" value={isEdit ? editForm.firstName : undefined} onChange={isEdit ? handleEditFormChange : handleFormChange} required />
            <input name="lastName" placeholder="Last Name" value={isEdit ? editForm.lastName : undefined} onChange={isEdit ? handleEditFormChange : handleFormChange} required />
            <input name="email" placeholder="Email" value={isEdit ? editForm.email : undefined} onChange={isEdit ? handleEditFormChange : handleFormChange} required />
            <input name="password" placeholder="Password" type="password" value={isEdit ? editForm.password : undefined} onChange={isEdit ? handleEditFormChange : handleFormChange} required />
            <input name="departement" placeholder="Department" value={isEdit ? editForm.departement : undefined} onChange={isEdit ? handleEditFormChange : handleFormChange} required />
          </>
        );
      case 'teachers':
        return (
          <>
            <input name="firstName" placeholder="First Name" value={isEdit ? editForm.firstName : undefined} onChange={isEdit ? handleEditFormChange : handleFormChange} required />
            <input name="lastName" placeholder="Last Name" value={isEdit ? editForm.lastName : undefined} onChange={isEdit ? handleEditFormChange : handleFormChange} required />
            <input name="email" placeholder="Email" value={isEdit ? editForm.email : undefined} onChange={isEdit ? handleEditFormChange : handleFormChange} required />
            <input name="password" placeholder="Password" type="password" value={isEdit ? editForm.password : undefined} onChange={isEdit ? handleEditFormChange : handleFormChange} required />
            <input name="material" placeholder="Material" value={isEdit ? editForm.material : undefined} onChange={isEdit ? handleEditFormChange : handleFormChange} required />
          </>
        );
      case 'admins':
        return (
          <>
            <input name="firstName" placeholder="First Name" value={isEdit ? editForm.firstName : undefined} onChange={isEdit ? handleEditFormChange : handleFormChange} required />
            <input name="lastName" placeholder="Last Name" value={isEdit ? editForm.lastName : undefined} onChange={isEdit ? handleEditFormChange : handleFormChange} required />
            <input name="email" placeholder="Email" value={isEdit ? editForm.email : undefined} onChange={isEdit ? handleEditFormChange : handleFormChange} required />
            <input name="password" placeholder="Password" type="password" value={isEdit ? editForm.password : undefined} onChange={isEdit ? handleEditFormChange : handleFormChange} required />
            <input name="privilegesDescription" placeholder="Privileges Description" value={isEdit ? editForm.privilegesDescription : undefined} onChange={isEdit ? handleEditFormChange : handleFormChange} />
          </>
        );
      default:
        return null;
    }
  };

  return (
    <div style={{ padding: 36 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 32 }}>
        <h2 style={{ fontWeight: 700, fontSize: 28, letterSpacing: 1 }}>Users</h2>
        <button onClick={handleAddUser} style={{
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
        }}><FaPlus /> Add User</button>
      </div>
      {loading ? (
        <div>Loading...</div>
      ) : (
        <div style={{
          background: 'rgba(35,36,58,0.95)',
          borderRadius: 18,
          boxShadow: '0 2px 12px #0002',
          padding: 24,
          overflowX: 'auto',
        }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', color: '#fff' }}>
            <thead>
              <tr style={{ background: 'rgba(90,90,150,0.15)' }}>
                <th style={thStyle}>ID</th>
                <th style={thStyle}>First Name</th>
                <th style={thStyle}>Last Name</th>
                <th style={thStyle}>Email</th>
                <th style={thStyle}>Role</th>
                <th style={thStyle}>Option</th>
              </tr>
            </thead>
            <tbody>
              {users.map(u => (
                <tr key={u.id} style={{ borderBottom: '1px solid #444' }}>
                  <td style={tdStyle}>{u.id}</td>
                  <td style={tdStyle}>{u.firstName}</td>
                  <td style={tdStyle}>{u.lastName}</td>
                  <td style={tdStyle}>{u.email}</td>
                  <td style={tdStyle}>{u.role}</td>
                  <td style={{ ...tdStyle, display: 'flex', gap: 12 }}>
                    <span style={{ cursor: 'pointer', color: '#36d1c4' }} onClick={() => handleEditUser(u)}><FaEdit /></span>
                    <span style={{ cursor: 'pointer', color: '#ff5e62' }} onClick={() => handleDeleteUser(u.id)}><FaTrash /></span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {showAdd && (
        <div style={{
          position: 'fixed', top: 0, left: 0, width: '100vw', height: '100vh',
          background: 'rgba(30,30,50,0.85)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000
        }}>
          <form onSubmit={handleSubmit} style={{
            background: '#23243a', color: '#fff', borderRadius: 18, padding: 36, minWidth: 340, boxShadow: '0 2px 12px #0004', display: 'flex', flexDirection: 'column', gap: 16
          }}>
            <div style={{ display: 'flex', gap: 12, marginBottom: 12 }}>
              <select value={userType} onChange={handleUserTypeChange} style={{ padding: 8, borderRadius: 8, border: 'none', fontWeight: 600 }}>
                {userTypeOptions.map(opt => (
                  <option key={opt.value} value={opt.value}>{opt.label}</option>
                ))}
              </select>
              {userTypeOptions.find(opt => opt.value === userType)?.icon}
            </div>
            {renderFormFields()}
            {error && <div style={{ color: '#ff5e62', fontWeight: 600 }}>{error}</div>}
            <div style={{ display: 'flex', gap: 16, marginTop: 12 }}>
              <button type="submit" style={{
                background: 'linear-gradient(90deg, #36d1c4 0%, #5b86e5 100%)',
                color: '#fff', border: 'none', borderRadius: 8, padding: '10px 22px', fontWeight: 600, fontSize: 16, cursor: 'pointer', boxShadow: '0 2px 8px #0002'
              }}>Add</button>
              <button type="button" onClick={() => setShowAdd(false)} style={{
                background: 'rgba(90,90,150,0.15)', color: '#fff', border: 'none', borderRadius: 8, padding: '10px 22px', fontWeight: 600, fontSize: 16, cursor: 'pointer'
              }}>Cancel</button>
            </div>
          </form>
        </div>
      )}
      {showEdit && (
        <div style={{
          position: 'fixed', top: 0, left: 0, width: '100vw', height: '100vh',
          background: 'rgba(30,30,50,0.85)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000
        }}>
          <form onSubmit={handleEditSubmit} style={{
            background: '#23243a', color: '#fff', borderRadius: 18, padding: 36, minWidth: 340, boxShadow: '0 2px 12px #0004', display: 'flex', flexDirection: 'column', gap: 16
          }}>
            <h3 style={{ textAlign: 'center', marginBottom: 12 }}>Edit User</h3>
            {renderFormFields(true)}
            {error && <div style={{ color: '#ff5e62', fontWeight: 600 }}>{error}</div>}
            <div style={{ display: 'flex', gap: 16, marginTop: 12 }}>
              <button type="submit" style={{
                background: 'linear-gradient(90deg, #36d1c4 0%, #5b86e5 100%)',
                color: '#fff', border: 'none', borderRadius: 8, padding: '10px 22px', fontWeight: 600, fontSize: 16, cursor: 'pointer', boxShadow: '0 2px 8px #0002'
              }}>Save</button>
              <button type="button" onClick={() => setShowEdit(false)} style={{
                background: 'rgba(90,90,150,0.15)', color: '#fff', border: 'none', borderRadius: 8, padding: '10px 22px', fontWeight: 600, fontSize: 16, cursor: 'pointer'
              }}>Cancel</button>
            </div>
          </form>
        </div>
      )}
      <div style={{ display: 'flex', alignItems: 'center', gap: 18, marginBottom: 24 }}>
        <button
          onClick={handleGlobalBoost}
          disabled={boostLoading}
          style={{
            background: boostActive ? 'linear-gradient(90deg, #ffb347 0%, #43cea2 100%)' : 'linear-gradient(90deg, #36d1c4 0%, #5b86e5 100%)',
            color: '#fff',
            border: 'none',
            borderRadius: 8,
            padding: '10px 22px',
            fontWeight: 600,
            fontSize: 16,
            cursor: boostLoading ? 'wait' : 'pointer',
            boxShadow: '0 2px 8px #0002',
            transition: 'background 0.5s',
            outline: boostActive ? '2px solid #43cea2' : 'none',
            animation: boostLoading ? 'pulse 0.8s infinite' : 'none',
          }}
        >
          {boostActive ? 'Global XP Boost Active' : 'Activate Global XP Boost'}
        </button>
        {boostLoading && <span style={{ color: '#43cea2', fontWeight: 700 }}>Processing...</span>}
      </div>
      {/* Add keyframes for animation (inject in style tag if not present) */}
      {typeof window !== 'undefined' && (
        (() => {
          const style = document.createElement('style');
          style.innerHTML = `@keyframes pulse { 0% { box-shadow: 0 0 0 0 #43cea288; } 70% { box-shadow: 0 0 0 10px #43cea200; } 100% { box-shadow: 0 0 0 0 #43cea200; } }`;
          document.head.appendChild(style);
        })()
      )}
    </div>
  );
};

const thStyle = {
  padding: '12px 18px',
  fontWeight: 700,
  fontSize: 16,
  letterSpacing: 0.5,
  textAlign: 'left',
  borderBottom: '2px solid #444',
};
const tdStyle = {
  padding: '10px 18px',
  fontSize: 15,
  letterSpacing: 0.2,
};

export default UsersScreen;
