import { api } from './client'
import type { ChatMessage, ChatReply } from '../types'

// One chat turn: the recent conversation, ending with the candidate's new message. 429 when
// they've sent too many messages in the last hour, 502 if the model call fails.
export function sendChat(messages: ChatMessage[]) {
  return api.post<ChatReply>('/api/chat', { messages })
}
