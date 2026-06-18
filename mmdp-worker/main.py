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
from pipelines import PIPELINES


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
        return None
    except Exception as e:
        print(f"[claim] 请求失败: {e}")
        return None


def download_files(input_files: list, work_dir: str) -> None:
    """从 OSS 下载输入文件到本地工作目录"""
    auth = oss2.Auth(OSS_ACCESS_KEY_ID, OSS_ACCESS_KEY_SECRET)
    bucket = oss2.Bucket(auth, OSS_ENDPOINT, OSS_BUCKET)

    for f in input_files:
        object_key = f.get("objectKey", "")
        source_key = f.get("sourceKey")
        filename = f.get("originalFilename")

        # 跳过 sourceKey 或文件名为空的记录（如 manifest.json）
        if not source_key or not filename:
            print(f"[download] 跳过 sourceKey={source_key} filename={filename}")
            continue

        # 按 sourceKey 组织本地目录结构
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

    # 选择 Pipeline
    pipeline = PIPELINES.get(pipeline_id)
    if not pipeline:
        print(f"[job-{job_id}] 未知 pipeline: {pipeline_id}")
        report_failure(job_id, f"Unknown pipeline: {pipeline_id}")
        return

    work_dir = os.path.join(WORK_DIR, f"job-{job_id}")
    output_dir = os.path.join(work_dir, "output")
    os.makedirs(output_dir, exist_ok=True)

    try:
        # 1. 下载输入文件
        print(f"[job-{job_id}] 下载 {len(input_files)} 个输入文件...")
        download_files(input_files, work_dir)

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
            print(f"[job-{job_id}] ✅ 完成")
        else:
            print(f"[job-{job_id}] ⚠️ 处理完成但上报失败")

    except Exception as e:
        error_msg = f"{e}\n{traceback.format_exc()}"
        print(f"[job-{job_id}] ❌ 失败: {e}")
        report_failure(job_id, error_msg[:5000])

    finally:
        # 清理临时文件
        if os.path.exists(work_dir):
            shutil.rmtree(work_dir, ignore_errors=True)


def main():
    print("=" * 50)
    print("mmdp-worker 启动")
    print(f"  后端: {BACKEND_URL}")
    print(f"  OSS: {OSS_ENDPOINT} / {OSS_BUCKET}")
    print(f"  工作目录: {WORK_DIR}")
    print(f"  轮询间隔: {POLL_INTERVAL}s")
    print("=" * 50)

    validate()

    while True:
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
