import type { DefaultTheme } from 'vitepress/types/default-theme'

export const DeveloperGuideSidebar: DefaultTheme.SidebarItem[] = [
  {
    text: 'Developer Guide',
    link: '/docs/developer-guide/',
  },
  {
    text: 'Setup',
    link: '/docs/developer-guide/setup',
  },
  {
    text: 'External APIs',
    link: '/docs/developer-guide/external-apis/',
    items: [
      {
        text: 'Currency Exchange Rates',
        link: '/docs/developer-guide/external-apis/exchange-rates',
      },
      {
        text: 'Google Cloud Services',
        link: '/docs/developer-guide/external-apis/google',
      },
      {
        text: 'Weather Services',
        link: '/docs/developer-guide/external-apis/weather',
      },
      {
        text: 'Wikipedia',
        link: '/docs/developer-guide/external-apis/wikipedia',
      },
    ],
  },
  {
    text: 'Project Structure',
    items: [
      {
        text: 'Modules',
        link: '/docs/developer-guide/project-structure/modules',
      },
      {
        text: 'Libraries',
        link: '/docs/developer-guide/project-structure/libraries',
      },
    ],
  },
  {
    text: 'Integrations',
    items: [
      {
        text: 'Icon Packs',
        link: '/docs/developer-guide/integrations/icon-packs',
      },
    ],
  },
]
