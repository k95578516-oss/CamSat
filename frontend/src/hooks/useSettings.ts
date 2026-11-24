import { useCallback, useState } from 'react'
import { loadSettings, saveSettings, type UiSettings } from '../utils/settings'

export function useSettings() {
  const [settings, setSettings] = useState<UiSettings>(() => loadSettings())
  const update = useCallback((patch: Partial<UiSettings>) => {
    setSettings((prev) => {
      const next = { ...prev, ...patch }
      saveSettings(next)
      return next
    })
  }, [])
  return { settings, update }
}
