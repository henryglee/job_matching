import { useState } from 'react'
import { MAX_SKILLS, SKILL_MAX } from '../limits'
import { LimitHint } from './LimitHint'

const PREDEFINED_SKILLS = [
  'Python',
  'JavaScript',
  'Java',
  'React',
  'Node.js',
  'SQL',
  'Machine Learning',
  'AWS',
  'Docker',
  'Git',
]

interface SkillPickerProps {
  value: string[]
  onChange: (skills: string[]) => void
}

export function SkillPicker({ value, onChange }: SkillPickerProps) {
  const [customInput, setCustomInput] = useState('')

  const selectedLower = value.map((s) => s.toLowerCase())
  const atLimit = value.length >= MAX_SKILLS

  const toggleSkill = (skill: string) => {
    if (selectedLower.includes(skill.toLowerCase())) {
      onChange(value.filter((s) => s.toLowerCase() !== skill.toLowerCase()))
    } else if (!atLimit) {
      onChange([...value, skill])
    }
  }

  const addCustomSkill = () => {
    const trimmed = customInput.trim()
    if (!trimmed || atLimit) return
    if (selectedLower.includes(trimmed.toLowerCase())) {
      setCustomInput('')
      return
    }
    onChange([...value, trimmed])
    setCustomInput('')
  }

  const removeSkill = (skill: string) => {
    onChange(value.filter((s) => s !== skill))
  }

  const customSkills = value.filter(
    (s) => !PREDEFINED_SKILLS.some((p) => p.toLowerCase() === s.toLowerCase()),
  )

  return (
    <div>
      <div className="skill-picker">
        {PREDEFINED_SKILLS.map((skill) => (
          <label key={skill} style={{ position: 'relative' }} className={`skill-pill ${selectedLower.includes(skill.toLowerCase()) ? 'selected' : ''}`}>
            <input
              type="checkbox"
              checked={selectedLower.includes(skill.toLowerCase())}
              onChange={() => toggleSkill(skill)}
              // Visually hidden rather than display:none, so it stays reachable by keyboard.
              style={{ position: 'absolute', opacity: 0, width: 1, height: 1, pointerEvents: 'none' }}
            />
            {skill}
          </label>
        ))}
        {customSkills.map((skill) => (
          <span key={skill} className="skill-pill selected">
            {skill}
            <span
              className="remove"
              role="button"
              tabIndex={0}
              aria-label={`Remove ${skill}`}
              onClick={() => removeSkill(skill)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault()
                  removeSkill(skill)
                }
              }}
            >
              ×
            </span>
          </span>
        ))}
      </div>
      <div className="skill-add-row">
        <input
          type="text"
          placeholder="Add your own skill"
          maxLength={SKILL_MAX}
          value={customInput}
          onChange={(e) => setCustomInput(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault()
              addCustomSkill()
            }
          }}
        />
        <button type="button" className="btn btn-secondary btn-sm" disabled={atLimit} onClick={addCustomSkill}>
          Add
        </button>
      </div>
      <LimitHint value={customInput} max={SKILL_MAX} />
      {atLimit && <p className="field-hint">You've added the most skills allowed ({MAX_SKILLS}). Remove one to add another.</p>}
    </div>
  )
}
