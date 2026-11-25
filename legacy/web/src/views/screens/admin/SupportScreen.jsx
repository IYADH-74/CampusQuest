import React from 'react';

const   SupportScreen = () => (
  <div style={{
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: '80vh',
    width: '100%',
    padding: 32,
    background: 'rgba(35,36,58,0.95)',
    borderRadius: 18,
    boxShadow: '0 2px 12px #0002',
    margin: '3vw auto',
    color: '#fff',
    fontSize: 'clamp(1.2rem, 2vw, 2rem)',
    textAlign: 'center',
    fontWeight: 600,
    letterSpacing: 0.5,
  }}>
    <span style={{ marginBottom: 16 }}>
      If you ever feel overwhelmed or in need of support, remember: there are Palestinians who face unimaginable horrors every day, yet continue to hope and persevere with little help from the world.<br /><br />
      May their courage inspire us all.<br />
    </span>
    <span style={{ color: '#43cea2', fontWeight: 700, fontSize: '1.3em', marginTop: 16 }}>Viva Palestina</span>
  </div>
);

export default SupportScreen;
