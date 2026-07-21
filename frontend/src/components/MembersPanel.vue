<script setup lang="ts">
import { UserPlus, Users } from '@lucide/vue'
import type { Member } from '../types'
import { avatarText } from '../utils/format'

defineProps<{ members: Member[]; maxMembers: number; currentUserId?: number }>()

function preferenceLabel(status: string) {
  return status === 'CONFIRMED' ? '已确认' : status === 'DRAFT' ? '待确认' : '待填写'
}
</script>

<template>
  <section class="panel members-panel">
    <div class="panel-heading">
      <div><span class="panel-kicker"><Users :size="15" />成员</span><h2>{{ members.length }} / {{ maxMembers }}</h2></div>
      <a class="icon-button" href="/meetmate.html?openJoin=1" aria-label="邀请好友"><UserPlus :size="19" /></a>
    </div>
    <ul class="member-list">
      <li v-for="member in members" :key="member.userId">
        <img v-if="member.icon" class="avatar" :src="member.icon" alt="" />
        <span v-else class="avatar">{{ avatarText(member.nickName, member.userId) }}</span>
        <div><strong>{{ member.userId === currentUserId ? '我' : member.nickName || `成员 ${member.userId}` }}</strong><span>{{ member.role === 'OWNER' ? '房主' : '成员' }}</span></div>
        <span class="preference-state" :class="{ 'is-complete': member.preferenceStatus === 'CONFIRMED' }">{{ preferenceLabel(member.preferenceStatus) }}</span>
      </li>
    </ul>
  </section>
</template>
