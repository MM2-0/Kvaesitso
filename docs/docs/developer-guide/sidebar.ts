import type { DefaultTheme } from 'vitepress/types/default-theme'

export const DeveloperGuideSidebar: DefaultTheme.SidebarItem[] = [
  {
    text: 'Developer Guide',
    link: '/docs/developer-guide/',
  },
  {
    text: 'Virtual-app surfaces',
    link: '/docs/developer-guide/virtual-app-surfaces',
  },
  {
    text: 'Rondo icons',
    link: '/docs/developer-guide/rondo-icons',
  },
  {
    text: 'Adaptive UI contract',
    link: '/docs/developer-guide/adaptive-ui',
  },
  {
    text: 'Startup scripts and Shizuku',
    link: '/docs/developer-guide/startup-scripts',
  },
  {
    text: 'UI validation',
    link: '/docs/developer-guide/ui-validation',
  },
  {
    text: 'Dependabot alerts',
    link: '/docs/developer-guide/dependabot',
  },
  {
    text: 'Setup',
    link: '/docs/developer-guide/setup',
  },
  {
    text: 'Telos Desktop module',
    link: '/docs/developer-guide/desktop-linux',
  },
  {
    text: 'TelosAndroidVm (QEMU + scrcpy)',
    link: '/docs/developer-guide/windows-qemu-scrcpy',
  },
  {
    text: 'Telos Desktop cold start',
    link: '/docs/developer-guide/desktop-startup',
  },
  {
    text: 'Telos Desktop roadmap',
    link: '/docs/developer-guide/desktop-roadmap',
  },
  {
    text: 'Telos Helix chrome',
    link: '/docs/developer-guide/telos-helix',
  },
  {
    text: 'Telos Wayland Shell',
    link: '/docs/developer-guide/telos-wayland-shell',
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
      {
        text: 'Telos Files internals',
        link: '/docs/developer-guide/project-structure/telos-files',
      },
      {
        text: 'Telos Network internals',
        link: '/docs/developer-guide/project-structure/telos-network',
      },
      {
        text: 'Protection and optimization',
        link: '/docs/developer-guide/project-structure/protection-and-performance',
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
        text: 'SDK Changelogs',
        items: [
          {
            text: 'v2.3.0',
            link: '/docs/developer-guide/plugins/changelogs/v2.3.0',
          },
          {
            text: 'v2.2.0',
            link: '/docs/developer-guide/plugins/changelogs/v2.2.0',
          },
          {
            text: 'v2.1.0',
            link: '/docs/developer-guide/plugins/changelogs/v2.1.0',
          },
          {
            text: 'v2.0.0',
            link: '/docs/developer-guide/plugins/changelogs/v2.0.0',
          },

        ],
      },
      {
        text: 'Reference',
        link: '/reference/index.html',
        target: '_blank',
      },
    ],
  },
]
