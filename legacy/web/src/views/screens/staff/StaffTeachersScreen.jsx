import React, { useEffect, useState } from 'react';
import { FaChalkboardTeacher, FaListUl } from 'react-icons/fa';

const StaffTeachersScreen = () => {
  const [teachers, setTeachers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [showAssign, setShowAssign] = useState(false);
  const [selectedTeacher, setSelectedTeacher] = useState(null);
  const [classes, setClasses] = useState([]);
  const [selectedClasses, setSelectedClasses] = useState([]);
  const [assignLoading, setAssignLoading] = useState(false);
  const [assignSuccess, setAssignSuccess] = useState('');
  const [assignError, setAssignError] = useState('');

  useEffect(() => {
    const token = localStorage.getItem('jwt');
    setLoading(true);
    fetch('http://localhost:8080/api/v1/staff/teachers', {
      headers: { 'Authorization': `Bearer ${token}` }
    })
      .then(res => res.json())
      .then(setTeachers)
      .catch(() => setError('Failed to load teachers'))
      .finally(() => setLoading(false));
  }, []);

  const handleAssignClick = (teacher) => {
    setSelectedTeacher(teacher);
    setAssignError('');
    setAssignSuccess('');
    setSelectedClasses([]);
    setShowAssign(true);
    // Fetch classes
    const token = localStorage.getItem('jwt');
    fetch('http://localhost:8080/api/v1/classes/all', {
      headers: { 'Authorization': `Bearer ${token}` }
    })
      .then(res => res.json())
      .then(setClasses)
      .catch(() => setAssignError('Failed to load classes'));
  };

  const handleClassSelect = (id) => {
    setSelectedClasses(prev =>
      prev.includes(id) ? prev.filter(cid => cid !== id) : [...prev, id]
    );
  };

  const handleAssignSubmit = async (e) => {
    e.preventDefault();
    setAssignLoading(true);
    setAssignError('');
    setAssignSuccess('');
    try {
      const token = localStorage.getItem('jwt');
      const res = await fetch(`http://localhost:8080/api/v1/staff/teachers/${selectedTeacher.id}/assign-classes`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
        body: JSON.stringify(selectedClasses),
      });
      if (!res.ok) throw new Error('Failed to assign classes');
      setAssignSuccess('Classes assigned successfully');
      setTimeout(() => setShowAssign(false), 1200);
    } catch (err) {
      setAssignError(err.message);
    } finally {
      setAssignLoading(false);
    }
  };

  return (
    <div style={{ padding: 36 }}>
      <h2 style={{ fontWeight: 700, fontSize: 28, letterSpacing: 1, marginBottom: 32 }}>Teachers</h2>
      {error && <div style={{ color: '#ff5e62', fontWeight: 600, marginBottom: 12 }}>{error}</div>}
      {loading ? (
        <div>Loading...</div>
      ) : teachers.length === 0 ? (
        <div style={{ color: '#fff', marginTop: 24 }}>No teachers found.</div>
      ) : (
        <div style={{ background: 'rgba(35,36,58,0.95)', borderRadius: 18, boxShadow: '0 2px 12px #0002', padding: 24, overflowX: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', color: '#fff' }}>
            <thead>
              <tr style={{ background: 'rgba(90,90,150,0.15)' }}>
                <th style={thStyle}>ID</th>
                <th style={thStyle}>First Name</th>
                <th style={thStyle}>Last Name</th>
                <th style={thStyle}>Option</th>
              </tr>
            </thead>
            <tbody>
              {teachers.map(teacher => (
                <tr key={teacher.id} style={{ borderBottom: '1px solid #444' }}>
                  <td style={tdStyle}>{teacher.id}</td>
                  <td style={tdStyle}>{teacher.firstName}</td>
                  <td style={tdStyle}>{teacher.lastName}</td>
                  <td style={{ ...tdStyle, display: 'flex', gap: 12 }}>
                    <button onClick={() => handleAssignClick(teacher)} style={{ background: 'linear-gradient(90deg, #36d1c4 0%, #5b86e5 100%)', color: '#fff', border: 'none', borderRadius: 8, padding: '8px 18px', fontWeight: 600, fontSize: 15, cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 8 }}><FaListUl /> Assign Classes</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {showAssign && (
        <div style={{ position: 'fixed', top: 0, left: 0, width: '100vw', height: '100vh', background: 'rgba(30,30,50,0.85)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
          <form onSubmit={handleAssignSubmit} style={{ background: '#23243a', color: '#fff', borderRadius: 18, padding: 36, minWidth: 340, boxShadow: '0 2px 12px #0004', display: 'flex', flexDirection: 'column', gap: 16 }}>
            <h3 style={{ textAlign: 'center', marginBottom: 12 }}>Assign Classes to {selectedTeacher.firstName} {selectedTeacher.lastName}</h3>
            <div style={{ maxHeight: 200, overflowY: 'auto', marginBottom: 12 }}>
              {classes.map(cls => (
                <label key={cls.id} style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
                  <input
                    type="checkbox"
                    value={cls.id}
                    checked={selectedClasses.includes(cls.id)}
                    onChange={() => handleClassSelect(cls.id)}
                    disabled={assignLoading}
                  />
                  {cls.className}
                </label>
              ))}
            </div>
            {assignError && <div style={{ color: '#ff5e62', fontWeight: 600 }}>{assignError}</div>}
            {assignSuccess && <div style={{ color: '#43cea2', fontWeight: 600 }}>{assignSuccess}</div>}
            <div style={{ display: 'flex', gap: 16, marginTop: 12 }}>
              <button type="submit" style={{ background: 'linear-gradient(90deg, #36d1c4 0%, #5b86e5 100%)', color: '#fff', border: 'none', borderRadius: 8, padding: '10px 22px', fontWeight: 600, fontSize: 16, cursor: assignLoading ? 'wait' : 'pointer', boxShadow: '0 2px 8px #0002' }} disabled={assignLoading}>Assign</button>
              <button type="button" onClick={() => setShowAssign(false)} style={{ background: 'rgba(90,90,150,0.15)', color: '#fff', border: 'none', borderRadius: 8, padding: '10px 22px', fontWeight: 600, fontSize: 16, cursor: 'pointer' }}>Cancel</button>
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

export default StaffTeachersScreen;
