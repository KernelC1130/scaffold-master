package com.scaffold.vuln.service;

import com.scaffold.common.domain.PageQuery;
import com.scaffold.common.domain.PageResult;
import com.scaffold.common.exception.BusinessException;
import com.scaffold.vuln.domain.VulnAssetRow;
import com.scaffold.vuln.domain.dto.AssetWriteRequest;
import com.scaffold.vuln.mapper.VulnAssetMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 资产业务：CRUD、导出与删除校验。
 * 业务规则（迁移自原漏洞平台）：
 * - risk_level 由关联漏洞自动计算，新增强制 '0'，编辑不触碰；
 * - 删除前锁行并校验无关联漏洞。
 */
@Service
public class VulnAssetService {

    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");
    private static final List<String> RISK_LEVELS = List.of("0", "1", "2");
    private static final List<String> ASSET_STATUSES = List.of("0", "1");
    private static final int EXPORT_LIMIT = 5000;

    private final VulnAssetMapper mapper;

    public VulnAssetService(VulnAssetMapper mapper) {
        this.mapper = mapper;
    }

    public PageResult<VulnAssetRow> assets(PageQuery query, String assetIp, String assetName, String riskLevel, String status) {
        Page page = pageOf(query);
        long total = mapper.countAssets(assetIp, assetName, riskLevel, status);
        return PageResult.of(mapper.selectAssets(assetIp, assetName, riskLevel, status, page.offset(), page.pageSize()), total, page.pageNum(), page.pageSize());
    }

    public String exportAssets(String assetIp, String assetName, String riskLevel, String status) {
        List<VulnAssetRow> rows = mapper.selectAssets(assetIp, assetName, riskLevel, status, 0, EXPORT_LIMIT);
        List<List<Object>> data = rows.stream()
                .map(row -> csvRow(
                        row.getAssetIp(),
                        row.getAssetName(),
                        row.getOwner(),
                        riskLevelText(row.getRiskLevel()),
                        statusText(row.getStatus()),
                        row.getRemark(),
                        row.getCreateTime() == null ? "" : row.getCreateTime().toString()
                ))
                .toList();
        return csv(List.of("IP地址", "服务/应用名", "负责人", "风险等级", "状态", "备注", "创建时间"), data);
    }

    public VulnAssetRow asset(Long id) {
        VulnAssetRow row = mapper.selectAssetById(id);
        if (row == null) {
            throw new BusinessException("资产不存在");
        }
        return row;
    }

    public Long createAsset(AssetWriteRequest request) {
        VulnAssetRow row = new VulnAssetRow();
        applyWrite(row, request);
        row.setRiskLevel("0"); // 风险等级由漏洞自动计算，新建归零
        mapper.insertAsset(row, currentUsername());
        return row.getId();
    }

    public void updateAsset(Long id, AssetWriteRequest request) {
        if (mapper.selectAssetById(id) == null) {
            throw new BusinessException("资产不存在");
        }
        VulnAssetRow row = new VulnAssetRow();
        applyWrite(row, request);
        if (mapper.updateAsset(id, row, currentUsername()) == 0) {
            throw new BusinessException("资产不存在");
        }
    }

    @Transactional
    public void deleteAsset(Long id) {
        if (mapper.selectIdForUpdate(id) == null) {
            throw new BusinessException("资产不存在");
        }
        if (mapper.countVulnsByAssetId(id) > 0) {
            throw new BusinessException("该资产下存在关联漏洞，请先处理漏洞再删除");
        }
        mapper.deleteAsset(id);
    }

    private void applyWrite(VulnAssetRow row, AssetWriteRequest request) {
        row.setAssetIp(validIp(request.getAssetIp()));
        row.setAssetName(required(request.getAssetName(), "服务/应用名不能为空"));
        if (row.getAssetName().length() > 128) {
            throw new BusinessException("服务/应用名不能超过 128 字");
        }
        String owner = trimToNull(request.getOwner());
        if (owner != null && owner.length() > 64) {
            throw new BusinessException("负责人不能超过 64 字");
        }
        row.setOwner(owner == null ? "" : owner);
        String status = request.getStatus() == null ? "0" : request.getStatus();
        if (!ASSET_STATUSES.contains(status)) {
            throw new BusinessException("状态取值不合法");
        }
        row.setStatus(status);
        String remark = trimToNull(request.getRemark());
        if (remark != null && remark.length() > 500) {
            throw new BusinessException("备注不能超过 500 字");
        }
        row.setRemark(remark);
    }

    /** IPv4 校验（每段 0-255；拒绝 IPv6 与域名，与原平台 ValidIp 语义一致）。 */
    private String validIp(String value) {
        String ip = required(value, "IP地址不能为空");
        var m = IPV4.matcher(ip);
        if (!m.matches()) {
            throw new BusinessException("IP地址格式不合法（仅支持 IPv4）");
        }
        for (int i = 1; i <= 4; i++) {
            int seg = Integer.parseInt(m.group(i));
            if (seg > 255) {
                throw new BusinessException("IP地址格式不合法（每段需在 0-255 之间）");
            }
        }
        return ip;
    }

    private String riskLevelText(String level) {
        return switch (level == null ? "" : level) {
            case "0" -> "低";
            case "1" -> "中";
            case "2" -> "高";
            default -> "";
        };
    }

    private String statusText(String status) {
        return "1".equals(status) ? "停用" : "正常";
    }

    private String required(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new BusinessException(message);
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "system" : String.valueOf(authentication.getPrincipal());
    }

    private record Page(int pageNum, int pageSize, int offset) {
    }

    private Page pageOf(PageQuery query) {
        int pageNum = query.pageNum() == null || query.pageNum() < 1 ? 1 : query.pageNum();
        int pageSize = query.pageSize() == null || query.pageSize() < 1 ? 10 : Math.min(query.pageSize(), 100);
        return new Page(pageNum, pageSize, (pageNum - 1) * pageSize);
    }

    private String csv(List<String> headers, List<List<Object>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", headers)).append('\n');
        for (List<Object> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(csvCell(row.get(i)));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private List<Object> csvRow(Object... values) {
        return Arrays.asList(values);
    }

    private String csvCell(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if (text.contains("\"")) {
            text = text.replace("\"", "\"\"");
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text + "\"";
        }
        return text;
    }
}
