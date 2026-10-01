<template>
  <el-container class="layout">
    <el-aside width="200px" class="aside">
      <div class="logo">📚 智能知识库</div>
      <el-menu :default-active="$route.path" router>
        <el-menu-item index="/chat">
          <el-icon><ChatDotRound /></el-icon><span>问答对话</span>
        </el-menu-item>
        <el-menu-item index="/docs">
          <el-icon><Document /></el-icon><span>文档管理</span>
        </el-menu-item>
        <el-menu-item index="/history">
          <el-icon><Clock /></el-icon><span>历史会话</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-main class="main">
      <router-view />
    </el-main>
  </el-container>
</template>

<style>
html, body, #app { height: 100%; margin: 0; }
.layout { height: 100vh; }
.aside { background: #f5f7fa; border-right: 1px solid #e6e8eb; }
.logo { padding: 18px; font-size: 17px; font-weight: 700; }
.main { padding: 0; background: #fff; }
</style>
