<script setup>
  import DefaultTheme from 'vitepress/theme'
  import Footer from './Footer.vue'

  const { Layout } = DefaultTheme
</script>

<template>
  <Layout>
    <template #doc-bottom><Footer /></template>
  </Layout>
</template>
