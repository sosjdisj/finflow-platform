<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { roleApi, userApi } from '@/api/system'
import { usePaging } from '@/composables/usePaging'
import { dateTimeFormatter } from '@/utils/format'
import { useUserStore } from '@/stores/user'
import type { Role, UserAdmin } from '@/types/api'

/**
 * 用户管理：分页 + 搜索 + 新增/编辑/启停/重置密码/分配角色。
 * 按钮按权限码显隐（v-permission）；禁用操作者本人会被后端拒绝，前端也提前禁用。
 */
const userStore = useUserStore()
const keyword = ref('')

const paging = usePaging<UserAdmin>((page, size) => userApi.page(page, size, keyword.value || undefined))
onMounted(paging.load)

function isSelf(row: UserAdmin): boolean {
  return row.id === userStore.profile?.id
}

/** ---------- 新增 / 编辑 ---------- */
const editVisible = ref(false)
const editingId = ref<number | null>(null)
const editFormRef = ref<FormInstance>()
const editForm = reactive({ username: '', nickname: '', email: '', phone: '', password: '' })
const editRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, min: 8, max: 64, message: '密码长度 8~64', trigger: 'blur' }],
  nickname: [{ max: 64, message: '最长 64 个字符', trigger: 'blur' }],
}

function openCreate(): void {
  editingId.value = null
  Object.assign(editForm, { username: '', nickname: '', email: '', phone: '', password: '' })
  editVisible.value = true
}

function openEdit(row: UserAdmin): void {
  editingId.value = row.id
  Object.assign(editForm, {
    username: row.username,
    nickname: row.nickname ?? '',
    email: row.email ?? '',
    phone: row.phone ?? '',
    password: '',
  })
  editVisible.value = true
}

async function submitEdit(): Promise<void> {
  const valid = await editFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  if (editingId.value === null) {
    await userApi.create({
      username: editForm.username.trim(),
      password: editForm.password,
      nickname: editForm.nickname || undefined,
      email: editForm.email || undefined,
      phone: editForm.phone || undefined,
    })
    ElMessage.success('创建成功')
  } else {
    await userApi.update(editingId.value, {
      nickname: editForm.nickname || undefined,
      email: editForm.email || undefined,
      phone: editForm.phone || undefined,
    })
    ElMessage.success('保存成功')
  }
  editVisible.value = false
  await paging.load()
}

/** ---------- 启停 ---------- */
async function toggleStatus(row: UserAdmin): Promise<void> {
  const next = row.status === 1 ? 0 : 1
  if (next === 0) {
    await ElMessageBox.confirm(`确定禁用用户「${row.username}」吗？其登录会话将全部失效。`, '提示', { type: 'warning' })
  }
  await userApi.updateStatus(row.id, next)
  ElMessage.success(next === 1 ? '已启用' : '已禁用')
  await paging.load()
}

/** ---------- 重置密码 ---------- */
const pwdVisible = ref(false)
const pwdFormRef = ref<FormInstance>()
const pwdForm = reactive({ userId: 0, username: '', newPassword: '' })
const pwdRules = {
  newPassword: [{ required: true, min: 8, max: 64, message: '密码长度 8~64', trigger: 'blur' }],
}

function openResetPassword(row: UserAdmin): void {
  Object.assign(pwdForm, { userId: row.id, username: row.username, newPassword: '' })
  pwdVisible.value = true
}

async function submitResetPassword(): Promise<void> {
  const valid = await pwdFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  await userApi.resetPassword(pwdForm.userId, pwdForm.newPassword)
  ElMessage.success('密码已重置，该用户全部会话已失效')
  pwdVisible.value = false
}

/** ---------- 分配角色 ---------- */
const rolesVisible = ref(false)
const rolesTarget = ref<UserAdmin | null>(null)
const roleOptions = ref<Role[]>([])
const checkedRoleIds = ref<number[]>([])

async function openAssignRoles(row: UserAdmin): Promise<void> {
  rolesTarget.value = row
  const [owned, pageData] = await Promise.all([
    userApi.roles(row.id),
    roleApi.page(1, 200), // 角色数量有限，一次拉全
  ])
  roleOptions.value = pageData.records
  checkedRoleIds.value = owned.roleIds
  rolesVisible.value = true
}

async function submitAssignRoles(): Promise<void> {
  if (!rolesTarget.value) {
    return
  }
  await userApi.assignRoles(rolesTarget.value.id, checkedRoleIds.value)
  ElMessage.success('角色已更新')
  rolesVisible.value = false
}
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-input
        v-model="keyword"
        class="search"
        placeholder="用户名 / 昵称"
        clearable
        @keyup.enter="paging.reload()"
        @clear="paging.reload()"
      />
      <el-button type="primary" plain @click="paging.reload()">查询</el-button>
      <el-button v-permission="'system:user:create'" type="primary" @click="openCreate">新增用户</el-button>
    </div>

    <el-table v-loading="paging.loading" :data="paging.records" border>
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column prop="nickname" label="昵称" min-width="120" />
      <el-table-column prop="email" label="邮箱" min-width="160" />
      <el-table-column prop="phone" label="手机号" min-width="120" />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastLoginAt" label="最后登录" width="170" :formatter="dateTimeFormatter" />
      <el-table-column prop="createdAt" label="创建时间" width="170" :formatter="dateTimeFormatter" />
      <el-table-column label="操作" width="300" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="'system:user:update'" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button
            v-permission="'system:user:update'"
            link
            :type="row.status === 1 ? 'danger' : 'success'"
            :disabled="isSelf(row)"
            @click="toggleStatus(row)"
          >
            {{ row.status === 1 ? '禁用' : '启用' }}
          </el-button>
          <el-button v-permission="'system:user:reset-password'" link type="warning" @click="openResetPassword(row)">
            重置密码
          </el-button>
          <el-button v-permission="'system:user:assign-role'" link type="primary" @click="openAssignRoles(row)">
            分配角色
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

  <el-dialog v-model="editVisible" :title="editingId === null ? '新增用户' : `编辑用户：${editForm.username}`" width="480px">
    <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="80px">
      <el-form-item label="用户名" prop="username">
        <el-input v-model="editForm.username" :disabled="editingId !== null" placeholder="字母、数字、下划线" />
      </el-form-item>
      <el-form-item v-if="editingId === null" label="初始密码" prop="password">
        <el-input v-model="editForm.password" type="password" show-password placeholder="8~64 位" />
      </el-form-item>
      <el-form-item label="昵称" prop="nickname">
        <el-input v-model="editForm.nickname" />
      </el-form-item>
      <el-form-item label="邮箱" prop="email">
        <el-input v-model="editForm.email" />
      </el-form-item>
      <el-form-item label="手机号" prop="phone">
        <el-input v-model="editForm.phone" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="editVisible = false">取消</el-button>
      <el-button type="primary" @click="submitEdit">确定</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="pwdVisible" :title="`重置密码：${pwdForm.username}`" width="420px">
    <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="80px">
      <el-form-item label="新密码" prop="newPassword">
        <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="8~64 位" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="pwdVisible = false">取消</el-button>
      <el-button type="primary" @click="submitResetPassword">确定</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="rolesVisible" :title="`分配角色：${rolesTarget?.username ?? ''}`" width="420px">
    <el-checkbox-group v-model="checkedRoleIds">
      <el-checkbox v-for="role in roleOptions" :key="role.id" :value="role.id" :disabled="role.status !== 1">
        {{ role.name }}（{{ role.code }}）
      </el-checkbox>
    </el-checkbox-group>
    <template #footer>
      <el-button @click="rolesVisible = false">取消</el-button>
      <el-button type="primary" @click="submitAssignRoles">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.search {
  width: 240px;
}

.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
