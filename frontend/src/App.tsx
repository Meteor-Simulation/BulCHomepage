import React, { useEffect, useState } from 'react';
import logo from './logo.svg';
import './App.css';

interface HealthStatus {
  status: string;
  message: string;
}

function App() {
  const [health, setHealth] = useState<HealthStatus | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetch('/api/health')
      .then(response => response.json())
      .then(data => setHealth(data))
      .catch(err => setError('백엔드 연결 실패: ' + err.message));
  }, []);

  return (
    <div className="App">
      <header className="App-header">
        <img src={logo} className="App-logo" alt="logo" />
        <h1>BulC Homepage</h1>
        <p>
          프로젝트가 정상적으로 실행되고 있습니다!
        </p>
        <div style={{ marginTop: '20px', padding: '20px', backgroundColor: 'rgba(255,255,255,0.1)', borderRadius: '8px' }}>
          <h3>백엔드 상태</h3>
          {error ? (
            <p style={{ color: '#ff6b6b' }}>{error}</p>
          ) : health ? (
            <p style={{ color: '#51cf66' }}>
              상태: {health.status} <br />
              메시지: {health.message}
            </p>
          ) : (
            <p>연결 중...</p>
          )}
        </div>
      </header>
    </div>
  );
}

export default App;
