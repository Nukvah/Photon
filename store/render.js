// Renders the Play Store icon (512 x 512) and feature graphic (1024 x 500) into store/,
// both from store/icon.svg (written by icon.py).
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

  // The wave-and-photon mark from icon.svg, cropped to its bounds, beside the title on soft white.
  const mark = read('store/icon.svg')
    .replace(/<rect[^>]*\/>\n?/, '')
    .replace(/viewBox="[^"]*" width="\d+" height="\d+"/, 'viewBox="25.5 44 56 20" width="392" height="140"');
  page = await browser.newPage({ viewport: { width: 1024, height: 500 } });
  await page.setContent(`<style>
    @font-face { font-family: SG; src: url(${font}); }
    body { margin: 0; width: 1024px; height: 500px; overflow: hidden; background: #FAF7F2; position: relative; }
    .mark { position: absolute; left: 64px; top: 180px; }
    .text { position: absolute; left: 500px; top: 148px; }
    h1 { font-family: SG; font-size: 104px; margin: 0; letter-spacing: -2px; color: #2E3A8C; line-height: 1; }
    p { font-family: sans-serif; font-size: 34px; margin: 22px 0 0; color: #4A4F66; font-weight: 600; line-height: 1.25; }
  </style><body><div class="mark">${mark}</div>
  <div class="text"><h1>Photon</h1><p>Photos small enough<br>for any email.</p></div></body>`);
  await page.waitForTimeout(300);
  await page.screenshot({ path: `${repo}/store/feature-graphic.png` });
  await browser.close();
})();
