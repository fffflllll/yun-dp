export type RoomStatus =
  | 'DRAFT'
  | 'COLLECTING_PREFERENCES'
  | 'MEMBERS_LOCKED'
  | 'READY_TO_PLAN'
  | 'PLANNING'
  | 'WAITING_INPUT'
  | 'PLANS_READY'
  | 'FINALIZED'
  | 'FAILED'

export interface Member {
  userId: number
  nickName?: string
  icon?: string
  role: 'OWNER' | 'MEMBER'
  status: string
  preferenceStatus: 'PENDING' | 'DRAFT' | 'CONFIRMED'
  joinTime?: string
}

export interface RoomSummary {
  roomId: number
  creatorId?: number
  title: string
  inviteCode: string
  status: RoomStatus
  maxMembers: number
  memberCount: number
  createTime: string
  members?: Member[]
}

export interface RoomDetail extends RoomSummary {
  centerX: number
  centerY: number
  searchRadiusMeter: number
  lockedAt?: string
  confirmedProposalId?: number
  latestPlanRunId?: number
  latestPlanRunStatus?: string
  members: Member[]
}

export interface PreferenceData {
  budgetMax: number | null
  preferredCuisines: string[]
  avoidFoods: string[]
  allergens: string[]
  acceptsSpicy: boolean | null
  maxDistanceMeters: number | null
  preferredTime: string
  hardConstraintKeys: string[]
  notes: string[]
}

export interface StoredPreference {
  rawText: string
  status: 'DRAFT' | 'CONFIRMED'
  draft?: PreferenceData
  confirmed?: PreferenceData
  confirmedAt?: string
}

export interface ChatMessage {
  id: number | string
  userId: number
  userName?: string
  icon?: string
  content: string
  createTime: string
  pending?: boolean
  failed?: boolean
}

export interface PlanEvent {
  sequence: number
  eventType: string
  summary: string
}

export interface Clarification {
  id?: number
  clarificationId?: number
  targetUserId: number
  status: string
  question: string
}

export interface Proposal {
  id?: number
  proposalId?: number
  rank: number
  recommended?: boolean
  shopName: string
  suggestedTime: string
  estimatedPerCapita?: number
  reasoning: string
  satisfiedPreferences?: string[]
}

export interface PlanRunView {
  run: { id: number; status: string }
  events: PlanEvent[]
  clarification?: Clarification
  proposals: Proposal[]
}

export interface CurrentUser {
  id: number
  nickName?: string
  icon?: string
}
