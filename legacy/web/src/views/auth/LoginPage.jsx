// src/Views/LoginPage.jsx
import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { IonIcon } from '@ionic/react';
import '../../assets/style1.css';

export default function LoginPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      const res = await fetch('http://localhost:8080/api/v1/auth/authenticate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password }),
      });

      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        alert('Login failed: ' + (err.message || res.status));
        return;
      }

      const { access_token, refresh_token, role ,id } = await res.json();

      localStorage.setItem('jwt', access_token);
      localStorage.setItem('refresh_token', refresh_token);
      localStorage.setItem('role', role);
      localStorage.setItem('userId', id);
      localStorage.setItem('loginTime', Date.now());

      const route = {
        STUDENT: '/student/home',
        TEACHER: '/teacher/home',
        ADMIN: '/admin/home',
        STAFF: '/staff/home',
      }[role];

      if (route) {
        navigate(route);
      } else {
        alert('Unknown role: ' + role);
      }
    } catch (err) {
      console.error('Error:', err);
      alert('An error occurred: ' + err.message);
    }
  };

  return (
    <section>
      <form id="login-form" onSubmit={handleSubmit}>
        <h1>Login</h1>

        <div className="dialog-row">
          {/* Placeholder for error messages */}
        </div>

        <div className="inputbox">
          <IonIcon name="mail-outline" />
          <input
            name="email"
            id="email"
            type="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
          <label htmlFor="email">Email</label>
        </div>

        <div className="inputbox">
          <IonIcon name="lock-closed-outline" />
          <input
            name="password"
            id="password"
            type="password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          <label htmlFor="password">Password</label>
        </div>

        <div className="register">
          <p>
            <a href="#">Forget Password?</a>
          </p>
        </div>

        <button type="submit">Log in</button>

        <div className="register">
          <p>
            Don't have an account? <a href="/signup">Register!!</a>
          </p>
        </div>
      </form>
    </section>
  );
}
