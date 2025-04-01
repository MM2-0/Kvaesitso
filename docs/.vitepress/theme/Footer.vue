<template>
  <footer class="VPFooter">
    <div class="layout-footer">
      <div class="link-columns">
        <div class="column">
          <h4>Documentation</h4>
          <a href="/docs/user-guide">User Guide</a>
          <a href="/docs/developer-guide">Developer Guide</a>
          <a href="/docs/contributor-guide">Contributor Guide</a>
          <a href="/reference/index.html" target="_blank">SDK Reference</a>
        </div>
        <div class="column">
          <h4>Legal</h4>
          <a href="/privacy-policy">Privacy Policy</a>
          <a href="/license">License</a>
        </div>
        <div class="column">
          <h4>Links</h4>
          <a href="https://github.com/MM2-0/Kvaesitso" target="_blank"
            >Github</a
          >
          <a href="https://t.me/Kvaesitso" target="_blank">Telegram</a>
        </div>
      </div>
      <p class="copyright">
        Copyright © 2025 MM2-0 and the Kvaesitso contributors. Built with
        VitePress.
      </p>
    </div>
  </footer>
</template>
<style lang="scss">
  .layout-footer {
    border-top: 1px solid var(--vp-c-divider);
    padding: 2rem 2rem 4rem;
    max-width: 1100px;
    margin: 0 auto;
    .link-columns {
      display: grid;
      grid-template-columns: repeat(auto-fit, 200px);
      .column {
        display: flex;
        flex-direction: column;
        row-gap: 0.25rem;
        h4 {
          font-weight: 600;
          margin-bottom: 0.5rem;
        }
        a {
          color: var(--vp-c-text-2);
          text-decoration: none;
          &:hover {
            text-decoration: underline;
            color: var(--vp-c-brand);
          }
        }
      }
      gap: 2rem;
    }
    .copyright {
      margin-top: 2rem;
      font-size: 0.8em;
      color: var(--vp-c-text);
    }
  }
</style>
