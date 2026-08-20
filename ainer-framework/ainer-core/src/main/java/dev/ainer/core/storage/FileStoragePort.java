package dev.ainer.core.storage;

import java.io.InputStream;
import java.util.Optional;

/**
 * 文件存储操作端口（ADR-0038）。Ainer-Boot 提供本地文件系统适配器；产品可通过实现本接口
 * 提供 S3/OSS/MinIO 适配器。
 *
 * <p>该端口刻意保持最小——仅覆盖存储、解析（下载流）与删除。元数据持久化（谁在何时上传、
 * 业务关联）是产品的职责，不属于存储端口。{@code namespace} 参数提供逻辑隔离
 * （例如按 workspace 或按模块分目录），且不把端口耦合到任何业务概念。
 *
 * <p>实现必须：
 * <ul>
 *   <li>保证 {@link #delete} 幂等（key 不存在时返回 {@code false}，不抛异常）；</li>
 *   <li>生成的 {@code storageKey} 在 namespace 内唯一，且在适配器重启后仍然有效；</li>
 *   <li>校验 namespace/filename 以防止路径穿越（本地适配器拒绝 {@code ..} 和绝对路径）；</li>
 *   <li>读取完成后（或失败时）关闭调用方传入的 {@link InputStream}。</li>
 * </ul>
 */
public interface FileStoragePort {

    /**
     * 在指定 namespace 下存储一个文件。
     *
     * @param namespace   用于隔离的逻辑分组（例如 workspace 或模块范围）
     * @param filename    原始或生成的文件名（仅用于展示）
     * @param contentType MIME 类型，未知时为 null
     * @param content     文件内容流（将被实现读取并关闭）
     * @return 存储后的文件元数据，包含生成的 storage key
     * @throws FileStorageException 内容无法读取或持久化失败时抛出
     */
    StoredFile store(String namespace, String filename, String contentType, InputStream content);

    /**
     * 打开输入流以读取此前存储的文件。
     *
     * @param storageKey 由 {@link #store} 返回的 key
     * @return 内容流；key 不存在时为 empty
     * @throws FileStorageException 流无法打开时抛出
     */
    Optional<InputStream> resolve(String storageKey);

    /**
     * 删除已存储的文件。幂等——key 不存在时返回 {@code false}。
     *
     * @param storageKey 由 {@link #store} 返回的 key
     * @return 删除了文件返回 true；key 不存在返回 false
     * @throws FileStorageException 删除因 not-found 之外的原因失败时抛出
     */
    boolean delete(String storageKey);
}
