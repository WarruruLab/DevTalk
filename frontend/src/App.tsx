// src/App.tsx
import { useState } from 'react';
import SessionList from './SessionList';
import ChatView from './ChatView';

type View = 'list' | 'chat';

function App() {
  const [currentView, setCurrentView] = useState<View>('list');
  const [selectedSessionId, setSelectedSessionId] = useState<string>('');

  const handleSelectSession = (sessionId: string) => {
    setSelectedSessionId(sessionId);
    setCurrentView('chat');
  };

  const handleBackToList = () => {
    setCurrentView('list');
    setSelectedSessionId('');
  };

  return (
      <div className="app">
        {currentView === 'list' && (
            <SessionList onSelectSession={handleSelectSession} />
        )}

        {currentView === 'chat' && selectedSessionId && (
            <ChatView
                sessionId={selectedSessionId}
                onBack={handleBackToList}
                onSelectSession={handleSelectSession}
            />
        )}
      </div>
  );
}

export default App;
