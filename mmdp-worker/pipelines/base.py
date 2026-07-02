"""
Pipeline 基类 — 所有处理脚本必须继承此类

每个 Pipeline 子类需要提供：
  必须:
    pipeline_id: str       — 唯一标识，与后端 pipeline_definition.pipeline_id 对应
    display_name: str      — 显示名称（中文）
    execute(input_dir, output_dir, input_files) -> List[Dict]  — 核心处理方法

  推荐:
    description: str       — 功能描述（显示在管理后台）
    version: str           — 版本号
    input_asset_types: List[str]  — 需要的输入资产类型（如 ["MOCAP_CSV", "RGB_SEQ_RAW"]）
    output_asset_types: List[str] — 产出的资产类型（如 ["ALIGNED_RESULT"]）
    runtime_dependencies: List[str] — 运行时依赖（如 ["ffmpeg"]）

使用方法 — 加一个新 Pipeline 只需两步：
  1. 在 pipelines/ 目录下新建一个 .py 文件
  2. 写一个继承 BasePipeline 的类，实现 execute() 方法
  3. 重启 Worker，自动发现并注册
"""
from abc import ABC, abstractmethod
from typing import List, Dict, Optional


class BasePipeline(ABC):
    """所有 Pipeline 的抽象基类"""

    # === 子类必须覆盖 ===
    pipeline_id: str = ""          # 唯一标识，如 "BUILD_PLAYBACK"
    display_name: str = ""         # 显示名称，如 "图像序列 → MP4"

    # === 子类可选覆盖 ===
    description: str = ""          # 功能描述
    version: str = "1.0.0"         # 版本号
    input_asset_types: List[str] = []   # 输入 AssetType 列表
    output_asset_types: List[str] = []  # 输出 AssetType 列表
    runtime_dependencies: List[str] = []  # 如 ["ffmpeg", "python>=3.10"]

    @abstractmethod
    def execute(
        self,
        input_dir: str,
        output_dir: str,
        input_files: List[Dict],
    ) -> List[Dict]:
        """
        执行 Pipeline 核心逻辑

        Args:
            input_dir:  本地输入根目录，input_files 已按 sourceKey 组织在此目录下
            output_dir: 输出目录，产物放在此目录下
            input_files: claim 返回的 inputFiles 列表，每项结构:
                {
                    "sourceKey": "session-import-xxx",   # 数据来源标识
                    "assetType": "RGB_SEQ_RAW",          # 资产类型
                    "originalFilename": "000001.jpg",    # 原始文件名
                    "objectKey": "mmdp/xxx/yyy.jpg",     # OSS 对象键
                    "bucketName": "mmdp-data",           # OSS 桶名
                    "ossEndpoint": "https://...",        # OSS 端点
                    "fileSize": 1048576,                 # 文件大小（字节）
                }

        Returns:
            产物列表，每项结构:
                {
                    "sourceKey": "session-import-xxx",   # 关联的输入 sourceKey
                    "fileName": "output.mp4",            # 产物文件名
                    "localPath": "/tmp/.../output.mp4",  # 本地路径（upload 后会被删除）
                    "assetType": "RGB_VIDEO_MP4",        # AssetType 枚举值
                    "contentType": "video/mp4",          # MIME 类型
                }
        """
        ...

    @classmethod
    def manifest(cls) -> Dict:
        """导出 Pipeline 元数据，供 Worker 生成 manifest.json"""
        return {
            "pipeline_id": cls.pipeline_id,
            "display_name": cls.display_name,
            "description": cls.description,
            "version": cls.version,
            "input_asset_types": cls.input_asset_types,
            "output_asset_types": cls.output_asset_types,
            "runtime_dependencies": cls.runtime_dependencies,
        }
