import type { HttpClient, QueryParams } from './http.ts'
import { crudPage, type IdResponse, type PageResult } from './system.ts'

/** 资产行（vuln_asset）：riskLevel 由关联漏洞自动计算。 */
export interface AssetRow {
  id: number
  assetIp: string
  assetName: string
  owner: string
  riskLevel: string
  status: string
  remark?: string
  createTime: string
  updateTime: string
}

export interface AssetWriteRequest {
  assetIp: string
  assetName: string
  owner?: string
  status: string
  remark?: string
}

/** 漏洞行（vuln_vulnerability）：vulnStatus 由修复工单自动计算；assetIp/assetName 为 JOIN 展示字段。 */
export interface VulnRow {
  id: number
  assetId: number
  vulnTitle: string
  cveNo: string
  riskLevel: string
  vulnStatus: string
  foundTime: string
  remark?: string
  assetIp: string
  assetName: string
  createTime: string
  updateTime: string
}

export interface VulnWriteRequest {
  assetId: number
  vulnTitle: string
  cveNo?: string
  riskLevel: string
  foundTime: string
  remark?: string
}

/** 修复工单行（vuln_repair_task）：taskStatus 状态机 0待处理/1处理中/2已修复/3已验证/4已关闭。 */
export interface TaskRow {
  id: number
  vulnId: number
  handlerId: number
  taskStatus: string
  priority: string
  progressDesc?: string
  planTime?: string
  finishTime?: string
  verifierId?: number
  verifyRemark?: string
  remark?: string
  vulnTitle: string
  vulnRiskLevel: string
  assetName: string
  assetIp: string
  handlerName: string
  verifierName: string
  createTime: string
  updateTime: string
}

export interface TaskWriteRequest {
  vulnId: number
  handlerId: number
  priority: string
  progressDesc?: string
  planTime?: string
  remark?: string
}

export interface TaskActionRequest {
  pass?: boolean
  finishTime?: string
  progressDesc?: string
  verifyRemark?: string
}

export function createVulnApi(client: HttpClient) {
  return {
    assets: {
      ...crudPage<AssetRow, AssetWriteRequest>(client, '/vuln/assets'),
      export: (query?: QueryParams) => client.download('/vuln/assets/export', query),
    },
    vulnerabilities: {
      ...crudPage<VulnRow, VulnWriteRequest>(client, '/vuln/vulnerabilities'),
      export: (query?: QueryParams) => client.download('/vuln/vulnerabilities/export', query),
    },
    repairTasks: {
      ...crudPage<TaskRow, TaskWriteRequest>(client, '/vuln/repair-tasks'),
      export: (query?: QueryParams) => client.download('/vuln/repair-tasks/export', query),
      accept: (id: number) => client.post<void>(`/vuln/repair-tasks/${id}/accept`),
      fix: (id: number, body: TaskActionRequest) => client.post<void>(`/vuln/repair-tasks/${id}/fix`, body),
      verify: (id: number, body: TaskActionRequest) => client.post<void>(`/vuln/repair-tasks/${id}/verify`, body),
      close: (id: number) => client.post<void>(`/vuln/repair-tasks/${id}/close`),
    },
    statistics: {
      summary: () => client.get<{ totalAssets: number; openVulns: number; highRiskOpen: number; weekTasks: number }>('/vuln/statistics/summary'),
      vulnByRiskLevel: () => client.get<Array<{ name: string; value: number }>>('/vuln/statistics/vulnByRiskLevel'),
      vulnByMonth: () => client.get<Array<{ name: string; value: number }>>('/vuln/statistics/vulnByMonth'),
      topRiskAssets: () => client.get<Array<{ name: string; value: number }>>('/vuln/statistics/topRiskAssets'),
    },
    ai: {
      newSession: (question?: string) => client.post<{ sessionId: number; title: string }>('/vuln/ai/chat/new', { question }),
      sessions: () => client.get<{ list: Array<{ sessionId: number; title: string; updateTime: string }> }>('/vuln/ai/chat/sessions'),
      history: (sessionId: number) => client.get<{ list: Array<{ id: number; role: string; content: string; createTime: string }> }>(`/vuln/ai/chat/history/${sessionId}`),
    },
  }
}

export type VulnApi = ReturnType<typeof createVulnApi>
export type { IdResponse, PageResult }
