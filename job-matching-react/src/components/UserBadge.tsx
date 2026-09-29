import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

export function UserBadge() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  if (!user) return null

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  return (
    <div className="user-badge">
      <span>
        {user.username} ({user.activeRole})
      </span>
      <button type="button" onClick={handleLogout}>
        Logout
      </button>
    </div>
  )
}
