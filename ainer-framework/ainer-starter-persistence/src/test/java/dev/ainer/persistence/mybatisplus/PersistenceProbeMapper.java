package dev.ainer.persistence.mybatisplus;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

public interface PersistenceProbeMapper extends BaseMapper<PersistenceProbeRow> {

    List<String> selectNamesByScope(@Param("scopeId") UUID scopeId);
}
