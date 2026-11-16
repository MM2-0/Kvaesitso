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
    text: 'Telos',
    items: [
      { text: 'Telos at a glance', link: '/docs/user-guide/telos/' },
      { text: 'Help', link: '/docs/user-guide/telos/help' },
      { text: 'Linux Desktop', link: '/docs/user-guide/telos/linux-desktop' },
      { text: 'TelosAndroidVm', link: '/docs/user-guide/telos/android-vm' },
      {
        text: 'The launcher',
        collapsed: true,
        link: '/docs/user-guide/telos/launcher/',
        items: [
          { text: 'Search', link: '/docs/user-guide/telos/launcher/search' },
          { text: 'Home screen', link: '/docs/user-guide/telos/launcher/home-screen' },
          { text: 'Widgets and feed', link: '/docs/user-guide/telos/launcher/widgets-feed' },
          { text: 'Favorites and tags', link: '/docs/user-guide/telos/launcher/favorites-tags' },
          { text: 'Customization', link: '/docs/user-guide/telos/launcher/customization' },
          { text: 'Languages', link: '/docs/user-guide/telos/launcher/languages' },
          { text: 'Plugins and integrations', link: '/docs/user-guide/telos/launcher/plugins-integrations' },
          { text: 'Desktop mode and overlays', link: '/docs/user-guide/telos/launcher/desktop-and-overlays' },
          { text: 'Privacy and protection', link: '/docs/user-guide/telos/launcher/privacy-protection' },
          { text: 'Performance', link: '/docs/user-guide/telos/launcher/performance' },
          { text: 'Catalogue: search', link: '/docs/user-guide/telos/launcher/features/search-catalogue' },
          { text: 'Catalogue: home and appearance', link: '/docs/user-guide/telos/launcher/features/home-appearance-catalogue' },
          { text: 'Catalogue: system and plugins', link: '/docs/user-guide/telos/launcher/features/system-catalogue' },
        ],
      },
      {
        text: 'Phone',
        collapsed: true,
        link: '/docs/user-guide/telos/phone/',
        items: [
          { text: 'Calls, keypad and SIM', link: '/docs/user-guide/telos/phone/calls' },
          { text: 'Contacts', link: '/docs/user-guide/telos/phone/contacts' },
          { text: 'Recents and recording', link: '/docs/user-guide/telos/phone/recents-recording' },
          { text: 'Privacy and screening', link: '/docs/user-guide/telos/phone/privacy-screening' },
          { text: 'SIP and FRITZ!Box', link: '/docs/user-guide/telos/phone/sip-fritzbox' },
        ],
      },
      {
        text: 'Messages',
        collapsed: true,
        link: '/docs/user-guide/telos/messages/',
        items: [
          { text: 'SMS and MMS', link: '/docs/user-guide/telos/messages/sms-mms' },
        ],
      },
      {
        text: 'Ermis',
        link: '/docs/user-guide/telos/ermis/',
      },
      {
        text: 'Files',
        collapsed: true,
        link: '/docs/user-guide/telos/files/',
        items: [
          { text: 'Browsing and operations', link: '/docs/user-guide/telos/files/browsing' },
          { text: 'Root explorer', link: '/docs/user-guide/telos/files/root' },
          { text: 'Network and cloud', link: '/docs/user-guide/telos/files/network-cloud' },
          { text: 'Archives and vaults', link: '/docs/user-guide/telos/files/archives-vaults' },
        ],
      },
      {
        text: 'Search in the apps',
        link: '/docs/user-guide/telos/search-in-apps',
      },
      {
        text: 'Calculator',
        link: '/docs/user-guide/telos/calculator/',
      },
      {
        text: 'Screenshot',
        link: '/docs/user-guide/telos/screenshot/',
      },
      {
        text: 'Screen Recorder',
        link: '/docs/user-guide/telos/screen-recorder/',
      },
      {
        text: 'Voice Recorder',
        link: '/docs/user-guide/telos/voice-recorder/',
      },
      {
        text: 'Camera',
        link: '/docs/user-guide/telos/camera/',
      },
      {
        text: 'Notes',
        link: '/docs/user-guide/telos/notes/',
      },
      {
        text: 'Calendar',
        link: '/docs/user-guide/telos/calendar/',
      },
      {
        text: 'Downloads',
        collapsed: true,
        link: '/docs/user-guide/telos/downloads/',
        items: [
          { text: 'Torrents', link: '/docs/user-guide/telos/downloads/torrents' },
          { text: 'Video and audio sites', link: '/docs/user-guide/telos/downloads/media' },
        ],
      },
      {
        text: 'Clock',
        link: '/docs/user-guide/telos/clock',
      },
      {
        text: 'Termux (external)',
        link: '/docs/user-guide/telos/terminal',
      },
      {
        text: 'Network',
        collapsed: true,
        link: '/docs/user-guide/telos/network/',
        items: [
          { text: 'DNS', link: '/docs/user-guide/telos/network/dns' },
          { text: 'WireGuard', link: '/docs/user-guide/telos/network/wireguard' },
          { text: 'Firewall', link: '/docs/user-guide/telos/network/firewall' },
          { text: 'Blocklists and exceptions', link: '/docs/user-guide/telos/network/blocklists-bypass' },
          { text: 'Logs and settings', link: '/docs/user-guide/telos/network/logs-settings' },
        ],
      },
      {
        text: 'Viewer',
        collapsed: true,
        link: '/docs/user-guide/telos/photos/',
        items: [
          { text: 'Viewer and editor', link: '/docs/user-guide/telos/photos/viewer-editor' },
          { text: 'Document viewer', link: '/docs/user-guide/telos/photos/documents' },
          { text: 'Office editing and PDF search', link: '/docs/user-guide/telos/photos/office-editing' },
          { text: 'PDF tools', link: '/docs/user-guide/telos/photos/pdf-tools' },
        ],
      },
      {
        text: 'Media',
        link: '/docs/user-guide/telos/media/',
        collapsed: true,
        items: [
          { text: 'YouTube account', link: '/docs/user-guide/telos/media/youtube-account' },
        ],
      },
      {
        text: 'Music',
        collapsed: true,
        link: '/docs/user-guide/telos/music/',
        items: [
          { text: 'Library and playback', link: '/docs/user-guide/telos/music/library-playback' },
          { text: 'Tags, lyrics, scrobbling', link: '/docs/user-guide/telos/music/tags-lyrics-scrobbling' },
        ],
      },
      {
        text: 'Video',
        collapsed: true,
        link: '/docs/user-guide/telos/video/',
        items: [
          { text: 'Library and player', link: '/docs/user-guide/telos/video/library-player' },
          { text: 'Streams, torrents, subtitles', link: '/docs/user-guide/telos/video/streams-torrents-subtitles' },
        ],
      },
      { text: 'Radio', link: '/docs/user-guide/telos/radio/' },
      {
        text: 'Store',
        collapsed: true,
        link: '/docs/user-guide/telos/store/',
        items: [
          { text: 'Sources and updates', link: '/docs/user-guide/telos/store/sources-updates' },
        ],
      },
      { text: 'System', link: '/docs/user-guide/telos/system/' },
      {
        text: 'Smart Freeze',
        collapsed: true,
        link: '/docs/user-guide/telos/freeze/',
        items: [
          { text: 'Backends and profiles', link: '/docs/user-guide/telos/freeze/backends-profiles' },
        ],
      },
    ],
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
