<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { menuApi, permissionApi, type MenuPayload } from '@/api/system'
import { hasPerm } from '@/directives/permission'
import type { MenuNode, Permission } from '@/types/api'

/**
 * 菜单管理：树形表格（目录/页面两级），页面菜单引用权限点（permission_id）控制可见性。
 * 新建页面菜单后需在前端 router 的 pageComponents 登记 path → 组件映射，否则路由不生效。
 */
const tree = ref<MenuNode[]>([])
const loading = ref(false)
/** 权限点 id → name 映射，表格展示引用名称。 */
const permNameById = ref<Record<number, string>>({})

async function load(): Promise<void> {
  loading.value = true
  try {
    tree.value = await menuApi.tree()
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await load()
  const permPage = await permissionApi.page(1, 500) // 权限点数量有限，一次拉全做名称映射
  permNameById.value = Object.fromEntries(permPage.records.map((p) => [p.id, p.name]))
})

const canOperate = computed(() => hasPerm('system:menu:update') || hasPerm('system:menu:create'))

/** ---------- 新增 / 编辑 ---------- */
const editVisible = ref(false)
const editingId = ref<number | null>(null)
const editFormRef = ref<FormInstance>()
const editForm = reactive<MenuPayload>({
  parentId: 0,
  title: '',
  path: '',
  icon: null,
  menuType: 2,
  sortNo: 0,
  permissionId: null,
  visible: 1,
})
const editRules = {
  title: [{ required: true, message: '请输入菜单标题', trigger: 'blur' }],
  path: [
    {
      validator: (_rule: unknown, value: string, callback: (error?: Error) => void) => {
        if (editForm.menuType === 2 && !value.startsWith('/')) {
          callback(new Error('页面菜单路径须以 / 开头'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
  permissionId: [
    {
      validator: (_rule: unknown, value: number | null, callback: (error?: Error) => void) => {
        if (editForm.menuType === 2 && !value) {
          callback(new Error('页面菜单必须引用权限点'))
        } else {
          callback()
        }
      },
      trigger: 'change',
    },
  ],
}

/** 父菜单选项：根目录 + 全部目录节点（页面不能作父级）。 */
const parentOptions = computed(() => {
  const directories = tree.value.filter((node) => node.menuType === 1)
  return [{ id: 0, title: '根目录' }, ...directories]
})

/** 可见性权限点选项（type=1）。 */
const visiblePermissions = ref<Permission[]>([])
onMounted(async () => {
  const permPage = await permissionApi.page(1, 500)
  visiblePermissions.value = permPage.records.filter((p) => p.type === 1)
})

function openCreate(parent?: MenuNode): void {
  editingId.value = null
  Object.assign(editForm, {
    parentId: parent?.id ?? 0,
    title: '',
    path: '',
    icon: null,
    menuType: parent ? 2 : 1,
    sortNo: 0,
    permissionId: null,
    visible: 1,
  })
  editVisible.value = true
}

function openEdit(node: MenuNode): void {
  editingId.value = node.id
  Object.assign(editForm, {
    parentId: node.parentId,
    title: node.title,
    path: node.path ?? '',
    icon: node.icon,
    menuType: node.menuType,
    sortNo: node.sortNo,
    permissionId: node.permissionId,
    visible: node.visible,
  })
  editVisible.value = true
}

async function submitEdit(): Promise<void> {
  const valid = await editFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  const payload: MenuPayload = {
    ...editForm,
    title: editForm.title.trim(),
    path: editForm.menuType === 2 ? editForm.path.trim() : '',
    permissionId: editForm.menuType === 2 ? editForm.permissionId : null,
  }
  if (editingId.value === null) {
    await menuApi.create(payload)
    ElMessage.success('创建成功')
  } else {
    await menuApi.update(editingId.value, payload)
    ElMessage.success('保存成功')
  }
  editVisible.value = false
  await load()
}

async function remove(node: MenuNode): Promise<void> {
  await ElMessageBox.confirm(`确定删除菜单「${node.title}」吗？其子菜单将一并失效。`, '提示', { type: 'warning' })
  await menuApi.remove(node.id)
  ElMessage.success('已删除')
  await load()
}
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-button v-permission="'system:menu:create'" type="primary" @click="openCreate()">新增目录</el-button>
    </div>

    <el-table
      v-loading="loading"
      :data="tree"
      row-key="id"
      border
      default-expand-all
      :tree-props="{ children: 'children' }"
    >
      <el-table-column prop="title" label="标题" min-width="180" />
      <el-table-column label="类型" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.menuType === 1 ? 'warning' : 'info'">{{ row.menuType === 1 ? '目录' : '页面' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="path" label="路径" min-width="160" />
      <el-table-column label="权限点" min-width="140">
        <template #default="{ row }">
          {{ row.permissionId ? (permNameById[row.permissionId] ?? row.permissionId) : '—' }}
        </template>
      </el-table-column>
      <el-table-column prop="sortNo" label="排序" width="70" align="center" />
      <el-table-column label="可见" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.visible === 1 ? 'success' : 'info'">{{ row.visible === 1 ? '显示' : '隐藏' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="canOperate" label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.menuType === 1"
            v-permission="'system:menu:create'"
            link
            type="primary"
            @click="openCreate(row)"
          >
            新增子级
          </el-button>
          <el-button v-permission="'system:menu:update'" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-permission="'system:menu:delete'" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>

  <el-dialog v-model="editVisible" :title="editingId === null ? '新增菜单' : `编辑菜单：${editForm.title}`" width="520px">
    <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="90px">
      <el-form-item label="父菜单" prop="parentId">
        <el-select v-model="editForm.parentId" class="full">
          <el-option v-for="option in parentOptions" :key="option.id" :value="option.id" :label="option.title" />
        </el-select>
      </el-form-item>
      <el-form-item label="类型" prop="menuType">
        <el-radio-group v-model="editForm.menuType">
          <el-radio :value="1">目录</el-radio>
          <el-radio :value="2">页面</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="标题" prop="title">
        <el-input v-model="editForm.title" />
      </el-form-item>
      <el-form-item v-if="editForm.menuType === 2" label="路径" prop="path">
        <el-input v-model="editForm.path" placeholder="/system/xxx，需与前端页面组件映射一致" />
      </el-form-item>
      <el-form-item v-if="editForm.menuType === 2" label="权限点" prop="permissionId">
        <el-select v-model="editForm.permissionId" class="full" placeholder="控制此页面可见的权限点">
          <el-option v-for="perm in visiblePermissions" :key="perm.id" :value="perm.id" :label="`${perm.name}（${perm.code}）`" />
        </el-select>
      </el-form-item>
      <el-form-item label="图标" prop="icon">
        <el-input v-model="editForm.icon" placeholder="可选，图标名（组件按需接入后生效）" />
      </el-form-item>
      <el-form-item label="排序" prop="sortNo">
        <el-input-number v-model="editForm.sortNo" :min="0" :max="999" />
      </el-form-item>
      <el-form-item label="可见" prop="visible">
        <el-radio-group v-model="editForm.visible">
          <el-radio :value="1">显示</el-radio>
          <el-radio :value="0">隐藏</el-radio>
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

.full {
  width: 100%;
}
</style>
