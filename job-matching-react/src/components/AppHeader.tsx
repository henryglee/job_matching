import type { NavItem } from './NavBar'
import { NavBar } from './NavBar'
import { UserBadge } from './UserBadge'

interface AppHeaderProps {
  title: string
  subtitle?: string
  navItems?: NavItem[]
}

export function AppHeader({ title, subtitle, navItems }: AppHeaderProps) {
  return (
    <div className="header">
      <UserBadge />
      <h1>{title}</h1>
      {subtitle && <p>{subtitle}</p>}
      {navItems && navItems.length > 0 && <NavBar items={navItems} />}
    </div>
  )
}
