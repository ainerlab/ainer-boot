package dev.ainer.module.file.file.infrastructure;

import dev.ainer.module.file.file.application.FileObjectRepository;
import dev.ainer.module.file.file.domain.FileObject;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** MyBatis adapter for {@code ainer_file_object}. */
@Repository
public class MybatisFileObjectRepository implements FileObjectRepository {

    private final FileObjectMapper mapper;

    public MybatisFileObjectRepository(FileObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(FileObject object) {
        mapper.insert(toRow(object));
    }

    @Override
    public Optional<FileObject> findById(UUID id) {
        return Optional.ofNullable(mapper.selectById(id)).map(MybatisFileObjectRepository::toDomain);
    }

    @Override
    public FilePageSlice findPage(@Nullable String namespace, long offset, int size) {
        List<FileObject> items = mapper.selectPage(namespace, offset, size).stream()
                .map(MybatisFileObjectRepository::toDomain)
                .toList();
        long total = mapper.countForPage(namespace);
        return new FilePageSlice(items, total);
    }

    @Override
    public boolean deleteById(UUID id) {
        return mapper.deleteById(id) > 0;
    }

    private static FileObjectRow toRow(FileObject object) {
        FileObjectRow row = new FileObjectRow();
        row.setId(object.id());
        row.setStorageKey(object.storageKey());
        row.setNamespace(object.namespace());
        row.setFilename(object.filename());
        row.setContentType(object.contentType());
        row.setContentLength(object.contentLength());
        row.setChecksumSha256(object.checksumSha256());
        row.setWorkspaceId(object.workspaceId());
        row.setUploadedByIssuer(object.uploadedByIssuer());
        row.setUploadedByType(object.uploadedByType());
        row.setUploadedById(object.uploadedById());
        row.setCreatedAt(object.createdAt());
        return row;
    }

    private static FileObject toDomain(FileObjectRow row) {
        return new FileObject(
                row.getId(),
                row.getStorageKey(),
                row.getNamespace(),
                row.getFilename(),
                row.getContentType(),
                row.getContentLength(),
                row.getChecksumSha256(),
                row.getWorkspaceId(),
                row.getUploadedByIssuer(),
                row.getUploadedByType(),
                row.getUploadedById(),
                row.getCreatedAt());
    }
}
