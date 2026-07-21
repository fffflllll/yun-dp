import { apiRequest } from './http'
import type {
  ChatMessage,
  CurrentUser,
  PlanRunView,
  PreferenceData,
  RoomDetail,
  RoomSummary,
  StoredPreference,
} from '../types'

export const meetmateApi = {
  currentUser: () => apiRequest<CurrentUser>('/user/me'),
  listRooms: () => apiRequest<RoomSummary[]>('/meet/rooms'),
  room: (roomId: number) => apiRequest<RoomDetail>(`/meet/rooms/${roomId}`),
  createRoom: (payload: { title: string; centerX: number; centerY: number; searchRadiusMeter: number; maxMembers: number }) =>
    apiRequest<{ roomId: number; inviteCode: string; status: string }>('/meet/rooms', {
      method: 'POST', body: JSON.stringify(payload),
    }),
  joinRoom: (inviteCode: string) => apiRequest<number>('/meet/rooms/join-by-code', {
    method: 'POST', body: JSON.stringify({ inviteCode }),
  }),
  lockMembers: (roomId: number) => apiRequest<void>(`/meet/rooms/${roomId}/lock-members`, { method: 'POST' }),
  preference: (roomId: number) => apiRequest<StoredPreference | null>(`/meet/rooms/${roomId}/preferences/me`),
  parsePreference: (roomId: number, rawText: string) =>
    apiRequest<{ preference: PreferenceData; aiParsed: boolean; warning?: string }>(`/meet/rooms/${roomId}/preferences/parse`, {
      method: 'POST', body: JSON.stringify({ rawText }),
    }),
  confirmPreference: (roomId: number, preference: PreferenceData) =>
    apiRequest<void>(`/meet/rooms/${roomId}/preferences/confirm`, {
      method: 'PUT', body: JSON.stringify({ preference }),
    }),
  messages: (roomId: number, after = 0) =>
    apiRequest<ChatMessage[]>(`/meet/rooms/${roomId}/messages?after=${after}`),
  sendMessage: (roomId: number, content: string) =>
    apiRequest<ChatMessage>(`/meet/rooms/${roomId}/messages`, {
      method: 'POST', body: JSON.stringify({ content }),
    }),
  startPlan: (roomId: number) => apiRequest<{ runId: number }>(`/meet/rooms/${roomId}/plan-runs`, { method: 'POST' }),
  planRun: (runId: number) => apiRequest<PlanRunView>(`/meet/plan-runs/${runId}`),
  answerClarification: (runId: number, clarificationId: number, answer: string) =>
    apiRequest<void>(`/meet/plan-runs/${runId}/clarifications/${clarificationId}/answer`, {
      method: 'POST', body: JSON.stringify({ answer }),
    }),
  confirmPlan: (runId: number, proposalId: number) =>
    apiRequest<void>(`/meet/plan-runs/${runId}/confirm`, {
      method: 'POST', body: JSON.stringify({ proposalId }),
    }),
}
