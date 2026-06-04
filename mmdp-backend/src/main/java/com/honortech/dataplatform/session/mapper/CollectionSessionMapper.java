package com.honortech.dataplatform.session.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.honortech.dataplatform.session.dto.SessionListItemResponse;
import com.honortech.dataplatform.session.dto.SessionListQueryRequest;
import com.honortech.dataplatform.session.entity.CollectionSession;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface CollectionSessionMapper extends BaseMapper<CollectionSession> {

    @Select({
            "<script>",
            "SELECT",
            " s.id AS id,",
            " s.session_code AS sessionCode,",
            " s.task_id AS taskId,",
            " t.task_code AS taskCode,",
            " t.task_name AS taskName,",
            " s.session_id AS sessionId,",
            " s.subject_code AS subjectCode,",
            " s.action_name AS actionName,",
            " p.profile_name AS profileName,",
            " t.modality AS modality,",
            " s.started_at AS startedAt,",
            " s.created_at AS createdAt,",
            " s.upload_status AS uploadStatus,",
            " COALESCE(qcAgg.qc_status, 'PENDING') AS qcStatus,",
            " CASE WHEN COALESCE(assetAgg.exportable_count, 0) &gt; 0 THEN 'READY' ELSE 'PENDING' END AS exportStatus,",
            " COALESCE(assetAgg.asset_count, 0) AS assetCount,",
            " COALESCE(fileAgg.file_count, 0) AS fileCount,",
            " COALESCE(fileAgg.total_size, 0) AS totalSize,",
            " COALESCE(assetAgg.source_summary, '暂无资产') AS sourceSummary",
            "FROM collection_session s",
            "LEFT JOIN acquisition_task t ON t.id = s.task_id",
            "LEFT JOIN collection_profile p ON p.id = s.profile_id",
            "LEFT JOIN (",
            "  SELECT f.session_id AS session_id, COUNT(*) AS file_count, COALESCE(SUM(f.file_size), 0) AS total_size",
            "  FROM data_file f",
            "  GROUP BY f.session_id",
            ") fileAgg ON fileAgg.session_id = s.id",
            "LEFT JOIN (",
            "  SELECT a.session_id AS session_id,",
            "         COUNT(*) AS asset_count,",
            "         COALESCE(SUM(CASE WHEN df.storage_url IS NOT NULL AND df.storage_url != '' THEN 1 ELSE 0 END), 0) AS exportable_count,",
            "         GROUP_CONCAT(DISTINCT COALESCE(a.source_key, a.asset_type) ORDER BY COALESCE(a.source_key, a.asset_type) SEPARATOR ' / ') AS source_summary",
            "  FROM data_asset a",
            "  LEFT JOIN data_file df ON df.id = a.file_id",
            "  GROUP BY a.session_id",
            ") assetAgg ON assetAgg.session_id = s.id",
            "LEFT JOIN (",
            "  SELECT q.session_id AS session_id,",
            "         CASE",
            "           WHEN MAX(CASE WHEN q.qc_status IN ('QC_FAILED', 'FAILED') THEN 3 WHEN q.qc_status IN ('QC_WARNING', 'WARNING') THEN 2 WHEN q.qc_status IN ('QC_PASSED', 'PASSED') THEN 1 ELSE 0 END) = 3 THEN 'QC_FAILED'",
            "           WHEN MAX(CASE WHEN q.qc_status IN ('QC_FAILED', 'FAILED') THEN 3 WHEN q.qc_status IN ('QC_WARNING', 'WARNING') THEN 2 WHEN q.qc_status IN ('QC_PASSED', 'PASSED') THEN 1 ELSE 0 END) = 2 THEN 'QC_WARNING'",
            "           WHEN MAX(CASE WHEN q.qc_status IN ('QC_FAILED', 'FAILED') THEN 3 WHEN q.qc_status IN ('QC_WARNING', 'WARNING') THEN 2 WHEN q.qc_status IN ('QC_PASSED', 'PASSED') THEN 1 ELSE 0 END) = 1 THEN 'QC_PASSED'",
            "           ELSE 'PENDING'",
            "         END AS qc_status",
            "  FROM qc_report q",
            "  WHERE q.session_id IS NOT NULL",
            "  GROUP BY q.session_id",
            ") qcAgg ON qcAgg.session_id = s.id",
            "WHERE 1 = 1",
            "<if test='query.taskId != null'> AND s.task_id = #{query.taskId}</if>",
            "<if test='(query.sessionId != null and query.sessionId != \"\") or (query.sessionCode != null and query.sessionCode != \"\")'>",
            " AND (",
            "   <if test='query.sessionId != null and query.sessionId != \"\"'> s.session_id = #{query.sessionId}</if>",
            "   <if test='query.sessionId != null and query.sessionId != \"\" and query.sessionCode != null and query.sessionCode != \"\"'> OR </if>",
            "   <if test='query.sessionCode != null and query.sessionCode != \"\"'> s.session_code = #{query.sessionCode}</if>",
            " )",
            "</if>",
            "<if test='query.uploadStatus != null and query.uploadStatus != \"\"'> AND s.upload_status = #{query.uploadStatus}</if>",
            "<if test='query.modality != null and query.modality != \"\"'> AND t.modality LIKE CONCAT('%', #{query.modality}, '%')</if>",
            "<if test='query.startedAtFrom != null'> AND s.started_at <![CDATA[ >= ]]> #{query.startedAtFrom}</if>",
            "<if test='query.startedAtTo != null'> AND s.started_at <![CDATA[ <= ]]> #{query.startedAtTo}</if>",
            "<if test='query.qcStatus != null and query.qcStatus != \"\"'> AND COALESCE(qcAgg.qc_status, 'PENDING') = #{query.qcStatus}</if>",
            "<if test='query.exportStatus != null and query.exportStatus != \"\"'> AND CASE WHEN COALESCE(assetAgg.exportable_count, 0) &gt; 0 THEN 'READY' ELSE 'PENDING' END = #{query.exportStatus}</if>",
            "ORDER BY ${orderByClause}",
            "</script>"
    })
    IPage<SessionListItemResponse> selectSessionListPage(
            Page<SessionListItemResponse> page,
            @Param("query") SessionListQueryRequest query,
            @Param("orderByClause") String orderByClause
    );
}
