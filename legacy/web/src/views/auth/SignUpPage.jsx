// src/Views/SignUpPage.jsx
import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import '../../assets/style1.css';

const SignUpPage = () => {
  const [formData, setFormData] = useState({
    firstName: '',
    lastName: '',
    email: '',
    password: '',
    classeId: ''
  });
  const [classes, setClasses] = useState([]);
  const [loadingClasses, setLoadingClasses] = useState(true);
  const [classError, setClassError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const navigate = useNavigate();

  useEffect(() => {
    fetch('http://localhost:8080/api/v1/classes/all')
      .then((res) => {
        if (!res.ok) throw new Error('Failed to fetch classes');
        return res.json();
      })
      .then((data) => {
        setClasses(data);
        setLoadingClasses(false);
      })
      .catch((err) => {
        setClassError('Could not load classes');
        setLoadingClasses(false);
      });
  }, []);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      const payload = {
        ...formData,
        classeId: Number(formData.classeId),
      };
      const res = await fetch('http://localhost:8080/api/v1/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        alert('Signup failed: ' + (err.message || res.status));
        setSubmitting(false);
        return;
      }
      localStorage.setItem('emailForVerification', formData.email);
      navigate('/verify');
    } catch (error) {
      alert('Signup failed: ' + (error.message || 'Unknown error'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <section>
      <form id="signup-form" onSubmit={handleSubmit}>
        <h1>Sign Up</h1>
        <div className="inputbox">
          <input
            type="text"
            id="firstname"
            name="firstName"
            value={formData.firstName}
            onChange={handleChange}
            required
          />
          <label htmlFor="firstname">First Name</label>
        </div>
        <div className="inputbox">
          <input
            type="text"
            id="lastname"
            name="lastName"
            value={formData.lastName}
            onChange={handleChange}
            required
          />
          <label htmlFor="lastname">Last Name</label>
        </div>
        <div className="inputbox">
          <input
            type="text"
            id="username"
            name="username"
            value={formData.username}
            onChange={handleChange}
            required
          />
          <label htmlFor="email">Email</label>
        </div>
        <div className="inputbox">
          <input
            type="password"
            id="password"
            name="password"
            value={formData.password}
            onChange={handleChange}
            required
          />
          <label htmlFor="password">Password</label>
        </div>
        <label htmlFor="classe">Class:</label>
        <select
          id="classe"
          name="classeId"
          value={formData.classeId}
          onChange={handleChange}
          required
        >
          <option value="">{loadingClasses ? 'Loading...' : 'Select a class'}</option>
          {classError && (
            <option value="" disabled>{classError}</option>
          )}
          {classes.map((cls) => (
            <option key={cls.id} value={cls.id}>{cls.className}</option>
          ))}
        </select>
        <button type="submit" disabled={submitting}>{submitting ? 'Signing Up...' : 'Sign Up'}</button>
        <div className="register">
          <p>
            Already have an account? <a href="/login">Log In</a>
          </p>
        </div>
      </form>
    </section>
  );
};

export default SignUpPage;
