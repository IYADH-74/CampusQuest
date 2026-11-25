import React from 'react';

const DummyScreen = ({ title }) => (
  <div style={{
    background: 'rgba(35,36,58,0.95)',
    borderRadius: 18,
    boxShadow: '0 2px 12px #0002',
    padding: 48,
    margin: 48,
    color: '#fff',
    fontSize: 28,
    textAlign: 'center',
    fontWeight: 600,
    letterSpacing: 0.5,
  }}>
    {title} (Coming Soon)
  </div>
);

export default DummyScreen;
