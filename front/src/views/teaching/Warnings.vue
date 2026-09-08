<template>
  <div v-if="items.length" class="warning-list">
    <el-alert
      v-for="(item, index) in items"
      :key="index"
      :title="label(item)"
      type="warning"
      :closable="false"
      show-icon
    />
  </div>
</template>
<script>
export default {
  props: { items: { type: Array, default: () => [] } },
  methods: {
    label(item) {
      return typeof item === 'string' ? item : item.message || item.msg || JSON.stringify(item)
    }
  }
}
</script>
