package {{package.name}}.crud;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.UUID;

/**
 * MyBatis-Plus mapper for {@code {{table.name}}} (manifest v1, ADR-0036). The insert
 * relies on the PostgreSQL {@code uuidv7()} default: the SQL returns the generated ID
 * instead of the application fabricating one (ADR-0020).
 */
@Mapper
public interface {{entity.className}}Mapper extends BaseMapper<{{entity.className}}Entity> {

    @Select("INSERT INTO {{table.name}} ({{entity.insertColumns}}) VALUES ({{entity.insertParams}}) RETURNING id")
    UUID insertReturningId({{entity.className}}Entity row);
}