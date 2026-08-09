package {{package.name}}.crud;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Application service for {@code {{table.name}}} (manifest v1, ADR-0036). All SQL is
 * bound-parameter only; the identity is always the PostgreSQL-generated uuidv7 value.
 */
@Service
public class {{entity.className}}ApplicationService {

    private final {{entity.className}}Mapper mapper;

    public {{entity.className}}ApplicationService({{entity.className}}Mapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public {{entity.className}}Entity create({{entity.className}}Entity row) {
        Instant now = Instant.now();
        row.setId(null);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        UUID id = mapper.insertReturningId(row);
        return get(id);
    }

    public {{entity.className}}Entity get(UUID id) {
        {{entity.className}}Entity row = mapper.selectById(id);
        if (row == null) {
            throw new BusinessException(StandardErrorCode.NOT_FOUND,
                    "{{entity.className}} 不存在: " + id);
        }
        return row;
    }

    public IPage<{{entity.className}}Entity> page(long current, long size) {
        QueryWrapper<{{entity.className}}Entity> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("updated_at");
        return mapper.selectPage(new Page<>(current, size), wrapper);
    }

    @Transactional
    public {{entity.className}}Entity update(UUID id, {{entity.className}}Entity changes) {
        {{entity.className}}Entity existing = get(id);
        changes.setId(id);
        changes.setCreatedAt(existing.getCreatedAt());
        changes.setUpdatedAt(Instant.now());
        mapper.updateById(changes);
        return get(id);
    }

    @Transactional
    public void delete(UUID id) {
        get(id);
        mapper.deleteById(id);
    }
}