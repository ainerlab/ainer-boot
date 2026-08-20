package dev.ainer.module.file.file.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/** {@code ainer_file_object} 的 MyBatis mapper；SQL 位于 {@code mapper/file/FileObjectMapper.xml}。 */
@Mapper
public interface FileObjectMapper {

    int insert(@Param("row") FileObjectRow row);

    FileObjectRow selectById(@Param("id") UUID id);

    List<FileObjectRow> selectPage(
            @Nullable @Param("namespace") String namespace,
            @Param("offset") long offset,
            @Param("limit") int limit);

    long countForPage(@Nullable @Param("namespace") String namespace);

    int deleteById(@Param("id") UUID id);
}
