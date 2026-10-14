-- V3.1__audit_time_fix.sql：修复 vuln 三表审计时间列默认值缺失问题
-- 现象：V3 建表时 create_time/update_time 为 DEFAULT NULL，INSERT 未显式写入 → 新记录时间为 NULL，
--       列表"创建时间"显示空、统计（近七天新工单）查不到。
-- 修复：对齐 V1 基座惯例（DEFAULT CURRENT_TIMESTAMP / ON UPDATE CURRENT_TIMESTAMP），并补齐历史 NULL 行。
-- 可重复执行（重复 ALTER 无副作用，UPDATE 只影响 NULL 行）。

ALTER TABLE vuln_asset
  MODIFY create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE vuln_vulnerability
  MODIFY create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE vuln_repair_task
  MODIFY create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

UPDATE vuln_asset         SET create_time = NOW(), update_time = NOW() WHERE create_time IS NULL;
UPDATE vuln_vulnerability SET create_time = NOW(), update_time = NOW() WHERE create_time IS NULL;
UPDATE vuln_repair_task   SET create_time = NOW(), update_time = NOW() WHERE create_time IS NULL;
