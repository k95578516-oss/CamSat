import { del, get, post, put } from './client'
import type { MissionRequest, MissionResponse } from '../types/api'

export const missionApi = {
  list: (search?: string, signal?: AbortSignal) =>
    get<MissionResponse[]>('/api/missions', { params: search?.trim() ? { search: search.trim() } : undefined, signal }),
  get: (id: number, signal?: AbortSignal) => get<MissionResponse>(`/api/missions/${id}`, { signal }),
  create: (body: MissionRequest) => post<MissionResponse>('/api/missions', body),
  update: (id: number, body: MissionRequest) => put<MissionResponse>(`/api/missions/${id}`, body),
  remove: (id: number) => del(`/api/missions/${id}`),
}
