import { defineConfig } from 'vitepress'
import { UserGuideSidebar } from '../docs/user-guide/sidebar.ts'
import { DeveloperGuideSidebar } from '../docs/developer-guide/sidebar.ts'
import { ContributorGuideSidebar } from '../docs/contributor-guide/sidebar.ts'

// https://vitepress.dev/reference/site-config
export default defineConfig({
  title: 'Kvaesitso',
  description: 'A search-focused, free and open source launcher for Android',
  themeConfig: {
    logo: '/icon.png',
    // https://vitepress.dev/reference/default-theme-config
    nav: [
      { text: 'User Guide', link: '/docs/user-guide/' },
      { text: 'Developer Guide', link: '/docs/developer-guide/' },
      { text: 'Contributor Guide', link: '/docs/contributor-guide/' },
    ],

    sidebar: {
      '/docs/user-guide/': UserGuideSidebar,
      '/docs/developer-guide/': DeveloperGuideSidebar,
      '/docs/contributor-guide/': ContributorGuideSidebar,
    },

    socialLinks: [
      { icon: 'github', link: 'https://github.com/MM2-0/Kvaesitso' },
    ],
    search: {
      provider: 'local',
    },
    editLink: {
      pattern: 'https://github.com/MM2-0/Kvaesitso/edit/main/docs/:path',
    },
  },
  head: [['link', { rel: 'icon', href: '/icon.png' }]],
})
