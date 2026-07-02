package com.honortech.dataplatform.common.enums;

/**
 * 资产血缘关系类型，描述两个资产之间或资产与外部实体之间的溯源关系。
 */
public enum AssetLineageRelationType {
    /** 处理作业的输入输出关系 */
    JOB_INPUT_OUTPUT,
    /** 上传产生的资产（上传文件 → 数据资产） */
    UPLOAD_PRODUCED,
    /** 外部导入产生的资产（ZED等外部设备导入 → 数据资产） */
    IMPORT_PRODUCED,
    /** QC检查关系（资产 → QC报告） */
    QC_CHECKED,
    /** 资产派生关系（非job场景的资产衍生） */
    DERIVED_FROM
}
