// Renders the Play Store icon (512 x 512) and feature graphic (1024 x 500) into store/,
// from store/icon.svg and store/orb.svg (written by icon.py and orb.py).
// Run from the repo root with Playwright installed: node store/render.js .
const { chromium } = require('playwright');
const fs = require('fs');

const repo = process.argv[2] || '.';
const read = (f) => fs.readFileSync(`${repo}/${f}`, 'utf8');
const sized = (svg, w, h) => svg.replace(/width="\d+" height="\d+"/, `width="${w}" height="${h}"`);
const font = 'data:font/ttf;base64,' + fs.readFileSync(`${repo}/app/src/main/res/font/space_grotesk_bold.ttf`).toString('base64');

(async () => {
  const browser = await chromium.launch();

  let page = await browser.newPage({ viewport: { width: 512, height: 512 } });
  await page.setContent(`<body style="margin:0">${sized(read('store/icon.svg'), 512, 512)}</body>`);
  await page.screenshot({ path: `${repo}/store/icon-512.png` });

  // The orb artwork is 1024 square; show its middle band behind the title.
  page = await browser.newPage({ viewport: { width: 1024, height: 500 } });
  await page.setContent(`<style>
    @font-face { font-family: SG; src: url(${font}); }
    body { margin: 0; width: 1024px; height: 500px; overflow: hidden; background: #000; position: relative; }
    .art { position: absolute; left: -120px; top: -150px; }
    .text { position: absolute; left: 560px; top: 150px; color: #fff; }
    h1 { font-family: SG; font-size: 96px; margin: 0; letter-spacing: -2px; }
    p { font-family: sans-serif; font-size: 32px; margin: 10px 0 0; color: #C9D4F2; font-weight: 600; line-height: 1.25; }
  </style><body><div class="art">${sized(read('store/orb.svg'), 800, 800)}</div>
  <div class="text"><h1>Photon</h1><p>Photos small enough<br>for any email.</p></div></body>`);
  await page.waitForTimeout(300);
  await page.screenshot({ path: `${repo}/store/feature-graphic.png` });
  await browser.close();
})();
