<template>
  <div class="page-heading">
    <div>
      <div class="eyebrow">{{ eyebrow }}</div>
      <h1>{{ title }}</h1>
      <p v-if="description">{{ description }}</p>
    </div>
    <div class="heading-actions"><slot /></div>
  </div>
</template>
<script>
export default {
  props: { title: String, description: String, eyebrow: { type: String, default: 'TEACHING WORKSPACE' } }
}
</script>
