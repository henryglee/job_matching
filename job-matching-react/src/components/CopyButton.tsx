import { useState } from 'react'

interface CopyButtonProps {
  // Evaluated on click, so it always copies the latest text.
  text: () => string
  label?: string
  className?: string
}

export function CopyButton({ text, label = 'Copy', className = 'btn btn-secondary btn-sm' }: CopyButtonProps) {
  const [state, setState] = useState<'idle' | 'copied' | 'failed'>('idle')

  const handleClick = async () => {
    try {
      await navigator.clipboard.writeText(text())
      setState('copied')
    } catch {
      // Clipboard access can be denied (insecure origin, browser permission).
      setState('failed')
    }
    setTimeout(() => setState('idle'), 2000)
  }

  return (
    <button type="button" className={className} onClick={handleClick}>
      {state === 'copied' ? 'Copied ✓' : state === 'failed' ? 'Copy failed, select and copy by hand' : label}
    </button>
  )
}
