package com.scaffold.vuln.mapper;

import com.scaffold.vuln.domain.VulnAssetRow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 资产表访问（vuln_asset）。
 * 分页为 scaffold 手写范式：countXxx + selectXxx(offset, pageSize) 两条 SQL。
 */
public interface VulnAssetMapper {

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM vuln_asset
            <where>
                <if test="assetIp != null and assetIp != ''">AND asset_ip LIKE CONCAT('%', #{assetIp}, '%')</if>
                <if test="assetName != null and assetName != ''">AND asset_name LIKE CONCAT('%', #{assetName}, '%')</if>
                <if test="riskLevel != null and riskLevel != ''">AND risk_level = #{riskLevel}</if>
                <if test="status != null and status != ''">AND status = #{status}</if>
            </where>
            </script>
            """)
    long countAssets(
            @Param("assetIp") String assetIp,
            @Param("assetName") String assetName,
            @Param("riskLevel") String riskLevel,
            @Param("status") String status
    );

    @Select("""
            <script>
            SELECT id,
                   asset_ip,
                   asset_name,
                   owner,
                   risk_level,
                   status,
                   remark,
                   DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS create_time,
                   DATE_FORMAT(update_time, '%Y-%m-%d %H:%i:%s') AS update_time
            FROM vuln_asset
            <where>
                <if test="assetIp != null and assetIp != ''">AND asset_ip LIKE CONCAT('%', #{assetIp}, '%')</if>
                <if test="assetName != null and assetName != ''">AND asset_name LIKE CONCAT('%', #{assetName}, '%')</if>
                <if test="riskLevel != null and riskLevel != ''">AND risk_level = #{riskLevel}</if>
                <if test="status != null and status != ''">AND status = #{status}</if>
            </where>
            ORDER BY id DESC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<VulnAssetRow> selectAssets(
            @Param("assetIp") String assetIp,
            @Param("assetName") String assetName,
            @Param("riskLevel") String riskLevel,
            @Param("status") String status,
            @Param("offset") int offset,
            @Param("pageSize") int pageSize
    );

    @Select("""
            SELECT id, asset_ip, asset_name, owner, risk_level, status, remark,
                   DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS create_time,
                   DATE_FORMAT(update_time, '%Y-%m-%d %H:%i:%s') AS update_time
            FROM vuln_asset
            WHERE id = #{id}
            """)
    VulnAssetRow selectAssetById(@Param("id") Long id);

    /** 行锁取 ID：删除前锁定，防与漏洞写入并发。 */
    @Select("SELECT id FROM vuln_asset WHERE id = #{id} FOR UPDATE")
    Long selectIdForUpdate(@Param("id") Long id);

    @Insert("""
            INSERT INTO vuln_asset (asset_ip, asset_name, owner, risk_level, status, remark, create_by, update_by)
            VALUES (#{row.assetIp}, #{row.assetName}, #{row.owner}, #{row.riskLevel}, #{row.status}, #{row.remark}, #{operator}, #{operator})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "row.id")
    int insertAsset(@Param("row") VulnAssetRow row, @Param("operator") String operator);

    @Update("""
            UPDATE vuln_asset
            SET asset_ip = #{row.assetIp},
                asset_name = #{row.assetName},
                owner = #{row.owner},
                status = #{row.status},
                remark = #{row.remark},
                update_by = #{operator}
            WHERE id = #{id}
            """)
    int updateAsset(@Param("id") Long id, @Param("row") VulnAssetRow row, @Param("operator") String operator);

    /** 定向更新风险等级（由漏洞模块在新增/变更/删除漏洞后联动调用）。 */
    @Update("UPDATE vuln_asset SET risk_level = #{riskLevel}, update_by = #{operator} WHERE id = #{id}")
    int updateRiskLevelById(@Param("id") Long id, @Param("riskLevel") String riskLevel, @Param("operator") String operator);

    /** 资产下未关闭漏洞数（删除校验：存在关联漏洞则禁止删除）。 */
    @Select("SELECT COUNT(*) FROM vuln_vulnerability WHERE asset_id = #{assetId}")
    long countVulnsByAssetId(@Param("assetId") Long assetId);

    @Delete("DELETE FROM vuln_asset WHERE id = #{id}")
    int deleteAsset(@Param("id") Long id);
}
