package dev.ainer.module.config.config.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ConfigEntryMapper {
    UUID insertReturningId(@Param("row") ConfigEntryRow row, @Param("now") java.time.Instant now);
    ConfigEntryRow selectByNamespaceAndKey(@Param("namespace") String namespace, @Param("key") String configKey);
    List<ConfigEntryRow> selectByNamespace(@Param("namespace") String namespace);
    int updateValue(@Param("id") UUID id, @Param("configValue") String configValue,
                    @Param("encryptedValue") String encryptedValue,
                    @Param("expectedVersion") long expectedVersion,
                    @Param("newVersion") long newVersion, @Param("now") java.time.Instant now);
}
