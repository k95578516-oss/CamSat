import { useCallback, useEffect, useRef, useState } from 'react'

/** Drives a playback clock (seconds of recorded flight time) from requestAnimationFrame. */
export function usePlayback(start: number, end: number) {
  const [t, setT] = useState(start)
  const [playing, setPlaying] = useState(false)
  const [speed, setSpeed] = useState(1)
  const raf = useRef<number | null>(null)
  const last = useRef<number | null>(null)

  // Reset when a different run (different time range) is loaded.
  useEffect(() => {
    setT(start)
    setPlaying(false)
  }, [start, end])

  useEffect(() => {
    if (!playing) return
    const tick = (now: number) => {
      const dt = last.current === null ? 0 : (now - last.current) / 1000
      last.current = now
      let finished = false
      setT((prev) => {
        const next = prev + dt * speed
        if (next >= end) {
          finished = true
          return end
        }
        return next
      })
      if (finished) setPlaying(false)
      else raf.current = requestAnimationFrame(tick)
    }
    last.current = null
    raf.current = requestAnimationFrame(tick)
    return () => {
      if (raf.current !== null) cancelAnimationFrame(raf.current)
    }
  }, [playing, speed, end])

  const toggle = useCallback(() => {
    setPlaying((p) => {
      if (!p) setT((cur) => (cur >= end ? start : cur))
      return !p
    })
  }, [start, end])
  const seek = useCallback((v: number) => {
    setPlaying(false)
    setT(Math.min(end, Math.max(start, v)))
  }, [start, end])
  const restart = useCallback(() => {
    setPlaying(false)
    setT(start)
  }, [start])

  return { t, playing, speed, setSpeed, toggle, seek, restart }
}
