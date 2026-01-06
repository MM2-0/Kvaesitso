import type { DefaultTheme } from 'vitepress/types/default-theme'

export const UserGuideSidebar: DefaultTheme.SidebarItem[] = [
  {
    text: 'Get Started',
    link: '/docs/user-guide/',
  },
  {
    text: 'Frequently Asked Questions',
    link: '/docs/user-guide/faq',
  },
  {
    text: 'Concepts',
    items: [
      {
        text: 'Favorites',
        link: '/docs/user-guide/concepts/favorites',
      },
      {
        text: 'Tags',
        link: '/docs/user-guide/concepts/tags',
      },
      {
        text: 'Plugins',
        link: '/docs/user-guide/concepts/plugins',
      },
    ],
  },
  {
    text: 'Customization',
    items: [
      {
        text: 'Color Schemes',
        link: '/docs/user-guide/customization/color-schemes',
      },
      {
        text: 'Per-item Customization',
        link: '/docs/user-guide/customization/per-item-customization',
      },
      {
        text: 'Themed Icons',
        link: '/docs/user-guide/customization/themed-icons',
      },
    ],
  },
  {
    text: 'Integrations',
    items: [
      {
        text: 'Media Control',
        link: '/docs/user-guide/integrations/mediacontrol',
      },
      {
        text: 'Weather',
        link: '/docs/user-guide/integrations/weather',
      },
      {
        text: 'Feed',
        link: '/docs/user-guide/integrations/feed',
      },
    ],
  },
  {
    text: 'Search',
    items: [
      {
        text: 'Calculator',
        link: '/docs/user-guide/search/calculator',
      },
      {
        text: 'Unit Converter',
        link: '/docs/user-guide/search/unit-converter',
      },
      {
        text: 'Quick Actions',
        link: '/docs/user-guide/search/quickactions',
      },
      {
        text: 'Online Results',
        link: '/docs/user-guide/search/online-results',
      },
      {
        text: 'Filters',
        link: '/docs/user-guide/search/filters',
      },
    ],
  },
  {
    text: 'Widgets',
    items: [
      {
        text: 'Calendar Widget',
        link: '/docs/user-guide/widgets/calendar-widget',
      },
      {
        text: 'Clock Widget',
        link: '/docs/user-guide/widgets/clock',
      },
      {
        text: 'Favorites Widget',
        link: '/docs/user-guide/widgets/favorites-widget',
      },
      {
        text: 'Music Widget',
        link: '/docs/user-guide/widgets/music-widget',
      },
      {
        text: 'Notes Widget',
        link: '/docs/user-guide/widgets/notes-widget',
      },
      {
        text: 'Weather Widget',
        link: '/docs/user-guide/widgets/weather-widget',
      },
    ],
  },
  {
    text: 'Troubleshooting',
    items: [
      {
        text: 'Crash Reporter',
        link: '/docs/user-guide/troubleshooting/crashreporter',
      },
      {
        text: 'Reccuring Permission Requests',
        link: '/docs/user-guide/troubleshooting/granted-permissions',
      },
      {
        text: 'Restricted Settings on Android 13+',
        link: '/docs/user-guide/troubleshooting/restricted-settings',
      },
      {
        text: 'Launcher Cannot Be Updated',
        link: '/docs/user-guide/troubleshooting/update-not-installed',
      },
    ],
  },
]
