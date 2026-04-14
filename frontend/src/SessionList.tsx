import { useState, useEffect } from 'react';
import { API_BASE } from './apiBase';

interface SessionSummary {
  sessionId: string;
  title: string;
  status: 'ACTIVE' | 'RESOLVED';
  description?: string;
  aiSummary?: string;
  createdAt: string;
  lastUpdatedAt: string;
}

interface SessionListProps {
  onSelectSession: (sessionId: string) => void;
  refreshTrigger?: number;
}

function SessionList({ onSelectSession, refreshTrigger }: SessionListProps) {
  const [sessions, setSessions] = useState<SessionSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [creating, setCreating] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [showTitleInput, setShowTitleInput] = useState(false);
  const [newSessionTitle, setNewSessionTitle] = useState('');
  const [isSidebarOpen, setIsSidebarOpen] = useState(true);

  // 편집 모달 State
  const [showEditModal, setShowEditModal] = useState(false);
  const [editingSession, setEditingSession] = useState<SessionSummary | null>(null);
  const [editTitle, setEditTitle] = useState('');
  const [editDescription, setEditDescription] = useState('');
  const [updating, setUpdating] = useState(false);

  // AI 요약 State
  const [aiSummaryText, setAiSummaryText] = useState('');
  const [showAiSummary, setShowAiSummary] = useState(false);

  useEffect(() => {
    loadSessions();
  }, [refreshTrigger]);

  const loadSessions = async () => {
    setLoading(true);
    try {
      const response = await fetch(`${API_BASE}/sessions`);
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      const data = await response.json();
      const sortedData = data.sort((a: SessionSummary, b: SessionSummary) => {
        return new Date(b.lastUpdatedAt).getTime() - new Date(a.lastUpdatedAt).getTime();
      });
      setSessions(sortedData);
    } catch (error) {
      console.error('세션 목록 로드 실패:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleNewSessionClick = () => {
    setShowTitleInput(true);
    setNewSessionTitle('');
  };

  const cancelNewSession = () => {
    setShowTitleInput(false);
    setNewSessionTitle('');
  };

  // ★★★ [수정됨] 새 세션 생성 로직 복구 ★★★
  const createNewSession = async () => {
    if (!newSessionTitle.trim()) return;

    setCreating(true);
    try {
      const response = await fetch(`${API_BASE}/sessions`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ title: newSessionTitle }),
      });

      if (response.ok) {
        setShowTitleInput(false);
        setNewSessionTitle('');
        await loadSessions(); // 목록 갱신
      } else {
        console.error('세션 생성 실패:', response.status);
        alert('세션 생성에 실패했습니다.');
      }
    } catch (error) {
      console.error('세션 생성 중 에러:', error);
      alert('서버 요청 중 오류가 발생했습니다.');
    } finally {
      setCreating(false); // 버튼 잠금 해제
    }
  };

  const handleEditClick = (e: React.MouseEvent, session: SessionSummary) => {
    e.stopPropagation();
    setEditingSession(session);
    setEditTitle(session.title);
    setEditDescription(session.description || '');
    setAiSummaryText(session.aiSummary || '');
    setShowAiSummary(false);
    setShowEditModal(true);
  };

  const cancelEdit = () => {
    setShowEditModal(false);
    setEditingSession(null);
  };

  const updateSession = async () => {
    // 1. 기본 유효성 검사 (세션이 없거나 제목이 비었으면 중단)
    if (!editingSession || !editTitle.trim()) return;

    // 2. ★ 변경 사항 확인 로직 추가 ★
    // 기존 데이터(editingSession)와 현재 입력창 데이터(editTitle, editDescription)를 비교합니다.
    const currentTitle = editTitle.trim();
    const originalTitle = editingSession.title;

    // description은 null일 수 있으므로 빈 문자열로 치환하여 비교
    const currentDesc = editDescription.trim();
    const originalDesc = (editingSession.description || '').trim();

    // 제목과 설명이 모두 기존과 똑같다면?
    if (currentTitle === originalTitle && currentDesc === originalDesc) {
      console.log('변경된 내용이 없어 요청을 보내지 않습니다.');
      setShowEditModal(false);
      setEditingSession(null);
      return; // 여기서 함수 종료! (서버 요청 안 함)
    }

    setUpdating(true); // 버튼 비활성화

    try {
      // 3. 서버 요청 (변경된 내용이 있을 때만 여기까지 옴)
      // 백엔드 코드가 @PutMapping이므로 method를 'PUT'으로 설정
      const response = await fetch(`${API_BASE}/sessions/${editingSession.sessionId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          title: editTitle,
          description: editDescription,
        }),
      });

      if (response.ok) {
        setShowEditModal(false);
        setEditingSession(null);
        await loadSessions(); // 목록 갱신
      } else {
        console.error('세션 수정 실패:', response.status);
        alert('세션 수정에 실패했습니다.');
      }
    } catch (error) {
      console.error('세션 수정 중 에러:', error);
      alert('서버 요청 중 오류가 발생했습니다.');
    } finally {
      setUpdating(false); // 버튼 잠금 해제
    }
  };

  // AI 요약 핸들러들
  const handleViewAiSummary = () => { setShowAiSummary(true); };
  const handleGenerateAiSummary = () => { alert('🚧 추후 기능 추가 예정'); };
  const handleRegenerateAiSummary = () => { alert('🚧 추후 기능 추가 예정'); };
  const handleConfirmAiSummary = () => { alert('🚧 추후 기능 추가 예정'); };

  // 유틸 함수들
  const formatDateTime = (dateString: string) => {
    const date = new Date(dateString);
    return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
  };

  const groupByDate = (sessions: SessionSummary[]) => {
    const today = new Date(); today.setHours(0, 0, 0, 0);
    const yesterday = new Date(today); yesterday.setDate(yesterday.getDate() - 1);
    const grouped: { [key: string]: SessionSummary[] } = { '오늘': [], '어제': [], '이전': [] };
    sessions.forEach(session => {
      const sessionDate = new Date(session.lastUpdatedAt); sessionDate.setHours(0, 0, 0, 0);
      if (sessionDate.getTime() === today.getTime()) grouped['오늘'].push(session);
      else if (sessionDate.getTime() === yesterday.getTime()) grouped['어제'].push(session);
      else grouped['이전'].push(session);
    });
    return grouped;
  };

  const getRecentWeekSessions = (sessions: SessionSummary[]) => {
    const weekAgo = new Date(); weekAgo.setDate(weekAgo.getDate() - 7); weekAgo.setHours(0, 0, 0, 0);
    return sessions.filter(session => new Date(session.lastUpdatedAt) >= weekAgo);
  };

  // 렌더링 준비
  const filteredSessions = sessions.filter(session =>
      session.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
      session.description?.toLowerCase().includes(searchQuery.toLowerCase()) ||
      session.aiSummary?.toLowerCase().includes(searchQuery.toLowerCase())
  );
  const groupedSessions = groupByDate(filteredSessions);
  const recentWeekSessions = getRecentWeekSessions(filteredSessions);

  if (loading) {
    return <div style={{ display: 'flex', height: '100vh', justifyContent: 'center', alignItems: 'center' }}><div>로딩 중...</div></div>;
  }

  return (
      <div className="home-layout">
        {/* 모달: 새 세션 */}
        {showTitleInput && (
            <div className="modal-overlay">
              <div className="modal-content">
                <h3>새 세션 제목</h3>
                <input
                    type="text"
                    className="title-input"
                    placeholder="세션 제목을 입력하세요"
                    value={newSessionTitle}
                    onChange={(e) => setNewSessionTitle(e.target.value)}
                    onKeyDown={(e) => e.key === 'Enter' && createNewSession()}
                    autoFocus
                />
                <div className="modal-buttons">
                  <button onClick={cancelNewSession} className="btn-cancel">취소</button>
                  <button onClick={createNewSession} className="btn-create" disabled={creating || !newSessionTitle.trim()}>
                    {creating ? '생성 중...' : '생성'}
                  </button>
                </div>
              </div>
            </div>
        )}

        {/* 모달: 세션 편집 */}
        {showEditModal && editingSession && (
            <div className="modal-overlay">
              <div className="modal-content modal-large">
                <h3>세션 편집</h3>
                <div className="modal-section">
                  <label className="modal-label">제목</label>
                  <input type="text" className="title-input" value={editTitle} onChange={(e) => setEditTitle(e.target.value)} />
                </div>
                <div className="modal-section">
                  <label className="modal-label">설명 (선택)</label>
                  <textarea className="description-input" value={editDescription} onChange={(e) => setEditDescription(e.target.value)} rows={3} />
                </div>
                <div className="modal-section ai-summary-section">
                  <div className="ai-summary-header">
                    <label className="modal-label">🤖 AI 요약</label>
                    <div className="ai-summary-buttons">
                      {aiSummaryText ?
                          <button onClick={handleViewAiSummary} className="btn-view-summary">{showAiSummary ? '요약 숨기기' : '요약 보기'}</button> :
                          <button onClick={handleGenerateAiSummary} className="btn-generate-summary">AI 요약 생성하기</button>
                      }
                    </div>
                  </div>
                  {showAiSummary && aiSummaryText && (
                      <div className="ai-summary-content">
                        <div className="ai-summary-box"><p>{aiSummaryText}</p></div>
                        <div className="ai-summary-actions">
                          <button onClick={handleRegenerateAiSummary} className="btn-regenerate">🔄 다시 요약하기</button>
                          <button onClick={handleConfirmAiSummary} className="btn-confirm">✓ 확정</button>
                        </div>
                      </div>
                  )}
                </div>
                <div className="modal-buttons">
                  <button onClick={cancelEdit} className="btn-cancel">취소</button>
                  <button onClick={updateSession} className="btn-create" disabled={updating || !editTitle.trim()}>
                    {updating ? '저장 중...' : '저장'}
                  </button>
                </div>
              </div>
            </div>
        )}

        {/* 사이드바 */}
        <nav className={`sidebar ${!isSidebarOpen ? 'closed' : ''}`}>
          <div className="sidebar-inner">
            <div className="logo">DevTalk</div>
            <div className="nav-group">
              <span className="nav-label">모든 세션 ({sessions.length})</span>
              {Object.entries(groupedSessions).map(([dateLabel, sessionList]) => {
                if (sessionList.length === 0) return null;
                return (
                    <div key={dateLabel} style={{ marginBottom: '20px' }}>
                      <div className="date-divider">{dateLabel}</div>
                      {sessionList.map(session => (
                          <div key={session.sessionId} className="sidebar-session-item" onClick={() => onSelectSession(session.sessionId)}>
                            <span className="sidebar-session-title">{session.title}</span>
                            <span className={`sidebar-session-status ${session.status.toLowerCase()}`}>{session.status === 'RESOLVED' ? '✓' : '○'}</span>
                          </div>
                      ))}
                    </div>
                );
              })}
            </div>
          </div>
        </nav>

        {/* 메인 콘텐츠 */}
        <main className="main-content">
          <div className="top-bar">
            <div style={{ display: 'flex', alignItems: 'center' }}>
              <button
                  className={`sidebar-toggle-btn-left ${isSidebarOpen ? 'active' : ''}`}
                  onClick={() => setIsSidebarOpen(!isSidebarOpen)}
                  title={isSidebarOpen ? "목록 접기" : "목록 펼치기"}
              >
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <rect x="3" y="3" width="18" height="18" rx="2" ry="2" />
                  <line x1="9" y1="3" x2="9" y2="21" />
                </svg>
              </button>
              {!isSidebarOpen && <div className="logo">DevTalk</div>}
            </div>

            <input
                type="text"
                className="search-input"
                placeholder="세션 검색..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
            />
            <button
                className="btn-new"
                onClick={handleNewSessionClick}
            >
              + 새 세션 시작
            </button>
          </div>

          <div className="content-scroll">
            <div className="section-header">
              <h2 className="section-title">최근 세션 (일주일)</h2>
              <span style={{ fontSize: '14px', color: '#636e72' }}>{recentWeekSessions.length}개</span>
            </div>
            <div className="session-grid">
              {recentWeekSessions.length === 0 ? (
                  <div className="empty-state"><p>최근 일주일 동안 세션이 없습니다</p><p>새 세션을 시작해보세요!</p></div>
              ) : (
                  recentWeekSessions.map(session => (
                      <div key={session.sessionId} className="session-card" onClick={() => onSelectSession(session.sessionId)}>
                        <div className="card-header-row">
                          <div className="card-meta">
                            <span>{formatDateTime(session.lastUpdatedAt)}</span>
                            <span className={session.status === 'RESOLVED' ? 'status-resolved' : 'status-active'}>
                              {session.status === 'RESOLVED' ? '✓' : '○'}
                            </span>
                          </div>
                          <button className="edit-button" onClick={(e) => handleEditClick(e, session)} title="편집">✏️</button>
                        </div>
                        <div className="card-title">{session.title}</div>
                        {session.description && <div className="card-description">{session.description}</div>}
                        <div className="card-tags">
                          <span className={`tag ${session.status === 'RESOLVED' ? 'tag-resolved' : 'tag-active'}`}>
                            {session.status === 'RESOLVED' ? '해결됨' : '진행중'}
                          </span>
                        </div>
                      </div>
                  ))
              )}
            </div>
          </div>
        </main>
      </div>
  );
}

export default SessionList;
