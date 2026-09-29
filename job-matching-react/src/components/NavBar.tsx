import { NavLink } from 'react-router-dom'

export interface NavItem {
  to: string
  label: string
}

export function NavBar({ items }: { items: NavItem[] }) {
  return (
    <nav className="navbar">
      {items.map((item) => (
        <NavLink key={item.to} to={item.to} end className={({ isActive }) => (isActive ? 'active' : '')}>
          {item.label}
        </NavLink>
      ))}
    </nav>
  )
}
