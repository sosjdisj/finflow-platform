<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { permissionApi, roleApi } from '@/api/system'
import { usePaging } from '@/composables/usePaging'
import { dateTimeFormatter } from '@/utils/format'
import type { Permission, Role } from '@/types/api'

/**
 * 角色管理：内置角色（builtIn=1）禁编辑/删除/分配权限，前端置灰 + 后端兜底。
 * 分配权限为全量覆盖：打开时回显已勾选，保存整包提交。
 */
const paging = usePaging<Role>((page, size) => roleApi.page(page, size))
onMounted(paging.load)

/** ---------- 新增 / 编辑 ---------- */
const editVisible = ref(false)
const editing = ref<Role | null>(null)
const editFormRef = ref<FormInstance>()
const editForm = reactive({ code: '', name: '', description: '', status: 1 })
const editRules = {
  code: [{ required: true, pattern: /^[A-Z][A-Z0-9_]*$/, message: '大写字母开头，仅大写字母、数字与下划线', trigger: 'blur' }],
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
}

function openCreate(): void {
  editing.value = null
  Object.assign(editForm, { code: '', name: '', description: '', status: 1 })
  editVisible.value = true
}

function openEdit(role: Role): void {
  editing.value = role
  Object.assign(editForm, { code: role.code, name: role.name, description: role.description ?? '', status: role.status })
  editVisible.value = true
}

async function submitEdit(): Promise<void> {
  const valid = await editFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  if (editing.value === null) {
    await roleApi.create({
      code: editForm.code.trim(),
      name: editForm.name.trim(),
      description: editForm.description || undefined,
    })
    ElMessage.success('创建成功')
  } else {
    await roleApi.update(editing.value.id, {
      name: editForm.name.trim(),
      description: editForm.description || undefined,
      status: editForm.status,
    })
    ElMessage.success('保存成功')
  }
  editVisible.value = false
  await paging.load()
}

/** ---------- 分配权限 ---------- */
const permVisible = ref(false)
const permTarget = ref<Role | null>(null)
const allPermissions = ref<Permission[]>([])
const checkedPermissionIds = ref<number[]>([])

/** 权限点按类型分组展示：1 菜单可见性 / 2 操作。 */
const groupedPermissions = (): Array<{ type: number; label: string; items: Permission[] }> => [
  { type: 1, label: '菜单可见性', items: allPermissions.value.filter((p) => p.type === 1) },
  { type: 2, label: '操作权限', items: allPermissions.value.filter((p) => p.type === 2) },
]

async function openAssignPermissions(role: Role): Promise<void> {
  permTarget.value = role
  const [owned, permPage] = await Promise.all([
    roleApi.permissions(role.id),
    permissionApi.page(1, 500), // 权限点数量有限，一次拉全
  ])
  allPermissions.value = permPage.records
  checkedPermissionIds.value = owned.permissionIds
  permVisible.value = true
}

async function submitAssignPermissions(): Promise<void> {
  if (!permTarget.value) {
    return
  }
  await roleApi.assignPermissions(permTarget.value.id, checkedPermissionIds.value)
  ElMessage.success('权限已更新')
  permVisible.value = false
}

/** ---------- 删除 ---------- */
async function remove(role: Role): Promise<void> {
  await ElMessageBox.confirm(`确定删除角色「${role.name}」吗？已关联的用户将失去该角色。`, '提示', { type: 'warning' })
  await roleApi.remove(role.id)
  ElMessage.success('已删除')
  await paging.load()
}
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-button v-permission="'system:role:create'" type="primary" @click="openCreate">新增角色</el-button>
    </div>

    <el-table v-loading="paging.loading" :data="paging.records" border>
      <el-table-column prop="code" label="角色编码" min-width="140" />
      <el-table-column prop="name" label="角色名称" min-width="120" />
      <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
      <el-table-column label="内置" width="80" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.builtIn === 1" type="info">内置</el-tag>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="170" :formatter="dateTimeFormatter" />
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button
            v-permission="'system:role:update'"
            link
            type="primary"
            :disabled="row.builtIn === 1"
            @click="openEdit(row)"
          >
            编辑
          </el-button>
          <el-button
            v-permission="'system:role:assign'"
            link
            type="primary"
            :disabled="row.builtIn === 1"
            @click="openAssignPermissions(row)"
          >
            分配权限
          </el-button>
          <el-button
            v-permission="'system:role:delete'"
            link
            type="danger"
            :disabled="row.builtIn === 1"
            @click="remove(row)"
          >
            删除
          </el-button>
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

  <el-dialog v-model="editVisible" :title="editing === null ? '新增角色' : `编辑角色：${editing.name}`" width="460px">
    <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="80px">
      <el-form-item label="角色编码" prop="code">
        <el-input v-model="editForm.code" :disabled="editing !== null" placeholder="如 OPERATOR" />
      </el-form-item>
      <el-form-item label="角色名称" prop="name">
        <el-input v-model="editForm.name" />
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

  <el-dialog v-model="permVisible" :title="`分配权限：${permTarget?.name ?? ''}`" width="560px">
    <el-checkbox-group v-model="checkedPermissionIds">
      <div v-for="group in groupedPermissions()" :key="group.type" class="perm-group">
        <div class="perm-label">{{ group.label }}</div>
        <el-checkbox v-for="perm in group.items" :key="perm.id" :value="perm.id">
          {{ perm.name }}（{{ perm.code }}）
        </el-checkbox>
      </div>
    </el-checkbox-group>
    <template #footer>
      <el-button @click="permVisible = false">取消</el-button>
      <el-button type="primary" @click="submitAssignPermissions">保存</el-button>
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

.perm-group {
  margin-bottom: 12px;
}

.perm-label {
  font-weight: 600;
  margin-bottom: 4px;
}
</style>
