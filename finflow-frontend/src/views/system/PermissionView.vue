<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { permissionApi } from '@/api/system'
import { usePaging } from '@/composables/usePaging'
import { dateTimeFormatter } from '@/utils/format'
import type { Permission } from '@/types/api'

/**
 * 权限点管理：code 是权限体系唯一事实源，创建后不可改（编辑只开放名称/描述/状态）。
 */
const paging = usePaging<Permission>((page, size) => permissionApi.page(page, size), 20)
onMounted(paging.load)

const typeLabel = (type: number): string => (type === 1 ? '菜单可见性' : '操作')

/** ---------- 新增 / 编辑 ---------- */
const editVisible = ref(false)
const editing = ref<Permission | null>(null)
const editFormRef = ref<FormInstance>()
const editForm = reactive({ code: '', name: '', type: 2, description: '', status: 1 })
const editRules = {
  code: [{ required: true, pattern: /^[a-z]+:[a-z-]+(:[a-z-]+)?$/, message: '格式如 system:user:create', trigger: 'blur' }],
  name: [{ required: true, message: '请输入权限名称', trigger: 'blur' }],
}

function openCreate(): void {
  editing.value = null
  Object.assign(editForm, { code: '', name: '', type: 2, description: '', status: 1 })
  editVisible.value = true
}

function openEdit(perm: Permission): void {
  editing.value = perm
  Object.assign(editForm, {
    code: perm.code,
    name: perm.name,
    type: perm.type,
    description: perm.description ?? '',
    status: perm.status,
  })
  editVisible.value = true
}

async function submitEdit(): Promise<void> {
  const valid = await editFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  if (editing.value === null) {
    await permissionApi.create({
      code: editForm.code.trim(),
      name: editForm.name.trim(),
      type: editForm.type,
      description: editForm.description || undefined,
    })
    ElMessage.success('创建成功')
  } else {
    await permissionApi.update(editing.value.id, {
      name: editForm.name.trim(),
      description: editForm.description || undefined,
      status: editForm.status,
    })
    ElMessage.success('保存成功')
  }
  editVisible.value = false
  await paging.load()
}

async function remove(perm: Permission): Promise<void> {
  await ElMessageBox.confirm(`确定删除权限点「${perm.code}」吗？已引用它的菜单与角色将失去关联。`, '提示', {
    type: 'warning',
  })
  await permissionApi.remove(perm.id)
  ElMessage.success('已删除')
  await paging.load()
}
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-button v-permission="'system:permission:create'" type="primary" @click="openCreate">新增权限点</el-button>
    </div>

    <el-table v-loading="paging.loading" :data="paging.records" border>
      <el-table-column prop="code" label="权限编码" min-width="200" />
      <el-table-column prop="name" label="名称" min-width="120" />
      <el-table-column label="类型" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="row.type === 1 ? 'warning' : 'info'">{{ typeLabel(row.type) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
      <el-table-column prop="createdAt" label="创建时间" width="170" :formatter="dateTimeFormatter" />
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="'system:permission:update'" link type="primary" @click="openEdit(row)">
            编辑
          </el-button>
          <el-button v-permission="'system:permission:delete'" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="paging.page"
      v-model:page-size="paging.size"
      class="pager"
      layout="total, sizes, prev, pager, next"
      :total="paging.total"
      @current-change="paging.load()"
      @size-change="paging.reload()"
    />
  </el-card>

  <el-dialog v-model="editVisible" :title="editing === null ? '新增权限点' : `编辑权限点：${editing.code}`" width="460px">
    <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="80px">
      <el-form-item label="编码" prop="code">
        <el-input v-model="editForm.code" :disabled="editing !== null" placeholder="如 system:user:create" />
      </el-form-item>
      <el-form-item label="名称" prop="name">
        <el-input v-model="editForm.name" />
      </el-form-item>
      <el-form-item label="类型" prop="type">
        <el-radio-group v-model="editForm.type" :disabled="editing !== null">
          <el-radio :value="1">菜单可见性</el-radio>
          <el-radio :value="2">操作</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="描述" prop="description">
        <el-input v-model="editForm.description" type="textarea" :rows="2" />
      </el-form-item>
      <el-form-item v-if="editing !== null" label="状态" prop="status">
        <el-radio-group v-model="editForm.status">
          <el-radio :value="1">启用</el-radio>
          <el-radio :value="0">停用</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="editVisible = false">取消</el-button>
      <el-button type="primary" @click="submitEdit">确定</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.toolbar {
  margin-bottom: 12px;
}

.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
