<template>
  <el-container class="layout">
    <el-aside :width="collapsed ? '64px' : '216px'" class="aside">
      <div class="logo" @click="$router.push('/chat')">
        <span class="logo-mark">知</span>
        <span v-if="!collapsed" class="logo-text">知库问答</span>
      </div>
      <el-menu
        :default-active="$route.path"
        router
        :collapse="collapsed"
        class="nav"
        :collapse-transition="false"
      >
        <el-menu-item index="/chat">
          <el-icon><ChatDotRound /></el-icon><template #title>问答对话</template>
        </el-menu-item>
        <el-menu-item index="/kb">
          <el-icon><Files /></el-icon><template #title>知识库</template>
        </el-menu-item>
        <el-menu-item index="/history">
          <el-icon><Clock /></el-icon><template #title>问答历史</template>
        </el-menu-item>
        <el-menu-item index="/feedback">
          <el-icon><WarningFilled /></el-icon><template #title>待优化</template>
        </el-menu-item>
        <el-menu-item index="/dashboard">
          <el-icon><DataAnalysis /></el-icon><template #title>数据看板</template>
        </el-menu-item>
      </el-menu>
      <div class="aside-foot" @click="collapsed = !collapsed">
        <el-icon><component :is="collapsed ? 'Expand' : 'Fold'" /></el-icon>
      </div>
    </el-aside>
    <el-main class="main">
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup>
import { ref } from 'vue'

const collapsed = ref(false)
</script>

<style lang="scss">
.layout {
  height: 100vh;
}

.aside {
  background: var(--brand-900);
  display: flex;
  flex-direction: column;
  transition: width 0.2s ease;
  overflow: hidden;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 18px 16px;
  cursor: pointer;
  user-select: none;

  .logo-mark {
    width: 34px;
    height: 34px;
    flex-shrink: 0;
    border-radius: 10px;
    background: linear-gradient(135deg, #10b981, #059669);
    color: #fff;
    font-size: 19px;
    font-weight: 800;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .logo-text {
    color: #f8fafc;
    font-size: 17px;
    font-weight: 700;
    white-space: nowrap;
  }
}

.nav {
  flex: 1;
  border: none;
  background: transparent;

  .el-menu-item {
    color: #94a3b8;
    margin: 2px 10px;
    border-radius: 10px;
    height: 46px;

    &:hover {
      background: rgb(255 255 255 / 0.06);
      color: #e2e8f0;
    }

    &.is-active {
      background: rgb(16 185 129 / 0.14);
      color: #6ee7b7;

      &::before {
        content: "";
        position: absolute;
        left: -10px;
        top: 10px;
        bottom: 10px;
        width: 3px;
        border-radius: 2px;
        background: #10b981;
      }
    }
  }
}

.aside-foot {
  padding: 14px;
  color: #64748b;
  cursor: pointer;
  text-align: center;

  &:hover {
    color: #e2e8f0;
  }
}

.main {
  padding: 0;
  background: var(--brand-50);
  overflow-y: auto;
}
</style>
