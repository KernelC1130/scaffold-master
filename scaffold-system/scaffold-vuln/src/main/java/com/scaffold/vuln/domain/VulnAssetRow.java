package com.scaffold.vuln.domain;

/**
 * 资产行（vuln_asset）。
 * asset_ip IP地址；asset_name 服务/应用名；owner 负责人；
 * risk_level 风险等级 0低/1中/2高（由关联漏洞自动计算）；status 状态 0正常/1停用；
 * create_time / update_time 由 SQL DATE_FORMAT 为 yyyy-MM-dd HH:mm:ss 展示串。
 */
public class VulnAssetRow {

    private Long id;
    private String assetIp;
    private String assetName;
    private String owner;
    private String riskLevel;
    private String status;
    private String remark;
    private String createTime;
    private String updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAssetIp() { return assetIp; }
    public void setAssetIp(String assetIp) { this.assetIp = assetIp; }
    public String getAssetName() { return assetName; }
    public void setAssetName(String assetName) { this.assetName = assetName; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
    public String getUpdateTime() { return updateTime; }
    public void setUpdateTime(String updateTime) { this.updateTime = updateTime; }
}
