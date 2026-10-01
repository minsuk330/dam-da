import { Link, Route, Routes } from 'react-router'
import ConversationDetailPage from './pages/ConversationDetailPage'
import ConversationListPage from './pages/ConversationListPage'

function App() {
  return (
    <div className="min-h-dvh bg-stone-50 text-stone-900 dark:bg-stone-950 dark:text-stone-100">
      <header className="border-b border-stone-200 bg-white dark:border-stone-800 dark:bg-stone-900">
        <div className="mx-auto max-w-2xl px-4 py-3">
          <Link to="/" className="font-semibold">
            AI Learning Companion
          </Link>
        </div>
      </header>
      <main className="mx-auto max-w-2xl px-4 py-6">
        <Routes>
          <Route path="/" element={<ConversationListPage />} />
          <Route path="/conversations/:id" element={<ConversationDetailPage />} />
          <Route path="*" element={<p className="text-stone-500">페이지를 찾을 수 없어요.</p>} />
        </Routes>
      </main>
    </div>
  )
}

export default App
