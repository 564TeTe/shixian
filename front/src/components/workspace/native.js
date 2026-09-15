import Schema from 'async-validator'
import Icon from './Icon.vue'

const icons = { plus: 'plus', download: 'download', upload2: 'upload', upload: 'upload', search: 'search', refresh: 'clock', 'copy-document': 'copy', right: 'arrow', 'chat-dot-round': 'spark' }
export default function register(Vue) {
  Vue.component('sf-icon', Icon)
  Vue.component('sf-button', {
    inheritAttrs: false,
    props: { type: String, nativeType: { default: 'button' }, loading: Boolean, disabled: Boolean, icon: String },
    render(h) { return h('button', { class: ['btn', this.type === 'primary' ? 'primary' : this.type === 'text' ? 'text' : ''], attrs: { ...this.$attrs, type: this.nativeType, disabled: this.disabled || this.loading }, on: this.$listeners }, [this.icon ? h(Icon, { props: { name: icons[this.icon.replace('el-icon-', '')] || 'arrow' } }) : null, this.loading ? '处理中…' : this.$slots.default]) }
  })
  Vue.component('sf-alert', {
    props: { title: String, type: String },
    render(h) { return h('div', { class: ['notice', this.type === 'warning' || this.type === 'error' ? 'warning' : ''], attrs: { role: 'status' } }, [h(Icon, { props: { name: 'info' } }), h('span', this.title)]) }
  })
  Vue.component('sf-tag', { props: { type: String }, render(h) { return h('span', { class: ['badge', { success: 'green', warning: 'amber', danger: 'amber', info: 'gray' }[this.type] || 'blue'] }, this.$slots.default) } })
  Vue.component('sf-dialog', {
    props: { visible: Boolean, title: String, width: String, closeOnClickModal: { default: true } },
    watch: { visible() { this.sync() } }, mounted() { this.sync() },
    methods: { sync() { this.$nextTick(() => { if (this.visible && !this.$el.open) this.$el.showModal(); else if (!this.visible && this.$el.open) this.$el.close() }) }, close() { this.$emit('update:visible', false) } },
    render(h) { return h('dialog', { style: { width: this.width }, attrs: { 'aria-label': this.title }, on: { cancel: e => { e.preventDefault(); this.close() }, close: () => this.$emit('closed'), click: e => { if (e.target === this.$el && this.closeOnClickModal) { const r = this.$el.getBoundingClientRect(); if (e.clientX < r.left || e.clientX > r.right || e.clientY < r.top || e.clientY > r.bottom) this.close() } } } }, this.visible ? [h('div', { class: 'modal-heading' }, [h('h2', this.title), h('button', { class: 'btn icon-btn', attrs: { type: 'button', 'aria-label': '关闭' }, on: { click: this.close } }, [h(Icon, { props: { name: 'close' } })])]), h('div', { class: 'modal-body' }, this.$slots.default), this.$slots.footer ? h('div', { class: 'modal-actions' }, this.$slots.footer) : null] : []) }
  })
  Vue.component('sf-form', {
    props: { model: Object, rules: Object, disabled: Boolean }, data: () => ({ errors: {} }), provide() { return { workspaceForm: this } },
    methods: {
      clearValidate() { this.errors = {} },
      async validate() {
        this.errors = {}
        if (!this.rules) return true
        try {
          // async-validator 1.x completes through a callback, not a Promise.
          await new Promise((resolve, reject) => {
            new Schema(this.rules).validate(this.model || {}, (errors, fields) => {
              if (errors) reject({ errors, fields })
              else resolve()
            })
          })
          return true
        } catch (e) {
          this.errors = (e.errors || []).reduce((m, x) => { m[x.field] = x.message; return m }, {})
          if (!e.errors) this.errors._form = '表单校验失败，请刷新页面后重试。'
          this.$nextTick(() => {
            const field = this.$el.querySelector('[aria-invalid="true"] input, [aria-invalid="true"] select')
            if (field) field.focus()
          })
          throw e
        }
      }
    },
    render(h) { return h('form', { on: { submit: e => e.preventDefault() } }, [h('fieldset', { attrs: { disabled: this.disabled }, class: 'native-fieldset' }, [this.$slots.default, Object.keys(this.errors).length ? h('p', { class: 'form-error', attrs: { role: 'alert' } }, '请检查必填项及输入格式。') : null])]) }
  })
  Vue.component('sf-form-item', {
    inject: { workspaceForm: { default: null } }, props: { label: String, prop: String },
    render(h) { const error = this.workspaceForm && this.workspaceForm.errors[this.prop]; return h('label', { class: 'native-field', attrs: { 'aria-invalid': !!error } }, [this.label ? h('span', this.label) : null, this.$slots.default, error ? h('span', { class: 'form-error' }, error) : null]) }
  })
  Vue.component('sf-input', {
    inheritAttrs: false, props: { value: {}, type: String, rows: {}, disabled: Boolean },
    render(h) { const tag = this.type === 'textarea' ? 'textarea' : 'input'; return h(tag, { attrs: { ...this.$attrs, type: this.type || 'text', rows: this.rows, disabled: this.disabled }, domProps: { value: this.value == null ? '' : this.value }, on: { ...this.$listeners, input: e => this.$emit('input', e.target.value), change: e => this.$emit('change', e.target.value) } }) }
  })
  Vue.component('sf-input-number', {
    inheritAttrs: false, props: { value: {}, min: {}, max: {}, precision: { default: 0 } },
    render(h) { return h('input', { attrs: { ...this.$attrs, type: 'number', min: this.min, max: this.max, step: Math.pow(10, -Number(this.precision)) }, domProps: { value: this.value == null ? '' : this.value }, on: { input: e => this.$emit('input', e.target.value === '' ? undefined : Number(e.target.value)) } }) }
  })
  Vue.component('sf-option', { props: { value: {}, label: String }, render(h) { return h('option', { domProps: { value: this.value } }, this.label || this.$slots.default) } })
  Vue.component('sf-select', {
    inheritAttrs: false, props: { value: {}, placeholder: String, multiple: Boolean, disabled: Boolean, clearable: Boolean },
    mounted() { this.sync() }, updated() { this.sync() },
    methods: { sync() { Array.from(this.$el.options).forEach(o => { const v = o._value === undefined ? o.value : o._value; o.selected = this.multiple ? (this.value || []).some(x => String(x) === String(v)) : String(this.value == null ? '' : this.value) === String(v) }) } },
    render(h) { return h('select', { attrs: { ...this.$attrs, multiple: this.multiple, disabled: this.disabled, 'aria-label': this.$attrs['aria-label'] || this.placeholder }, on: { change: e => { const values = Array.from(e.target.selectedOptions).map(o => o._value === undefined ? o.value : o._value); const v = this.multiple ? values : values[0]; this.$emit('input', v); this.$emit('change', v) } } }, [!this.multiple && (this.placeholder || this.clearable) ? h('option', { attrs: { value: '' } }, this.placeholder || '全部') : null, this.$slots.default]) }
  })
  Vue.component('sf-column', { render() { return null } })
  Vue.component('sf-table', {
    props: { data: { default: () => [] }, emptyText: String, maxHeight: {} },
    render(h) { const columns = (this.$slots.default || []).filter(v => v.componentOptions && v.componentOptions.tag === 'sf-column'); const attr = v => ({ ...v.data.attrs, ...v.componentOptions.propsData });
      if (!this.data.length) return h('div', { class: 'empty' }, [h(Icon, { props: { name: 'search' } }), h('strong', this.emptyText || '暂无记录')]);
      return h('div', { class: 'table-wrap', style: { maxHeight: this.maxHeight ? this.maxHeight + 'px' : null } }, [h('table', [h('thead', [h('tr', columns.map(c => h('th', { attrs: { scope: 'col' } }, attr(c).label)))]), h('tbody', this.data.map((row, i) => h('tr', { key: row.id || i }, columns.map(c => h('td', c.data.scopedSlots && c.data.scopedSlots.default ? c.data.scopedSlots.default({ row, $index: i }) : String(row[attr(c).prop] == null ? '—' : row[attr(c).prop]))))))])]) }
  })
  Vue.component('sf-pagination', {
    props: { currentPage: Number, pageSize: Number, total: Number },
    methods: { go(n) { this.$emit('update:currentPage', n); this.$emit('current-change', n) } },
    render(h) { const last = Math.max(1, Math.ceil(this.total / this.pageSize)); return h('div', { class: 'table-footer' }, [h('span', '共 ' + this.total + ' 条'), h('div', { class: 'actions' }, [h('button', { class: 'btn', attrs: { disabled: this.currentPage <= 1 }, on: { click: () => this.go(this.currentPage - 1) } }, '上一页'), h('span', this.currentPage + ' / ' + last), h('button', { class: 'btn', attrs: { disabled: this.currentPage >= last }, on: { click: () => this.go(this.currentPage + 1) } }, '下一页')])]) }
  })
}
