"""
Pipeline 自动发现与注册

遍历 pipelines/ 目录下所有 .py 文件，自动发现 BasePipeline 的子类，
以 pipeline_id 为键构建 PIPELINES 字典。

加入新 Pipeline：只需在 pipelines/ 下新建 .py 文件，写一个继承 BasePipeline 的类即可。
无需手动编辑本文件。
"""
import importlib
import inspect
import os
import pkgutil
from typing import Dict, List

from .base import BasePipeline


def _discover_pipelines() -> Dict[str, BasePipeline]:
    """扫描 pipelines/ 目录，发现所有 BasePipeline 子类并实例化"""
    discovered: Dict[str, BasePipeline] = {}
    seen_ids: Dict[str, str] = {}  # pipeline_id -> module_name，用于检测重复

    package_dir = os.path.dirname(__file__)

    for _, module_name, _ in pkgutil.iter_modules([package_dir]):
        # 跳过私有模块、基类模块和非 Pipeline 子包
        if module_name.startswith("_") or module_name in ("base", "body_model"):
            continue

        try:
            module = importlib.import_module(f".{module_name}", package="pipelines")
        except Exception as e:
            print(f"[pipeline] [WARN] 导入 {module_name} 失败: {e}")
            continue

        for name, obj in inspect.getmembers(module, inspect.isclass):
            # 只收集 BasePipeline 的直接子类（排除 BasePipeline 自身）
            if not issubclass(obj, BasePipeline) or obj is BasePipeline:
                continue

            # 跳过抽象类（未实现 execute 的中间类）
            if inspect.isabstract(obj):
                continue

            pipeline_id = getattr(obj, "pipeline_id", "")
            if not pipeline_id:
                print(f"[pipeline] [WARN] {module_name}.{name} 未设置 pipeline_id，跳过")
                continue

            if pipeline_id in seen_ids:
                print(
                    f"[pipeline] [ERR] pipeline_id '{pipeline_id}' 冲突: "
                    f"{seen_ids[pipeline_id]}.{name} vs 已注册的实例，跳过"
                )
                continue

            try:
                instance = obj()
            except Exception as e:
                print(f"[pipeline] [WARN] 实例化 {module_name}.{name} 失败: {e}")
                continue

            discovered[pipeline_id] = instance
            seen_ids[pipeline_id] = f"{module_name}.{name}"
            print(
                f"[pipeline] [OK] 注册 {pipeline_id} "
                f"({obj.display_name or obj.__name__}) <- {module_name}.py"
            )

    return discovered


def get_manifest() -> List[Dict]:
    """导出所有已注册 Pipeline 的元数据列表，用于生成 pipeline-manifest.json"""
    manifest = []
    for pipeline_id, instance in PIPELINES.items():
        cls = type(instance)
        if hasattr(cls, "manifest"):
            manifest.append(cls.manifest())
        else:
            manifest.append({
                "pipeline_id": pipeline_id,
                "display_name": getattr(instance, "display_name", ""),
            })
    return manifest


# === 自动发现并构建 PIPELINES ===
PIPELINES = _discover_pipelines()
