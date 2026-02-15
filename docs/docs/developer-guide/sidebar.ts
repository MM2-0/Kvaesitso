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
  {
    text: 'Plugin Development',
    items: [
      {
        text: 'Get Started',
        link: '/docs/developer-guide/plugins/get-started',
      },
      {
        text: 'Plugin Types',
        items: [
          {
            text: 'Weather Provider',
            link: '/docs/developer-guide/plugins/plugin-types/weather',
          },
          {
            text: 'File Search Provider',
            link: '/docs/developer-guide/plugins/plugin-types/file-search',
          },
          {
            text: 'Contact Search Provider',
            link: '/docs/developer-guide/plugins/plugin-types/contact-search',
          },
          {
            text: 'Places Search Provider',
            link: '/docs/developer-guide/plugins/plugin-types/places-search',
          },
          {
            text: 'Calendar Provider',
            link: '/docs/developer-guide/plugins/plugin-types/calendar',
          },
        ],
      },
      {
        text: 'Metadata',
        link: '/docs/developer-guide/plugins/metadata',
      },
      {
        text: 'Plugin Settings',
        link: '/docs/developer-guide/plugins/settings',
      },
      {
        text: 'Access Control',
        link: '/docs/developer-guide/plugins/access-control',
      },
      {
        text: 'Reference',
        link: '/reference/index.html',
        target: '_blank',
      },
      {
        text: 'Migrations',
        items: [
          {
            text: 'Migrate to plugin SDK v2.x',
            link: '/docs/developer-guide/plugins/migrations/v2',
          },
          ,
        ],
      },
    ],
  },
]
