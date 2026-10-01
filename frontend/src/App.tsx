import { useEffect, useState } from 'react'

type Health = 'checking' | 'ok' | 'down'

function App() {
  const [health, setHealth] = useState<Health>('checking')

  useEffect(() => {
    fetch('/healthz')
      .then((res) => setHealth(res.ok ? 'ok' : 'down'))
      .catch(() => setHealth('down'))
  }, [])

  return (
    <main>
      <h1>AI Learning Companion</h1>
      <p>backend: {health}</p>
    </main>
  )
}

export default App
