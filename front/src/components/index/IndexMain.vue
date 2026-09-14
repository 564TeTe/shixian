<template>
  <main id="main" tabindex="-1">
    <router-view :key="$route.path" />
  </main>
</template>
<script>
import '@/views/teaching/teaching.scss'
export default { name: 'IndexMain' }
</script>
