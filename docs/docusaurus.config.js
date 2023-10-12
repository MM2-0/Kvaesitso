// @ts-check
// Note: type annotations allow type checking and IDEs autocompletion

const lightCodeTheme = require('prism-react-renderer/themes/github')
const darkCodeTheme = require('prism-react-renderer/themes/dracula')

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'Kvaesitso',
  tagline: 'A search-focused, free and open source launcher for Android',
  url: 'https://kvaesitso.mm20.de',
  baseUrl: '/',
  onBrokenLinks: 'throw',
  onBrokenMarkdownLinks: 'warn',
  favicon: 'img/ic_launcher.png',

  // GitHub pages deployment config.
  // If you aren't using GitHub pages, you don't need these.
  organizationName: 'MM2-0', // Usually your GitHub org/user name.
  projectName: 'Kvaesitso', // Usually your repo name.

  // Even if you don't use internalization, you can use this field to set useful
  // metadata like html lang. For example, if your site is Chinese, you may want
  // to replace "en" with "zh-Hans".
  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  presets: [
    [
      'classic',
      /** @type {import('@docusaurus/preset-classic').Options} */
      ({
        docs: {
          sidebarPath: require.resolve('./sidebars.js'),
          editUrl:
            'https://github.com/MM2-0/Kvaesitso/tree/main/docs/',
        },
        blog: false,
        theme: {
          customCss: require.resolve('./src/css/custom.css'),
        },

      }),
    ],
  ],

  themeConfig:
    /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
    ({
      navbar: {
        title: 'Kvaesitso',
        logo: {
          alt: 'App Icon',
          src: 'img/ic_launcher.png',
        },
        items: [
          {
            type: 'doc',
            docId: 'user-guide/index',
            position: 'left',
            label: 'User Guide',
          },
          {
            type: 'doc',
            docId: 'developer-guide/index',
            position: 'left',
            label: 'Developer Guide',
          },
          {
            type: 'doc',
            docId: 'contributor-guide/index',
            position: 'left',
            label: 'Contributor Guide',
          },
          {
            href: 'https://github.com/MM2-0/Kvaesitso',
            label: 'GitHub',
            position: 'right',
          },
        ],
      },
      footer: {
        style: 'dark',
        links: [
          {
            title: 'Documentation',
            items: [
              {
                label: 'User guide',
                to: '/docs/user-guide',
              },
              {
                label: 'Developer guide',
                to: '/docs/developer-guide',
              },
              {
                label: 'Contributor guide',
                to: '/docs/contributor-guide',
              },
            ],
          },
          {
            title: 'Legal',
            items: [
              {
                label: 'Privacy policy',
                to: '/privacy-policy',
              },
              {
                label: 'License',
                href: '/license',
              },
            ],
          },
          {
            title: 'Links',
            items: [
              {
                label: 'GitHub',
                href: 'https://github.com/MM2-0/Kvaesitso',
              },
              {
                label: 'Telegram',
                href: 'https://t.me/Kvaesitso',
              },
            ],
          },
        ],
        copyright: `Copyright © ${new Date().getFullYear()} MM2-0 and the Kvaesitso contributors. Built with Docusaurus.`,
      },
      prism: {
        theme: lightCodeTheme,
        darkTheme: darkCodeTheme,
      },
      colorMode: {
        respectPrefersColorScheme: true
      },
    }),
}

module.exports = config
