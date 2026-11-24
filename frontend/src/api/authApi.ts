import { post } from './client'
import type { AuthResponse, LoginRequest, RegisterRequest } from '../types/api'

export const authApi = {
  login: (body: LoginRequest) => post<AuthResponse>('/api/auth/login', body),
  register: (body: RegisterRequest) => post<AuthResponse>('/api/auth/register', body),
}
