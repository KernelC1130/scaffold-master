package com.scaffold.vuln.mapper;

import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/** 工作台安全态势聚合（只读，无框架依赖）。 */
public interface VulnStatisticsMapper {

    @Select("SELECT COUNT(*) FROM vuln_asset")
    long countAssets();

    /** 未闭合漏洞：新发现 + 修复中（已修复未关闭不计入风险敞口）。 */
    @Select("SELECT COUNT(*) FROM vuln_vulnerability WHERE vuln_status IN ('0', '1')")
    long countOpenVulns();

    @Select("SELECT COUNT(*) FROM vuln_vulnerability WHERE risk_level = '2' AND vuln_status IN ('0', '1')")
    long countHighRiskOpen();

    @Select("SELECT COUNT(*) FROM vuln_repair_task WHERE create_time >= DATE_SUB(NOW(), INTERVAL 7 DAY)")
    long countTasksThisWeek();

    @Select("""
            SELECT CASE risk_level WHEN '0' THEN '低' WHEN '1' THEN '中' WHEN '2' THEN '高' END AS name,
                   COUNT(*) AS value
            FROM vuln_vulnerability
            GROUP BY risk_level ORDER BY risk_level
            """)
    List<Map<String, Object>> selectVulnByRiskLevel();

    @Select("""
            SELECT DATE_FORMAT(found_time, '%Y-%m') AS name, COUNT(*) AS value
            FROM vuln_vulnerability
            WHERE found_time IS NOT NULL
            GROUP BY DATE_FORMAT(found_time, '%Y-%m') ORDER BY name
            """)
    List<Map<String, Object>> selectVulnByMonth();

    @Select("""
            SELECT IFNULL(a.asset_name, '未关联资产') AS name, COUNT(*) AS value
            FROM vuln_vulnerability v
                     LEFT JOIN vuln_asset a ON v.asset_id = a.id
            WHERE v.risk_level = '2'
            GROUP BY v.asset_id, a.asset_name
            ORDER BY value DESC LIMIT 5
            """)
    List<Map<String, Object>> selectTopRiskAssets();
}
