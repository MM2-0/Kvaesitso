import React from 'react'
import styles from './styles.module.css'
import ThemedImage from '@theme/ThemedImage'

export default function HomepageScreenshots(): JSX.Element {
  return (
    <section className="padding-vert--xl">
      <div className="container">
        <div className="row">
          <div className="col col--4">
            <div className={styles.screenshot}>
              <img src="img/screenshot-1.png"></img>
            </div>
          </div>
          <div className="col col--4">
            <div className={styles.screenshot}>
              <img src="img/screenshot-2.png"></img>
            </div>
          </div>
          <div className="col col--4">
            <div className={styles.screenshot}>
              <img src="img/screenshot-3.png"></img>
            </div>
          </div>
        </div>
        <div className="row">
          <div className="col col--4">
            <div className={styles.screenshot}>
              <img src="img/screenshot-4.png"></img>
            </div>
          </div>
          <div className="col col--4">
            <div className={styles.screenshot}>
              <img src="img/screenshot-5.png"></img>
            </div>
          </div>
          <div className="col col--4">
            <div className={styles.screenshot}>
              <img src="img/screenshot-6.png"></img>
            </div>
          </div>
        </div>
      </div>
      <div className={styles.wallpaperLink}>
        Wallpaper: Johannes Plenio on{' '}
        <a target="_blank" href="https://unsplash.com/photos/ztiulja606U">
          Unsplash.com
        </a>
      </div>
    </section>
  )
}
