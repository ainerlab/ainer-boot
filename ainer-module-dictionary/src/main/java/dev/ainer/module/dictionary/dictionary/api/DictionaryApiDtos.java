package dev.ainer.module.dictionary.dictionary.api;

import dev.ainer.module.dictionary.dictionary.application.DictionaryPageSlice;
import dev.ainer.module.dictionary.dictionary.domain.DictionaryItem;
import dev.ainer.module.dictionary.dictionary.domain.DictionaryType;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/** API models for the dictionary management surface (ADR-0040). Explicitly designed records. */
public final class DictionaryApiDtos {

    private DictionaryApiDtos() {
    }

    public record CreateTypeRequest(
            @Nullable UUID parentId,
            String code,
            String name,
            @Nullable String nameEn,
            @Nullable String description) {
    }

    public record UpdateTypeRequest(
            @Nullable String name,
            @Nullable String nameEn,
            @Nullable String description,
            @Nullable Integer sortIndex,
            long expectedVersion) {
    }

    public record StatusChangeRequest(String status, long expectedVersion) {
    }

    public record CreateItemRequest(
            String code,
            String label,
            @Nullable String labelEn,
            String value,
            @Nullable Integer sortIndex,
            @Nullable String cssClass,
            @Nullable String remark) {
    }

    public record UpdateItemRequest(
            @Nullable String label,
            @Nullable String labelEn,
            @Nullable String value,
            @Nullable Integer sortIndex,
            @Nullable String cssClass,
            @Nullable String remark,
            long expectedVersion) {
    }

    public record DictionaryTypeResponse(
            UUID id,
            @Nullable UUID parentId,
            String code,
            String name,
            @Nullable String nameEn,
            @Nullable String description,
            String status,
            int sortIndex,
            long version) {

        public static DictionaryTypeResponse from(DictionaryType type) {
            return new DictionaryTypeResponse(
                    type.id(), type.parentId(), type.code(), type.name(), type.nameEn(),
                    type.description(), type.status().name(), type.sortIndex(), type.version());
        }
    }

    public record DictionaryTypePageResponse(
            List<DictionaryTypeResponse> items, int page, int size, long total) {

        public static DictionaryTypePageResponse from(
                DictionaryPageSlice<DictionaryType> slice, int page, int size) {
            return new DictionaryTypePageResponse(
                    slice.items().stream().map(DictionaryTypeResponse::from).toList(),
                    page, size, slice.total());
        }
    }

    public record DictionaryItemResponse(
            UUID id,
            UUID typeId,
            String code,
            String label,
            @Nullable String labelEn,
            @Nullable String value,
            int sortIndex,
            String status,
            @Nullable String cssClass,
            @Nullable String remark,
            long version) {

        public static DictionaryItemResponse from(DictionaryItem item) {
            return new DictionaryItemResponse(
                    item.id(), item.typeId(), item.code(), item.label(), item.labelEn(),
                    item.value(), item.sortIndex(), item.status().name(), item.cssClass(),
                    item.remark(), item.version());
        }
    }

    public record DictionaryItemPageResponse(
            List<DictionaryItemResponse> items, int page, int size, long total) {

        public static DictionaryItemPageResponse from(
                DictionaryPageSlice<DictionaryItem> slice, int page, int size) {
            return new DictionaryItemPageResponse(
                    slice.items().stream().map(DictionaryItemResponse::from).toList(),
                    page, size, slice.total());
        }
    }
}
