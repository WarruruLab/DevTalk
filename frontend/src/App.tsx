// src/App.tsx
import {useEffect, useRef, useState} from 'react';

type MessageRole = 'USER' | 'AI';
type MessageStatus = 'ok' | 'pending' | 'failed';

interface Message {
  id: string;
  role: MessageRole;
  content: string;
  status: MessageStatus;
  createdAt: number;
}

// backend
async function sendMessageToBackend(sessionId: string, content: string): Promise<{ reply: string }> {
  const response = await fetch(`/api/devtalk/sessions/${sessionId}/messages`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ message: content }),
  });

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${response.statusText}`);
  }

  return await response.json();
}

function App() {
  const [sessionId] = useState(() => Math.random().toString(36).substring(7));
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [isSending, setIsSending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  // 자동 스크롤
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const sendMessage = async () => {
    if (!input.trim() || isSending) return;

    const userMessage: Message = {
      id: Date.now().toString(),
      role: 'USER',
      content: input.trim(),
      status: 'ok',
      createdAt: Date.now(),
    };

    const aiPlaceholder: Message = {
      id: (Date.now() + 1).toString(),
      role: 'AI',
      content: 'AI typing...',
      status: 'pending',
      createdAt: Date.now() + 1,
    };

    setMessages(prev => [...prev, userMessage, aiPlaceholder]);
    setInput('');
    setIsSending(true);
    setError(null);

    try {
      // backend
      const response = await sendMessageToBackend(sessionId, userMessage.content);

      // placeholder 교체
      setMessages(prev =>
          prev.map(msg =>
              msg.id === aiPlaceholder.id
                  ? { ...msg, content: response.reply, status: 'ok' as MessageStatus }
                  : msg
          )
      );
    } catch (err) {
      // placeholder를 실패 메시지로 교체
      setMessages(prev =>
          prev.map(msg =>
              msg.id === aiPlaceholder.id
                  ? { ...msg, content: '(실패) 다시 시도하세요', status: 'failed' as MessageStatus }
                  : msg
          )
      );
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
          <h1>채팅 테스트 UI</h1>
          <span className="session-id">Session: {sessionId}</span>
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
            disabled={isSending}
        />
          <button onClick={sendMessage} disabled={!input.trim() || isSending}>
            {isSending ? '전송 중...' : '전송'}
          </button>
        </div>
      </div>
  );
}

export default App;
