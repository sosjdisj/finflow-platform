<script setup lang="ts">
import type { MenuNode } from '@/types/api'

/**
 * 递归菜单项：目录（menuType=1）渲染子菜单，页面（menuType=2 且 visible）渲染可点击项。
 * index 用菜单 path（el-menu 开启 router 模式后即跳转目标）。
 */
defineOptions({ name: 'MenuItem' })

const props = defineProps<{ node: MenuNode }>()

const isDirectory = props.node.menuType === 1 && !!props.node.children?.length
const isPage = props.node.menuType === 2 && !!props.node.path && props.node.visible === 1
</script>

<template>
  <el-sub-menu v-if="isDirectory" :index="String(props.node.id)">
    <template #title>{{ props.node.title }}</template>
    <MenuItem v-for="child in props.node.children" :key="child.id" :node="child" />
  </el-sub-menu>
  <el-menu-item v-else-if="isPage" :index="props.node.path">{{ props.node.title }}</el-menu-item>
</template>
