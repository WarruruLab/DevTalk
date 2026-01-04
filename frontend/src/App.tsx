// src/App.tsx
import { useState, useRef, useEffect } from 'react';

type MessageRole = 'USER' | 'AI';
type MessageStatus = 'ok' | 'pending' | 'failed';

interface Message {
  id: string;
  role: MessageRole;
  content: string;
  status: MessageStatus;
  createdAt: number;
}

interface Session {
  sessionId: string;
  status: string;
  createdAt: string;
}

async function createSession(): Promise<Session> {
  const response = await fetch('http://localhost:8080/api/devtalk/sessions', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    throw new Error(`Session creation failed: ${response.status}`);
  }

  return await response.json();
}

async function sendMessageToBackend(sessionId: string, content: string): Promise<any> {
  const response = await fetch(`http://localhost:8080/api/devtalk/sessions/${sessionId}/messages`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      id: Date.now().toString(),
      role: 'USER',
      content: content,
      status: 'OK'
    }),
  });

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${response.statusText}`);
  }

  return await response.json();
}

async function checkBackendHealth(): Promise<string> {
  const response = await fetch('http://localhost:8080/api/health', {
    method: 'GET',
  });

  if (!response.ok) {
    throw new Error(`Health check failed: ${response.status}`);
  }

  return await response.text();
}

function App() {
  const [sessionId, setSessionId] = useState<string>('');
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [isSending, setIsSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [healthStatus, setHealthStatus] = useState<'checking' | 'ok' | 'failed'>('checking');
  const [sessionStatus, setSessionStatus] = useState<'creating' | 'ok' | 'failed'>('creating');

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  useEffect(() => {
    checkHealth();
    initSession();
  }, []);

  const checkHealth = async () => {
    setHealthStatus('checking');
    try {
      const result = await checkBackendHealth();
      setHealthStatus(result === 'ok' ? 'ok' : 'failed');
    } catch (err) {
      setHealthStatus('failed');
    }
  };

  const initSession = async () => {
    setSessionStatus('creating');
    try {
      const session = await createSession();
      setSessionId(session.sessionId);
      setSessionStatus('ok');
    } catch (err) {
      setSessionStatus('failed');
      setError('세션 생성 실패: ' + (err as Error).message);
    }
  };

  const sendMessage = async () => {
    if (!input.trim() || isSending || !sessionId) return;

    const userMessage: Message = {
      id: Date.now().toString(),
      role: 'USER',
      content: input.trim(),
      status: 'ok',
      createdAt: Date.now(),
    };

    setMessages(prev => [...prev, userMessage]);
    setInput('');
    setIsSending(true);
    setError(null);

    try {
      const response = await sendMessageToBackend(sessionId, userMessage.content);

      if (response.role === 'AI') {
        const aiMessage: Message = {
          id: response.id || (Date.now() + 1).toString(),
          role: 'AI',
          content: response.content,
          status: response.status === 'FAILED' ? 'failed' : 'ok',
          createdAt: Date.now(),
        };
        setMessages(prev => [...prev, aiMessage]);
      }
    } catch (err) {
      const errorMessage: Message = {
        id: (Date.now() + 1).toString(),
        role: 'AI',
        content: '(실패) 다시 시도하세요',
        status: 'failed',
        createdAt: Date.now(),
      };
      setMessages(prev => [...prev, errorMessage]);
      setError((err as Error).message);
    } finally {
      setIsSending(false);
      textareaRef.current?.focus();
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      sendMessage();
    }
  };

  return (
      <div className="app">
        <header className="header">
          <div>
            <h1>채팅 테스트 UI</h1>
            <span className="session-id">
            Session: {sessionStatus === 'creating' ? '생성 중...' : sessionStatus === 'failed' ? '생성 실패' : sessionId}
          </span>
          </div>
          <div className="health-check">
          <span className={`health-status health-${healthStatus}`}>
            {healthStatus === 'checking' && '⏳ 확인 중...'}
            {healthStatus === 'ok' && '✅ 서버 연결됨'}
            {healthStatus === 'failed' && '❌ 서버 연결 실패'}
          </span>
            <button onClick={checkHealth} className="health-button">재확인</button>
          </div>
        </header>

        {error && (
            <div className="error-banner">
              ⚠️ {error}
            </div>
        )}

        <div className="messages-container">
          {messages.map(msg => (
              <div key={msg.id} className={`message message-${msg.role.toLowerCase()}`}>
                <div className="message-role">{msg.role}</div>
                <div className={`message-bubble ${msg.status === 'failed' ? 'failed' : ''} ${msg.status === 'pending' ? 'pending' : ''}`}>
                  {msg.content}
                </div>
                <div className="message-time">
                  {new Date(msg.createdAt).toLocaleTimeString()}
                </div>
              </div>
          ))}
          <div ref={messagesEndRef} />
        </div>

        <div className="composer">
        <textarea
            ref={textareaRef}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="메시지 입력 (Enter: 전송, Shift+Enter: 줄바꿈)"
            disabled={isSending || sessionStatus !== 'ok'}
        />
          <button
              onClick={sendMessage}
              disabled={!input.trim() || isSending || sessionStatus !== 'ok'}
          >
            {isSending ? '전송 중...' : '전송'}
          </button>
        </div>
      </div>
  );
}

export default App;
