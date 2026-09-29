import { Link } from 'react-router-dom'
import { AppHeader } from '../components/AppHeader'
import { useAuth } from '../context/AuthContext'
import { getNavItems } from '../nav'

export default function HomeDashboard() {
  const { user, hasProfile } = useAuth()
  const showAddCandidate = !(user?.activeRole === 'User' && hasProfile)

  return (
    <div className="container">
      <AppHeader title="Job Matching Platform" subtitle="Manage candidates and find their best-fit jobs" navItems={getNavItems(user)} />
      <div className="content">
        <div className="card-grid">
          {showAddCandidate && (
            <Link to="/apply" className="dashboard-card">
              <h2>Add Candidate</h2>
              <p>Submit a new candidate profile for job matching.</p>
            </Link>
          )}
          <Link to="/candidates" className="dashboard-card accent-green">
            <h2>View Candidates</h2>
            <p>Browse all candidates in the system.</p>
          </Link>
          <Link to="/job-matches" className="dashboard-card accent-purple">
            <h2>Job Matches</h2>
            <p>Look up the best matching jobs for a candidate.</p>
          </Link>
        </div>
      </div>
    </div>
  )
}
