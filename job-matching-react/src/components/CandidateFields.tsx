import { SkillPicker } from './SkillPicker'
import { LocationSponsorshipFields } from './LocationSponsorshipFields'
import type { LocationSponsorshipValue } from '../locationSponsorship'
import { DEGREE_MAX, EMAIL_MAX, JOB_TITLE_MAX, NAME_MAX } from '../limits'
import { LimitHint } from './LimitHint'

export interface CandidateFieldsValue extends LocationSponsorshipValue {
  candidate_name: string
  email: string
  college_degree: string
  graduation_date: string
  preferred_job_title: string
  skills: string[]
}

interface CandidateFieldsProps {
  value: CandidateFieldsValue
  onChange: (value: CandidateFieldsValue) => void
  idPrefix: string
  // A User's profile email has to be their account email (the server refuses anything else), so
  // their forms show it but don't let it be edited. Admin/Staff can enter any address.
  emailReadOnly?: boolean
}

export function CandidateFields({ value, onChange, idPrefix, emailReadOnly = false }: CandidateFieldsProps) {
  const set = <K extends keyof CandidateFieldsValue>(key: K, fieldValue: CandidateFieldsValue[K]) => {
    onChange({ ...value, [key]: fieldValue })
  }

  return (
    <>
      <div className="form-group">
        <label htmlFor={`${idPrefix}-name`}>Full Name *</label>
        <input
          id={`${idPrefix}-name`}
          maxLength={NAME_MAX}
          type="text"
          value={value.candidate_name}
          onChange={(e) => set('candidate_name', e.target.value)}
        />
        <LimitHint value={value.candidate_name} max={NAME_MAX} />
      </div>
      <div className="form-group">
        <label htmlFor={`${idPrefix}-email`}>Email Address</label>
        <input
          id={`${idPrefix}-email`}
          maxLength={EMAIL_MAX}
          type="email"
          value={value.email}
          readOnly={emailReadOnly}
          aria-describedby={emailReadOnly ? `${idPrefix}-email-note` : undefined}
          onChange={(e) => set('email', e.target.value)}
        />
        {emailReadOnly && (
          <p id={`${idPrefix}-email-note`} className="field-hint">
            This is the email on your account, so it can't be changed here.
          </p>
        )}
      </div>
      <div className="form-row">
        <div className="form-group">
          <label htmlFor={`${idPrefix}-degree`}>College Degree</label>
          <input
            id={`${idPrefix}-degree`}
          maxLength={DEGREE_MAX}
            type="text"
            value={value.college_degree}
            onChange={(e) => set('college_degree', e.target.value)}
          />
        <LimitHint value={value.college_degree} max={DEGREE_MAX} />
        </div>
        <div className="form-group">
          <label htmlFor={`${idPrefix}-grad`}>Graduation Date</label>
          <input
            id={`${idPrefix}-grad`}
            type="date"
            value={value.graduation_date}
            onChange={(e) => set('graduation_date', e.target.value)}
          />
        </div>
      </div>
      <div className="form-group">
        <label htmlFor={`${idPrefix}-title`}>Preferred Job Title</label>
        <input
          id={`${idPrefix}-title`}
          maxLength={JOB_TITLE_MAX}
          type="text"
          value={value.preferred_job_title}
          onChange={(e) => set('preferred_job_title', e.target.value)}
        />
        <LimitHint value={value.preferred_job_title} max={JOB_TITLE_MAX} />
      </div>
      <LocationSponsorshipFields value={value} onChange={(loc) => onChange({ ...value, ...loc })} idPrefix={idPrefix} />
      <div className="form-group">
        <label>Skills</label>
        <SkillPicker value={value.skills} onChange={(skills) => set('skills', skills)} />
      </div>
    </>
  )
}
