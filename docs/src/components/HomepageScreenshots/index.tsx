import React from 'react'
import styles from './styles.module.css'
import ThemedImage from '@theme/ThemedImage'

export default function HomepageScreenshots(): JSX.Element {
  return (
    <section>
      <div className="container padding-vert--xl">
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
    </section>
  )
}
