<template>
  <div class="space-y-5">
    <section class="app-shell-panel rounded-[18px] px-5 py-5">
      <div class="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
        <div class="min-w-0 space-y-3">
          <div class="flex flex-wrap items-center gap-2 text-xs font-medium tracking-[0.14em] text-slate-400">
            <span class="rounded-full bg-[var(--color-brand-50)] px-3 py-1 text-[var(--color-brand-600)]">任务上下文</span>
            <span class="rounded-full bg-slate-100 px-3 py-1 text-slate-500">主线：任务 -> 资产 -> 处理作业 -> 派生资产 -> 数据链路</span>
          </div>

          <div class="space-y-2">
            <div class="flex flex-wrap items-center gap-3">
              <h1 class="text-2xl font-semibold tracking-tight text-slate-900">{{ task?.taskName || "任务详情" }}</h1>
              <span class="app-pill border-[var(--color-brand-200)] bg-[var(--color-brand-50)] text-[var(--color-brand-700)]">
                {{ task?.subjectCode || "未填写被试编号" }}
              </span>
              <span class="app-pill border-slate-200 bg-slate-50 text-slate-700">{{ task?.actionName || "未填写动作名称" }}</span>
              <StatusBadge :status="task?.status" :label="currentTaskStatus.label" />
            </div>
            <div class="flex flex-wrap gap-x-4 gap-y-2 text-sm text-slate-500">
              <span>采集日期：{{ task?.collectDate || "-" }}</span>
              <span v-if="task?.deviceType">设备类型：{{ task.deviceType }}</span>
              <span v-if="task?.modality">数据模态：{{ task.modality }}</span>
              <span v-if="task?.captureLocation">采集地点：{{ task.captureLocation }}</span>
              <span v-if="task?.operatorName">采集人员：{{ task.operatorName }}</span>
              <span v-if="task?.scene">场景：{{ task.scene }}</span>
            </div>
          </div>
        </div>

        <div class="flex flex-wrap gap-2 xl:justify-end">
          <BaseButton @click="refreshTaskWorkspace">刷新数据</BaseButton>
        </div>
      </div>

      <div class="mt-4 grid gap-3 xl:grid-cols-[minmax(0,1.4fr)_minmax(260px,0.8fr)]">
        <div class="rounded-[16px] border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600">
          任务是当前数据生命周期的容器，负责承载资产接入、处理记录、派生产物与数据链路追踪。页面按阶段分组，帮助你顺着“资产进入平台 -> 记录处理过程 -> 查看派生产物与链路”的主线完成工作。
        </div>
        <div class="rounded-[16px] border border-slate-200 bg-white px-4 py-3 text-sm text-slate-600">
          <div class="text-xs font-medium tracking-[0.14em] text-slate-400">当前数据状态</div>
          <div class="mt-1 font-semibold text-slate-900">{{ currentTaskStatus.label }}</div>
          <p class="mt-2 text-xs leading-5 text-slate-500">
            {{ currentTaskStatus.description }}
          </p>
          <p class="mt-2 text-xs text-slate-400">状态原值：{{ task?.status || "-" }}</p>
        </div>
      </div>

      <div v-if="taskLoading" class="mt-3 text-xs text-slate-500">正在加载任务上下文...</div>
      <div v-if="taskError" class="mt-3 rounded-[10px] border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700">{{ taskError }}</div>
    </section>

    <section class="flex flex-wrap gap-2 rounded-[16px] border border-slate-200 bg-white p-2 shadow-[var(--shadow-card)]">
      <button
        v-for="item in mainTabs"
        :key="item.key"
        type="button"
        class="rounded-[12px] px-4 py-2 text-sm font-medium transition"
        :class="activeMainTab === item.key ? 'bg-[var(--color-brand-500)] text-white' : 'text-slate-600 hover:bg-slate-50 hover:text-slate-900'"
        @click="activeMainTab = item.key"
      >
        {{ item.label }}
      </button>
    </section>

    <section v-if="activeMainTab === 'overview'" class="space-y-5">
      <PageCard eyebrow="阶段说明" title="任务概览" description="任务负责承载资产接入、处理记录、派生产物与链路追踪。本页只展示任务基础信息和对象关系，不放入具体操作入口。">
        <div class="grid gap-4 xl:grid-cols-[minmax(0,1.2fr)_minmax(280px,0.8fr)]">
          <div class="grid gap-3 md:grid-cols-2">
            <div class="rounded-[14px] border border-slate-200 bg-slate-50 px-4 py-3">
              <div class="text-xs font-medium tracking-[0.14em] text-slate-400">任务名称</div>
              <div class="mt-1 text-sm font-semibold text-slate-900">{{ task?.taskName || "-" }}</div>
            </div>
            <div class="rounded-[14px] border border-slate-200 bg-slate-50 px-4 py-3">
              <div class="text-xs font-medium tracking-[0.14em] text-slate-400">被试编号</div>
              <div class="mt-1 text-sm font-semibold text-slate-900">{{ task?.subjectCode || "-" }}</div>
            </div>
            <div class="rounded-[14px] border border-slate-200 bg-slate-50 px-4 py-3">
              <div class="text-xs font-medium tracking-[0.14em] text-slate-400">动作名称</div>
              <div class="mt-1 text-sm font-semibold text-slate-900">{{ task?.actionName || "-" }}</div>
            </div>
            <div class="rounded-[14px] border border-slate-200 bg-slate-50 px-4 py-3">
              <div class="text-xs font-medium tracking-[0.14em] text-slate-400">当前数据状态</div>
              <div class="mt-1 text-sm font-semibold text-slate-900">{{ currentTaskStatus.label }}</div>
            </div>
            <div class="rounded-[14px] border border-slate-200 bg-slate-50 px-4 py-3">
              <div class="text-xs font-medium tracking-[0.14em] text-slate-400">采集日期</div>
              <div class="mt-1 text-sm font-semibold text-slate-900">{{ task?.collectDate || "-" }}</div>
            </div>
            <div class="rounded-[14px] border border-slate-200 bg-slate-50 px-4 py-3">
              <div class="text-xs font-medium tracking-[0.14em] text-slate-400">设备类型</div>
              <div class="mt-1 text-sm font-semibold text-slate-900">{{ task?.deviceType || "-" }}</div>
            </div>
            <div class="rounded-[14px] border border-slate-200 bg-slate-50 px-4 py-3">
              <div class="text-xs font-medium tracking-[0.14em] text-slate-400">数据模态</div>
              <div class="mt-1 text-sm font-semibold text-slate-900">{{ task?.modality || "-" }}</div>
            </div>
            <div class="rounded-[14px] border border-slate-200 bg-slate-50 px-4 py-3">
              <div class="text-xs font-medium tracking-[0.14em] text-slate-400">采集人员</div>
              <div class="mt-1 text-sm font-semibold text-slate-900">{{ task?.operatorName || "-" }}</div>
            </div>
            <div class="rounded-[14px] border border-slate-200 bg-slate-50 px-4 py-3 md:col-span-2">
              <div class="text-xs font-medium tracking-[0.14em] text-slate-400">采集地点</div>
              <div class="mt-1 text-sm font-semibold text-slate-900">{{ task?.captureLocation || "-" }}</div>
            </div>
            <div class="rounded-[14px] border border-slate-200 bg-slate-50 px-4 py-3 md:col-span-2">
              <div class="text-xs font-medium tracking-[0.14em] text-slate-400">备注</div>
              <div class="mt-1 text-sm leading-6 text-slate-700">{{ task?.remark || "暂无备注" }}</div>
            </div>
          </div>

          <div class="space-y-4">
            <div class="rounded-[16px] border border-[var(--color-brand-200)] bg-[var(--color-brand-50)] px-4 py-4">
              <div class="text-sm font-semibold text-[var(--color-brand-700)]">对象主线</div>
              <p class="mt-2 text-sm leading-6 text-[var(--color-brand-700)]">
                任务承载资产，处理作业消耗输入资产并生成派生资产，数据链路用于记录任务、资产与处理作业之间的关系。
              </p>
            </div>

            <div class="rounded-[16px] border border-slate-200 bg-white px-4 py-4">
              <div class="text-sm font-semibold text-slate-900">数据接入分支一：平台上传</div>
              <ul class="mt-3 space-y-2 text-sm leading-6 text-slate-600">
                <li>通过文件上传进入平台。</li>
                <li>自动写入 <code>data_file</code>。</li>
                <li>自动生成 <code>UPLOADED_FILE</code> 类型资产。</li>
                <li>自动执行当前 MVP 级质量检查。</li>
              </ul>
            </div>

            <div class="rounded-[16px] border border-slate-200 bg-white px-4 py-4">
              <div class="text-sm font-semibold text-slate-900">数据接入分支二：外部登记</div>
              <ul class="mt-3 space-y-2 text-sm leading-6 text-slate-600">
                <li>只登记外部路径和资产元数据。</li>
                <li>不上传文件。</li>
                <li>不写入 <code>data_file</code>。</li>
                <li>不自动生成质量检查报告。</li>
              </ul>
            </div>
          </div>
        </div>
      </PageCard>
    </section>

    <section v-else-if="activeMainTab === 'assets'" class="space-y-5">
      <PageCard eyebrow="阶段说明" title="资产管理" description="资产是平台中的核心数据对象。数据可以通过“平台上传”或“外部登记”两种方式进入系统，后续处理作业和链路追踪都围绕资产展开。">
        <div class="flex flex-wrap gap-3">
          <div class="rounded-[14px] border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600">
            资产总数：<span class="font-semibold text-slate-900">{{ assets.length }}</span>
          </div>
          <div class="rounded-[14px] border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600">
            平台上传：<span class="font-semibold text-slate-900">{{ uploadedAssets.length }}</span>
          </div>
          <div class="rounded-[14px] border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600">
            外部路径：<span class="font-semibold text-slate-900">{{ externalInputAssets.length }}</span>
          </div>
          <div class="rounded-[14px] border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600">
            处理生成：<span class="font-semibold text-slate-900">{{ derivedAssets.length }}</span>
          </div>
        </div>
      </PageCard>

      <PageCard eyebrow="阶段切换" title="数据接入方式" description="根据数据来源选择接入方式。平台上传会触发文件记录与质量检查，外部登记只保留路径和资产元数据。">
        <div class="flex flex-wrap gap-2">
          <button
            v-for="item in assetSubTabs"
            :key="item.key"
            type="button"
            class="rounded-[12px] px-4 py-2 text-sm font-medium transition"
            :class="activeAssetSubTab === item.key ? 'bg-[var(--color-brand-500)] text-white' : 'bg-slate-100 text-slate-600 hover:bg-slate-200 hover:text-slate-900'"
            @click="activeAssetSubTab = item.key"
          >
            {{ item.label }}
          </button>
        </div>
      </PageCard>

      <PageCard
        v-if="activeAssetSubTab === 'upload'"
        eyebrow="数据接入"
        title="平台上传"
        description="通过文件上传进入平台，系统会自动写入 data_file、生成 UPLOADED_FILE 类型资产，并执行当前 MVP 级质量检查。"
      >
        <div class="grid gap-4 xl:grid-cols-[minmax(0,0.95fr)_minmax(0,1.05fr)]">
          <div class="rounded-[16px] border border-slate-200 bg-slate-50 p-4">
            <div class="grid gap-4 md:grid-cols-2">
              <label class="block md:col-span-2">
                <span class="mb-1.5 block text-sm font-medium text-slate-700">选择接入文件</span>
                <input
                  type="file"
                  class="app-input border-dashed bg-white file:mr-3 file:rounded-[8px] file:border-0 file:bg-slate-100 file:px-3 file:py-1.5 file:text-sm file:font-medium file:text-slate-700"
                  @change="handleFileChange"
                />
              </label>
              <label class="block">
                <span class="mb-1.5 block text-sm font-medium text-slate-700">资产类型</span>
                <select v-model="selectedUploadAssetType" class="app-input">
                  <option v-for="option in assetTypeOptions" :key="option" :value="option">{{ option }}</option>
                </select>
              </label>
              <div class="rounded-[12px] border border-slate-200 bg-white px-3 py-3 text-xs text-slate-500">
                <div class="font-medium tracking-[0.14em] text-slate-400">当前选择</div>
                <div class="mt-1 text-sm font-medium text-slate-900">{{ selectedFile?.name ?? "尚未选择文件" }}</div>
                <div class="mt-1">{{ selectedFile ? formatFileSize(selectedFile.size) : "上传后会自动刷新资产、处理记录、质量检查和数据链路。" }}</div>
                <div v-if="uploading" class="mt-2">
                  <div class="h-2 overflow-hidden rounded-full bg-slate-200">
                    <div class="h-full bg-[var(--color-brand-500)] transition-all" :style="{ width: `${uploadProgress}%` }" />
                  </div>
                  <div class="mt-1 text-[11px] text-slate-500">上传进度：{{ uploadProgress }}%</div>
                </div>
              </div>
            </div>

            <div class="mt-4 flex items-center gap-3">
              <BaseButton variant="primary" :disabled="!selectedFile || uploading" @click="submitUpload">
                {{ uploading ? "正在接入..." : "开始接入" }}
              </BaseButton>
              <BaseButton
                v-if="pendingCompleteFileId"
                variant="ghost"
                :disabled="uploading"
                @click="retryPendingComplete"
              >
                重试完成登记
              </BaseButton>
              <span v-if="uploadMessage" :class="uploadSuccess ? 'text-emerald-700' : 'text-rose-700'" class="text-xs">
                {{ uploadMessage }}
              </span>
            </div>
          </div>

          <div class="rounded-[16px] border border-slate-200 bg-white p-4">
            <div class="text-sm font-semibold text-slate-900">平台上传后的系统行为</div>
            <ul class="mt-3 space-y-2 text-sm leading-6 text-slate-600">
              <li>写入平台文件记录 <code>data_file</code>。</li>
              <li>自动生成来源为 <code>UPLOADED_FILE</code> 的资产。</li>
              <li>自动执行当前 MVP 级质量检查，并生成上传文件对应的质量检查报告。</li>
              <li>上传成功后会刷新资产、处理条件检查、处理作业、质量检查与数据链路。</li>
            </ul>
          </div>
        </div>

        <div class="mt-5">
          <DataTableShell>
            <div v-if="assetsLoading" class="px-4 py-12 text-center text-sm text-slate-500">正在加载资产列表...</div>
            <EmptyState
              v-else-if="!uploadedAssets.length"
              title="暂无平台上传资产"
              description="请先通过平台上传接入文件。上传完成后，系统会自动生成资产并触发质量检查。"
              icon="资"
            />
            <table v-else class="min-w-full text-left text-sm">
              <thead class="bg-slate-50 text-xs tracking-[0.12em] text-slate-500">
                <tr>
                  <th class="px-4 py-3 font-medium">资产名称</th>
                  <th class="px-4 py-3 font-medium">资产类型</th>
                  <th class="px-4 py-3 font-medium">来源</th>
                  <th class="px-4 py-3 font-medium">关联文件</th>
                  <th class="px-4 py-3 font-medium">最近上传状态</th>
                  <th class="px-4 py-3 font-medium">创建时间</th>
                  <th class="px-4 py-3 font-medium text-right">操作</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-200 bg-white">
                <tr v-for="asset in uploadedAssets" :key="asset.id" class="hover:bg-slate-50/80">
                  <td class="px-4 py-3">
                    <button type="button" class="app-link font-medium" @click="openAssetDrawer(asset.id)">{{ asset.displayName }}</button>
                    <div class="mt-0.5 text-xs text-slate-500">资产 ID #{{ asset.id }}</div>
                  </td>
                  <td class="px-4 py-3">
                    <span class="app-pill border-[var(--color-brand-200)] bg-[var(--color-brand-50)] text-[var(--color-brand-700)]">{{ asset.assetType }}</span>
                  </td>
                  <td class="px-4 py-3">
                    <div class="text-sm font-medium text-slate-900">{{ resolveAssetSourceLabel(asset) }}</div>
                    <div class="mt-0.5 text-xs text-slate-500">原值：{{ asset.sourceType }}</div>
                  </td>
                  <td class="px-4 py-3 text-slate-600">{{ asset.originalFilename || "-" }}</td>
                  <td class="px-4 py-3">
                    <StatusBadge :status="asset.uploadStatus || undefined" :label="resolveUploadStatusLabel(asset.uploadStatus)" />
                  </td>
                  <td class="px-4 py-3 text-slate-500">{{ formatDateTime(asset.createdAt) }}</td>
                  <td class="px-4 py-3 text-right">
                    <div class="flex flex-wrap justify-end gap-2">
                      <button type="button" class="app-link text-xs font-medium" @click="openAssetDrawer(asset.id)">详情</button>
                      <button type="button" class="app-link text-xs font-medium" @click="setQualityTab()">查看质量检查</button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </DataTableShell>
        </div>
      </PageCard>

      <PageCard
        v-else
        eyebrow="资产登记"
        title="外部路径登记"
        description="只登记外部路径和资产元数据，不上传文件、不写入 data_file，也不会自动生成质量检查报告。"
      >
        <div class="grid gap-4 xl:grid-cols-[minmax(0,0.95fr)_minmax(0,1.05fr)]">
          <form class="rounded-[16px] border border-slate-200 bg-slate-50 p-4" @submit.prevent="submitExternalAsset">
            <div class="grid gap-4 md:grid-cols-2">
              <label class="block">
                <span class="mb-1.5 block text-sm font-medium text-slate-700">资产类型</span>
                <select v-model="externalAssetForm.assetType" class="app-input">
                  <option v-for="option in assetTypeOptions" :key="option" :value="option">{{ option }}</option>
                </select>
              </label>
              <label class="block">
                <span class="mb-1.5 block text-sm font-medium text-slate-700">显示名称</span>
                <input v-model="externalAssetForm.displayName" required class="app-input" placeholder="例如：SMPL 输出结果 v1" />
              </label>
              <label class="block md:col-span-2">
                <span class="mb-1.5 block text-sm font-medium text-slate-700">外部路径</span>
                <input v-model="externalAssetForm.externalPath" required class="app-input" placeholder="例如：\\\\存储\\项目数据\\受试者01\\smpl" />
              </label>
              <label class="block">
                <span class="mb-1.5 block text-sm font-medium text-slate-700">文件格式</span>
                <input v-model="externalAssetForm.fileFormat" class="app-input" placeholder="例如：npz / csv / folder" />
              </label>
              <label class="block">
                <span class="mb-1.5 block text-sm font-medium text-slate-700">大小说明</span>
                <input v-model="externalAssetForm.sizeRemark" class="app-input" placeholder="例如：约 12 GB / 3 个目录" />
              </label>
              <label class="block md:col-span-2">
                <span class="mb-1.5 block text-sm font-medium text-slate-700">描述</span>
                <textarea v-model="externalAssetForm.description" rows="2" class="app-input resize-y" placeholder="补充资产内容、来源或后续用途"></textarea>
              </label>
              <label class="block md:col-span-2">
                <span class="mb-1.5 block text-sm font-medium text-slate-700">操作备注</span>
                <textarea v-model="externalAssetForm.operatorRemark" rows="2" class="app-input resize-y" placeholder="记录来源、操作说明或注意事项"></textarea>
              </label>
            </div>

            <div class="mt-4 flex items-center gap-3">
              <BaseButton variant="primary" type="submit" :disabled="submittingExternalAsset">
                {{ submittingExternalAsset ? "正在登记..." : "登记外部资产" }}
              </BaseButton>
              <span v-if="externalAssetMessage" :class="externalAssetSuccess ? 'text-emerald-700' : 'text-rose-700'" class="text-xs">
                {{ externalAssetMessage }}
              </span>
            </div>
          </form>

          <div class="rounded-[16px] border border-slate-200 bg-white p-4">
            <div class="text-sm font-semibold text-slate-900">外部登记后的系统行为</div>
            <ul class="mt-3 space-y-2 text-sm leading-6 text-slate-600">
              <li>只保留资产元数据和外部路径。</li>
              <li>不会上传文件到平台，也不会生成 <code>data_file</code> 记录。</li>
              <li>不会自动执行质量检查。</li>
              <li>登记成功后会刷新资产列表、处理条件检查和数据链路。</li>
            </ul>
          </div>
        </div>

        <div class="mt-5">
          <DataTableShell>
            <div v-if="assetsLoading" class="px-4 py-12 text-center text-sm text-slate-500">正在加载资产列表...</div>
            <EmptyState
              v-else-if="!externalInputAssets.length"
              title="暂无外部登记资产"
              description="请登记外部路径资产。外部登记只记录资产元数据和路径，不会触发质量检查。"
              icon="外"
            />
            <table v-else class="min-w-full text-left text-sm">
              <thead class="bg-slate-50 text-xs tracking-[0.12em] text-slate-500">
                <tr>
                  <th class="px-4 py-3 font-medium">资产名称</th>
                  <th class="px-4 py-3 font-medium">资产类型</th>
                  <th class="px-4 py-3 font-medium">来源</th>
                  <th class="px-4 py-3 font-medium">外部路径</th>
                  <th class="px-4 py-3 font-medium">文件格式</th>
                  <th class="px-4 py-3 font-medium">创建时间</th>
                  <th class="px-4 py-3 font-medium text-right">操作</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-200 bg-white">
                <tr v-for="asset in externalInputAssets" :key="asset.id" class="hover:bg-slate-50/80">
                  <td class="px-4 py-3">
                    <button type="button" class="app-link font-medium" @click="openAssetDrawer(asset.id)">{{ asset.displayName }}</button>
                    <div class="mt-0.5 text-xs text-slate-500">资产 ID #{{ asset.id }}</div>
                  </td>
                  <td class="px-4 py-3">
                    <span class="app-pill border-[var(--color-brand-200)] bg-[var(--color-brand-50)] text-[var(--color-brand-700)]">{{ asset.assetType }}</span>
                  </td>
                  <td class="px-4 py-3">
                    <div class="text-sm font-medium text-slate-900">{{ resolveAssetSourceLabel(asset) }}</div>
                    <div class="mt-0.5 text-xs text-slate-500">原值：{{ asset.sourceType }}</div>
                  </td>
                  <td class="px-4 py-3 text-slate-600">
                    <div class="max-w-[280px] truncate">{{ asset.externalPath || "-" }}</div>
                  </td>
                  <td class="px-4 py-3 text-slate-600">{{ asset.fileFormat || "-" }}</td>
                  <td class="px-4 py-3 text-slate-500">{{ formatDateTime(asset.createdAt) }}</td>
                  <td class="px-4 py-3 text-right">
                    <div class="flex flex-wrap justify-end gap-2">
                      <button type="button" class="app-link text-xs font-medium" @click="openAssetDrawer(asset.id)">详情</button>
                      <button type="button" class="app-link text-xs font-medium" @click="openManualDrawer(asset.id)">作为处理输入</button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </DataTableShell>
        </div>
      </PageCard>

      <PageCard eyebrow="资产总览" title="全部资产" description="这里汇总当前任务下的全部资产，包括平台上传、外部登记和处理生成的派生资产。">
        <DataTableShell>
          <div v-if="assetsLoading" class="px-4 py-12 text-center text-sm text-slate-500">正在加载资产列表...</div>
          <EmptyState
            v-else-if="!assets.length"
            title="当前任务还没有资产"
            description="请先通过平台上传或外部登记接入资产，再继续记录处理作业。"
            icon="资"
          />
          <table v-else class="min-w-full text-left text-sm">
            <thead class="bg-slate-50 text-xs tracking-[0.12em] text-slate-500">
              <tr>
                <th class="px-4 py-3 font-medium">资产名称</th>
                <th class="px-4 py-3 font-medium">资产类型</th>
                <th class="px-4 py-3 font-medium">来源</th>
                <th class="px-4 py-3 font-medium">是否派生资产</th>
                <th class="px-4 py-3 font-medium">关联处理作业</th>
                <th class="px-4 py-3 font-medium">路径 / 文件</th>
                <th class="px-4 py-3 font-medium">创建时间</th>
                <th class="px-4 py-3 font-medium text-right">操作</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-200 bg-white">
              <tr v-for="asset in assets" :key="asset.id" class="hover:bg-slate-50/80">
                <td class="px-4 py-3">
                  <button type="button" class="app-link font-medium" @click="openAssetDrawer(asset.id)">{{ asset.displayName }}</button>
                  <div class="mt-0.5 text-xs text-slate-500">资产 ID #{{ asset.id }}</div>
                </td>
                <td class="px-4 py-3">
                  <span class="app-pill border-[var(--color-brand-200)] bg-[var(--color-brand-50)] text-[var(--color-brand-700)]">{{ asset.assetType }}</span>
                </td>
                <td class="px-4 py-3">
                  <div class="text-sm font-medium text-slate-900">{{ resolveAssetSourceLabel(asset) }}</div>
                  <div class="mt-0.5 text-xs text-slate-500">
                    {{ resolveAssetSourceDetail(asset) }}
                  </div>
                </td>
                <td class="px-4 py-3">
                  <span :class="asset.producedByJobId ? 'app-pill border-emerald-200 bg-emerald-50 text-emerald-700' : 'app-pill border-slate-200 bg-slate-50 text-slate-600'">
                    {{ asset.producedByJobId ? "派生资产" : "输入资产" }}
                  </span>
                </td>
                <td class="px-4 py-3 text-slate-600">{{ asset.producedByJobId ? `作业 #${asset.producedByJobId}` : "-" }}</td>
                <td class="px-4 py-3 text-slate-500">
                  <div class="max-w-[260px] truncate">{{ asset.externalPath || asset.originalFilename || "-" }}</div>
                </td>
                <td class="px-4 py-3 text-slate-500">{{ formatDateTime(asset.createdAt) }}</td>
                <td class="px-4 py-3 text-right">
                  <div class="flex flex-wrap justify-end gap-2">
                    <button type="button" class="app-link text-xs font-medium" @click="openAssetDrawer(asset.id)">详情</button>
                    <button type="button" class="app-link text-xs font-medium" @click="viewAssetOrigin(asset)">查看来源</button>
                    <button type="button" class="app-link text-xs font-medium" @click="openManualDrawer(asset.id)">作为处理输入</button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </DataTableShell>

        <p v-if="assetsError" class="mt-3 rounded-[10px] border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700">{{ assetsError }}</p>
      </PageCard>
    </section>

    <section v-else-if="activeMainTab === 'processing'" class="space-y-5">
      <PageCard eyebrow="阶段说明" title="处理流程" description="处理流程用于记录数据加工行为，并生成新的派生资产。当前页面区分“自动处理”和“人工登记”两种处理记录方式。">
        <div class="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
          <MetricCard label="处理作业数量" :value="jobs.length" description="当前任务下的全部处理作业记录" />
          <MetricCard label="自动处理记录" :value="automaticJobs.length" description="未来平台主线能力，当前仍是占位执行" />
          <MetricCard label="人工登记记录" :value="manualJobs.length" description="用于纳入线下处理过程" />
          <MetricCard label="派生资产数量" :value="derivedAssets.length" description="处理作业输出的派生资产总数" />
        </div>
      </PageCard>

      <PageCard eyebrow="处理条件检查" title="当前可执行处理" description="当前检查只判断资产类型是否齐全，不代表算法已经真实跑通。这里展示的是当前任务具备的最小处理条件。">
        <div v-if="pipelinesLoading" class="py-10 text-center text-sm text-slate-500">正在加载处理条件检查...</div>
        <EmptyState
          v-else-if="!pipelines.length"
          title="暂无可检查的处理能力"
          description="当前没有可展示的处理条件检查结果。"
          icon="处"
        />
        <div v-else class="grid gap-4 xl:grid-cols-2">
          <article v-for="pipeline in pipelines" :key="pipeline.pipelineId" class="rounded-[16px] border border-slate-200 bg-white p-4 shadow-[var(--shadow-card)]">
            <div class="flex flex-wrap items-center gap-2">
              <span class="app-pill border-[var(--color-brand-200)] bg-[var(--color-brand-50)] text-[var(--color-brand-700)]">{{ pipeline.pipelineId }}</span>
              <StatusBadge :status="pipeline.readinessStatus" :label="pipeline.readinessStatus === 'READY' ? '条件已齐全' : '缺少必需资产'" />
            </div>
            <div class="mt-3 text-sm font-semibold text-slate-900">{{ pipeline.displayName }}</div>
            <p class="mt-2 text-sm leading-6 text-slate-500">{{ pipeline.description }}</p>
            <div class="mt-3 text-xs text-slate-400">状态原值：{{ pipeline.readinessStatus }}</div>

            <div class="mt-4 grid gap-3 md:grid-cols-2">
              <div class="rounded-[14px] border border-slate-200 bg-slate-50 px-4 py-3">
                <div class="text-xs font-medium tracking-[0.14em] text-slate-400">所需资产</div>
                <div class="mt-2 flex flex-wrap gap-2">
                  <span v-for="item in requiredAssetTypes" :key="item" class="app-pill border-slate-200 bg-white text-slate-700">{{ item }}</span>
                </div>
              </div>
              <div class="rounded-[14px] border border-slate-200 bg-slate-50 px-4 py-3">
                <div class="text-xs font-medium tracking-[0.14em] text-slate-400">当前已有资产类型</div>
                <div class="mt-2 flex flex-wrap gap-2">
                  <span v-if="pipeline.existingAssets.length" v-for="item in pipeline.existingAssets" :key="item" class="app-pill border-slate-200 bg-white text-slate-700">{{ item }}</span>
                  <span v-else class="text-sm text-slate-500">暂无</span>
                </div>
              </div>
            </div>

            <div class="mt-3 rounded-[14px] border border-slate-200 bg-slate-50 px-4 py-3">
              <div class="text-xs font-medium tracking-[0.14em] text-slate-400">缺失资产类型</div>
              <div class="mt-2 flex flex-wrap gap-2">
                <span v-if="pipeline.missingRequiredAssets.length" v-for="item in pipeline.missingRequiredAssets" :key="item" class="app-pill border-amber-200 bg-amber-50 text-amber-700">{{ item }}</span>
                <span v-else class="text-sm text-slate-500">当前所需资产类型已齐全。</span>
              </div>
            </div>
          </article>
        </div>
        <p v-if="pipelinesError" class="mt-3 rounded-[10px] border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700">{{ pipelinesError }}</p>
      </PageCard>

      <PageCard eyebrow="自动处理" title="自动处理作业" description="自动处理代表未来平台主线能力。当前仍是占位执行，返回结果不是实际算法结果，但作业记录、状态持久化和详情查看都是真实能力。">
        <div class="flex flex-wrap items-center gap-3">
          <BaseButton
            variant="primary"
            :disabled="!readyPipeline"
            @click="readyPipeline && submitProcessingJob(readyPipeline.pipelineId)"
          >
            {{ readyPipeline ? `执行 ${readyPipeline.pipelineId}` : "当前还不能发起自动处理" }}
          </BaseButton>
          <div class="text-xs text-slate-500">
            当前自动处理仍是占位执行，不代表算法已经真实跑通。
          </div>
        </div>

        <div class="mt-5">
          <DataTableShell>
            <div v-if="jobsLoading" class="px-4 py-12 text-center text-sm text-slate-500">正在加载处理作业...</div>
            <EmptyState
              v-else-if="!automaticJobs.length"
              title="暂无自动处理作业"
              description="当处理条件检查显示所需资产类型齐全后，可以发起自动处理。当前自动处理仍是占位执行。"
              icon="自"
            />
            <table v-else class="min-w-full text-left text-sm">
              <thead class="bg-slate-50 text-xs tracking-[0.12em] text-slate-500">
                <tr>
                  <th class="px-4 py-3 font-medium">处理标识</th>
                  <th class="px-4 py-3 font-medium">执行类型</th>
                  <th class="px-4 py-3 font-medium">状态</th>
                  <th class="px-4 py-3 font-medium">创建时间</th>
                  <th class="px-4 py-3 font-medium">说明</th>
                  <th class="px-4 py-3 font-medium text-right">操作</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-200 bg-white">
                <tr v-for="job in automaticJobs" :key="job.id" class="hover:bg-slate-50/80">
                  <td class="px-4 py-3">
                    <div class="font-medium text-slate-900">{{ job.pipelineId }}</div>
                    <div class="mt-0.5 text-xs text-slate-500">作业 #{{ job.id }}</div>
                  </td>
                  <td class="px-4 py-3">
                    <div class="text-sm font-medium text-slate-900">{{ job.executorType === 'MOCK' ? '自动处理（占位执行）' : job.executorType }}</div>
                    <div class="mt-0.5 text-xs text-slate-500">原值：{{ job.executorType }}</div>
                  </td>
                  <td class="px-4 py-3">
                    <StatusBadge :status="job.status" />
                  </td>
                  <td class="px-4 py-3 text-slate-500">{{ formatDateTime(job.createdAt) }}</td>
                  <td class="px-4 py-3 text-slate-600">当前结果仅用于验证占位执行链路。</td>
                  <td class="px-4 py-3 text-right">
                    <button type="button" class="app-link text-xs font-medium" @click="selectJob(job.id)">查看详情</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </DataTableShell>
        </div>

        <div v-if="selectedJob && selectedJob.executorType === 'MOCK'" class="mt-5 rounded-[16px] border border-slate-200 bg-slate-50 p-4">
          <div class="flex flex-wrap items-center gap-2">
            <span class="app-pill border-[var(--color-brand-200)] bg-white text-[var(--color-brand-700)]">{{ selectedJob.pipelineId }}</span>
            <span class="app-pill border-slate-200 bg-white text-slate-600">{{ selectedJob.executorType }}</span>
            <StatusBadge :status="selectedJob.status" />
          </div>
          <div class="mt-3 text-sm font-semibold text-slate-900">自动处理详情</div>
          <div class="mt-3 grid gap-3 md:grid-cols-2">
            <div class="rounded-[14px] border border-slate-200 bg-white px-4 py-3 text-sm text-slate-600">
              <div>作业编号：#{{ selectedJob.id }}</div>
              <div class="mt-2">创建时间：{{ formatDateTime(selectedJob.createdAt) }}</div>
              <div class="mt-2">更新时间：{{ formatDateTime(selectedJob.updatedAt) }}</div>
            </div>
            <div class="rounded-[14px] border border-slate-200 bg-white px-4 py-3 text-sm text-slate-600">
              <div>返回结果状态：{{ formatStatusLabel(selectedJob.status) }}</div>
              <div class="mt-2">错误信息：{{ selectedJob.errorMessage || "无" }}</div>
            </div>
          </div>
          <div class="mt-4 grid gap-4 xl:grid-cols-2">
            <div class="rounded-[14px] border border-slate-200 bg-white px-4 py-3">
              <div class="text-xs font-medium tracking-[0.14em] text-slate-400">处理参数</div>
              <pre class="mt-2 overflow-x-auto rounded-[10px] bg-slate-900 px-3 py-3 text-xs leading-5 text-slate-100">{{ stringifyJson(selectedJob.parameters) }}</pre>
            </div>
            <div class="rounded-[14px] border border-slate-200 bg-white px-4 py-3">
              <div class="text-xs font-medium tracking-[0.14em] text-slate-400">返回结果</div>
              <pre class="mt-2 overflow-x-auto rounded-[10px] bg-slate-900 px-3 py-3 text-xs leading-5 text-slate-100">{{ stringifyJson(selectedJob.resultJson) }}</pre>
            </div>
          </div>
        </div>

        <p v-if="jobsError" class="mt-3 rounded-[10px] border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700">{{ jobsError }}</p>
      </PageCard>

      <PageCard eyebrow="人工登记" title="线下处理记录" description="人工登记用于把线下处理过程纳入平台追踪，不代表平台自动处理主能力。需要时再展开使用。">
        <template #actions>
          <BaseButton @click="showManualSection = !showManualSection">
            {{ showManualSection ? "收起人工登记" : "展开人工登记" }}
          </BaseButton>
        </template>

        <div class="rounded-[14px] border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600">
          人工登记用于记录线下执行的处理过程、输入资产、输出资产和工具信息，帮助平台形成可追溯的处理记录与数据链路。
        </div>

        <div v-if="showManualSection" class="mt-4 space-y-4">
          <div class="flex flex-wrap items-center gap-3">
            <BaseButton variant="primary" @click="openManualDrawer()">登记人工处理</BaseButton>
            <div class="text-xs text-slate-500">人工登记会把输入资产、输出资产和处理记录纳入数据链路。</div>
          </div>

          <DataTableShell>
            <div v-if="jobsLoading" class="px-4 py-12 text-center text-sm text-slate-500">正在加载处理作业...</div>
            <EmptyState
              v-else-if="!manualJobs.length"
              title="暂无人工登记记录"
              description="如果线下已经完成处理，可以在这里登记处理记录和输出资产。"
              icon="人"
            />
            <table v-else class="min-w-full text-left text-sm">
              <thead class="bg-slate-50 text-xs tracking-[0.12em] text-slate-500">
                <tr>
                  <th class="px-4 py-3 font-medium">处理标识</th>
                  <th class="px-4 py-3 font-medium">操作人员</th>
                  <th class="px-4 py-3 font-medium">工具信息</th>
                  <th class="px-4 py-3 font-medium">输出结果</th>
                  <th class="px-4 py-3 font-medium">创建时间</th>
                  <th class="px-4 py-3 font-medium text-right">操作</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-200 bg-white">
                <tr v-for="job in manualJobs" :key="job.id" class="hover:bg-slate-50/80">
                  <td class="px-4 py-3">
                    <div class="font-medium text-slate-900">{{ job.pipelineId }}</div>
                    <div class="mt-0.5 text-xs text-slate-500">作业 #{{ job.id }}</div>
                  </td>
                  <td class="px-4 py-3 text-slate-600">{{ job.operatorName || "未登记" }}</td>
                  <td class="px-4 py-3 text-slate-600">{{ job.toolName || "未登记" }}{{ job.toolVersion ? ` / ${job.toolVersion}` : "" }}</td>
                  <td class="px-4 py-3 text-slate-600">用于登记线下处理输出资产</td>
                  <td class="px-4 py-3 text-slate-500">{{ formatDateTime(job.createdAt) }}</td>
                  <td class="px-4 py-3 text-right">
                    <button type="button" class="app-link text-xs font-medium" @click="selectJob(job.id)">查看详情</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </DataTableShell>

          <div v-if="selectedJob && selectedJob.executorType === 'MANUAL'" class="rounded-[16px] border border-slate-200 bg-white p-4 shadow-[var(--shadow-card)]">
            <div class="flex flex-wrap items-center gap-2">
              <span class="app-pill border-[var(--color-brand-200)] bg-[var(--color-brand-50)] text-[var(--color-brand-700)]">{{ selectedJob.pipelineId }}</span>
              <span class="app-pill border-slate-200 bg-slate-50 text-slate-600">{{ selectedJob.executorType }}</span>
              <StatusBadge :status="selectedJob.status" />
            </div>
            <div class="mt-3 text-sm font-semibold text-slate-900">人工登记详情</div>
            <div class="mt-3 grid gap-3 md:grid-cols-2">
              <div class="rounded-[14px] border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600">
                <div>操作人员：{{ selectedJob.operatorName || "未登记" }}</div>
                <div class="mt-2">工具名称：{{ selectedJob.toolName || "未登记" }}</div>
                <div class="mt-2">工具版本：{{ selectedJob.toolVersion || "未登记" }}</div>
              </div>
              <div class="rounded-[14px] border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600">
                <div>日志路径：{{ selectedJob.logPath || "未登记" }}</div>
                <div class="mt-2">备注：{{ selectedJob.remark || "无" }}</div>
                <div class="mt-2">创建时间：{{ formatDateTime(selectedJob.createdAt) }}</div>
              </div>
            </div>
            <div class="mt-4 rounded-[14px] border border-slate-200 bg-slate-50 px-4 py-3">
              <div class="text-xs font-medium tracking-[0.14em] text-slate-400">登记参数</div>
              <pre class="mt-2 overflow-x-auto rounded-[10px] bg-slate-900 px-3 py-3 text-xs leading-5 text-slate-100">{{ stringifyJson(selectedJob.paramsJson) }}</pre>
            </div>
          </div>
        </div>

        <p v-if="manualProcessingMessage" :class="manualProcessingSuccess ? 'mt-3 rounded-[10px] border border-emerald-200 bg-emerald-50 px-3 py-2 text-xs text-emerald-700' : 'mt-3 rounded-[10px] border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700'">
          {{ manualProcessingMessage }}
        </p>
      </PageCard>
    </section>

    <section v-else-if="activeMainTab === 'quality'" class="space-y-5">
      <PageCard eyebrow="阶段说明" title="质量检查" description="当前质量检查仅针对平台上传文件自动执行。外部资产不会自动生成质量检查报告，这里展示的是上传文件的检查结果。">
        <div class="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
          <MetricCard label="上传文件数量" :value="files.length" description="当前任务已接入的上传文件数" />
          <MetricCard label="质量检查报告数" :value="reportsCount" description="仅统计上传文件自动生成的检查报告" />
          <MetricCard label="最近上传状态" :value="latestQcStatus.label" :description="latestQcStatus.description" />
          <MetricCard label="外部资产自动质检" value="未启用" description="外部登记资产不会自动生成质量检查报告" />
        </div>
      </PageCard>

      <PageCard eyebrow="上传文件" title="已接入文件" description="这里列出平台上传产生的文件记录，质量检查报告与这些文件一一关联。">
        <DataTableShell>
          <div v-if="filesLoading" class="px-4 py-12 text-center text-sm text-slate-500">正在加载上传文件...</div>
          <EmptyState
            v-else-if="!files.length"
            title="暂无上传文件"
            description="质量检查只在平台上传后自动触发。当前没有上传文件，因此也没有可展示的质量检查结果。"
            icon="文"
          />
          <table v-else class="min-w-full text-left text-sm">
            <thead class="bg-slate-50 text-xs tracking-[0.12em] text-slate-500">
              <tr>
                <th class="px-4 py-3 font-medium">文件名称</th>
                <th class="px-4 py-3 font-medium">文件类型</th>
                <th class="px-4 py-3 font-medium">最近上传状态</th>
                <th class="px-4 py-3 font-medium">创建时间</th>
                <th class="px-4 py-3 font-medium text-right">操作</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-200 bg-white">
              <tr v-for="file in files" :key="file.id" class="hover:bg-slate-50/80">
                <td class="px-4 py-3">
                  <div class="font-medium text-slate-900">{{ file.originalFilename }}</div>
                  <div class="mt-0.5 text-xs text-slate-500">文件 ID #{{ file.id }}</div>
                </td>
                <td class="px-4 py-3 text-slate-600">{{ file.fileExt || file.contentType || "-" }}</td>
                <td class="px-4 py-3">
                  <StatusBadge :status="file.uploadStatus" :label="resolveUploadStatusLabel(file.uploadStatus)" />
                </td>
                <td class="px-4 py-3 text-slate-500">{{ formatDateTime(file.createdAt) }}</td>
                <td class="px-4 py-3 text-right">
                  <RouterLink :to="`/tasks/${taskId}/qc-report?fileId=${file.id}`" class="app-link text-xs font-medium">查看该文件质量检查</RouterLink>
                </td>
              </tr>
            </tbody>
          </table>
        </DataTableShell>

        <p v-if="filesError" class="mt-3 rounded-[10px] border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700">{{ filesError }}</p>
      </PageCard>

      <PageCard eyebrow="质量检查报告" title="上传文件检查结果" description="质量检查报告来自平台上传后的自动检查。这里展示结构化检查项、告警、错误与原始 report_json。">
        <div v-if="reportsLoading" class="py-12 text-center text-sm text-slate-500">正在加载质量检查报告...</div>
        <EmptyState
          v-else-if="!qcReports.length"
          title="暂无质量检查报告"
          description="请先通过平台上传接入文件。上传成功后，系统会自动生成质量检查报告。"
          icon="检"
        />
        <div v-else class="space-y-5">
          <article v-for="report in qcReports" :key="report.id" class="app-section-card rounded-[14px] p-4">
            <div class="flex flex-col gap-4">
              <div class="flex flex-col gap-3 xl:flex-row xl:items-start xl:justify-between">
                <div class="space-y-2.5">
                  <div class="flex flex-wrap items-center gap-2">
                    <StatusBadge :status="report.reportJson.overallStatus || report.qcStatus" />
                    <span class="rounded-[8px] bg-slate-100 px-2.5 py-1 text-xs font-medium text-slate-500">文件 ID {{ report.fileId }}</span>
                  </div>
                  <div>
                    <h3 class="text-base font-semibold text-slate-900">报告 #{{ report.id }}</h3>
                    <p class="mt-1 text-sm leading-5 text-slate-500">{{ report.reportJson.summary || report.summary }}</p>
                  </div>
                </div>

                <div class="flex flex-wrap gap-2">
                  <div class="rounded-[10px] border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-500">
                    <div class="font-medium tracking-[0.14em] text-slate-400">创建时间</div>
                    <div class="mt-1 text-sm font-semibold text-slate-800">{{ formatDateTime(report.createdAt) }}</div>
                  </div>
                  <div class="rounded-[10px] border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-500">
                    <div class="font-medium tracking-[0.14em] text-slate-400">检查项数量</div>
                    <div class="mt-1 text-sm font-semibold text-slate-800">{{ report.reportJson.checks?.length ?? 0 }}</div>
                  </div>
                </div>
              </div>

              <div class="grid gap-4 xl:grid-cols-[minmax(0,1.3fr)_minmax(280px,0.7fr)]">
                <div class="rounded-[12px] border border-slate-200 bg-white p-4 shadow-[var(--shadow-card)]">
                  <div class="flex items-center justify-between gap-3">
                    <h4 class="text-sm font-semibold tracking-[0.14em] text-slate-500">检查项</h4>
                    <span class="text-xs text-slate-500">{{ report.reportJson.checks?.length ?? 0 }} 项</span>
                  </div>

                  <div v-if="report.reportJson.checks?.length" class="mt-3 space-y-2.5">
                    <div
                      v-for="check in report.reportJson.checks"
                      :key="`${report.id}-${check.name}`"
                      class="rounded-[10px] border border-slate-200 bg-slate-50 px-3 py-3"
                    >
                      <div class="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                        <div>
                          <div class="text-sm font-medium text-slate-900">{{ check.name }}</div>
                          <p class="mt-1 text-xs leading-5 text-slate-500">{{ check.message }}</p>
                        </div>
                        <StatusBadge :status="check.status" />
                      </div>
                    </div>
                  </div>
                  <p v-else class="mt-3 text-xs text-slate-500">当前报告未返回结构化检查项列表。</p>
                </div>

                <div class="space-y-3">
                  <PageCard eyebrow="告警信息" title="告警" secondary>
                    <ul v-if="report.reportJson.warnings?.length" class="space-y-2 text-xs leading-5 text-amber-800">
                      <li v-for="warning in report.reportJson.warnings" :key="warning">{{ warning }}</li>
                    </ul>
                    <p v-else class="text-xs text-slate-500">暂无告警信息。</p>
                  </PageCard>

                  <PageCard eyebrow="错误信息" title="错误" secondary>
                    <ul v-if="report.reportJson.errors?.length" class="space-y-2 text-xs leading-5 text-rose-800">
                      <li v-for="error in report.reportJson.errors" :key="error">{{ error }}</li>
                    </ul>
                    <p v-else class="text-xs text-slate-500">暂无错误信息。</p>
                  </PageCard>

                  <div class="rounded-[12px] border border-slate-200 bg-slate-50 px-4 py-3">
                    <div class="text-[11px] font-medium tracking-[0.14em] text-slate-400">原始 report_json</div>
                    <pre class="mt-2 overflow-x-auto rounded-[10px] bg-slate-900 px-3 py-3 text-xs leading-5 text-slate-100">{{ stringifyJson(report.reportJson) }}</pre>
                  </div>
                </div>
              </div>
            </div>
          </article>
        </div>
        <p v-if="reportsError" class="mt-3 rounded-[10px] border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700">{{ reportsError }}</p>
      </PageCard>
    </section>

    <section v-else class="space-y-5">
      <PageCard eyebrow="阶段说明" title="数据链路" description="数据链路用于追踪任务、资产与处理作业之间的关系。它是结果视图，不是主要操作入口。">
        <div class="flex flex-wrap gap-3">
          <div class="rounded-[14px] border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600">
            链路段数量：<span class="font-semibold text-slate-900">{{ lineageJobGroups.length }}</span>
          </div>
          <div class="rounded-[14px] border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600">
            输入资产：<span class="font-semibold text-slate-900">{{ inputAssets.length }}</span>
          </div>
          <div class="rounded-[14px] border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600">
            派生资产：<span class="font-semibold text-slate-900">{{ derivedAssets.length }}</span>
          </div>
          <BaseButton @click="loadLineage">刷新链路</BaseButton>
        </div>
      </PageCard>

      <PageCard eyebrow="结果视图" title="任务数据链路" description="这里按“输入资产 -> 处理作业 -> 派生资产”的方式展示当前任务下的可追溯加工过程。">
        <div v-if="lineageLoading" class="py-10 text-center text-sm text-slate-500">正在加载数据链路...</div>
        <EmptyState
          v-else-if="!lineageJobGroups.length"
          title="还没有形成数据链路"
          description="请先接入资产，再记录处理作业。形成处理输入与输出关系后，这里会展示对应的数据链路。"
          icon="链"
        />
        <div v-else class="space-y-5">
          <article v-for="group in lineageJobGroups" :key="group.job.id" class="rounded-[18px] border border-slate-200 bg-slate-50 p-4">
            <div class="grid gap-4 xl:grid-cols-[minmax(0,1fr)_84px_minmax(0,1fr)]">
              <div class="space-y-3">
                <div class="text-[11px] font-medium tracking-[0.14em] text-slate-400">输入资产</div>
                <button
                  v-for="asset in group.inputs"
                  :key="asset.id"
                  type="button"
                  class="w-full rounded-[14px] border border-slate-200 bg-white px-4 py-3 text-left shadow-[var(--shadow-soft)] transition hover:border-[var(--color-brand-200)]"
                  @click="openAssetDrawer(asset.detailId)"
                >
                  <div class="flex flex-wrap items-center gap-2">
                    <span class="app-pill border-[var(--color-brand-200)] bg-[var(--color-brand-50)] text-[var(--color-brand-700)]">{{ asset.assetType }}</span>
                    <span class="app-pill border-slate-200 bg-slate-50 text-slate-600">{{ assetSourceBadge(asset.detailId) }}</span>
                  </div>
                  <div class="mt-2 text-sm font-semibold text-slate-900">{{ asset.label }}</div>
                </button>
              </div>

              <div class="flex flex-col items-center justify-center gap-3">
                <div class="text-xs font-medium tracking-[0.14em] text-slate-400">处理作业</div>
                <div class="text-3xl text-[var(--color-brand-500)]">↓</div>
              </div>

              <div class="space-y-3">
                <div class="text-[11px] font-medium tracking-[0.14em] text-slate-400">派生资产</div>
                <button
                  type="button"
                  class="w-full rounded-[16px] border border-[var(--color-brand-200)] bg-white px-4 py-4 text-left shadow-[var(--shadow-soft)] transition hover:border-[var(--color-brand-500)]"
                  @click="selectJob(group.job.detailId)"
                >
                  <div class="flex flex-wrap items-center gap-2">
                    <span class="app-pill border-slate-200 bg-slate-50 text-slate-600">{{ group.job.pipelineId }}</span>
                    <span class="app-pill border-[var(--color-brand-200)] bg-[var(--color-brand-50)] text-[var(--color-brand-700)]">{{ relatedJob(group.job.detailId)?.executorType || "-" }}</span>
                    <StatusBadge :status="group.job.status || undefined" />
                  </div>
                  <div class="mt-3 text-sm font-semibold text-slate-900">
                    {{ relatedJob(group.job.detailId)?.operatorName || "未登记操作人员" }} / {{ relatedJob(group.job.detailId)?.toolName || "未登记工具" }}
                  </div>
                  <div class="mt-2 text-xs text-slate-500">点击查看处理作业详情</div>
                </button>
                <button
                  v-for="asset in group.outputs"
                  :key="asset.id"
                  type="button"
                  class="w-full rounded-[14px] border border-emerald-200 bg-white px-4 py-3 text-left shadow-[var(--shadow-soft)] transition hover:border-emerald-400"
                  @click="openAssetDrawer(asset.detailId)"
                >
                  <div class="flex flex-wrap items-center gap-2">
                    <span class="app-pill border-emerald-200 bg-emerald-50 text-emerald-700">{{ asset.assetType }}</span>
                    <span class="app-pill border-slate-200 bg-slate-50 text-slate-600">处理生成</span>
                  </div>
                  <div class="mt-2 text-sm font-semibold text-slate-900">{{ asset.label }}</div>
                </button>
              </div>
            </div>
          </article>
        </div>
        <p v-if="lineageError" class="mt-3 rounded-[10px] border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700">{{ lineageError }}</p>
      </PageCard>
    </section>

    <AppDrawer
      :open="manualDrawerOpen"
      eyebrow="人工登记"
      title="登记线下处理记录"
      description="用于把线下处理过程纳入平台追踪，记录输入资产、输出资产和工具信息。"
      @close="manualDrawerOpen = false"
    >
      <form class="space-y-5" @submit.prevent="submitManualProcessingJob">
        <div class="rounded-[16px] border border-slate-200 bg-slate-50 p-4">
          <div class="text-sm font-semibold text-slate-900">基础信息</div>
          <div class="mt-4 grid gap-4 md:grid-cols-2">
            <label class="block">
              <span class="mb-1.5 block text-sm font-medium text-slate-700">处理标识</span>
              <input v-model="manualProcessingForm.pipelineId" class="app-input" />
            </label>
            <label class="block">
              <span class="mb-1.5 block text-sm font-medium text-slate-700">操作人员</span>
              <input v-model="manualProcessingForm.operatorName" class="app-input" placeholder="例如：实验员 A" />
            </label>
            <label class="block">
              <span class="mb-1.5 block text-sm font-medium text-slate-700">工具名称</span>
              <input v-model="manualProcessingForm.toolName" class="app-input" placeholder="例如：对齐工具" />
            </label>
            <label class="block">
              <span class="mb-1.5 block text-sm font-medium text-slate-700">工具版本</span>
              <input v-model="manualProcessingForm.toolVersion" class="app-input" placeholder="例如：版本 1.0" />
            </label>
            <label class="block md:col-span-2">
              <span class="mb-1.5 block text-sm font-medium text-slate-700">日志路径</span>
              <input v-model="manualProcessingForm.logPath" class="app-input" placeholder="例如：\\\\存储\\处理日志\\对齐任务01" />
            </label>
            <label class="block md:col-span-2">
              <span class="mb-1.5 block text-sm font-medium text-slate-700">备注</span>
              <textarea v-model="manualProcessingForm.remark" rows="2" class="app-input resize-y" placeholder="补充说明线下处理过程"></textarea>
            </label>
          </div>
        </div>

        <div class="rounded-[16px] border border-slate-200 bg-white p-4">
          <div class="text-sm font-semibold text-slate-900">输入资产</div>
          <div class="mt-3 space-y-4">
            <div v-if="!groupedInputAssets.length" class="text-sm text-slate-500">当前任务还没有可选输入资产。</div>
            <div v-for="group in groupedInputAssets" :key="group.type" class="rounded-[14px] border border-slate-200 bg-slate-50 p-4">
              <div class="text-sm font-semibold text-slate-900">{{ group.type }}</div>
              <div class="mt-3 grid gap-3 md:grid-cols-2">
                <label v-for="asset in group.items" :key="asset.id" class="flex items-start gap-3 rounded-[12px] border border-slate-200 bg-white px-3 py-3">
                  <input v-model="manualProcessingForm.inputAssetIds" :value="asset.id" type="checkbox" class="mt-1 h-4 w-4 rounded border-slate-300 text-[var(--color-brand-600)]" />
                  <div class="min-w-0">
                    <div class="text-sm font-medium text-slate-900">{{ asset.displayName }}</div>
                    <div class="mt-1 text-xs text-slate-500">
                      来源：{{ resolveAssetSourceLabel(asset) }}{{ asset.producedByJobId ? ` / 作业 #${asset.producedByJobId}` : "" }}
                    </div>
                  </div>
                </label>
              </div>
            </div>
          </div>
        </div>

        <div class="rounded-[16px] border border-slate-200 bg-white p-4">
          <div class="flex items-center justify-between gap-3">
            <div>
              <div class="text-sm font-semibold text-slate-900">输出资产</div>
              <div class="mt-1 text-xs text-slate-500">用于登记线下处理生成的派生资产。</div>
            </div>
            <BaseButton @click="addOutputAsset">新增输出资产</BaseButton>
          </div>

          <div class="mt-4 space-y-4">
            <div v-for="(output, index) in manualProcessingForm.outputAssets" :key="index" class="rounded-[14px] border border-slate-200 bg-slate-50 p-4">
              <div class="mb-3 flex items-center justify-between gap-3">
                <div class="text-sm font-semibold text-slate-900">输出资产 {{ index + 1 }}</div>
                <BaseButton v-if="manualProcessingForm.outputAssets.length > 1" @click="removeOutputAsset(index)">移除</BaseButton>
              </div>

              <div class="grid gap-4 md:grid-cols-2">
                <label class="block">
                  <span class="mb-1.5 block text-sm font-medium text-slate-700">资产名称</span>
                  <input v-model="output.assetName" required class="app-input" placeholder="例如：对齐结果_001" />
                </label>
                <label class="block">
                  <span class="mb-1.5 block text-sm font-medium text-slate-700">资产类型</span>
                  <select v-model="output.assetType" class="app-input">
                    <option v-for="option in outputAssetTypeOptions" :key="option" :value="option">{{ option }}</option>
                  </select>
                </label>
                <label class="block">
                  <span class="mb-1.5 block text-sm font-medium text-slate-700">来源类型</span>
                  <select v-model="output.sourceType" class="app-input">
                    <option value="EXTERNAL_PATH">EXTERNAL_PATH</option>
                    <option value="UPLOADED_FILE">UPLOADED_FILE</option>
                  </select>
                </label>
                <label v-if="output.sourceType === 'EXTERNAL_PATH'" class="block">
                  <span class="mb-1.5 block text-sm font-medium text-slate-700">外部路径</span>
                  <input v-model="output.externalPath" class="app-input" placeholder="例如：\\\\存储\\处理结果\\对齐输出" />
                </label>
                <label v-else class="block">
                  <span class="mb-1.5 block text-sm font-medium text-slate-700">关联文件 ID</span>
                  <input v-model.number="output.fileId" type="number" class="app-input" placeholder="例如：1" />
                </label>
                <label class="block md:col-span-2">
                  <span class="mb-1.5 block text-sm font-medium text-slate-700">描述</span>
                  <textarea v-model="output.description" rows="2" class="app-input resize-y" placeholder="补充输出资产说明"></textarea>
                </label>
              </div>
            </div>
          </div>
        </div>

        <div class="rounded-[16px] border border-slate-200 bg-white p-4">
          <div class="text-sm font-semibold text-slate-900">处理参数</div>
          <textarea v-model="manualParamsText" rows="8" class="app-input mt-3 font-mono text-sm"></textarea>
        </div>

        <div class="flex items-center gap-3">
          <BaseButton variant="primary" type="submit" :disabled="submittingManualProcessing">
            {{ submittingManualProcessing ? "正在登记..." : "提交人工登记" }}
          </BaseButton>
          <span v-if="manualProcessingMessage" :class="manualProcessingSuccess ? 'text-emerald-700' : 'text-rose-700'" class="text-xs">
            {{ manualProcessingMessage }}
          </span>
        </div>
      </form>
    </AppDrawer>

    <AppDrawer
      :open="assetDrawerOpen"
      eyebrow="资产详情"
      title="查看资产"
      description="查看资产来源、处理关系和基础元数据。"
      @close="assetDrawerOpen = false"
    >
      <div v-if="selectedAsset" class="space-y-4">
        <div class="rounded-[16px] border border-slate-200 bg-slate-50 p-4">
          <div class="flex flex-wrap items-center gap-2">
            <span class="app-pill border-[var(--color-brand-200)] bg-white text-[var(--color-brand-700)]">{{ selectedAsset.assetType }}</span>
            <span class="app-pill border-slate-200 bg-white text-slate-600">{{ resolveAssetSourceLabel(selectedAsset) }}</span>
            <span v-if="selectedAsset.producedByJobId" class="app-pill border-emerald-200 bg-emerald-50 text-emerald-700">派生资产</span>
          </div>
          <div class="mt-3 text-lg font-semibold text-slate-900">{{ selectedAsset.displayName }}</div>
          <div class="mt-3 grid gap-3 text-sm text-slate-600 md:grid-cols-2">
            <div>资产 ID：#{{ selectedAsset.id }}</div>
            <div>创建时间：{{ formatDateTime(selectedAsset.createdAt) }}</div>
            <div>来源说明：{{ resolveAssetSourceDetail(selectedAsset) }}</div>
            <div>关联处理作业：{{ selectedAsset.producedByJobId ? `作业 #${selectedAsset.producedByJobId}` : "-" }}</div>
          </div>
        </div>

        <div class="rounded-[16px] border border-slate-200 bg-white p-4 text-sm text-slate-600">
          <div>路径 / 文件：{{ selectedAsset.externalPath || selectedAsset.originalFilename || "-" }}</div>
          <div class="mt-2">格式：{{ selectedAsset.fileFormat || selectedAsset.fileExt || "-" }}</div>
          <div class="mt-2">描述：{{ selectedAsset.description || "-" }}</div>
          <div class="mt-2">备注：{{ selectedAsset.operatorRemark || "-" }}</div>
        </div>
      </div>
    </AppDrawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { RouterLink, useRoute } from "vue-router";
import { createExternalAsset, fetchTaskAssets } from "@/api/assets";
import { fetchTaskQcReports } from "@/api/qc";
import {
  createManualProcessingJob,
  createProcessingJob,
  fetchAvailablePipelines,
  fetchProcessingJob,
  fetchTaskLineage,
  fetchTaskProcessingJobs
} from "@/api/processing";
import { fetchTask, fetchTaskFiles } from "@/api/tasks";
import AppDrawer from "@/components/AppDrawer.vue";
import BaseButton from "@/components/BaseButton.vue";
import DataTableShell from "@/components/DataTableShell.vue";
import EmptyState from "@/components/EmptyState.vue";
import MetricCard from "@/components/MetricCard.vue";
import PageCard from "@/components/PageCard.vue";
import StatusBadge from "@/components/StatusBadge.vue";
import type { AssetType, CreateExternalAssetRequest, DataAssetResponse } from "@/types/asset";
import type { DataFileResponse } from "@/types/file";
import type { QcReportResponse } from "@/types/qc";
import type {
  AvailablePipelineResponse,
  CreateManualProcessingJobRequest,
  ManualOutputAssetRequest,
  ProcessingJobResponse,
  TaskLineageNode,
  TaskLineageResponse
} from "@/types/processing";
import type { TaskResponse } from "@/types/task";
import { DirectUploadCompletionError, directUploadToOss, retryDirectUploadCompletion } from "@/utils/ossDirectUpload";
import { formatDateTime, formatFileSize, formatStatusLabel } from "@/utils/format";

type MainTabKey = "overview" | "assets" | "processing" | "quality" | "lineage";
type AssetSubTabKey = "upload" | "external";

const route = useRoute();
const taskId = Number(route.params.taskId);

const mainTabs: { key: MainTabKey; label: string }[] = [
  { key: "overview", label: "任务概览" },
  { key: "assets", label: "资产管理" },
  { key: "processing", label: "处理流程" },
  { key: "quality", label: "质量检查" },
  { key: "lineage", label: "数据链路" }
];

const assetSubTabs: { key: AssetSubTabKey; label: string }[] = [
  { key: "upload", label: "平台上传" },
  { key: "external", label: "外部登记" }
];

const requiredAssetTypes: AssetType[] = ["MOCAP_CSV", "SMPL_RESULT"];
const assetTypeOptions: AssetType[] = ["RGB_SEQ_RAW", "RGB_VIDEO_MP4", "MOCAP_CSV", "SMPL_RESULT", "ALIGNED_RESULT", "CAMERA_PARAM", "LEFT_IMAGE_SEQUENCE", "RIGHT_IMAGE_SEQUENCE", "RAW_IMU_CSV", "FRAME_TIMESTAMPS_CSV", "DEPTH_RAW", "POSE_CACHE", "OTHER"];
const outputAssetTypeOptions: AssetType[] = ["ALIGNED_RESULT", "SMPL_RESULT", "RGB_VIDEO_MP4", "MOCAP_CSV", "OTHER"];

const activeMainTab = ref<MainTabKey>("overview");
const activeAssetSubTab = ref<AssetSubTabKey>("upload");
const showManualSection = ref(false);

const task = ref<TaskResponse | null>(null);
const taskLoading = ref(false);
const taskError = ref("");

const files = ref<DataFileResponse[]>([]);
const filesLoading = ref(false);
const filesError = ref("");

const assets = ref<DataAssetResponse[]>([]);
const assetsLoading = ref(false);
const assetsError = ref("");

const pipelines = ref<AvailablePipelineResponse[]>([]);
const pipelinesLoading = ref(false);
const pipelinesError = ref("");

const jobs = ref<ProcessingJobResponse[]>([]);
const jobsLoading = ref(false);
const jobsError = ref("");
const jobsHint = ref("");
const selectedJob = ref<ProcessingJobResponse | null>(null);

const qcReports = ref<QcReportResponse[]>([]);
const reportsLoading = ref(false);
const reportsError = ref("");

const lineage = ref<TaskLineageResponse>({ nodes: [], edges: [] });
const lineageLoading = ref(false);
const lineageError = ref("");

const manualDrawerOpen = ref(false);
const assetDrawerOpen = ref(false);
const selectedAssetId = ref<number | null>(null);

const selectedFile = ref<File | null>(null);
const selectedUploadAssetType = ref<AssetType>("OTHER");
const uploading = ref(false);
const uploadProgress = ref(0);
const uploadMessage = ref("");
const uploadSuccess = ref(false);
const pendingCompleteFileId = ref<number | null>(null);

const submittingExternalAsset = ref(false);
const externalAssetMessage = ref("");
const externalAssetSuccess = ref(false);

const submittingManualProcessing = ref(false);
const manualProcessingMessage = ref("");
const manualProcessingSuccess = ref(false);
const manualParamsText = ref('{\n  "syncOffsetMs": 42\n}');

const externalAssetForm = reactive<CreateExternalAssetRequest>({
  assetType: "SMPL_RESULT",
  displayName: "",
  externalPath: "",
  fileFormat: "",
  sizeRemark: "",
  description: "",
  operatorRemark: ""
});

const manualProcessingForm = reactive<CreateManualProcessingJobRequest>({
  pipelineId: "RGB_MOCAP_ALIGNMENT",
  inputAssetIds: [],
  outputAssets: [createEmptyOutputAsset()],
  operatorName: "",
  toolName: "",
  toolVersion: "",
  paramsJson: null,
  logPath: "",
  remark: ""
});

const selectedAsset = computed(() => assets.value.find((asset) => asset.id === selectedAssetId.value) ?? null);
const uploadedAssets = computed(() => assets.value.filter((asset) => asset.sourceType === "UPLOADED_FILE" && !asset.producedByJobId));
const externalInputAssets = computed(() => assets.value.filter((asset) => asset.sourceType === "EXTERNAL_PATH" && !asset.producedByJobId));
const derivedAssets = computed(() => assets.value.filter((asset) => Boolean(asset.producedByJobId)));
const inputAssets = computed(() => assets.value.filter((asset) => !asset.producedByJobId));
const automaticJobs = computed(() => jobs.value.filter((job) => job.executorType === "MOCK"));
const manualJobs = computed(() => jobs.value.filter((job) => job.executorType === "MANUAL"));
const reportsCount = computed(() => qcReports.value.length);

const currentTaskStatus = computed(() => {
  const status = task.value?.status;
  switch (status) {
    case "CREATED":
      return {
        label: "任务已建档",
        description: "当前任务已经创建，但还没有形成上传检查结果。后续状态会随着最近一次平台上传而变化。"
      };
    case "UPLOADED":
      return {
        label: "最近一次上传已完成",
        description: "最近一次平台上传已经完成，系统正在基于上传内容形成文件记录、资产记录和质量检查结果。"
      };
    case "QC_PASSED":
      return {
        label: "最近一次上传检查通过",
        description: "该状态仅反映最近一次平台上传对应的检查结果，不代表任务下全部资产都已通过检查。"
      };
    case "QC_WARNING":
      return {
        label: "最近一次上传检查有告警",
        description: "该状态仅反映最近一次平台上传对应的检查结果，不代表整个任务存在统一结论。"
      };
    case "QC_FAILED":
      return {
        label: "最近一次上传检查失败",
        description: "该状态仅反映最近一次平台上传对应的检查结果，不代表任务下全部资产或全部处理都失败。"
      };
    default:
      return {
        label: status ? formatStatusLabel(status) : "暂无状态",
        description: "当前状态仅作为任务容器的最近数据状态展示使用。"
      };
  }
});

const latestQcStatus = computed(() => {
  if (!files.value.length) {
    return {
      label: "暂无上传检查",
      description: "当前任务还没有平台上传文件，因此没有自动触发的质量检查结果。"
    };
  }
  if (task.value?.status === "QC_PASSED") {
    return {
      label: "最近一次上传检查通过",
      description: "该状态来自最近一次平台上传触发的质量检查。"
    };
  }
  if (task.value?.status === "QC_WARNING") {
    return {
      label: "最近一次上传检查有告警",
      description: "该状态来自最近一次平台上传触发的质量检查。"
    };
  }
  if (task.value?.status === "QC_FAILED") {
    return {
      label: "最近一次上传检查失败",
      description: "该状态来自最近一次平台上传触发的质量检查。"
    };
  }
  return {
    label: "最近一次上传已完成",
    description: "最近一次平台上传已经写入文件记录和资产记录。"
  };
});

const readyPipeline = computed(() => pipelines.value.find((pipeline) => pipeline.readinessStatus === "READY") ?? null);

const groupedInputAssets = computed(() => {
  const groups = new Map<AssetType, DataAssetResponse[]>();
  for (const asset of assets.value) {
    const list = groups.get(asset.assetType) ?? [];
    list.push(asset);
    groups.set(asset.assetType, list);
  }
  return Array.from(groups.entries()).map(([type, items]) => ({ type, items }));
});

const lineageNodeMap = computed(() => new Map(lineage.value.nodes.map((node) => [node.id, node])));
const lineageJobGroups = computed(() => {
  const jobNodes = lineage.value.nodes.filter((node) => node.type === "job");
  return jobNodes.map((job) => ({
    job,
    inputs: lineage.value.edges
      .filter((edge) => edge.target === job.id && edge.label === "input")
      .map((edge) => lineageNodeMap.value.get(edge.source))
      .filter((node): node is TaskLineageNode => Boolean(node)),
    outputs: lineage.value.edges
      .filter((edge) => edge.source === job.id && edge.label === "output")
      .map((edge) => lineageNodeMap.value.get(edge.target))
      .filter((node): node is TaskLineageNode => Boolean(node))
  }));
});

function createEmptyOutputAsset(): ManualOutputAssetRequest {
  return {
    assetName: "",
    assetType: "ALIGNED_RESULT",
    sourceType: "EXTERNAL_PATH",
    externalPath: "",
    fileId: null,
    description: ""
  };
}

function resolveAssetSourceLabel(asset: DataAssetResponse) {
  if (asset.producedByJobId) {
    return "处理生成";
  }
  if (asset.sourceType === "UPLOADED_FILE") {
    return "平台上传";
  }
  if (asset.sourceType === "EXTERNAL_PATH") {
    return "外部路径";
  }
  return asset.sourceType;
}

function resolveAssetSourceDetail(asset: DataAssetResponse) {
  if (asset.producedByJobId) {
    return `由作业 #${asset.producedByJobId} 生成`;
  }
  if (asset.sourceType === "UPLOADED_FILE") {
    return "原值：UPLOADED_FILE";
  }
  if (asset.sourceType === "EXTERNAL_PATH") {
    return "原值：EXTERNAL_PATH";
  }
  return asset.sourceType;
}

function resolveUploadStatusLabel(status?: string | null) {
  if (!status) {
    return "暂无上传状态";
  }
  if (status === "SUCCESS") {
    return "上传成功";
  }
  return formatStatusLabel(status);
}

function assetSourceBadge(assetId?: number | null) {
  const asset = assets.value.find((item) => item.id === assetId);
  if (!asset) {
    return "-";
  }
  return resolveAssetSourceLabel(asset);
}

function relatedJob(jobId?: number | null) {
  return jobs.value.find((job) => job.id === jobId) ?? null;
}

function stringifyJson(value: unknown) {
  return JSON.stringify(value ?? {}, null, 2);
}

function parseManualParams() {
  const raw = manualParamsText.value.trim();
  if (!raw) {
    return null;
  }
  return JSON.parse(raw) as Record<string, unknown>;
}

function handleFileChange(event: Event) {
  const target = event.target as HTMLInputElement;
  selectedFile.value = target.files?.[0] ?? null;
}

function openManualDrawer(assetId?: number) {
  manualDrawerOpen.value = true;
  showManualSection.value = true;
  if (assetId && !manualProcessingForm.inputAssetIds.includes(assetId)) {
    manualProcessingForm.inputAssetIds.push(assetId);
  }
}

function openAssetDrawer(assetId?: number | null) {
  selectedAssetId.value = assetId ?? null;
  assetDrawerOpen.value = true;
}

function addOutputAsset() {
  manualProcessingForm.outputAssets.push(createEmptyOutputAsset());
}

function removeOutputAsset(index: number) {
  manualProcessingForm.outputAssets.splice(index, 1);
}

function viewAssetOrigin(asset: DataAssetResponse) {
  if (asset.producedByJobId) {
    activeMainTab.value = "processing";
    selectJob(asset.producedByJobId);
    jobsHint.value = `该资产来自作业 #${asset.producedByJobId}。`;
    return;
  }
  if (asset.sourceType === "UPLOADED_FILE") {
    activeMainTab.value = "assets";
    activeAssetSubTab.value = "upload";
    jobsHint.value = "该资产来自平台上传。";
    return;
  }
  activeMainTab.value = "assets";
  activeAssetSubTab.value = "external";
  jobsHint.value = "该资产来自外部路径登记。";
}

function setQualityTab() {
  activeMainTab.value = "quality";
}

async function loadTask() {
  taskLoading.value = true;
  taskError.value = "";
  try {
    task.value = await fetchTask(taskId);
  } catch (error) {
    taskError.value = error instanceof Error ? error.message : "加载任务详情失败";
  } finally {
    taskLoading.value = false;
  }
}

async function loadFiles() {
  filesLoading.value = true;
  filesError.value = "";
  try {
    files.value = await fetchTaskFiles(taskId);
  } catch (error) {
    filesError.value = error instanceof Error ? error.message : "加载上传文件失败";
  } finally {
    filesLoading.value = false;
  }
}

async function loadAssets() {
  assetsLoading.value = true;
  assetsError.value = "";
  try {
    assets.value = await fetchTaskAssets(taskId);
  } catch (error) {
    assetsError.value = error instanceof Error ? error.message : "加载资产列表失败";
  } finally {
    assetsLoading.value = false;
  }
}

async function loadPipelines() {
  pipelinesLoading.value = true;
  pipelinesError.value = "";
  try {
    pipelines.value = await fetchAvailablePipelines(taskId);
  } catch (error) {
    pipelinesError.value = error instanceof Error ? error.message : "加载处理条件检查失败";
  } finally {
    pipelinesLoading.value = false;
  }
}

async function loadJobs() {
  jobsLoading.value = true;
  jobsError.value = "";
  try {
    jobs.value = await fetchTaskProcessingJobs(taskId);
  } catch (error) {
    jobsError.value = error instanceof Error ? error.message : "加载处理作业失败";
  } finally {
    jobsLoading.value = false;
  }
}

async function loadReports() {
  reportsLoading.value = true;
  reportsError.value = "";
  try {
    qcReports.value = await fetchTaskQcReports(taskId);
  } catch (error) {
    reportsError.value = error instanceof Error ? error.message : "加载质量检查报告失败";
  } finally {
    reportsLoading.value = false;
  }
}

async function loadLineage() {
  lineageLoading.value = true;
  lineageError.value = "";
  try {
    lineage.value = await fetchTaskLineage(taskId);
  } catch (error) {
    lineageError.value = error instanceof Error ? error.message : "加载数据链路失败";
    lineage.value = { nodes: [], edges: [] };
  } finally {
    lineageLoading.value = false;
  }
}

async function refreshTaskWorkspace() {
  await Promise.all([loadTask(), loadFiles(), loadAssets(), loadPipelines(), loadJobs(), loadReports(), loadLineage()]);
}

async function submitUpload() {
  if (!selectedFile.value) {
    return;
  }
  uploading.value = true;
  uploadProgress.value = 0;
  uploadMessage.value = "";
  pendingCompleteFileId.value = null;
  try {
    await directUploadToOss({
      taskId,
      file: selectedFile.value,
      assetType: selectedUploadAssetType.value,
      onProgress: (percent) => {
        uploadProgress.value = percent;
      },
    });
    uploadSuccess.value = true;
    uploadMessage.value = "接入成功：文件已直传 OSS 并完成平台登记";
    selectedFile.value = null;
    await refreshTaskWorkspace();
  } catch (error) {
    uploadSuccess.value = false;
    if (error instanceof DirectUploadCompletionError) {
      pendingCompleteFileId.value = error.fileId;
      uploadMessage.value = "文件已上传到 OSS，但平台登记失败，可重试完成登记";
    } else {
      uploadMessage.value = error instanceof Error ? error.message : "平台上传失败";
    }
  } finally {
    uploading.value = false;
  }
}

async function retryPendingComplete() {
  if (!pendingCompleteFileId.value) {
    return;
  }
  uploading.value = true;
  uploadMessage.value = "";
  try {
    await retryDirectUploadCompletion(pendingCompleteFileId.value);
    pendingCompleteFileId.value = null;
    uploadSuccess.value = true;
    uploadMessage.value = "平台登记已完成";
    selectedFile.value = null;
    await refreshTaskWorkspace();
  } catch (error) {
    uploadSuccess.value = false;
    uploadMessage.value = error instanceof Error ? error.message : "完成登记失败";
  } finally {
    uploading.value = false;
  }
}

async function submitExternalAsset() {
  submittingExternalAsset.value = true;
  externalAssetMessage.value = "";
  try {
    await createExternalAsset(taskId, externalAssetForm);
    externalAssetSuccess.value = true;
    externalAssetMessage.value = "外部资产登记成功";
    Object.assign(externalAssetForm, {
      assetType: externalAssetForm.assetType,
      displayName: "",
      externalPath: "",
      fileFormat: "",
      sizeRemark: "",
      description: "",
      operatorRemark: ""
    });
    await Promise.all([loadAssets(), loadPipelines(), loadLineage()]);
  } catch (error) {
    externalAssetSuccess.value = false;
    externalAssetMessage.value = error instanceof Error ? error.message : "登记外部资产失败";
  } finally {
    submittingExternalAsset.value = false;
  }
}

async function submitProcessingJob(pipelineId: string) {
  jobsError.value = "";
  try {
    const createdJob = await createProcessingJob(taskId, { pipelineId });
    selectedJob.value = createdJob;
    activeMainTab.value = "processing";
    jobsHint.value = "已创建一条自动处理作业。当前结果仅用于验证占位执行链路。";
    await Promise.all([loadJobs(), loadLineage()]);
  } catch (error) {
    jobsError.value = error instanceof Error ? error.message : "创建自动处理作业失败";
  }
}

async function submitManualProcessingJob() {
  submittingManualProcessing.value = true;
  manualProcessingMessage.value = "";
  try {
    manualProcessingForm.paramsJson = parseManualParams();
    const response = await createManualProcessingJob(taskId, manualProcessingForm);
    manualProcessingSuccess.value = true;
    manualProcessingMessage.value = `已登记处理作业，并生成 ${response.outputAssets.length} 个派生资产。`;
    selectedJob.value = response.job;
    manualDrawerOpen.value = false;
    manualProcessingForm.inputAssetIds = [];
    manualProcessingForm.outputAssets = [createEmptyOutputAsset()];
    manualProcessingForm.operatorName = "";
    manualProcessingForm.toolName = "";
    manualProcessingForm.toolVersion = "";
    manualProcessingForm.paramsJson = null;
    manualProcessingForm.logPath = "";
    manualProcessingForm.remark = "";
    manualParamsText.value = '{\n  "syncOffsetMs": 42\n}';
    showManualSection.value = true;
    await Promise.all([loadAssets(), loadJobs(), loadPipelines(), loadLineage()]);
  } catch (error) {
    manualProcessingSuccess.value = false;
    manualProcessingMessage.value = error instanceof Error ? error.message : "登记人工处理失败";
  } finally {
    submittingManualProcessing.value = false;
  }
}

async function selectJob(jobId?: number | null) {
  if (!jobId) {
    return;
  }
  try {
    selectedJob.value = await fetchProcessingJob(jobId);
  } catch (error) {
    jobsError.value = error instanceof Error ? error.message : "加载处理作业详情失败";
  }
}

onMounted(refreshTaskWorkspace);
</script>
