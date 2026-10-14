package com.scaffold.vuln.ai.rag;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scaffold.system.service.SystemManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

/**
 * 知识库 RAG 管线：
 * 启动时扫描 knowledge 目录下的 .md/.txt 文件，切分（约400字符、重叠50）后
 * 向量化存入 SimpleVectorStore（JSON 持久化到目录内 store.json）；
 * 以「文件路径 + 内容MD5」记录已处理清单（.processed.json），未变化的文件跳过；
 * 文档新增/修改后可调 rebuild 接口重建
 */
@Component
public class KnowledgePipeline {

    private static final Logger log = LoggerFactory.getLogger(KnowledgePipeline.class);

    /** 每段目标长度（字符） */
    private static final int CHUNK_SIZE = 400;
    /** 相邻段重叠长度（字符） */
    private static final int CHUNK_OVERLAP = 50;
    /** 检索返回段数 */
    private static final int TOP_K = 4;

    @Value("${ai.knowledge.dir:./knowledge}")
    private String knowledgeDir;

    @Autowired
    private EmbeddingModelProvider embeddingProvider;

    @Autowired
    private ObjectMapper objectMapper;

    /** 向量库（JSON 持久化到 knowledgeDir/store.json） */
    private volatile SimpleVectorStore store;

    /** 已处理清单：embedding模型 -> 文件路径 -> {md5, ids} */
    private volatile ProcessedIndex processed;

    /** 段落总数（状态展示用） */
    private volatile int chunkCount = 0;

    /** 应用启动后：加载持久化向量库 + 增量扫描知识目录 */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            rebuild();
        } catch (Exception e) {
            log.warn("[AI知识库] 初始化失败，知识问答不可用：{}", e.getMessage());
        }
    }

    /** 全量重建：重新构建向量库并扫描目录内全部文档（含新增/修改/删除的文件） */
    public synchronized String rebuild() {
        EmbeddingModelGuard guard = checkReady();
        File dir = ensureDir();

        SimpleVectorStore newStore = SimpleVectorStore.builder(guard.embeddingModel).build();
        ProcessedIndex newIndex = new ProcessedIndex();
        newIndex.model = guard.modelName;

        List<String> files = listKnowledgeFiles(dir);
        int changed = 0;
        for (String path : files) {
            changed += ingestFile(new File(path), newIndex, newStore, null);
        }
        store = newStore;
        processed = newIndex;
        chunkCount = newIndex.totalChunks();
        persistStoreAndIndex(dir);
        String summary = "[AI知识库] 重建完成：扫描 " + files.size() + " 个文件，"
                + "本次向量化变更 " + changed + " 个，共 " + chunkCount + " 段";
        log.info(summary);
        return summary;
    }

    /** 知识库检索（供 AI 工具调用）：返回「来源文档名 + 段落内容」拼接文本 */
    public String search(String query, int topK) {
        if (store == null || processed == null) {
            return "知识库尚未构建或当前不可用";
        }
        List<Document> docs = store.similaritySearch(
                SearchRequest.builder().query(query).topK(topK).build());
        if (docs == null || docs.isEmpty()) {
            return "知识库中未找到与该问题相关的内容";
        }
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (Document doc : docs) {
            if (sb.length() > 0) {
                sb.append("\n----\n");
            }
            sb.append("【片段").append(i++).append("·来源：")
              .append(doc.getMetadata().getOrDefault("source", "未知")).append("】\n")
              .append(doc.getText());
        }
        return sb.toString();
    }

    /** 知识库状态信息（管理端展示用） */
    public Map<String, Object> status() {
        Map<String, Object> data = new HashMap<>();
        data.put("dir", new File(knowledgeDir).getAbsolutePath());
        data.put("indexedFiles", processed == null ? 0 : processed.files.size());
        data.put("chunks", chunkCount);
        data.put("model", processed == null ? "" : processed.model == null ? "" : processed.model);
        data.put("ready", store != null);
        return data;
    }

    // ---------------- 内部实现 ----------------

    /** 处理单个文件：md5 未变化则跳过；变化/新增则删除旧向量、切分、向量化、写入清单 */
    private int ingestFile(File file, ProcessedIndex index, SimpleVectorStore target, FileRecord oldRecord) {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String md5 = DigestUtils.md5DigestAsHex(bytes);
            if (oldRecord != null && md5.equals(oldRecord.md5)) {
                return 0;
            }
            if (oldRecord != null && oldRecord.ids != null && !oldRecord.ids.isEmpty()) {
                target.delete(oldRecord.ids);
            }
            String text = new String(bytes, StandardCharsets.UTF_8);
            String pathKey = file.getAbsolutePath();
            List<String> ids = new ArrayList<>();
            int i = 0;
            for (String chunk : splitContent(text)) {
                String id = DigestUtils.md5DigestAsHex(pathKey.getBytes(StandardCharsets.UTF_8)) + "-" + i++;
                target.add(List.of(new Document(chunk, Map.of(
                        "source", file.getName(),
                        "path", pathKey,
                        "chunk", i - 1))));
                ids.add(id);
            }
            FileRecord record = new FileRecord();
            record.md5 = md5;
            record.ids = ids;
            index.files.put(pathKey, record);
            return 1;
        } catch (Exception e) {
            log.warn("[AI知识库] 处理文件失败，跳过：{}（{}）", file.getName(), e.getMessage());
            return 0;
        }
    }

    /** 切分文档：先按空行合并段落到目标长度（保持语义完整），超长段落再用滑动窗口切分（重叠 CHUNK_OVERLAP） */
    private List<String> splitContent(String text) {
        List<String> chunks = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        for (String paragraph : text.split("\n\\s*\n")) {
            String p = paragraph.trim();
            if (p.isEmpty()) {
                continue;
            }
            if (p.length() > CHUNK_SIZE) {
                // 先落盘缓冲内容，再对超长段落滑窗切分
                if (buffer.length() > 0) {
                    chunks.add(buffer.toString());
                    buffer.setLength(0);
                }
                int step = CHUNK_SIZE - CHUNK_OVERLAP;
                for (int i = 0; i < p.length(); i += step) {
                    chunks.add(p.substring(i, Math.min(p.length(), i + CHUNK_SIZE)));
                    if (i + CHUNK_SIZE >= p.length()) {
                        break;
                    }
                }
                continue;
            }
            if (buffer.length() + p.length() + 2 > CHUNK_SIZE && buffer.length() > 0) {
                chunks.add(buffer.toString());
                buffer.setLength(0);
            }
            if (buffer.length() > 0) {
                buffer.append("\n\n");
            }
            buffer.append(p);
        }
        if (buffer.length() > 0) {
            chunks.add(buffer.toString());
        }
        return chunks;
    }

    private EmbeddingModelGuard checkReady() {
        EmbeddingModelGuard guard = new EmbeddingModelGuard();
        guard.embeddingModel = embeddingProvider.getEmbeddingModel();
        guard.modelName = trimToEmpty(embeddingProvider.getConfigValue(EmbeddingModelProvider.KEY_MODEL));
        return guard;
    }

    /** 当前可用的向量模型与其配置的模型名 */
    private static class EmbeddingModelGuard {
        private org.springframework.ai.embedding.EmbeddingModel embeddingModel;
        private String modelName;
    }

    private File ensureDir() {
        File dir = new File(knowledgeDir);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("知识库目录创建失败：" + dir.getAbsolutePath());
        }
        return dir;
    }

    /** 列出知识目录下的 .md/.txt 文件（排除向量库与清单自身），按路径排序保证稳定 */
    private List<String> listKnowledgeFiles(File dir) {
        List<String> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir.toPath())) {
            stream.map(Path::toFile)
                    .filter(f -> f.isFile() && (f.getName().toLowerCase().endsWith(".md")
                            || f.getName().toLowerCase().endsWith(".txt")))
                    .map(f -> f.getAbsolutePath())
                    .sorted()
                    .forEach(files::add);
        } catch (Exception e) {
            log.warn("[AI知识库] 扫描目录失败：{}", e.getMessage());
        }
        return files;
    }

    /** 向量库与已处理清单持久化 */
    private void persistStoreAndIndex(File dir) {
        try {
            store.save(new File(dir, "store.json"));
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(new File(dir, ".processed.json"), processed);
        } catch (Exception e) {
            log.warn("[AI知识库] 持久化失败：{}", e.getMessage());
        }
    }

    /** 已处理清单：按文件路径记录 md5 与已写入的向量 id */
    public static class ProcessedIndex {
        public String model = "";
        public Map<String, FileRecord> files = new HashMap<>();

        public int totalChunks() {
            return files.values().stream().mapToInt(r -> r.ids == null ? 0 : r.ids.size()).sum();
        }
    }

    public static class FileRecord {
        public String md5 = "";
        public List<String> ids = new ArrayList<>();
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
