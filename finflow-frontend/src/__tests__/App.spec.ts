import { describe, it, expect } from 'vitest'
import { createRouter, createMemoryHistory } from 'vue-router'

import { mount } from '@vue/test-utils'
import App from '../App.vue'

describe('App', () => {
  it('渲染路由出口内容', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/', component: { template: '<div>home</div>' } }],
    })
    await router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [router],
      },
    })
    expect(wrapper.text()).toContain('home')
    wrapper.unmount()
  })
})
