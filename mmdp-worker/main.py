"""
mmdp-worker — 多模态数据处理 Worker
轮询后端领取处理任务 → 从 OSS 下载输入 → 执行 Pipeline → 上传产物到 OSS → 上报结果
"""
import os
import sys
import time
import json
import shutil
import traceback
import requests
import oss2

from config import (
    BACKEND_URL, OSS_ENDPOINT, OSS_ACCESS_KEY_ID, OSS_ACCESS_KEY_SECRET,
    OSS_BUCKET, WORK_DIR, POLL_INTERVAL, OUTPUT_PREFIX, validate
)
from pipelines import PIPELINES, get_manifest

# manifest 文件路径
MANIFEST_PATH = os.path.join(os.path.dirname(__file__), "pipeline-manifest.json")


def generate_manifest():
    """生成 pipeline-manifest.json，供后端/前端发现 Worker 支持的 Pipeline"""
    manifest = get_manifest()
    try:
        with open(MANIFEST_PATH, "w", encoding="utf-8") as f:
            json.dump(manifest, f, ensure_ascii=False, indent=2)
        print(f"[manifest] 已生成 {MANIFEST_PATH} ({len(manifest)} 个 Pipeline)")
    except Exception as e:
        print(f"[manifest] [WARN] 写入失败: {e}")


def register_with_backend() -> bool:
    """向 Backend 注册当前 Worker 的 Pipeline 清单"""
    manifest = get_manifest()
    try:
        resp = requests.post(
            f"{BACKEND_URL}/api/worker/pipelines/register",
            json=manifest,
            timeout=10
        )
        data = resp.json()
        if data.get("success"):
            print(f"[register] 已向 Backend 注册 {len(manifest)} 个 Pipeline")
            return True
        else:
            print(f"[register] Backend 返回失败: {data.get('message')}")
            return False
    except Exception as e:
        print(f"[register] 注册失败: {e}")
        return False


def print_registered_pipelines():
    """启动时打印已注册的 Pipeline 列表"""
    if not PIPELINES:
        print("  [WARN] 未发现任何 Pipeline!")
        return
    print(f"  已注册 {len(PIPELINES)} 个 Pipeline:")
    for pid, instance in PIPELINES.items():
        cls = type(instance)
        name = getattr(cls, "display_name", "") or pid
        ver = getattr(cls, "version", "")
        deps = getattr(cls, "runtime_dependencies", [])
        dep_str = f" (依赖: {', '.join(deps)})" if deps else ""
        print(f"    - {pid} -- {name} v{ver}{dep_str}")


def claim_job() -> dict | None:
    """领取一个待处理任务"""
    try:
        resp = requests.post(
            f"{BACKEND_URL}/api/worker/jobs/claim",
            timeout=10
        )
        data = resp.json()
        if data.get("success") and data.get("data"):
            return data["data"]
        # 调试: 后端返回了 success 但没有 data，说明当前无 CREATED 作业
        msg = data.get("message", "")
        if "No pending" not in msg:
            # 非预期的空响应，打印详情便于排查
            print(f"\n[claim] 后端返回空作业, message={msg}, data={data.get('data')}")
        return None
    except requests.JSONDecodeError:
        print(f"\n[claim] 响应非JSON格式, status={resp.status_code}, body={resp.text[:500]}")
        return None
    except Exception as e:
        print(f"\n[claim] 请求失败: {e}")
        return None


def download_files(input_files: list, work_dir: str) -> None:
    """从 OSS 下载输入文件到本地工作目录"""
    auth = oss2.Auth(OSS_ACCESS_KEY_ID, OSS_ACCESS_KEY_SECRET)
    bucket = oss2.Bucket(auth, OSS_ENDPOINT, OSS_BUCKET)

    for f in input_files:
        object_key = f.get("objectKey", "")
        source_key = f.get("sourceKey")
        filename = f.get("originalFilename")

        if not source_key or not filename:
            print(f"[download] 跳过 sourceKey={source_key} filename={filename}")
            continue

        dest_dir = os.path.join(work_dir, source_key)
        os.makedirs(dest_dir, exist_ok=True)
        dest_path = os.path.join(dest_dir, filename)

        print(f"[download] {object_key} → {dest_path}")
        bucket.get_object_to_file(object_key, dest_path)


def upload_outputs(outputs: list, job: dict) -> list:
    """上传产物到 OSS，返回含 objectKey 的产物信息列表"""
    auth = oss2.Auth(OSS_ACCESS_KEY_ID, OSS_ACCESS_KEY_SECRET)
    bucket = oss2.Bucket(auth, OSS_ENDPOINT, OSS_BUCKET)

    session_id = job.get("sessionId", "unknown")
    job_id = job.get("jobId", "unknown")
    prefix = OUTPUT_PREFIX.format(session_id=session_id, job_id=job_id)

    result = []
    for out in outputs:
        object_key = f"{prefix}/{out['fileName']}"
        local_path = out["localPath"]
        file_size = os.path.getsize(local_path)

        print(f"[upload] {local_path} → oss://{OSS_BUCKET}/{object_key}")
        bucket.put_object_from_file(object_key, local_path)

        result.append({
            "sourceKey": out.get("sourceKey", ""),
            "fileName": out.get("fileName", ""),
            "objectKey": object_key,
            "assetType": out.get("assetType", "OTHER"),
            "contentType": out.get("contentType", "application/octet-stream"),
            "fileSize": file_size,
        })

    return result


def report_success(job_id: int, outputs: list) -> bool:
    """上报处理成功"""
    try:
        resp = requests.post(
            f"{BACKEND_URL}/api/worker/jobs/{job_id}/success",
            json={"outputFiles": outputs},
            timeout=30
        )
        data = resp.json()
        return data.get("success", False)
    except Exception as e:
        print(f"[success] 上报失败: {e}")
        return False


def report_failure(job_id: int, error_message: str) -> bool:
    """上报处理失败"""
    try:
        resp = requests.post(
            f"{BACKEND_URL}/api/worker/jobs/{job_id}/failure",
            json={"errorMessage": error_message},
            timeout=10
        )
        data = resp.json()
        return data.get("success", False)
    except Exception as e:
        print(f"[failure] 上报失败: {e}")
        return False


def process_job(job: dict) -> None:
    """处理单个任务"""
    job_id = job["jobId"]
    pipeline_id = job["pipelineId"]
    input_files = job.get("inputFiles", [])

    if not input_files:
        print(f"[job-{job_id}] 无输入文件，标记失败")
        report_failure(job_id, "No input files in claim response")
        return

    pipeline = PIPELINES.get(pipeline_id)
    if not pipeline:
        # ── 详细调试信息：帮助定位 pipeline 匹配失败的原因 ──
        import difflib
        import datetime as _dt
        registered = list(PIPELINES.keys())
        # 字符串相似度匹配，找出最接近的已注册 pipeline
        close_matches = difflib.get_close_matches(pipeline_id, registered, n=3, cutoff=0.4)
        # repr 对比：发现不可见字符（空格、零宽字符、编码差异等）
        pid_repr = repr(pipeline_id)
        registered_repr = {k: repr(k) for k in registered}
        close_repr = {k: registered_repr[k] for k in close_matches} if close_matches else {}

        print(f"\n{'='*60}")
        print(f"[job-{job_id}] ❌ 未知 pipeline，调试信息如下：")
        print(f"  进程 PID: {os.getpid()}")
        print(f"  时间:     {_dt.datetime.now().isoformat()}")
        print(f"  查询 pipeline_id: {pid_repr}")
        print(f"  长度:     {len(pipeline_id)} 字符")
        print(f"  hex:      {pipeline_id.encode('utf-8', errors='replace').hex()}")
        print(f"  已注册 pipeline ({len(registered)} 个):")
        for k in registered:
            marker = " ← 最接近" if k in (close_matches or []) else ""
            r = registered_repr[k]
            match_note = ""
            if k == pipeline_id:
                match_note = " [字符串相等但 dict.get 返回 None — 疑似 key 类型不同！]"
            elif k.strip() == pipeline_id.strip():
                match_note = " [去空格后相等 — 存在首尾空格差异]"
            elif k.lower() == pipeline_id.lower():
                match_note = " [忽略大小写相等]"
            print(f"    {r}{marker}{match_note}")
        if close_repr:
            print(f"  最接近匹配: {close_repr}")
        print(f"  后端返回 job 摘要: jobId={job_id}, taskId={job.get('taskId')}, sessionId={job.get('sessionId')}")
        print(f"{'='*60}")
        report_failure(job_id, f"Unknown pipeline: {pipeline_id}")
        return

    work_dir = os.path.join(WORK_DIR, f"job-{job_id}")
    output_dir = os.path.join(work_dir, "output")
    os.makedirs(output_dir, exist_ok=True)

    success = False
    try:
        # 1. 下载输入文件
        print(f"[job-{job_id}] 下载 {len(input_files)} 个输入文件...")
        download_files(input_files, work_dir)

        # 1.5 保存作业参数到 work_dir，供 Pipeline 读取
        params = job.get("parameters")
        if params:
            params_path = os.path.join(work_dir, "parameters.json")
            with open(params_path, "w", encoding="utf-8") as f:
                json.dump(params, f, ensure_ascii=False)
            print(f"[job-{job_id}] 参数已保存: {params_path}")

        # 2. 执行 Pipeline
        print(f"[job-{job_id}] 执行 {pipeline_id}...")
        outputs = pipeline.execute(work_dir, output_dir, input_files)

        if not outputs:
            raise RuntimeError("Pipeline 未产出任何文件")

        # 3. 上传产物
        print(f"[job-{job_id}] 上传 {len(outputs)} 个产物...")
        uploaded = upload_outputs(outputs, job)

        # 4. 上报成功
        ok = report_success(job_id, uploaded)
        if ok:
            success = True
            print(f"[job-{job_id}] [OK] 完成")
        else:
            print(f"[job-{job_id}] [WARN] 处理完成但上报失败")

    except Exception as e:
        error_msg = f"{e}\n{traceback.format_exc()}"
        print(f"[job-{job_id}] [ERR] 失败: {e}")
        report_failure(job_id, error_msg[:5000])

    finally:
        if success:
            # 成功后清理临时文件
            if os.path.exists(work_dir):
                shutil.rmtree(work_dir, ignore_errors=True)
        else:
            # 失败时保留 work_dir，方便排查
            print(f"[job-{job_id}] [KEEP] 工作目录已保留: {work_dir}")


def main():
    # 支持 --list-pipelines 查看已注册的 Pipeline
    if "--list-pipelines" in sys.argv:
        print("=" * 50)
        print("mmdp-worker 已注册 Pipeline 列表")
        print("=" * 50)
        print_registered_pipelines()
        print()
        generate_manifest()
        print()
        print("在管理后台创建处理规则时，pipelineId 请使用上述列表中的值。")
        return

    print("=" * 50)
    print("mmdp-worker 启动")
    print(f"  后端: {BACKEND_URL}")
    print(f"  OSS: {OSS_ENDPOINT} / {OSS_BUCKET}")
    print(f"  工作目录: {WORK_DIR}")
    print(f"  轮询间隔: {POLL_INTERVAL}s")
    print("=" * 50)
    print_registered_pipelines()
    print("=" * 50)

    validate()

    # 生成 manifest 文件（供本地调试查看）
    generate_manifest()

    # 向 Backend 注册 Pipeline 清单
    register_with_backend()

    last_register_time = time.time()
    while True:
        # 每 60 秒向 Backend 心跳注册（处理 Backend 重启导致注册表清空的场景）
        now = time.time()
        if now - last_register_time > 60:
            register_with_backend()
            last_register_time = now

        job = claim_job()
        if job:
            job_id = job.get("jobId", "?")
            print(f"\n[领取] job-{job_id} pipeline={job.get('pipelineId')}")
            process_job(job)
        else:
            print(".", end="", flush=True)
            time.sleep(POLL_INTERVAL)


if __name__ == "__main__":
    main()
