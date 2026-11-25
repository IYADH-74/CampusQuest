// src/Views/VerificationPage.jsx
import React, { useState } from 'react';
import axios from 'axios';
import { useNavigate } from 'react-router-dom';
import '../../assets/style1.css';

const VerificationPage = () => {
  const navigate = useNavigate();
  const email = localStorage.getItem('emailForVerification');
  const [code, setCode] = useState('');

  const handleSubmit = async (e) => {
    e.preventDefault();

    const payload = {
      email,
      verificationCode: code,
    };

    try {
      const res = await axios.post('http://localhost:8080/api/v1/auth/verify', payload);
      alert('Verification successful! You can now log in.');
      localStorage.removeItem('emailForVerification');
      navigate('/login');
    } catch (error) {
      console.error('Verification failed:', error.response?.data || error.message);
      alert('Verification failed: ' + (error.response?.data || 'Unknown error'));
    }
  };

  if (!email) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-red-500 text-white font-bold text-xl">
        ⚠️ Email not found. Please register first.
      </div>
    );
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-r from-purple-600 to-pink-500">
      <form onSubmit={handleSubmit} className="bg-white p-8 rounded-xl shadow-2xl w-full max-w-md">
        <h1 className="text-2xl font-bold mb-6 text-center text-gray-800">
          Email Verification
        </h1>

        <p className="mb-4 text-sm text-gray-600 text-center">
          A verification code has been sent to <strong>{email}</strong>
        </p>

        <div className="mb-4">
          <label htmlFor="code" className="block text-gray-700 font-semibold mb-2">
            Verification Code
          </label>
          <input
            type="text"
            id="code"
            value={code}
            onChange={(e) => setCode(e.target.value)}
            className="w-full px-4 py-2 border rounded-md focus:outline-none focus:ring-2 focus:ring-purple-500"
            required
          />
        </div>

        <button
          type="submit"
          className="w-full py-3 mt-4 bg-purple-600 text-white font-bold rounded-lg hover:bg-purple-700 transition"
        >
          VERIFY
        </button>
      </form>
    </div>
  );
};

export default VerificationPage;
