export function Spinner({ small = false }: { small?: boolean }) {
  return <div className={small ? 'spinner spinner-sm' : 'spinner'} />
}
