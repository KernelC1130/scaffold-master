package com.scaffold.vuln.domain.dto;

/**
 * 资产新增/修改请求体。
 * 不含 riskLevel：风险等级由关联漏洞自动计算，写入时强制归零。
 */
public class AssetWriteRequest {

    private String assetIp;
    private String assetName;
    private String owner;
    private String status;
    private String remark;

    public String getAssetIp() { return assetIp; }
    public void setAssetIp(String assetIp) { this.assetIp = assetIp; }
    public String getAssetName() { return assetName; }
    public void setAssetName(String assetName) { this.assetName = assetName; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
