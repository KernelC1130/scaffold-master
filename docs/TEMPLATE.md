# 新项目接入指南

本仓库双分支分工：

| 分支 | 内容 | 用途 |
| --- | --- | --- |
| `template` | 干净底座：认证、菜单/按钮权限、数据权限、组织/岗位、字典、参数、日志、工作台、代码生成 | **新项目从这里开始**（GitHub 上可用 Use this template / fork） |
| `main` | 完整参考实现：template 全部能力 + 漏洞管理业务样例 + AI 助手（Spring AI 对话/SSE/RAG/Function Calling） | 查「这件事在真实业务里怎么做」 |

## 1. 前置依赖

- JDK 17+、Node 18+、MySQL 8、Redis
- 后端端口等配置：`scaffold-system/scaffold-admin/src/main/resources/application.yml`

## 2. 初始化数据库

按序执行 `scaffold-system/sql/` 下仅存的两个脚本：

1. `V1__init_schema.sql` —— 全部表结构（sys_user / sys_role / sys_menu / sys_dept / sys_post / sys_dict_* / sys_config / sys_oper_log / sys_login_log 及关联表）
2. `V2__seed_data.sql` —— 种子数据：内置账号、框架菜单、通用字典、参数（含账号初始密码 `Yy@123456`）

初始账号：`admin`，密码为初始密码 `Yy@123456`（首登后可在个人中心修改）。

## 3. 启动与验证清单

```bash
# 后端
cd scaffold-system && mvn -pl scaffold-admin -am spring-boot:run
# 前端
cd scaffold-vue && npm install && npm run dev
```

- [ ] 登录页可登录（验证码、记住登录）
- [ ] 用户 / 角色 / 菜单 / 部门 / 岗位 CRUD 正常
- [ ] 字典「样本册」与参数「面板」视图正常
- [ ] 操作日志 / 登录日志有数据（登录后即有）
- [ ] 代码生成：导入一张表 → 下载前后端骨架
- [ ] 工作台：登录脉搏、操作量、成功率、数据权限分布、当日操作泳道

## 4. 新增一个业务模块（标准路径）

1. **建表**：业务表带四件套 `create_by / update_by / create_time / update_time`
2. **后端**：在 `scaffold-system` 下按 `controller / service / mapper / domain` 四层手写，或用「代码生成」页导骨架；Mapper 记得加入 `ScaffoldAdminApplication` 的 `@MapperScan`
3. **前端**：`src/views/<模块>/` 用 `ListPage + useListPage` 组装（参考 `views/system/Users.vue`）；页面组件路径登记进 `composables/auth.ts` 的 `COMPONENT_MAP`
4. **菜单**：`sys_menu` 插目录/页面/按钮三行（权限标识 `<模块>:<资源>:<动作>`），角色授权后即可见
5. **导出**：复用 `useListPage` 的导出能力，不自拼

## 5. 品牌替换清单（改品牌只动这些）

| 位置 | 内容 |
| --- | --- |
| `scaffold-vue/src/data/index.ts` | `APP` 常量：name（标题后缀）/ shortName / subtitle / copyright |
| `scaffold-vue/index.html` | 静态 `<title>` |
| `scaffold-vue/src/components/business/LoginBrandPanel.vue` | 登录品牌区大标题与副文案（品牌名/副标/版权已绑 APP 常量） |
| `scaffold-vue/public/favicon.*`、`icon-*.png` | 图标（ico/png/svg/192/512） |
| `scaffold-vue/public/manifest.json`、`sw.js` | PWA 名称与缓存版本号（换静态资源时升版本） |
| `scaffold-system/scaffold-admin/src/main/resources/application.yml` | 后端侧名称（如有） |

## 6. 约定与已知问题

- **列表视口固定**：列表页统一 `fit-viewport`（查询/分页固定、表体滚动）；**审计/日志类列表刻意不固定**（整页滚动阅读流）；画布类 alt 视图（星图/矩阵/画布/阶梯）接 `alt-fit` 与列表同底
- **参数分组**：无分组字段，参数「名称」写成 `卡片名-参数名` 即自动归卡（`-` `－` `—` `·` 均可）；卡片名 = 渲染时从该组参数名前缀推导，改卡名 = 批量改前缀
- **看板拖拽**：取消回弹在列体滚动后可能错位（已知问题，见 changelog 2026-10-15）
- **验证码**：算术式，服务端渲染 PNG、答案只存 Redis——不要改成把答案回传前端

## 7. AI 助手与漏洞业务样例

`main` 分支包含完整实现，可按需搬移：

- 后端 `com.scaffold.vuln.ai`：Spring AI ChatClient 动态装配、SSE 流式、会话持久化、RAG 知识库（SimpleVectorStore）、Function Calling 工具、参数配置页测试/重建入口
- 前端 `components/ai/AiAssistant.vue`：全局悬浮对话窗
- 搬移提示：包名/接口路径/权限标识（`vuln:ai:*`）需去掉 vuln 前缀通用化；`quickTo` 不支持 `scale` 别名（拆 scaleX/scaleY）
