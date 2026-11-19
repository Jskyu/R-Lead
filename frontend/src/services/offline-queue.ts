import { openDB, DBSchema, IDBPDatabase } from 'idb'
import type { CardUploadRequest } from '../types/lead'

interface QueuedCard {
  id: string
  imageBlob: Blob
  metadata: CardUploadRequest
  createdAt: number
  retryCount: number
}

interface OfflineQueueDB extends DBSchema {
  'queued-cards': {
    key: string
    value: QueuedCard
    indexes: { 'by-created': number }
  }
}

class OfflineQueue {
  private db: IDBPDatabase<OfflineQueueDB> | null = null
  private readonly DB_NAME = 'rlead-offline-queue'
  private readonly DB_VERSION = 1

  async init(): Promise<void> {
    this.db = await openDB<OfflineQueueDB>(this.DB_NAME, this.DB_VERSION, {
      upgrade(db) {
        const store = db.createObjectStore('queued-cards', { keyPath: 'id' })
        store.createIndex('by-created', 'createdAt')
      },
    })
  }

  async addToQueue(id: string, imageBlob: Blob, metadata: CardUploadRequest): Promise<void> {
    if (!this.db) await this.init()

    const queuedCard: QueuedCard = {
      id,
      imageBlob,
      metadata,
      createdAt: Date.now(),
      retryCount: 0,
    }

    await this.db!.add('queued-cards', queuedCard)
    console.log('카드가 오프라인 큐에 추가되었습니다:', id)
  }

  async getAllQueued(): Promise<QueuedCard[]> {
    if (!this.db) await this.init()
    return await this.db!.getAll('queued-cards')
  }

  async removeFromQueue(id: string): Promise<void> {
    if (!this.db) await this.init()
    await this.db!.delete('queued-cards', id)
    console.log('카드가 오프라인 큐에서 제거되었습니다:', id)
  }

  async incrementRetryCount(id: string): Promise<void> {
    if (!this.db) await this.init()
    const card = await this.db!.get('queued-cards', id)
    if (card) {
      card.retryCount++
      await this.db!.put('queued-cards', card)
    }
  }

  async getQueueSize(): Promise<number> {
    if (!this.db) await this.init()
    return await this.db!.count('queued-cards')
  }
}

export const offlineQueue = new OfflineQueue()
